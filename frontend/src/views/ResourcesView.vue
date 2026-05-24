<template>
  <section class="panel">
    <div class="panel-head">
      <h2>公益资源对接</h2>
      <p>登记公益资源与帮扶需求，建立匹配关系并跟进资源流转状态。</p>
    </div>
    <div class="panel-body">
      <p v-if="message" :class="['notice', ok ? 'success' : 'error']" style="margin-bottom: 12px;">
        {{ message }}
      </p>

      <div v-if="canManage" class="grid two" style="margin-bottom: 14px;">
        <div class="card">
          <h3>登记公益资源</h3>
          <div class="grid two">
            <div class="field">
              <label>资源名称</label>
              <input v-model.trim="resourceForm.name" placeholder="例如：儿童绘本" />
            </div>
            <div class="field">
              <label>资源类别</label>
              <input v-model.trim="resourceForm.category" placeholder="物资、资金、服务等" />
            </div>
          </div>
          <div class="grid two" style="margin-top: 10px;">
            <div class="field">
              <label>来源</label>
              <input v-model.trim="resourceForm.source" placeholder="捐赠方或来源说明" />
            </div>
            <div class="field">
              <label>数量</label>
              <input v-model.number="resourceForm.quantity" type="number" min="1" />
            </div>
          </div>
          <div class="grid two" style="margin-top: 10px;">
            <div class="field">
              <label>单位</label>
              <input v-model.trim="resourceForm.unit" placeholder="件、册、元等" />
            </div>
            <div class="field">
              <label>可用范围</label>
              <input v-model.trim="resourceForm.availableScope" placeholder="适用活动或对象" />
            </div>
          </div>
          <button class="btn primary" style="margin-top: 12px;" @click="createResource">保存资源</button>
        </div>

        <div class="card">
          <h3>登记帮扶需求</h3>
          <div class="grid two">
            <div class="field">
              <label>需求标题</label>
              <input v-model.trim="needForm.title" placeholder="例如：社区阅读角补充图书" />
            </div>
            <div class="field">
              <label>需求对象</label>
              <input v-model.trim="needForm.requester" placeholder="受助对象或机构" />
            </div>
          </div>
          <div class="grid two" style="margin-top: 10px;">
            <div class="field">
              <label>数量</label>
              <input v-model.number="needForm.quantity" type="number" min="1" />
            </div>
            <div class="field">
              <label>单位</label>
              <input v-model.trim="needForm.unit" placeholder="件、册、元等" />
            </div>
          </div>
          <div class="grid two" style="margin-top: 10px;">
            <div class="field">
              <label>地点</label>
              <input v-model.trim="needForm.location" />
            </div>
            <div class="field">
              <label>期望时间</label>
              <input v-model="needForm.requiredAt" type="datetime-local" />
            </div>
          </div>
          <div class="field" style="margin-top: 10px;">
            <label>需求内容</label>
            <textarea v-model.trim="needForm.content" placeholder="说明帮扶内容、数量、时间要求和注意事项"></textarea>
          </div>
          <button class="btn primary" style="margin-top: 12px;" @click="createNeed">保存需求</button>
        </div>
      </div>

      <div v-if="canManage" class="card" style="margin-bottom: 14px;">
        <h3>建立资源需求匹配</h3>
        <div class="grid three">
          <div class="field">
            <label>选择资源</label>
            <select v-model="matchForm.resourceId">
              <option value="">请选择可用资源</option>
              <option v-for="item in availableResources" :key="item.id" :value="item.id">
                {{ item.name }}（{{ item.quantity }}{{ item.unit || "" }}）
              </option>
            </select>
          </div>
          <div class="field">
            <label>选择需求</label>
            <select v-model="matchForm.needId">
              <option value="">请选择待匹配需求</option>
              <option v-for="item in openNeeds" :key="item.id" :value="item.id">
                {{ item.title }}（{{ item.quantity }}{{ item.unit || "" }}）
              </option>
            </select>
          </div>
          <div class="field">
            <label>分配数量</label>
            <input v-model.number="matchForm.allocatedQuantity" type="number" min="1" />
          </div>
        </div>
        <div class="field" style="margin-top: 10px;">
          <label>进度说明</label>
          <input v-model.trim="matchForm.progressNote" placeholder="例如：已确认资源，等待统一发放" />
        </div>
        <button class="btn primary" style="margin-top: 12px;" @click="createMatch">建立匹配</button>
      </div>

      <div class="grid three">
        <div class="card">
          <h3>资源列表</h3>
          <div class="list">
            <article v-for="item in resources" :key="item.id" class="card">
              <div class="stack" style="justify-content: space-between;">
                <strong>{{ item.name }}</strong>
                <span class="tag">{{ formatResourceStatus(item.status) }}</span>
              </div>
              <p class="muted">类别：{{ item.category }} | 来源：{{ item.source }}</p>
              <p class="muted">数量：{{ item.quantity }}{{ item.unit || "" }}</p>
              <p class="muted">可用范围：{{ item.availableScope || "-" }}</p>
            </article>
            <p v-if="!resources.length" class="notice">暂无公益资源。</p>
          </div>
        </div>

        <div class="card">
          <h3>需求列表</h3>
          <div class="list">
            <article v-for="item in needs" :key="item.id" class="card">
              <div class="stack" style="justify-content: space-between;">
                <strong>{{ item.title }}</strong>
                <span class="tag">{{ formatNeedStatus(item.status) }}</span>
              </div>
              <p class="muted">对象：{{ item.requester }}</p>
              <p class="muted">地点：{{ item.location }} | 数量：{{ item.quantity }}{{ item.unit || "" }}</p>
              <p class="muted">期望时间：{{ formatDate(item.requiredAt) }}</p>
              <p class="muted">{{ item.content }}</p>
            </article>
            <p v-if="!needs.length" class="notice">暂无帮扶需求。</p>
          </div>
        </div>

        <div class="card">
          <h3>匹配推进</h3>
          <div class="list">
            <article v-for="item in matches" :key="item.id" class="card">
              <div class="stack" style="justify-content: space-between;">
                <strong>{{ item.resourceName }} → {{ item.needTitle }}</strong>
                <span class="tag">{{ formatMatchStatus(item.status) }}</span>
              </div>
              <p class="muted">分配数量：{{ item.allocatedQuantity }}</p>
              <p class="muted">进度说明：{{ item.progressNote || "-" }}</p>
              <div v-if="canManage && !['COMPLETED', 'CANCELLED'].includes(item.status)" class="stack" style="margin-top: 10px;">
                <select v-model="matchStatuses[item.id]">
                  <option value="MATCHED">{{ formatMatchStatus("MATCHED") }}</option>
                  <option value="ALLOCATED">{{ formatMatchStatus("ALLOCATED") }}</option>
                  <option value="COMPLETED">{{ formatMatchStatus("COMPLETED") }}</option>
                  <option value="CANCELLED">{{ formatMatchStatus("CANCELLED") }}</option>
                </select>
                <button class="btn warn" @click="updateMatchStatus(item)">更新</button>
              </div>
            </article>
            <p v-if="!matches.length" class="notice">暂无资源匹配记录。</p>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { resourceApi } from "../api";
