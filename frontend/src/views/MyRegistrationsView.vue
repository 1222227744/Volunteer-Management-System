<template>
  <section class="panel">
    <div class="panel-head">
      <h2>我的活动报名</h2>
      <p>查看你的报名、签到与完成状态。</p>
    </div>
    <div class="panel-body">
      <p v-if="error" class="notice error">{{ error }}</p>
      <div v-else class="list">
        <article v-for="item in registrations" :key="item.registrationId" class="card">
          <div class="stack" style="justify-content: space-between;">
            <h3>{{ item.activityTitle }}</h3>
            <span class="tag">{{ item.status }}</span>
          </div>
          <p class="muted">时间：{{ formatDate(item.startTime) }} ~ {{ formatDate(item.endTime) }}</p>
          <p class="muted">报名于：{{ formatDate(item.registeredAt) }}</p>
          <p v-if="item.checkInAt" class="muted">签到时间：{{ formatDate(item.checkInAt) }}</p>
        </article>
        <p v-if="!registrations.length" class="notice">你还没有任何报名记录。</p>
      </div>
    </div>
  </section>
</template>

<script setup>
import { onMounted, ref } from "vue";
import { activityApi } from "../api";

const registrations = ref([]);
const error = ref("");

function formatDate(raw) {
  if (!raw) return "-";
  return raw.replace("T", " ").slice(0, 16);
}

onMounted(async () => {
  try {
    registrations.value = await activityApi.myRegistrations();
  } catch (err) {
    error.value = err.message;
  }
});
</script>
