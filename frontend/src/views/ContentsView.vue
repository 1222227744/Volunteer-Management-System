<template>
  <section class="panel">
    <div class="panel-head">
      <h2>内容发布与审核</h2>
      <p>志愿者可投稿展示服务成果，组织方/管理员可进行审核。</p>
    </div>
    <div class="panel-body">
      <p v-if="message" :class="['notice', ok ? 'success' : 'error']" style="margin-bottom: 12px;">
        {{ message }}
      </p>

      <div class="card" style="margin-bottom: 14px;">
        <h3>提交内容</h3>
        <div class="field">
          <label>标题</label>
          <input v-model.trim="submitForm.title" placeholder="成果标题" />
        </div>
        <div class="field" style="margin-top: 10px;">
          <label>正文</label>
          <textarea v-model.trim="submitForm.content" placeholder="填写成果内容"></textarea>
        </div>
        <div class="stack" style="margin-top: 12px;">
          <button class="btn primary" :disabled="submitting" @click="submitContent">
            {{ submitting ? "提交中..." : "提交审核" }}
          </button>
        </div>
      </div>

      <div class="grid two">
        <div class="card">
          <h3>我的投稿</h3>
          <div class="list">
            <article class="card" v-for="item in myContents" :key="item.id">
              <div class="stack" style="justify-content: space-between;">
                <strong>{{ item.title }}</strong>
                <span class="tag">{{ item.status }}</span>
              </div>
              <p class="muted">{{ item.content }}</p>
              <p class="muted">提交时间：{{ formatDate(item.createdAt) }}</p>
              <p v-if="item.reviewComment" class="muted">审核意见：{{ item.reviewComment }}</p>
            </article>
            <p v-if="!myContents.length" class="notice">你还没有提交内容。</p>
          </div>
        </div>

        <div class="card">
          <h3>已通过展示</h3>
          <div class="list">
            <article class="card" v-for="item in approvedContents" :key="item.id">
              <div class="stack" style="justify-content: space-between;">
                <strong>{{ item.title }}</strong>
                <span class="tag">{{ item.status }}</span>
              </div>
              <p class="muted">{{ item.content }}</p>
              <p class="muted">审核时间：{{ formatDate(item.reviewedAt) }}</p>
            </article>
            <p v-if="!approvedContents.length" class="notice">暂无已通过内容。</p>
          </div>
        </div>
      </div>

      <div class="card" v-if="canManage" style="margin-top: 14px;">
        <h3>待审核内容</h3>
        <div class="list">
          <article class="card" v-for="item in pendingContents" :key="item.id">
            <div class="stack" style="justify-content: space-between;">
              <strong>{{ item.title }}</strong>
              <span class="tag">{{ item.status }}</span>
            </div>
            <p class="muted">{{ item.content }}</p>
            <div class="field">
              <label>审核意见</label>
              <textarea v-model.trim="reviewComments[item.id]" placeholder="可填写通过/驳回原因"></textarea>
            </div>
            <div class="stack" style="margin-top: 10px;">
              <button class="btn primary" @click="review(item.id, 'APPROVED')">通过</button>
              <button class="btn danger" @click="review(item.id, 'REJECTED')">驳回</button>
            </div>
          </article>
          <p v-if="!pendingContents.length" class="notice">当前没有待审核内容。</p>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { contentApi } from "../api";
import { authState } from "../stores/auth";

// 展示层：对应 SRS FR-06 内容投稿、审核和成果展示。
const myContents = ref([]);
const approvedContents = ref([]);
const pendingContents = ref([]);
const reviewComments = reactive({});

const submitting = ref(false);
const message = ref("");
const ok = ref(false);

const submitForm = reactive({
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

async function loadData() {
  const tasks = [contentApi.my(), contentApi.approved()];
  if (canManage.value) {
    tasks.push(contentApi.pending());
  }
  const [mine, approved, pending = []] = await Promise.all(tasks);
  myContents.value = mine;
  approvedContents.value = approved;
  pendingContents.value = pending;
}

async function submitContent() {
  message.value = "";
  ok.value = false;
  if (!submitForm.title || !submitForm.content) {
    message.value = "请填写标题和正文";
    return;
  }
  submitting.value = true;
  try {
    await contentApi.submit({ ...submitForm });
    submitForm.title = "";
    submitForm.content = "";
    ok.value = true;
    message.value = "提交成功，等待审核";
    await loadData();
  } catch (err) {
    message.value = err.message;
  } finally {
    submitting.value = false;
  }
}

async function review(contentId, status) {
  message.value = "";
  ok.value = false;
  try {
    await contentApi.review(contentId, {
      status,
      reviewComment: reviewComments[contentId] || ""
    });
    ok.value = true;
    message.value = status === "APPROVED" ? "审核已通过" : "内容已驳回";
    await loadData();
  } catch (err) {
    message.value = err.message;
  }
}

onMounted(async () => {
  try {
    await loadData();
  } catch (err) {
    message.value = err.message;
  }
});
</script>
