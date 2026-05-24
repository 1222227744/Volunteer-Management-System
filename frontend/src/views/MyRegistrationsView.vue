<template>
  <section class="panel">
    <div class="panel-head">
      <h2>我的活动报名</h2>
      <p>查看你的报名、签到、完成状态，并在服务结束后提交活动评价。</p>
    </div>
    <div class="panel-body">
      <p v-if="message" :class="['notice', messageType]" style="margin-bottom: 12px;">{{ message }}</p>
      <div class="list">
        <article v-for="item in registrations" :key="item.registrationId" class="card">
          <div class="stack" style="justify-content: space-between;">
            <h3>{{ item.activityTitle }}</h3>
            <span class="tag">{{ formatRegistrationStatus(item.status) }}</span>
          </div>
          <p class="muted">时间：{{ formatDate(item.startTime) }} ~ {{ formatDate(item.endTime) }}</p>
          <p class="muted">报名于：{{ formatDate(item.registeredAt) }}</p>
          <p v-if="item.checkInAt" class="muted">签到时间：{{ formatDate(item.checkInAt) }}</p>
          <p v-if="item.checkOutAt" class="muted">签退时间：{{ formatDate(item.checkOutAt) }}</p>
          <p v-if="item.reviewComment" class="muted">处理说明：{{ item.reviewComment }}</p>
          <p v-if="item.reviewedAt" class="muted">处理时间：{{ formatDate(item.reviewedAt) }}</p>
          <div v-if="canCancel(item)" class="card" style="margin-top: 12px;">
            <div class="field">
              <label>取消报名原因</label>
              <input v-model.trim="cancelForms[item.activityId].reason" placeholder="可选，用于告知组织方取消原因" />
            </div>
            <div class="stack" style="margin-top: 10px;">
              <button class="btn danger" @click="cancelRegistration(item.activityId)">
                取消报名
              </button>
            </div>
          </div>
          <div v-if="item.status === 'COMPLETED'" class="card" style="margin-top: 12px;">
            <template v-if="feedbackMap[item.activityId]">
              <div class="stack" style="justify-content: space-between;">
                <strong>我的活动评价</strong>
                <span class="tag">{{ feedbackMap[item.activityId].rating }} / 5</span>
              </div>
              <p class="muted" style="margin-top: 8px;">{{ feedbackMap[item.activityId].comment }}</p>
              <p class="muted">评价时间：{{ formatDate(feedbackMap[item.activityId].createdAt) }}</p>
            </template>
            <template v-else>
              <div class="grid two">
                <div class="field">
                  <label>活动评分</label>
                  <select v-model.number="feedbackForms[item.activityId].rating">
                    <option :value="5">5 分</option>
                    <option :value="4">4 分</option>
                    <option :value="3">3 分</option>
                    <option :value="2">2 分</option>
                    <option :value="1">1 分</option>
                  </select>
                </div>
                <div class="field">
                  <label>活动评价</label>
                  <textarea
                    v-model.trim="feedbackForms[item.activityId].comment"
                    placeholder="填写你对本次活动组织、流程和现场体验的评价"
                  ></textarea>
                </div>
              </div>
              <div class="stack" style="margin-top: 10px;">
                <button class="btn primary" @click="submitFeedback(item.activityId)">
                  提交活动评价
                </button>
              </div>
            </template>
          </div>
        </article>
        <p v-if="!registrations.length" class="notice">你还没有任何报名记录。</p>
      </div>
    </div>
  </section>
</template>

<script setup>
import { onMounted, reactive, ref } from "vue";
import { activityApi, activityFeedbackApi } from "../api";
import { formatRegistrationStatus } from "../utils/labels";

const registrations = ref([]);
const feedbackMap = reactive({});
const feedbackForms = reactive({});
const cancelForms = reactive({});
const message = ref("");
const messageType = ref("success");

function formatDate(raw) {
  if (!raw) return "-";
  return raw.replace("T", " ").slice(0, 16);
}

function ensureFeedbackForm(activityId) {
  if (!feedbackForms[activityId]) {
    feedbackForms[activityId] = {
      rating: 5,
      comment: ""
    };
  }
}

function ensureCancelForm(activityId) {
  if (!cancelForms[activityId]) {
    cancelForms[activityId] = {
      reason: ""
    };
  }
}

function canCancel(item) {
  return item.status === "PENDING" || item.status === "APPROVED";
}

async function loadFeedbacks() {
  const data = await activityFeedbackApi.my();
  for (const key of Object.keys(feedbackMap)) {
    delete feedbackMap[key];
  }
  data.forEach((item) => {
    feedbackMap[item.activityId] = item;
  });
}

async function loadData() {
  registrations.value = await activityApi.myRegistrations();
  registrations.value.forEach((item) => {
    ensureFeedbackForm(item.activityId);
    ensureCancelForm(item.activityId);
  });
  await loadFeedbacks();
}

async function cancelRegistration(activityId) {
  message.value = "";
  const form = cancelForms[activityId];
  try {
    await activityApi.cancelRegistration(activityId, form?.reason || "");
    messageType.value = "success";
    message.value = "报名已取消";
    if (form) {
      form.reason = "";
    }
    await loadData();
  } catch (err) {
    messageType.value = "error";
    message.value = err.message;
  }
}

async function submitFeedback(activityId) {
  message.value = "";
  const form = feedbackForms[activityId];
  if (!form?.comment) {
    messageType.value = "error";
    message.value = "请填写活动评价内容";
    return;
  }
  try {
    await activityFeedbackApi.submit({
      activityId,
      rating: Number(form.rating),
      comment: form.comment
    });
    messageType.value = "success";
    message.value = "活动评价提交成功";
    await loadFeedbacks();
  } catch (err) {
    messageType.value = "error";
    message.value = err.message;
  }
}

onMounted(async () => {
  try {
    await loadData();
  } catch (err) {
    messageType.value = "error";
    message.value = err.message;
  }
});
</script>