import { authState } from "../stores/auth";
import { formatMatchStatus, formatNeedStatus, formatResourceStatus } from "../utils/labels";

const resources = ref([]);
const needs = ref([]);
const matches = ref([]);
const matchStatuses = reactive({});
const message = ref("");
const ok = ref(false);

const resourceForm = reactive({
  name: "",
  category: "",
  source: "",
  quantity: 1,
  unit: "",
  availableScope: ""
});

const needForm = reactive({
  title: "",
  requester: "",
  content: "",
  quantity: 1,
  unit: "",
  location: "",
  requiredAt: ""
});

const matchForm = reactive({
  resourceId: "",
  needId: "",
  allocatedQuantity: 1,
  progressNote: ""
});

const canManage = computed(
  () => authState.user?.role === "ADMIN" || authState.user?.role === "ORGANIZER"
);
const availableResources = computed(() => resources.value.filter((item) => item.status === "AVAILABLE"));
const openNeeds = computed(() => needs.value.filter((item) => item.status === "OPEN"));

function formatDate(raw) {
  if (!raw) return "-";
  return raw.replace("T", " ").slice(0, 16);
}

function toPayloadDateTime(value) {
  return value ? `${value}:00` : null;
}

async function loadBoard() {
  const data = await resourceApi.board();
  resources.value = data.resources || [];
  needs.value = data.needs || [];
  matches.value = data.matches || [];
  matches.value.forEach((item) => {
    matchStatuses[item.id] = item.status;
  });
}

function showSuccess(text) {
  ok.value = true;
  message.value = text;
}

function showError(err) {
  ok.value = false;
  message.value = err.message;
}

async function createResource() {
  message.value = "";
  try {
    await resourceApi.createResource({ ...resourceForm, quantity: Number(resourceForm.quantity) });
    Object.assign(resourceForm, { name: "", category: "", source: "", quantity: 1, unit: "", availableScope: "" });
    showSuccess("公益资源已登记");
    await loadBoard();
  } catch (err) {
    showError(err);
  }
}

async function createNeed() {
  message.value = "";
  try {
    await resourceApi.createNeed({
      ...needForm,
      quantity: Number(needForm.quantity),
      requiredAt: toPayloadDateTime(needForm.requiredAt)
    });
    Object.assign(needForm, { title: "", requester: "", content: "", quantity: 1, unit: "", location: "", requiredAt: "" });
    showSuccess("帮扶需求已登记");
    await loadBoard();
  } catch (err) {
    showError(err);
  }
}

async function createMatch() {
  message.value = "";
  try {
    await resourceApi.createMatch({
      resourceId: Number(matchForm.resourceId),
      needId: Number(matchForm.needId),
      allocatedQuantity: Number(matchForm.allocatedQuantity),
      progressNote: matchForm.progressNote
    });
    Object.assign(matchForm, { resourceId: "", needId: "", allocatedQuantity: 1, progressNote: "" });
    showSuccess("资源需求匹配已建立");
    await loadBoard();
  } catch (err) {
    showError(err);
  }
}

async function updateMatchStatus(item) {
  message.value = "";
  try {
    await resourceApi.updateMatchStatus(item.id, {
      status: matchStatuses[item.id],
      progressNote: item.progressNote || ""
    });
    showSuccess("匹配状态已更新");
    await loadBoard();
  } catch (err) {
    showError(err);
  }
}

onMounted(async () => {
  try {
    await loadBoard();
  } catch (err) {
    showError(err);
  }
});
</script>
