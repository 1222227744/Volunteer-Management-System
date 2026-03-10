<template>
  <section class="panel">
    <div class="panel-head">
      <h2>消息通知</h2>
      <p>查看系统公告通知与反馈处理提醒。</p>
    </div>
    <div class="panel-body">
      <p v-if="error" class="notice error">{{ error }}</p>
      <p v-else class="notice" style="margin-bottom: 12px;">未读消息：{{ unreadCount }}</p>
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
</template>

<script setup>
import { onMounted, ref } from "vue";
import { notificationApi } from "../api";

const items = ref([]);
const unreadCount = ref(0);
const error = ref("");

function formatDate(raw) {
  if (!raw) return "-";
  return raw.replace("T", " ").slice(0, 16);
}

async function loadData() {
  const data = await notificationApi.my();
  items.value = data.items;
  unreadCount.value = data.unreadCount;
}

async function markRead(id) {
  try {
    await notificationApi.markRead(id);
    await loadData();
  } catch (err) {
    error.value = err.message;
  }
}

onMounted(async () => {
  try {
    await loadData();
  } catch (err) {
    error.value = err.message;
  }
});
</script>
