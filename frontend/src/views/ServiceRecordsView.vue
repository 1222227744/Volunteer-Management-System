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
              <option v-for="person in eligibleRegistrations" :key="person.registrationId" :value="person.userId">
                {{ person.userDisplayName }}（ID: {{ person.userId }} / {{ formatRegistrationStatus(person.status) }}）
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
          <label>服务证明材料（pdf、docx、xlsx、pptx、zip、rar）</label>
          <input type="file" @change="uploadEvidence" />
          <p v-if="evidenceName" class="muted">已上传：{{ evidenceName }}</p>
        </div>
        <div class="field" style="margin-top: 10px;">
          <label>服务成果描述</label>
          <textarea v-model.trim="recordForm.achievement" placeholder="填写本次志愿服务成果"></textarea>
        </div>
        <div class="stack" style="margin-top: 12px;">
          <button class="btn primary" :disabled="submitting || uploading" @click="submitRecord">
            {{ submitting || uploading ? "处理中..." : "登记服务记录" }}
          </button>
        </div>
      </div>

      <div class="card" style="margin-bottom: 14px;">
        <h3>服务记录更正申请</h3>
        <p class="muted">当服务时长、成果描述或证明材料登记有误时，可提交更正申请，审核通过后系统会同步修正积分。</p>
        <div class="grid two" style="margin-top: 10px;">
          <div class="field">
            <label>选择需更正的服务记录</label>
            <select v-model="correctionForm.recordId" @change="prefillCorrectionFromRecord">
              <option value="">请选择服务记录</option>
              <option v-for="record in myRecords" :key="record.id" :value="record.id">
                {{ record.activityTitle }}（{{ record.hours }} 小时 / {{ formatDate(record.createdAt) }}）
              </option>
            </select>
          </div>
          <div class="field">
            <label>更正后服务时长（小时）</label>
            <input v-model.number="correctionForm.hours" type="number" min="0.5" max="24" step="0.5" />
          </div>
        </div>
        <div class="grid two" style="margin-top: 10px;">
          <div class="field">
            <label>更正后证明链接（可选）</label>
            <input v-model.trim="correctionForm.evidenceUrl" placeholder="http(s)://..." />
          </div>
          <div class="field">
            <label>更正后证明材料（可选）</label>
            <input type="file" @change="uploadCorrectionEvidence" />
            <p v-if="correctionEvidenceName" class="muted">已上传：{{ correctionEvidenceName }}</p>
          </div>
        </div>
        <div class="field" style="margin-top: 10px;">
          <label>更正后服务成果描述</label>
          <textarea v-model.trim="correctionForm.achievement" placeholder="填写更正后的服务成果"></textarea>
        </div>
        <div class="field" style="margin-top: 10px;">
          <label>更正原因</label>
          <textarea v-model.trim="correctionForm.reason" placeholder="说明为什么需要更正"></textarea>
        </div>
        <button class="btn primary" :disabled="correctionSubmitting || correctionUploading" @click="submitCorrection">
          {{ correctionSubmitting || correctionUploading ? "处理中..." : "提交更正申请" }}
        </button>

        <div class="list" style="margin-top: 14px;">
          <article v-for="item in myCorrections" :key="item.id" class="card">
            <div class="stack" style="justify-content: space-between;">
              <strong>{{ item.activityTitle }}</strong>
              <span class="tag">{{ formatCorrectionStatus(item.status) }}</span>
            </div>
            <p class="muted">服务时长：{{ item.oldHours }} 小时 → {{ item.newHours }} 小时</p>
            <p class="muted">申请原因：{{ item.reason }}</p>
            <p v-if="item.reviewComment" class="muted">审核说明：{{ item.reviewComment }}</p>
            <p class="muted">申请时间：{{ formatDate(item.requestedAt) }}，审核时间：{{ formatDate(item.reviewedAt) }}</p>
          </article>
          <p v-if="!myCorrections.length" class="notice">暂无服务记录更正申请。</p>
        </div>
      </div>

      <div v-if="canManage && recordForm.activityId" class="card" style="margin-bottom: 14px;">
        <h3>活动评价概览</h3>
        <p class="muted">
          当前活动平均评分：{{ activityFeedbackSummary.averageRating || 0 }} / 5，
          已提交评价 {{ activityFeedbackSummary.count || 0 }} 条
        </p>
        <div class="list" style="margin-top: 10px;">
          <article v-for="item in activityFeedbackSummary.items" :key="item.id" class="card">
            <div class="stack" style="justify-content: space-between;">
              <strong>{{ item.userDisplayName }}</strong>
              <span class="tag">{{ item.rating }} / 5</span>
            </div>
            <p class="muted">{{ item.comment }}</p>
            <p class="muted">评价时间：{{ formatDate(item.createdAt) }}</p>
          </article>
          <p v-if="!(activityFeedbackSummary.items || []).length" class="notice">当前活动还没有评价记录。</p>
        </div>
      </div>

      <div v-if="canManage" class="card" style="margin-bottom: 14px;">
        <div class="stack" style="justify-content: space-between;">
          <div>
            <h3>服务记录更正审核</h3>
            <p class="muted">组织方只能处理自己活动的申请，管理员可处理全部申请。</p>
          </div>
          <button class="btn ghost" @click="loadManageCorrections">刷新</button>
        </div>
        <div class="list" style="margin-top: 10px;">
          <article v-for="item in manageCorrections" :key="item.id" class="card">
            <div class="stack" style="justify-content: space-between;">
              <strong>{{ item.activityTitle }}</strong>
              <span class="tag">{{ formatCorrectionStatus(item.status) }}</span>
            </div>
            <p class="muted">申请人：{{ item.requesterName }}（用户ID: {{ item.userId }}）</p>
            <p class="muted">服务时长：{{ item.oldHours }} 小时 → {{ item.newHours }} 小时</p>
            <p class="muted">原成果：{{ item.oldAchievement }}</p>
            <p class="muted">新成果：{{ item.newAchievement }}</p>
            <p class="muted">申请原因：{{ item.reason }}</p>
            <p v-if="item.reviewComment" class="muted">审核说明：{{ item.reviewComment }}</p>
            <div v-if="item.status === 'PENDING'" class="field" style="margin-top: 10px;">
              <label>审核说明</label>
              <input v-model.trim="correctionReviewComments[item.id]" placeholder="填写通过或驳回说明" />
            </div>
            <div v-if="item.status === 'PENDING'" class="stack" style="margin-top: 10px;">
              <button class="btn primary" @click="reviewCorrection(item.id, 'APPROVED')">通过并应用更正</button>
              <button class="btn danger" @click="reviewCorrection(item.id, 'REJECTED')">驳回申请</button>
            </div>
            <p class="muted">申请时间：{{ formatDate(item.requestedAt) }}，审核时间：{{ formatDate(item.reviewedAt) }}</p>
          </article>
          <p v-if="!manageCorrections.length" class="notice">暂无服务记录更正申请。</p>
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
              <button v-if="record.evidenceFileId" class="btn ghost" @click="downloadFile(record.evidenceFileId)">下载证明材料</button>
              <p v-else-if="record.evidenceUrl" class="muted">证明链接：{{ record.evidenceUrl }}</p>
              <button class="btn ghost" @click="selectCorrectionRecord(record)">申请更正</button>
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
              <button v-if="record.evidenceFileId" class="btn ghost" @click="downloadFile(record.evidenceFileId)">下载证明材料</button>
              <p v-else-if="record.evidenceUrl" class="muted">证明链接：{{ record.evidenceUrl }}</p>
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
import { activityApi, activityFeedbackApi, fileApi, serviceRecordApi } from "../api";
import { authState } from "../stores/auth";
import { formatRegistrationStatus, formatServiceRecordCorrectionStatus } from "../utils/labels";

