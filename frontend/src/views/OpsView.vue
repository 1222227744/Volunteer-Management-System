<template>
  <section class="panel">
    <div class="panel-head">
      <h2>系统运维</h2>
      <p>管理基础配置项，记录故障处理过程，支撑系统日常运维治理。</p>
    </div>
    <div class="panel-body">
      <p v-if="message" :class="['notice', ok ? 'success' : 'error']" style="margin-bottom: 12px;">{{ message }}</p>

      <div class="card" style="margin-bottom: 14px;">
        <h3>系统配置</h3>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>配置项</th>
                <th>当前值</th>
                <th>说明</th>
                <th>最后更新</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="config in configs" :key="config.configKey">
                <td>
                  <strong>{{ config.configName }}</strong>
                </td>
                <td>
                  <button
                    v-if="isBooleanConfig(config)"
                    type="button"
                    :class="['switch-control', { active: booleanValue(config) }]"
                    :disabled="!config.editable"
                    :aria-pressed="booleanValue(config)"
                    :aria-label="config.configName + (booleanValue(config) ? '已启用' : '已停用')"
                    @click="toggleConfig(config)"
                  >
                    <span></span>
                  </button>
                  <input v-else v-model.trim="configForms[config.configKey]" :disabled="!config.editable" />
                </td>
                <td>{{ config.description }}</td>
                <td>{{ formatDate(config.updatedAt) }} / {{ config.updatedByName }}</td>
                <td>
                  <button class="btn ghost" :disabled="!config.editable" @click="saveConfig(config.configKey)">
                    保存
                  </button>
                </td>
              </tr>
              <tr v-if="!configs.length">
                <td colspan="5" class="muted">暂无配置项。</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div class="card" style="margin-bottom: 14px;">
        <h3>登记故障处理记录</h3>
        <div class="grid two">
          <div class="field">
            <label>故障标题</label>
            <input v-model.trim="incidentForm.title" placeholder="例如：通知推送连接异常" />
          </div>
          <div class="field">
            <label>严重程度</label>
            <select v-model="incidentForm.severity">
              <option value="LOW">{{ formatIncidentSeverity("LOW") }}</option>
              <option value="MEDIUM">{{ formatIncidentSeverity("MEDIUM") }}</option>
              <option value="HIGH">{{ formatIncidentSeverity("HIGH") }}</option>
              <option value="CRITICAL">{{ formatIncidentSeverity("CRITICAL") }}</option>
            </select>
          </div>
        </div>
        <div class="field" style="margin-top: 10px;">
          <label>故障描述</label>
          <textarea v-model.trim="incidentForm.description" placeholder="记录发生时间、影响范围和初步判断"></textarea>
        </div>
        <div class="grid two" style="margin-top: 10px;">
          <div class="field">
            <label>处理措施</label>
            <textarea v-model.trim="incidentForm.handlingMeasure" placeholder="记录排查过程和采取措施"></textarea>
          </div>
          <div class="field">
            <label>处理结果</label>
            <textarea v-model.trim="incidentForm.result" placeholder="记录恢复情况或后续动作"></textarea>
          </div>
        </div>
        <div class="stack" style="margin-top: 12px;">
          <button class="btn primary" @click="createIncident">登记故障</button>
        </div>
      </div>

      <div class="card">
        <div class="stack" style="justify-content: space-between;">
          <h3>故障处理记录</h3>
          <span class="tag">待处理 {{ incidentOpenCount }}</span>
        </div>
        <div class="list" style="margin-top: 12px;">
          <article v-for="incident in incidents" :key="incident.id" class="card">
            <div class="stack" style="justify-content: space-between;">
              <strong>{{ incident.title }}</strong>
              <span class="tag">{{ formatIncidentSeverity(incident.severity) }} / {{ formatIncidentStatus(incident.status) }}</span>
            </div>
            <p class="muted">{{ incident.description }}</p>
            <p class="muted">处理措施：{{ incident.handlingMeasure || "-" }}</p>
            <p class="muted">处理结果：{{ incident.result || "-" }}</p>
            <p class="muted">登记人：{{ incident.createdByName }}，登记时间：{{ formatDate(incident.createdAt) }}</p>
            <div class="grid two" style="margin-top: 10px;">
              <div class="field">
                <label>更新状态</label>
                <select v-model="incidentForms[incident.id].status">
                  <option value="OPEN">{{ formatIncidentStatus("OPEN") }}</option>
                  <option value="PROCESSING">{{ formatIncidentStatus("PROCESSING") }}</option>
                  <option value="RESOLVED">{{ formatIncidentStatus("RESOLVED") }}</option>
                  <option value="CLOSED">{{ formatIncidentStatus("CLOSED") }}</option>
                </select>
              </div>
              <div class="field">
                <label>处理措施</label>
                <input v-model.trim="incidentForms[incident.id].handlingMeasure" />
              </div>
            </div>
            <div class="field" style="margin-top: 10px;">
              <label>处理结果</label>
              <input v-model.trim="incidentForms[incident.id].result" />
            </div>
            <div class="stack" style="margin-top: 10px;">
              <button class="btn ghost" @click="updateIncident(incident.id)">更新记录</button>
            </div>
          </article>
          <p v-if="!incidents.length" class="notice">暂无故障处理记录。</p>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup>
