<template>
  <section class="panel">
    <div class="panel-head">
      <h2>志愿活动中心</h2>
      <p>浏览活动并报名参与，组织方可发布与管理活动。</p>
    </div>
    <div class="panel-body">
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
          <div class="stack" style="margin-top: 10px;">
            <button class="btn ghost" :disabled="registeringIds.has(activity.id)" @click="register(activity.id)">
              {{ registeringIds.has(activity.id) ? "报名中..." : "我要报名" }}
            </button>
          </div>
        </article>
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
const message = ref("");
const messageType = ref("success");
const registeringIds = ref(new Set());

const createForm = reactive({
  title: "",
  description: "",
  location: "",
  startTime: "",
  endTime: "",
  maxParticipants: 20,
  status: "PUBLISHED"
});

const canManage = computed(
  () => authState.user?.role === "ADMIN" || authState.user?.role === "ORGANIZER"
);

function formatDate(raw) {
  if (!raw) return "-";
  return raw.replace("T", " ").slice(0, 16);
}

async function loadActivities() {
  activities.value = await activityApi.list();
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
    await activityApi.create({ ...createForm });
    messageType.value = "success";
    message.value = "活动发布成功";
    createForm.title = "";
    createForm.description = "";
    createForm.location = "";
    createForm.startTime = "";
    createForm.endTime = "";
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