// 展示层：对应 SRS FR-04 服务记录与 FR-05 积分激励页面。
const message = ref("");
const messageType = ref("success");
const submitting = ref(false);
const uploading = ref(false);
const evidenceName = ref("");
const correctionSubmitting = ref(false);
const correctionUploading = ref(false);
const correctionEvidenceName = ref("");

const activities = ref([]);
const registrations = ref([]);
const myRecords = ref([]);
const myTotalHours = ref(0);
const selectedUserRecords = ref([]);
const selectedUserTotalHours = ref(0);
const activityFeedbackSummary = ref({ averageRating: 0, count: 0, items: [] });
const myCorrections = ref([]);
const manageCorrections = ref([]);
const correctionReviewComments = reactive({});

const recordForm = reactive({
  activityId: "",
  userId: "",
  hours: 1,
  achievement: "",
  evidenceUrl: "",
  evidenceFileId: null
});

const correctionForm = reactive({
  recordId: "",
  hours: 1,
  achievement: "",
  evidenceUrl: "",
  evidenceFileId: null,
  reason: ""
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

const eligibleRegistrations = computed(() =>
  registrations.value.filter((item) => item.status === "CHECKED_OUT" || item.status === "COMPLETED")
);

function formatDate(raw) {
  if (!raw) return "-";
  return raw.replace("T", " ").slice(0, 16);
}

async function uploadEvidence(event) {
  const file = event.target.files?.[0];
  if (!file) return;
  uploading.value = true;
  message.value = "";
  try {
    const asset = await fileApi.upload({
      file,
      category: "ATTACHMENT",
      businessType: "SERVICE_RECORD"
    });
    recordForm.evidenceFileId = asset.id;
    evidenceName.value = asset.originalName;
    messageType.value = "success";
    message.value = "证明材料上传成功";
  } catch (err) {
    messageType.value = "error";
    message.value = err.message;
  } finally {
    uploading.value = false;
    event.target.value = "";
  }
}

function downloadFile(fileId) {
  fileApi.download(fileId, `evidence-${fileId}`);
}

function formatCorrectionStatus(status) {
  return formatServiceRecordCorrectionStatus(status);
}

function selectCorrectionRecord(record) {
  correctionForm.recordId = record.id;
  prefillCorrectionFromRecord();
}

function prefillCorrectionFromRecord() {
  const record = myRecords.value.find((item) => String(item.id) === String(correctionForm.recordId));
  if (!record) {
    correctionForm.hours = 1;
    correctionForm.achievement = "";
    correctionForm.evidenceUrl = "";
    correctionForm.evidenceFileId = null;
    correctionForm.reason = "";
    correctionEvidenceName.value = "";
    return;
  }
  correctionForm.hours = Number(record.hours);
  correctionForm.achievement = record.achievement || "";
  correctionForm.evidenceUrl = record.evidenceUrl || "";
  correctionForm.evidenceFileId = record.evidenceFileId || null;
  correctionForm.reason = "";
  correctionEvidenceName.value = record.evidenceFileId ? `沿用原证明材料ID：${record.evidenceFileId}` : "";
}

async function uploadCorrectionEvidence(event) {
  const file = event.target.files?.[0];
  if (!file) return;
  correctionUploading.value = true;
  message.value = "";
  try {
    const asset = await fileApi.upload({
      file,
      category: "ATTACHMENT",
      businessType: "SERVICE_RECORD_CORRECTION"
    });
    correctionForm.evidenceFileId = asset.id;
    correctionEvidenceName.value = asset.originalName;
    messageType.value = "success";
    message.value = "更正证明材料上传成功";
  } catch (err) {
    messageType.value = "error";
    message.value = err.message;
  } finally {
    correctionUploading.value = false;
    event.target.value = "";
  }
}

async function loadMyRecords() {
  const data = await serviceRecordApi.my();
  myTotalHours.value = data.totalHours;
  myRecords.value = data.records;
}

async function loadMyCorrections() {
  myCorrections.value = await serviceRecordApi.myCorrections();
}

async function loadManageCorrections() {
  if (!canManage.value) {
    manageCorrections.value = [];
    return;
  }
  manageCorrections.value = await serviceRecordApi.corrections();
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
  activityFeedbackSummary.value = { averageRating: 0, count: 0, items: [] };
  if (!recordForm.activityId) {
    registrations.value = [];
    return;
  }
  const tasks = [activityApi.registrations(recordForm.activityId)];
  if (canManage.value) {
    tasks.push(activityFeedbackApi.byActivity(recordForm.activityId));
  }
  const [registrationData, feedbackData] = await Promise.all(tasks);
  registrations.value = registrationData;
  if (feedbackData) {
    activityFeedbackSummary.value = feedbackData;
  }
}

async function onUserChange() {
  selectedUserTotalHours.value = 0;
  selectedUserRecords.value = [];
  if (!recordForm.userId) {
    return;
  }
  const data = await serviceRecordApi.byUser(recordForm.userId, recordForm.activityId || null);
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
      evidenceUrl: recordForm.evidenceUrl || null,
      evidenceFileId: recordForm.evidenceFileId || null
    });
    messageType.value = "success";
    message.value = "登记成功，积分已自动发放";
    recordForm.hours = 1;
    recordForm.achievement = "";
    recordForm.evidenceUrl = "";
    recordForm.evidenceFileId = null;
    evidenceName.value = "";
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

async function submitCorrection() {
  message.value = "";
  if (!correctionForm.recordId || !correctionForm.hours || !correctionForm.achievement || !correctionForm.reason) {
    messageType.value = "error";
    message.value = "请完整填写更正申请信息";
    return;
  }
  correctionSubmitting.value = true;
  try {
    await serviceRecordApi.createCorrection(correctionForm.recordId, {
      hours: Number(correctionForm.hours),
      achievement: correctionForm.achievement,
      evidenceUrl: correctionForm.evidenceUrl || null,
      evidenceFileId: correctionForm.evidenceFileId || null,
      reason: correctionForm.reason
    });
    messageType.value = "success";
    message.value = "更正申请已提交，等待审核";
    correctionForm.recordId = "";
    correctionForm.hours = 1;
    correctionForm.achievement = "";
    correctionForm.evidenceUrl = "";
    correctionForm.evidenceFileId = null;
    correctionForm.reason = "";
    correctionEvidenceName.value = "";
    await Promise.all([loadMyCorrections(), loadManageCorrections()]);
  } catch (err) {
    messageType.value = "error";
    message.value = err.message;
  } finally {
    correctionSubmitting.value = false;
  }
}

async function reviewCorrection(correctionId, status) {
  message.value = "";
  try {
    await serviceRecordApi.reviewCorrection(correctionId, status, correctionReviewComments[correctionId] || "");
    delete correctionReviewComments[correctionId];
    messageType.value = "success";
    message.value = status === "APPROVED" ? "更正申请已通过并应用" : "更正申请已驳回";
    await Promise.all([loadManageCorrections(), loadMyCorrections(), loadMyRecords()]);
    if (recordForm.userId) {
      await onUserChange();
    }
  } catch (err) {
    messageType.value = "error";
    message.value = err.message;
  }
}

onMounted(async () => {
  try {
    await loadMyRecords();
    await loadMyCorrections();
    if (canManage.value) {
      await Promise.all([loadActivities(), loadManageCorrections()]);
    }
  } catch (err) {
    messageType.value = "error";
    message.value = err.message;
  }
});
</script>