import { onMounted, reactive, ref } from "vue";
import { opsApi } from "../api";
import { formatIncidentSeverity, formatIncidentStatus } from "../utils/labels";

const configs = ref([]);
const incidents = ref([]);
const incidentOpenCount = ref(0);
const configForms = reactive({});
const incidentForms = reactive({});
const message = ref("");
const ok = ref(false);

const incidentForm = reactive({
  title: "",
  description: "",
  severity: "MEDIUM",
  handlingMeasure: "",
  result: ""
});

function formatDate(raw) {
  if (!raw) return "-";
  return raw.replace("T", " ").slice(0, 16);
}

function configValueText(config) {
  return String(configForms[config.configKey] ?? "").trim().toLowerCase();
}

function isBooleanConfig(config) {
  const value = configValueText(config);
  return value === "true" || value === "false";
}

function booleanValue(config) {
  return configValueText(config) === "true";
}

function toggleConfig(config) {
  if (!config.editable) {
    return;
  }
  configForms[config.configKey] = booleanValue(config) ? "false" : "true";
}

async function loadConfigs() {
  configs.value = await opsApi.configs();
  configs.value.forEach((config) => {
    configForms[config.configKey] = config.configValue;
  });
}

function syncIncidentForms() {
  incidents.value.forEach((incident) => {
    incidentForms[incident.id] = {
      status: incident.status,
      handlingMeasure: incident.handlingMeasure || "",
      result: incident.result || ""
    };
  });
}

async function loadIncidents() {
  const data = await opsApi.incidents();
  incidentOpenCount.value = data.openCount;
  incidents.value = data.items;
  syncIncidentForms();
}

async function loadData() {
  await Promise.all([loadConfigs(), loadIncidents()]);
}

async function saveConfig(configKey) {
  message.value = "";
  ok.value = false;
  try {
    await opsApi.updateConfig(configKey, configForms[configKey]);
    ok.value = true;
    message.value = "系统配置已更新";
    await loadConfigs();
  } catch (err) {
    message.value = err.message;
  }
}

async function createIncident() {
  message.value = "";
  ok.value = false;
  if (!incidentForm.title || !incidentForm.description) {
    message.value = "请填写故障标题和描述";
    return;
  }
  try {
    await opsApi.createIncident({
      ...incidentForm,
      status: "OPEN"
    });
    ok.value = true;
    message.value = "故障处理记录已登记";
    incidentForm.title = "";
    incidentForm.description = "";
    incidentForm.severity = "MEDIUM";
    incidentForm.handlingMeasure = "";
    incidentForm.result = "";
    await loadIncidents();
  } catch (err) {
    message.value = err.message;
  }
}

async function updateIncident(incidentId) {
  message.value = "";
  ok.value = false;
  try {
    await opsApi.updateIncident(incidentId, incidentForms[incidentId]);
    ok.value = true;
    message.value = "故障处理记录已更新";
    await loadIncidents();
  } catch (err) {
    message.value = err.message;
  }
}

onMounted(async () => {
  try {
    await loadData();
  } catch (err) {
    message.value = err.message;
  }
});
</script>
