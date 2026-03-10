<template>
  <section class="panel">
    <div class="panel-head">
      <h2>操作审计日志</h2>
      <p>记录关键管理操作，便于追溯角色变更、审核行为和活动执行动作。</p>
    </div>
    <div class="panel-body">
      <p v-if="message" :class="['notice', messageType]" style="margin-bottom: 12px;">{{ message }}</p>

      <div class="card" style="margin-bottom: 14px;">
        <div class="grid two">
          <div class="field">
            <label>动作类型</label>
            <select v-model="filters.action">
              <option value="">全部</option>
              <option value="USER_ROLE_UPDATED">USER_ROLE_UPDATED</option>
              <option value="ACTIVITY_CREATED">ACTIVITY_CREATED</option>
              <option value="ACTIVITY_STATUS_UPDATED">ACTIVITY_STATUS_UPDATED</option>
              <option value="ACTIVITY_CHECKIN">ACTIVITY_CHECKIN</option>
              <option value="SERVICE_RECORD_CREATED">SERVICE_RECORD_CREATED</option>
              <option value="CONTENT_REVIEWED">CONTENT_REVIEWED</option>
              <option value="FEEDBACK_RESOLVED">FEEDBACK_RESOLVED</option>
              <option value="ANNOUNCEMENT_CREATED">ANNOUNCEMENT_CREATED</option>
            </select>
          </div>
          <div class="field">
            <label>关键字</label>
            <input v-model.trim="filters.keyword" placeholder="操作人、目标、详情关键字" />
          </div>
        </div>
        <div class="grid two" style="margin-top: 10px;">
          <div class="field">
            <label>操作人</label>
            <input v-model.trim="filters.operatorName" placeholder="按操作人昵称筛选" />
          </div>
          <div class="field">
            <label>目标类型</label>
            <select v-model="filters.targetType">
              <option value="">全部</option>
              <option value="USER">USER</option>
              <option value="ACTIVITY">ACTIVITY</option>
              <option value="ACTIVITY_REGISTRATION">ACTIVITY_REGISTRATION</option>
              <option value="SERVICE_RECORD">SERVICE_RECORD</option>
              <option value="CONTENT">CONTENT</option>
              <option value="FEEDBACK">FEEDBACK</option>
              <option value="ANNOUNCEMENT">ANNOUNCEMENT</option>
            </select>
          </div>
        </div>
        <div class="grid three" style="margin-top: 10px;">
          <div class="field">
            <label>开始时间</label>
            <input v-model="filters.from" type="datetime-local" />
          </div>
          <div class="field">
            <label>结束时间</label>
            <input v-model="filters.to" type="datetime-local" />
          </div>
          <div class="field">
            <label>条数上限</label>
            <select v-model.number="filters.limit">
              <option :value="60">60</option>
              <option :value="120">120</option>
              <option :value="200">200</option>
              <option :value="300">300</option>
            </select>
          </div>
        </div>
        <div class="stack" style="margin-top: 12px;">
          <button class="btn primary" :disabled="loading" @click="searchLogs">
            {{ loading ? "查询中..." : "查询日志" }}
          </button>
          <button class="btn ghost" :disabled="exporting" @click="exportLogs">
            {{ exporting ? "导出中..." : "导出 CSV" }}
          </button>
        </div>
      </div>

      <div class="list">
        <article class="card" v-for="item in logs" :key="item.id">
          <div class="stack" style="justify-content: space-between; align-items: center;">
            <div>
              <strong>{{ item.action }}</strong>
              <p class="muted" style="margin: 6px 0 0;">
                {{ item.operatorName }}（{{ item.operatorRole }} / ID {{ item.operatorId }}）
              </p>
            </div>
            <span class="tag">{{ item.targetType }}#{{ item.targetId }}</span>
          </div>
          <p class="muted" style="margin-top: 10px;">{{ item.detail }}</p>
          <div class="stack muted" style="justify-content: space-between; margin-top: 8px; font-size: 12px;">
            <span>IP: {{ item.ipAddress }}</span>
            <span>{{ formatDate(item.createdAt) }}</span>
          </div>
        </article>
        <p v-if="!logs.length && !loading" class="notice">暂无符合条件的审计日志。</p>
      </div>

      <div class="card" style="margin-top: 14px;">
        <div class="stack" style="justify-content: space-between; align-items: center;">
          <p class="muted">第 {{ pagination.page }} 页，每页 {{ pagination.size }} 条，共 {{ pagination.total }} 条</p>
          <div class="stack">
            <button class="btn ghost" :disabled="loading || pagination.page <= 1" @click="prevPage">上一页</button>
            <button
              class="btn ghost"
              :disabled="loading || pagination.page >= totalPages"
              @click="nextPage"
            >
              下一页
            </button>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup>
import { onMounted, reactive, ref } from "vue";
import { auditApi } from "../api";

const logs = ref([]);
const loading = ref(false);
const exporting = ref(false);
const message = ref("");
const messageType = ref("success");

const filters = reactive({
  action: "",
  keyword: "",
  operatorName: "",
  targetType: "",
  from: "",
  to: "",
  limit: 120
});
const pagination = reactive({
  page: 1,
  size: 20,
  total: 0
});
const totalPages = ref(1);

function formatDate(raw) {
  if (!raw) return "-";
  return raw.replace("T", " ").slice(0, 19);
}

async function loadLogs() {
  loading.value = true;
  message.value = "";
  try {
    const data = await auditApi.paged({
      ...filters,
      page: pagination.page,
      size: pagination.size
    });
    logs.value = data.items;
    pagination.total = data.total;
    pagination.page = data.page;
    pagination.size = data.size;
    totalPages.value = Math.max(1, Math.ceil(data.total / data.size));
  } catch (err) {
    messageType.value = "error";
    message.value = err.message;
  } finally {
    loading.value = false;
  }
}

async function searchLogs() {
  pagination.page = 1;
  await loadLogs();
}

async function prevPage() {
  if (pagination.page <= 1) {
    return;
  }
  pagination.page -= 1;
  await loadLogs();
}

async function nextPage() {
  if (pagination.page >= totalPages.value) {
    return;
  }
  pagination.page += 1;
  await loadLogs();
}

async function exportLogs() {
  exporting.value = true;
  message.value = "";
  try {
    await auditApi.exportCsv({
      ...filters,
      limit: 2000
    });
    messageType.value = "success";
    message.value = "CSV 导出成功";
  } catch (err) {
    messageType.value = "error";
    message.value = err.message;
  } finally {
    exporting.value = false;
  }
}

onMounted(loadLogs);
</script>
