<template>
  <section class="panel">
    <div class="panel-head">
      <h2>消息通知</h2>
      <p>查看系统公告通知与反馈处理提醒。</p>
    </div>
    <div class="panel-body">
      <p v-if="error" class="notice error">{{ error }}</p>
      <p v-else class="notice" style="margin-bottom: 12px;">
        未读消息：{{ unreadCount }} | 实时推送：{{ realtimeStatus }}
      </p>
      <div class="list">
        <article v-for="item in items" :key="item.id" class="card">
          <div class="stack" style="justify-content: space-between;">
            <h3>{{ item.title }}</h3>
            <span class="tag">{{ item.readFlag ? "已读" : "未读" }}</span>
          </div>
          <p class="muted">{{ item.content }}</p>
          <div class="stack" style="justify-content: space-between; margin-top: 6px;">
            <small class="muted">{{ formatDate(item.createdAt) }}</small>
            <button v-if="!item.readFlag" class="btn ghost" @click="markRead(item.id)">标记已读</button>
          </div>
        </article>
        <p v-if="!items.length" class="notice">暂无通知。</p>
      </div>
    </div>
  </section>

  <section v-if="isAdmin" class="panel">
    <div class="panel-head">
      <h2>外部通知任务</h2>
      <p>查看邮件、短信外部发送结果；启用 SMTP 后邮件会真实发送，短信仍为模拟发送。</p>
    </div>
    <div class="panel-body">
      <div class="stack" style="justify-content: space-between; margin-bottom: 12px;">
        <p class="notice" style="margin: 0;">失败任务：{{ failedTaskCount }}</p>
        <button class="btn ghost" :disabled="!failedTaskCount || retryingAll" @click="retryFailedTasks">
          {{ retryingAll ? "正在重试" : "重试全部失败任务" }}
        </button>
      </div>
      <p v-if="externalError" class="notice error">{{ externalError }}</p>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>任务</th>
              <th>接收方</th>
              <th>状态</th>
              <th>重试次数</th>
              <th>最后处理</th>
              <th>失败原因</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="task in externalTasks" :key="task.id">
              <td>
                <strong>{{ formatExternalNotificationChannel(task.channel) }}</strong>
                <span class="muted">#{{ task.id }} / 用户 {{ task.userId }}</span>
                <span class="muted">{{ task.title }}</span>
              </td>
              <td>{{ task.recipient || "未配置" }}</td>
              <td>
                <span class="tag">{{ formatExternalNotificationStatus(task.status) }}</span>
              </td>
              <td>{{ task.retryCount }} / {{ task.maxRetries }}</td>
              <td>{{ formatDate(task.lastTriedAt || task.createdAt) }}</td>
              <td>{{ task.lastError || "-" }}</td>
              <td>
                <button
                  class="btn ghost"
                  :disabled="task.status !== 'FAILED' || retryingTaskId === task.id"
                  @click="retryTask(task.id)"
                >
                  {{ retryingTaskId === task.id ? "重试中" : "重试" }}
                </button>
              </td>
            </tr>
            <tr v-if="!externalTasks.length">
              <td colspan="7" class="muted">暂无外部通知任务。</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { notificationApi } from "../api";
import { authState } from "../stores/auth";
import {
  formatExternalNotificationChannel,
  formatExternalNotificationStatus
} from "../utils/labels";

const items = ref([]);
const externalTasks = ref([]);
const unreadCount = ref(0);
const error = ref("");
const externalError = ref("");
const realtimeStatus = ref("未连接");
const retryingTaskId = ref(null);
const retryingAll = ref(false);
let socket = null;

const isAdmin = computed(() => authState.user?.role === "ADMIN");
const failedTaskCount = computed(() => externalTasks.value.filter((task) => task.status === "FAILED").length);

function formatDate(raw) {
  if (!raw) return "-";
  return raw.replace("T", " ").slice(0, 16);
}

async function loadData() {
  const data = await notificationApi.my();
  items.value = data.items;
  unreadCount.value = data.unreadCount;
}

async function loadExternalTasks() {
  if (!isAdmin.value) {
    externalTasks.value = [];
    return;
  }
  const data = await notificationApi.externalTasks();
  externalTasks.value = data;
}

async function markRead(id) {
  try {
    await notificationApi.markRead(id);
    await loadData();
  } catch (err) {
    error.value = err.message;
  }
}

async function retryTask(taskId) {
  externalError.value = "";
  retryingTaskId.value = taskId;
  try {
    await notificationApi.retryExternalTask(taskId);
    await loadExternalTasks();
  } catch (err) {
    externalError.value = err.message;
  } finally {
    retryingTaskId.value = null;
  }
}

async function retryFailedTasks() {
  externalError.value = "";
  retryingAll.value = true;
  try {
    await notificationApi.retryFailedExternalTasks();
    await loadExternalTasks();
  } catch (err) {
    externalError.value = err.message;
  } finally {
    retryingAll.value = false;
  }
}

function connectRealtime() {
  if (!authState.token || socket) {
    return;
  }
  socket = new WebSocket(notificationApi.websocketUrl(authState.token));
  socket.onopen = () => {
    realtimeStatus.value = "已连接";
  };
  socket.onmessage = async () => {
    await loadData();
    await loadExternalTasks();
  };
  socket.onerror = () => {
    realtimeStatus.value = "连接异常";
  };
  socket.onclose = () => {
    realtimeStatus.value = "已断开";
    socket = null;
  };
}

onMounted(async () => {
  try {
    await loadData();
    await loadExternalTasks();
    connectRealtime();
  } catch (err) {
    error.value = err.message;
  }
});

onBeforeUnmount(() => {
  if (socket) {
    socket.close();
    socket = null;
  }
});
</script>
