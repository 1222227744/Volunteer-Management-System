<template>
  <section class="panel">
    <div class="panel-head">
      <h2>志愿活动中心</h2>
      <p>浏览活动并报名参与，组织方可发布与管理活动。</p>
    </div>
    <div class="panel-body">
      <div class="card" style="margin-bottom: 14px;">
        <h3>活动筛选</h3>
        <div class="grid three">
          <div class="field">
            <label>关键词</label>
            <input v-model.trim="filters.keyword" placeholder="按标题或描述搜索" @keyup.enter="loadActivities" />
          </div>
          <div class="field">
            <label>地点</label>
            <input v-model.trim="filters.location" placeholder="按地点搜索" @keyup.enter="loadActivities" />
          </div>
          <div class="field">
            <label>状态</label>
            <select v-model="filters.status">
              <option value="">全部状态</option>
              <option value="DRAFT">{{ formatActivityStatus("DRAFT") }}</option>
              <option value="PUBLISHED">{{ formatActivityStatus("PUBLISHED") }}</option>
              <option value="ONGOING">{{ formatActivityStatus("ONGOING") }}</option>
              <option value="OFFLINE">{{ formatActivityStatus("OFFLINE") }}</option>
              <option value="FINISHED">{{ formatActivityStatus("FINISHED") }}</option>
              <option value="CANCELLED">{{ formatActivityStatus("CANCELLED") }}</option>
            </select>
          </div>
        </div>
        <div class="grid two" style="margin-top: 10px;">
          <div class="field">
            <label>开始时间不早于</label>
            <input v-model="filters.startFrom" type="datetime-local" />
          </div>
          <div class="field">
            <label>开始时间不晚于</label>
            <input v-model="filters.startTo" type="datetime-local" />
          </div>
        </div>
        <div class="stack" style="margin-top: 12px;">
          <button class="btn primary" @click="loadActivities">查询活动</button>
          <button class="btn ghost" @click="resetFilters">重置筛选</button>
        </div>
      </div>

      <div v-if="canManage" class="card" style="margin-bottom: 14px;">
        <h3>发布新活动</h3>
        <div class="grid two">
          <div class="field">
            <label>活动标题</label>
            <input v-model.trim="createForm.title" placeholder="例如：周末环保清洁" />
          </div>
          <div class="field">
            <label>地点</label>
            <input v-model.trim="createForm.location" placeholder="活动地点" />
          </div>
        </div>
        <div class="grid two" style="margin-top: 10px;">
          <div class="field">
            <label>开始时间</label>
            <input v-model="createForm.startTime" type="datetime-local" />
          </div>
          <div class="field">
            <label>结束时间</label>
            <input v-model="createForm.endTime" type="datetime-local" />
          </div>
        </div>
        <div class="grid two" style="margin-top: 10px;">
          <div class="field">
            <label>人数上限</label>
            <input v-model.number="createForm.maxParticipants" type="number" min="1" />
          </div>
          <div class="field">
            <label>报名截止时间</label>
            <input v-model="createForm.registrationDeadline" type="datetime-local" />
          </div>
        </div>
        <div class="grid two" style="margin-top: 10px;">
          <div class="field">
            <label>状态</label>
            <select v-model="createForm.status">
              <option value="PUBLISHED">{{ formatActivityStatus("PUBLISHED") }}</option>
              <option value="ONGOING">{{ formatActivityStatus("ONGOING") }}</option>
              <option value="DRAFT">{{ formatActivityStatus("DRAFT") }}</option>
            </select>
          </div>
        </div>
        <div class="field" style="margin-top: 10px;">
          <label>活动描述</label>
          <textarea v-model.trim="createForm.description" placeholder="活动内容与注意事项"></textarea>
        </div>
        <div class="field" style="margin-top: 10px;">
          <label>参与要求</label>
          <textarea v-model.trim="createForm.participationRequirement" placeholder="例如：需具备基础沟通能力，服从现场安排"></textarea>
        </div>
        <div class="stack" style="margin-top: 12px;">
          <button class="btn primary" :disabled="creating" @click="createActivity">
            {{ creating ? "发布中..." : "发布活动" }}
          </button>
        </div>
      </div>

      <p v-if="message" :class="['notice', messageType]" style="margin-bottom: 12px;">{{ message }}</p>

      <div class="list">
        <article v-for="activity in activities" :key="activity.id" class="card">
          <div class="stack" style="justify-content: space-between;">
            <h3>{{ activity.title }}</h3>
            <span class="tag">{{ formatActivityStatus(activity.status) }}</span>
          </div>
          <p class="muted">{{ activity.description }}</p>
          <div class="grid three">
            <div>
              <strong>地点</strong>
              <p class="muted">{{ activity.location }}</p>
            </div>
            <div>
              <strong>时间</strong>
              <p class="muted">{{ formatDate(activity.startTime) }} ~ {{ formatDate(activity.endTime) }}</p>
            </div>
            <div>
              <strong>名额</strong>
              <p class="muted">{{ activity.registeredCount }}/{{ activity.maxParticipants }}</p>
            </div>
          </div>
          <p v-if="activity.registrationDeadline" class="muted">
            报名截止：{{ formatDate(activity.registrationDeadline) }}
          </p>
          <p v-if="activity.participationRequirement" class="muted">
            参与要求：{{ activity.participationRequirement }}
          </p>
          <div class="stack" style="margin-top: 10px;">
            <button class="btn ghost" :disabled="registeringIds.has(activity.id) || !canRegister(activity)" @click="register(activity.id)">
              {{ registeringIds.has(activity.id) ? "报名中..." : "我要报名" }}
            </button>
            <button v-if="canEdit(activity)" class="btn warn" @click="startEdit(activity)">
              编辑活动
            </button>
          </div>
          <div v-if="editingActivityId === activity.id" class="card" style="margin-top: 12px;">
            <h3>编辑活动信息</h3>
            <div class="grid two">
              <div class="field">
                <label>活动标题</label>
                <input v-model.trim="editForm.title" />
              </div>
              <div class="field">
                <label>地点</label>
                <input v-model.trim="editForm.location" />
              </div>
            </div>
            <div class="grid two" style="margin-top: 10px;">
              <div class="field">
                <label>开始时间</label>
                <input v-model="editForm.startTime" type="datetime-local" />
              </div>
              <div class="field">
                <label>结束时间</label>
                <input v-model="editForm.endTime" type="datetime-local" />
              </div>
            </div>
            <div class="grid two" style="margin-top: 10px;">
              <div class="field">
                <label>人数上限</label>
                <input v-model.number="editForm.maxParticipants" type="number" min="1" />
              </div>
              <div class="field">
                <label>报名截止时间</label>
                <input v-model="editForm.registrationDeadline" type="datetime-local" />
              </div>
            </div>
            <div class="field" style="margin-top: 10px;">
              <label>活动描述</label>
              <textarea v-model.trim="editForm.description"></textarea>
            </div>
            <div class="field" style="margin-top: 10px;">
              <label>参与要求</label>
              <textarea v-model.trim="editForm.participationRequirement"></textarea>
            </div>
            <div class="stack" style="margin-top: 12px;">
              <button class="btn primary" :disabled="updating" @click="updateActivity(activity.id)">
                {{ updating ? "保存中..." : "保存修改" }}
              </button>
              <button class="btn ghost" @click="cancelEdit">取消编辑</button>
            </div>
          </div>
        </article>
        <p v-if="!activities.length" class="notice">没有符合条件的活动。</p>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { activityApi } from "../api";
