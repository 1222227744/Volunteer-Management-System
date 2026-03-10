<template>
  <section class="panel">
    <div class="panel-head">
      <h2>服务记录</h2>
      <p>志愿者可查看个人服务时长，组织方/管理员可登记服务记录并发放积分。</p>
    </div>
    <div class="panel-body">
      <p v-if="message" :class="['notice', messageType]" style="margin-bottom: 12px;">{{ message }}</p>

      <div class="card" v-if="canManage" style="margin-bottom: 14px;">
        <h3>登记服务记录</h3>
        <div class="grid two">
          <div class="field">
            <label>选择活动</label>
            <select v-model="recordForm.activityId" @change="onActivityChange">
              <option value="">请选择活动</option>
              <option v-for="activity in manageableActivities" :key="activity.id" :value="activity.id">
                {{ activity.title }}（{{ formatDate(activity.startTime) }}）
              </option>
            </select>
          </div>
          <div class="field">
            <label>报名志愿者</label>
            <select v-model="recordForm.userId" @change="onUserChange">
              <option value="">请选择志愿者</option>
              <option v-for="person in registrations" :key="person.registrationId" :value="person.userId">
                {{ person.userDisplayName }}（ID: {{ person.userId }} / {{ person.status }}）
              </option>
            </select>
          </div>
        </div>
        <div class="grid two" style="margin-top: 10px;">
          <div class="field">
            <label>服务时长（小时）</label>
            <input v-model.number="recordForm.hours" type="number" min="0.5" max="24" step="0.5" />
          </div>
          <div class="field">
            <label>证明链接（可选）</label>
            <input v-model.trim="recordForm.evidenceUrl" placeholder="http(s)://..." />
          </div>
        </div>
        <div class="field" style="margin-top: 10px;">
          <label>服务成果描述</label>
          <textarea v-model.trim="recordForm.achievement" placeholder="填写本次志愿服务成果"></textarea>
        </div>
        <div class="stack" style="margin-top: 12px;">
          <button class="btn primary" :disabled="submitting" @click="submitRecord">
            {{ submitting ? "提交中..." : "登记服务记录" }}
          </button>
        </div>
      </div>

      <div class="grid two">
        <div class="card">
          <h3>我的服务记录</h3>
          <p class="muted">累计时长：{{ myTotalHours }}</p>
          <div class="list">
            <article class="card" v-for="record in myRecords" :key="record.id">
              <div class="stack" style="justify-content: space-between;">
                <strong>{{ record.activityTitle }}</strong>
                <span class="tag">{{ record.hours }} 小时</span>
              </div>
              <p class="muted">{{ record.achievement }}</p>
              <p class="muted">时间：{{ formatDate(record.createdAt) }}</p>
            </article>
            <p v-if="!myRecords.length" class="notice">暂无服务记录。</p>
          </div>
        </div>

        <div class="card" v-if="canManage">
          <h3>当前所选志愿者记录</h3>
          <p class="muted">累计时长：{{ selectedUserTotalHours }}</p>
          <div class="list">
            <article class="card" v-for="record in selectedUserRecords" :key="record.id">
              <div class="stack" style="justify-content: space-between;">
                <strong>{{ record.activityTitle }}</strong>
                <span class="tag">{{ record.hours }} 小时</span>
              </div>
              <p class="muted">{{ record.achievement }}</p>
              <p class="muted">时间：{{ formatDate(record.createdAt) }}</p>
            </article>
            <p v-if="!selectedUserRecords.length" class="notice">选择志愿者后可查看其服务记录。</p>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { activityApi, serviceRecordApi } from "../api";
import { authState } from "../stores/auth";

const message = ref("");
const messageType = ref("success");
const submitting = ref(false);

const activities = ref([]);
const registrations = ref([]);
const myRecords = ref([]);
const myTotalHours = ref(0);
const selectedUserRecords = ref([]);
const selectedUserTotalHours = ref(0);

const recordForm = reactive({
  activityId: "",
  userId: "",
  hours: 1,
  achievement: "",
  evidenceUrl: ""
});

const canManage = computed(
  () => authState.user?.role === "ADMIN" || authState.user?.role === "ORGANIZER"
);

const manageableActivities = computed(() => {
  if (authState.user?.role === "ADMIN") {
    return activities.value;
  }
  return activities.value.filter((item) => item.organizerId === authState.user?.id);
});

function formatDate(raw) {
  if (!raw) return "-";
  return raw.replace("T", " ").slice(0, 16);
}

async function loadMyRecords() {
  const data = await serviceRecordApi.my();
  myTotalHours.value = data.totalHours;
  myRecords.value = data.records;
}

async function loadActivities() {
  activities.value = await activityApi.list();
}

async function onActivityChange(resetUser = true) {
  if (resetUser) {
    recordForm.userId = "";
    selectedUserTotalHours.value = 0;
    selectedUserRecords.value = [];
  }
  if (!recordForm.activityId) {
    registrations.value = [];
    return;
  }
  registrations.value = await activityApi.registrations(recordForm.activityId);
}

async function onUserChange() {
  selectedUserTotalHours.value = 0;
  selectedUserRecords.value = [];
  if (!recordForm.userId) {
    return;
  }
  const data = await serviceRecordApi.byUser(recordForm.userId);
  selectedUserTotalHours.value = data.totalHours;
  selectedUserRecords.value = data.records;
}

async function submitRecord() {
  message.value = "";
  if (!recordForm.activityId || !recordForm.userId || !recordForm.hours || !recordForm.achievement) {
    messageType.value = "error";
    message.value = "请完整填写登记信息";
    return;
  }
  submitting.value = true;
  try {
    const selectedUserId = recordForm.userId;
    await serviceRecordApi.create({
      activityId: Number(recordForm.activityId),
      userId: Number(recordForm.userId),
      hours: Number(recordForm.hours),
      achievement: recordForm.achievement,
      evidenceUrl: recordForm.evidenceUrl || null
    });
    messageType.value = "success";
    message.value = "登记成功，积分已自动发放";
    recordForm.hours = 1;
    recordForm.achievement = "";
    recordForm.evidenceUrl = "";
    await Promise.all([loadMyRecords(), onActivityChange(false)]);
    recordForm.userId = selectedUserId;
    await onUserChange();
  } catch (err) {
    messageType.value = "error";
    message.value = err.message;
  } finally {
    submitting.value = false;
  }
}

onMounted(async () => {
  try {
    await loadMyRecords();
    if (canManage.value) {
      await loadActivities();
    }
  } catch (err) {
    messageType.value = "error";
    message.value = err.message;
  }
});
</script>
