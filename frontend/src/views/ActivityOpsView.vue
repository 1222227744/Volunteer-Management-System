<template>
  <section class="panel">
    <div class="panel-head">
      <h2>活动执行管理</h2>
      <p>组织方/管理员可修改活动状态、审核报名并执行签到签退。</p>
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
                {{ activity.title }}（{{ formatActivityStatus(activity.status) }}）
              </option>
            </select>
          </div>
          <div class="field">
            <label>活动状态</label>
            <div class="stack">
              <select v-model="nextStatus">
                <option value="DRAFT">{{ formatActivityStatus("DRAFT") }}</option>
                <option value="PUBLISHED">{{ formatActivityStatus("PUBLISHED") }}</option>
                <option value="ONGOING">{{ formatActivityStatus("ONGOING") }}</option>
                <option value="OFFLINE">{{ formatActivityStatus("OFFLINE") }}</option>
                <option value="FINISHED">{{ formatActivityStatus("FINISHED") }}</option>
                <option value="CANCELLED">{{ formatActivityStatus("CANCELLED") }}</option>
              </select>
              <button class="btn warn" :disabled="!selectedActivityId" @click="updateStatus">更新状态</button>
            </div>
          </div>
        </div>
      </div>

      <div class="card" v-if="selectedActivity">
        <h3>{{ selectedActivity.title }}</h3>
        <p class="muted">
          当前状态：{{ formatActivityStatus(selectedActivity.status) }} | 报名人数：{{ selectedActivity.registeredCount }}/{{ selectedActivity.maxParticipants }}
        </p>
        <p class="muted">
          活动评价：平均 {{ feedbackSummary.averageRating || 0 }} / 5，共 {{ feedbackSummary.count || 0 }} 条
        </p>
        <div class="card" style="margin: 12px 0;">
          <div class="stack" style="justify-content: space-between; align-items: center;">
            <div>
              <strong>自助签到码</strong>
              <p class="muted" style="margin: 6px 0 0;">
                志愿者可在“我的报名”中输入该签到码完成自助签到或签退。
              </p>
            </div>
            <div class="stack" style="align-items: center;">
              <span class="tag check-code">{{ selectedActivity.checkCode || "未生成" }}</span>
              <button class="btn ghost" @click="refreshCheckCode">刷新签到码</button>
            </div>
          </div>
        </div>
        <div class="field" style="margin: 12px 0;">
          <label>报名审核/取消说明</label>
          <input v-model.trim="reviewComment" placeholder="可填写通过、驳回或取消报名的处理说明" />
        </div>
        <div class="list">
          <article class="card" v-for="item in registrations" :key="item.registrationId">
            <div class="stack" style="justify-content: space-between; align-items: center;">
              <div>
                <strong>{{ item.userDisplayName }}（ID: {{ item.userId }}）</strong>
                <p class="muted" style="margin: 6px 0 0;">
                  报名时间：{{ formatDate(item.registeredAt) }} | 状态：{{ formatRegistrationStatus(item.status) }}
                </p>
                <p v-if="item.checkInAt" class="muted" style="margin: 6px 0 0;">
                  签到时间：{{ formatDate(item.checkInAt) }}
                </p>
                <p v-if="item.checkOutAt" class="muted" style="margin: 6px 0 0;">
                  签退时间：{{ formatDate(item.checkOutAt) }}
                </p>
                <p v-if="item.reviewComment" class="muted" style="margin: 6px 0 0;">
                  处理说明：{{ item.reviewComment }}
                </p>
                <p v-if="item.reviewedAt" class="muted" style="margin: 6px 0 0;">
                  处理时间：{{ formatDate(item.reviewedAt) }}
                </p>
                <div v-if="item.corrections?.length" class="correction-box">
                  <strong>考勤更正记录</strong>
                  <p v-for="correction in item.corrections" :key="correction.id" class="muted" style="margin: 6px 0 0;">
                    {{ formatDate(correction.correctedAt) }}，
                    {{ correction.correctedByName }}执行“{{ formatAttendanceCorrectionAction(correction.action) }}”，
                    {{ formatRegistrationStatus(correction.beforeStatus) }} -> {{ formatRegistrationStatus(correction.afterStatus) }}，
                    原因：{{ correction.reason }}
                  </p>
                </div>
              </div>
              <div class="stack">
                <button
                  v-if="item.status === 'PENDING'"
                  class="btn primary"
                  @click="reviewRegistration(item.userId, 'APPROVED')"
                >
                  审核通过
                </button>
                <button
                  v-if="item.status === 'PENDING'"
                  class="btn danger"
                  @click="reviewRegistration(item.userId, 'REJECTED')"
                >
                  驳回
                </button>
                <button
                  v-if="item.status === 'APPROVED'"
                  class="btn danger"
                  @click="reviewRegistration(item.userId, 'CANCELLED')"
                >
                  取消报名
                </button>
                <button
                  v-if="item.status === 'APPROVED'"
                  class="btn primary"
                  @click="checkIn(item.userId)"
                >
                  签到
                </button>
                <button
                  v-if="item.status === 'CHECKED_IN'"
                  class="btn warn"
                  @click="checkOut(item.userId)"
                >
                  签退
                </button>
                <button class="btn ghost" @click="startCorrection(item)">
                  更正考勤
                </button>
                <span
                  v-if="['REJECTED', 'CANCELLED', 'CHECKED_OUT', 'COMPLETED'].includes(item.status)"
                  class="tag"
                >
                  {{ formatRegistrationStatus(item.status) }}
                </span>
              </div>
            </div>
            <div v-if="correctionForm.registrationId === item.registrationId" class="card" style="margin-top: 12px;">
              <h3>异常考勤更正</h3>
              <div class="grid two">
                <div class="field">
                  <label>更正动作</label>
                  <select v-model="correctionForm.action">
                    <option value="SET_APPROVED">{{ formatAttendanceCorrectionAction("SET_APPROVED") }}</option>
                    <option value="SET_CHECKED_IN">{{ formatAttendanceCorrectionAction("SET_CHECKED_IN") }}</option>
                    <option value="SET_CHECKED_OUT">{{ formatAttendanceCorrectionAction("SET_CHECKED_OUT") }}</option>
                    <option value="CLEAR_CHECK_IN">{{ formatAttendanceCorrectionAction("CLEAR_CHECK_IN") }}</option>
                    <option value="CLEAR_CHECK_OUT">{{ formatAttendanceCorrectionAction("CLEAR_CHECK_OUT") }}</option>
                    <option value="SET_CANCELLED">{{ formatAttendanceCorrectionAction("SET_CANCELLED") }}</option>
                  </select>
                </div>
                <div class="field">
                  <label>更正原因</label>
                  <input v-model.trim="correctionForm.reason" placeholder="需填写现场核验、补签或误操作等原因" />
                </div>
              </div>
              <div class="stack" style="margin-top: 10px;">
                <button class="btn primary" @click="submitCorrection(item.userId)">提交更正</button>
                <button class="btn ghost" @click="cancelCorrection">取消</button>
              </div>
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
import { activityApi, activityFeedbackApi } from "../api";
import { authState } from "../stores/auth";
import {
  formatActivityStatus,
  formatAttendanceCorrectionAction,
  formatRegistrationStatus
} from "../utils/labels";