import { authState } from "../stores/auth";
import { formatActivityStatus } from "../utils/labels";

// 展示层：对应 SRS FR-02 活动发布与查询，并作为 FR-03 报名申请入口。
const activities = ref([]);
const creating = ref(false);
const updating = ref(false);
const message = ref("");
const messageType = ref("success");
const registeringIds = ref(new Set());
const editingActivityId = ref(null);

const createForm = reactive({
  title: "",
  description: "",
  location: "",
  startTime: "",
  endTime: "",
  registrationDeadline: "",
  participationRequirement: "",
  maxParticipants: 20,
  status: "PUBLISHED"
});

const editForm = reactive({
  title: "",
  description: "",
  location: "",
  startTime: "",
  endTime: "",
  registrationDeadline: "",
  participationRequirement: "",
  maxParticipants: 20
});

const filters = reactive({
  keyword: "",
  location: "",
  status: "",
  startFrom: "",
  startTo: ""
});

const canManage = computed(
  () => authState.user?.role === "ADMIN" || authState.user?.role === "ORGANIZER"
);

function formatDate(raw) {
  if (!raw) return "-";
  return raw.replace("T", " ").slice(0, 16);
}

function toInputDateTime(raw) {
  if (!raw) return "";
  return raw.slice(0, 16);
}

