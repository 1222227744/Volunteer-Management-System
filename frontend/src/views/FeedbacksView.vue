<template>
  <section class="panel">
    <div class="panel-head">
      <h2>互动反馈</h2>
      <p>收集用户意见并形成处理闭环。</p>
    </div>
    <div class="panel-body">
      <p v-if="message" :class="['notice', ok ? 'success' : 'error']" style="margin-bottom: 12px;">
        {{ message }}
      </p>

      <div class="card" style="margin-bottom: 14px;">
        <h3>提交反馈</h3>
        <div class="field">
          <label>反馈内容</label>
          <textarea v-model.trim="feedbackContent" placeholder="请输入建议、问题或改进意见"></textarea>
        </div>
        <div class="stack" style="margin-top: 12px;">
          <button class="btn primary" :disabled="submitting" @click="submitFeedback">
            {{ submitting ? "提交中..." : "提交反馈" }}
          </button>
        </div>
      </div>

      <div class="grid two">
        <div class="card">
          <h3>我的反馈</h3>
          <div class="list">
            <article class="card" v-for="item in myFeedbacks" :key="item.id">
              <div class="stack" style="justify-content: space-between;">
                <strong>反馈 #{{ item.id }}</strong>
                <span class="tag">{{ item.status }}</span>
              </div>
              <p class="muted">{{ item.content }}</p>
              <p class="muted">提交时间：{{ formatDate(item.createdAt) }}</p>
              <p v-if="item.reply" class="muted">处理回复：{{ item.reply }}</p>
            </article>
            <p v-if="!myFeedbacks.length" class="notice">暂无反馈记录。</p>
          </div>
        </div>

        <div class="card" v-if="canManage">
          <h3>反馈处理台</h3>
          <div class="list">
            <article class="card" v-for="item in allFeedbacks" :key="item.id">
              <div class="stack" style="justify-content: space-between;">
                <strong>反馈 #{{ item.id }}（用户ID: {{ item.userId }}）</strong>
                <span class="tag">{{ item.status }}</span>
              </div>
              <p class="muted">{{ item.content }}</p>
              <p class="muted">提交时间：{{ formatDate(item.createdAt) }}</p>
              <template v-if="item.status === 'OPEN'">
                <div class="field">
                  <label>处理回复</label>
                  <textarea v-model.trim="resolveReplies[item.id]" placeholder="填写处理结果"></textarea>
                </div>
                <div class="stack" style="margin-top: 10px;">
                  <button class="btn primary" @click="resolve(item.id)">标记已处理</button>
                </div>
              </template>
              <p v-else-if="item.reply" class="muted">处理回复：{{ item.reply }}</p>
            </article>
            <p v-if="!allFeedbacks.length" class="notice">暂无反馈数据。</p>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { feedbackApi } from "../api";
import { authState } from "../stores/auth";

// 展示层：对应 SRS FR-08 互动反馈闭环。
const feedbackContent = ref("");
const submitting = ref(false);
const message = ref("");
const ok = ref(false);

const myFeedbacks = ref([]);
const allFeedbacks = ref([]);
const resolveReplies = reactive({});

const canManage = computed(
  () => authState.user?.role === "ADMIN" || authState.user?.role === "ORGANIZER"
);

function formatDate(raw) {
  if (!raw) return "-";
  return raw.replace("T", " ").slice(0, 16);
}

async function loadData() {
  const tasks = [feedbackApi.my()];
  if (canManage.value) {
    tasks.push(feedbackApi.all());
  }
  const [mine, all = []] = await Promise.all(tasks);
  myFeedbacks.value = mine;
  allFeedbacks.value = all;
}

async function submitFeedback() {
  message.value = "";
  ok.value = false;
  if (!feedbackContent.value) {
    message.value = "请输入反馈内容";
    return;
  }
  submitting.value = true;
  try {
    await feedbackApi.submit({ content: feedbackContent.value });
    feedbackContent.value = "";
    ok.value = true;
    message.value = "反馈已提交";
    await loadData();
  } catch (err) {
    message.value = err.message;
  } finally {
    submitting.value = false;
  }
}

async function resolve(id) {
  message.value = "";
  ok.value = false;
  const reply = resolveReplies[id];
  if (!reply) {
    message.value = "请填写处理回复";
    return;
  }
  try {
    await feedbackApi.resolve(id, { reply });
    ok.value = true;
    message.value = "反馈处理完成";
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