const activities = ref([]);
const registrations = ref([]);
const feedbackSummary = ref({ averageRating: 0, count: 0, items: [] });
const selectedActivityId = ref("");
const nextStatus = ref("PUBLISHED");
const reviewComment = ref("");
const correctionForm = ref({
  registrationId: null,
  action: "SET_CHECKED_IN",
  reason: ""
});

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
    feedbackSummary.value = { averageRating: 0, count: 0, items: [] };
    return;
  }
  const activity = selectedActivity.value;
  if (activity) {
    nextStatus.value = activity.status;
  }
  const [registrationData, feedbackData] = await Promise.all([
    activityApi.registrations(selectedActivityId.value),
    activityFeedbackApi.byActivity(selectedActivityId.value)
  ]);
  registrations.value = registrationData;
  feedbackSummary.value = feedbackData;
}

function startCorrection(item) {
  correctionForm.value = {
    registrationId: item.registrationId,
    action: item.status === "CHECKED_IN" ? "SET_CHECKED_OUT" : "SET_CHECKED_IN",
    reason: ""
  };
}

function cancelCorrection() {
  correctionForm.value = {
    registrationId: null,
    action: "SET_CHECKED_IN",
    reason: ""
  };
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
    await onSelectActivity();
  } catch (err) {
    message.value = err.message;
  }
}

async function refreshCheckCode() {
  message.value = "";
  ok.value = false;
  if (!selectedActivityId.value) {
    return;
  }
  try {
    await activityApi.refreshCheckCode(selectedActivityId.value);
    ok.value = true;
    message.value = "签到码已刷新";
    await loadActivities();
    await onSelectActivity();
  } catch (err) {
    message.value = err.message;
  }
}

async function reviewRegistration(userId, status) {
  message.value = "";
  ok.value = false;
  if (!selectedActivityId.value) {
    return;
  }
  try {
    await activityApi.reviewRegistration(selectedActivityId.value, userId, status, reviewComment.value);
    ok.value = true;
    message.value = status === "APPROVED" ? "报名审核通过" : status === "REJECTED" ? "报名已驳回" : "报名已取消";
    reviewComment.value = "";
    await loadActivities();
    await onSelectActivity();
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
    await loadActivities();
    await onSelectActivity();
  } catch (err) {
    message.value = err.message;
  }
}

async function checkOut(userId) {
  message.value = "";
  ok.value = false;
  if (!selectedActivityId.value) {
    return;
  }
  try {
    await activityApi.checkOut(selectedActivityId.value, userId);
    ok.value = true;
    message.value = "签退成功";
    await loadActivities();
    await onSelectActivity();
  } catch (err) {
    message.value = err.message;
  }
}

async function submitCorrection(userId) {
  message.value = "";
  ok.value = false;
  if (!selectedActivityId.value) {
    return;
  }
  if (!correctionForm.value.reason) {
    message.value = "请填写考勤更正原因";
    return;
  }
  try {
    await activityApi.correctAttendance(selectedActivityId.value, {
      userId,
      action: correctionForm.value.action,
      reason: correctionForm.value.reason
    });
    ok.value = true;
    message.value = "考勤更正已提交";
    cancelCorrection();
    await loadActivities();
    await onSelectActivity();
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