function toPayloadDateTime(value) {
  return value ? `${value}:00` : null;
}

function buildActivityPayload(form) {
  return {
    title: form.title,
    description: form.description,
    location: form.location,
    startTime: toPayloadDateTime(form.startTime),
    endTime: toPayloadDateTime(form.endTime),
    registrationDeadline: toPayloadDateTime(form.registrationDeadline),
    participationRequirement: form.participationRequirement || null,
    maxParticipants: Number(form.maxParticipants),
    status: form.status
  };
}

function canRegister(activity) {
  return activity.status === "PUBLISHED" || activity.status === "ONGOING";
}

function canEdit(activity) {
  return authState.user?.role === "ADMIN" || activity.organizerId === authState.user?.id;
}

async function loadActivities() {
  activities.value = await activityApi.list({
    keyword: filters.keyword,
    location: filters.location,
    status: filters.status,
    startFrom: toPayloadDateTime(filters.startFrom),
    startTo: toPayloadDateTime(filters.startTo)
  });
}

async function resetFilters() {
  filters.keyword = "";
  filters.location = "";
  filters.status = "";
  filters.startFrom = "";
  filters.startTo = "";
  await loadActivities();
}

async function createActivity() {
  message.value = "";
  if (!createForm.title || !createForm.description || !createForm.location || !createForm.startTime || !createForm.endTime) {
    messageType.value = "error";
    message.value = "请填写完整活动信息";
    return;
  }
  creating.value = true;
  try {
    await activityApi.create(buildActivityPayload(createForm));
    messageType.value = "success";
    message.value = "活动发布成功";
    createForm.title = "";
    createForm.description = "";
    createForm.location = "";
    createForm.startTime = "";
    createForm.endTime = "";
    createForm.registrationDeadline = "";
    createForm.participationRequirement = "";
    createForm.maxParticipants = 20;
    createForm.status = "PUBLISHED";
    await loadActivities();
  } catch (err) {
    messageType.value = "error";
    message.value = err.message;
  } finally {
    creating.value = false;
  }
}

function startEdit(activity) {
  editingActivityId.value = activity.id;
  editForm.title = activity.title;
  editForm.description = activity.description;
  editForm.location = activity.location;
  editForm.startTime = toInputDateTime(activity.startTime);
  editForm.endTime = toInputDateTime(activity.endTime);
  editForm.registrationDeadline = toInputDateTime(activity.registrationDeadline);
  editForm.participationRequirement = activity.participationRequirement || "";
  editForm.maxParticipants = activity.maxParticipants;
}

function cancelEdit() {
  editingActivityId.value = null;
}

async function updateActivity(activityId) {
  message.value = "";
  if (!editForm.title || !editForm.description || !editForm.location || !editForm.startTime || !editForm.endTime) {
    messageType.value = "error";
    message.value = "请填写完整活动信息";
    return;
  }
  updating.value = true;
  try {
    const payload = buildActivityPayload(editForm);
    delete payload.status;
    await activityApi.update(activityId, payload);
    messageType.value = "success";
    message.value = "活动信息已更新";
    editingActivityId.value = null;
    await loadActivities();
  } catch (err) {
    messageType.value = "error";
    message.value = err.message;
  } finally {
    updating.value = false;
  }
}

async function register(activityId) {
  message.value = "";
  const set = new Set(registeringIds.value);
  set.add(activityId);
  registeringIds.value = set;
  try {
    await activityApi.register(activityId);
    messageType.value = "success";
    message.value = "报名成功";
    await loadActivities();
  } catch (err) {
    messageType.value = "error";
    message.value = err.message;
  } finally {
    const next = new Set(registeringIds.value);
    next.delete(activityId);
    registeringIds.value = next;
  }
}

onMounted(async () => {
  try {
    await loadActivities();
  } catch (err) {
    messageType.value = "error";
    message.value = err.message;
  }
});
</script>
