<template>
  <section class="panel">
    <div class="panel-head">
      <h2>公告中心</h2>
      <p>统一发布活动通知、结果反馈与系统信息。</p>
    </div>
    <div class="panel-body">
      <div v-if="canManage" class="card" style="margin-bottom: 14px;">
        <h3>发布公告</h3>
        <div class="field">
          <label>标题</label>
          <input v-model.trim="form.title" placeholder="公告标题" />
        </div>
        <div class="field" style="margin-top: 10px;">
          <label>内容</label>
          <textarea v-model.trim="form.content" placeholder="公告内容"></textarea>
        </div>
        <div class="stack" style="margin-top: 12px;">
          <button class="btn primary" :disabled="creating" @click="createAnnouncement">
            {{ creating ? "发布中..." : "发布公告" }}
          </button>
        </div>
      </div>

      <p v-if="message" :class="['notice', ok ? 'success' : 'error']" style="margin-bottom: 12px;">
        {{ message }}
      </p>

      <div class="list">
        <article v-for="item in announcements" :key="item.id" class="card">
          <h3>{{ item.title }}</h3>
          <p class="muted">{{ item.content }}</p>
          <p class="muted">发布时间：{{ formatDate(item.createdAt) }}</p>
        </article>
        <p v-if="!announcements.length" class="notice">暂无公告。</p>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { announcementApi } from "../api";
import { authState } from "../stores/auth";

const announcements = ref([]);
const creating = ref(false);
const ok = ref(false);
const message = ref("");

const form = reactive({
  title: "",
  content: ""
});

const canManage = computed(
  () => authState.user?.role === "ADMIN" || authState.user?.role === "ORGANIZER"
);

function formatDate(raw) {
  if (!raw) return "-";
  return raw.replace("T", " ").slice(0, 16);
}

async function loadAnnouncements() {
  announcements.value = await announcementApi.list();
}

async function createAnnouncement() {
  message.value = "";
  ok.value = false;
  if (!form.title || !form.content) {
    message.value = "请填写公告标题和内容";
    return;
  }
  creating.value = true;
  try {
    await announcementApi.create({ ...form });
    ok.value = true;
    message.value = "公告发布成功";
    form.title = "";
    form.content = "";
    await loadAnnouncements();
  } catch (err) {
    message.value = err.message;
  } finally {
    creating.value = false;
  }
}

onMounted(async () => {
  try {
    await loadAnnouncements();
  } catch (err) {
    message.value = err.message;
  }
});
</script>
