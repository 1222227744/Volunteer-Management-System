<template>
  <section class="panel">
    <div class="panel-head">
      <h2>活动执行管理</h2>
      <p>组织方/管理员可修改活动状态、查看报名名单并执行签到。</p>
    </div>
    <div class="panel-body">
      <p v-if="message" :class="['notice', ok ? 'success' : 'error']" style="margin-bottom: 12px;">
        {{ message }}
      </p>

      <div class="card" style="margin-bottom: 14px;">
        <div class="grid two">
          <div class="field">
            <label>选择活动</label>
            <select v-model="selectedActivityId" @change="onSelectActivity">
              <option value="">请选择活动</option>
              <option v-for="activity in manageableActivities" :key="activity.id" :value="activity.id">
                {{ activity.title }}（{{ activity.status }}）
              </option>
            </select>
          </div>
          <div class="field">
            <label>活动状态</label>
            <div class="stack">
              <select v-model="nextStatus">
                <option value="DRAFT">DRAFT</option>
                <option value="PUBLISHED">PUBLISHED</option>
                <option value="ONGOING">ONGOING</option>
                <option value="FINISHED">FINISHED</option>
                <option value="CANCELLED">CANCELLED</option>
              </select>
              <button class="btn warn" :disabled="!selectedActivityId" @click="updateStatus">更新状态</button>
            </div>
          </div>
        </div>
      </div>

      <div class="card" v-if="selectedActivity">
        <h3>{{ selectedActivity.title }}</h3>
        <p class="muted">
          当前状态：{{ selectedActivity.status }} | 报名人数：{{ selectedActivity.registeredCount }}/{{ selectedActivity.maxParticipants }}
        </p>
        <div class="list">
          <article class="card" v-for="item in registrations" :key="item.registrationId">
            <div class="stack" style="justify-content: space-between; align-items: center;">
              <div>
                <strong>{{ item.userDisplayName }}（ID: {{ item.userId }}）</strong>
                <p class="muted" style="margin: 6px 0 0;">
                  报名时间：{{ formatDate(item.registeredAt) }} | 状态：{{ item.status }}
                </p>
                <p v-if="item.checkInAt" class="muted" style="margin: 6px 0 0;">
                  签到时间：{{ formatDate(item.checkInAt) }}
                </p>
              </div>
              <button
                class="btn primary"
                :disabled="item.status === 'CHECKED_IN' || item.status === 'COMPLETED'"
                @click="checkIn(item.userId)"
              >
                {{ item.status === "CHECKED_IN" || item.status === "COMPLETED" ? "已签到" : "签到" }}
              </button>
            </div>
          </article>
          <p v-if="!registrations.length" class="notice">当前活动暂无报名用户。</p>
        </div>
      </div>

      <p v-else class="notice">请先选择活动。</p>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { activityApi } from "../api";
import { authState } from "../stores/auth";

const activities = ref([]);
const registrations = ref([]);
const selectedActivityId = ref("");
const nextStatus = ref("PUBLISHED");

const message = ref("");
const ok = ref(false);

const manageableActivities = computed(() => {
  if (authState.user?.role === "ADMIN") {
    return activities.value;
  }
  return activities.value.filter((item) => item.organizerId === authState.user?.id);
});

const selectedActivity = computed(() => {
  if (!selectedActivityId.value) {
    return null;
  }
  return manageableActivities.value.find((item) => String(item.id) === String(selectedActivityId.value)) || null;
});

function formatDate(raw) {
  if (!raw) return "-";
  return raw.replace("T", " ").slice(0, 16);
}

async function loadActivities() {
  activities.value = await activityApi.list();
}

async function onSelectActivity() {
  message.value = "";
  ok.value = false;
  if (!selectedActivityId.value) {
    registrations.value = [];
    return;
  }
  const activity = selectedActivity.value;
  if (activity) {
    nextStatus.value = activity.status;
  }
  registrations.value = await activityApi.registrations(selectedActivityId.value);
}

async function updateStatus() {
  message.value = "";
  ok.value = false;
  if (!selectedActivityId.value) {
    return;
  }
  try {
    await activityApi.updateStatus(selectedActivityId.value, nextStatus.value);
    ok.value = true;
    message.value = "活动状态更新成功";
    await loadActivities();
  } catch (err) {
    message.value = err.message;
  }
}

async function checkIn(userId) {
  message.value = "";
  ok.value = false;
  if (!selectedActivityId.value) {
    return;
  }
  try {
    await activityApi.checkIn(selectedActivityId.value, userId);
    ok.value = true;
    message.value = "签到成功";
    registrations.value = await activityApi.registrations(selectedActivityId.value);
    await loadActivities();
  } catch (err) {
    message.value = err.message;
  }
}

onMounted(async () => {
  try {
    await loadActivities();
  } catch (err) {
    message.value = err.message;
  }
});
</script>
