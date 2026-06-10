<template>
  <section class="panel">
    <div class="panel-head">
      <h2>荣誉激励与风采展示</h2>
      <p>基于服务时长、参与次数、活动评价和积分形成候选名单，管理员确认后生成荣誉与公开风采。</p>
    </div>
    <div class="panel-body">
      <p v-if="message" :class="['notice', ok ? 'success' : 'error']" style="margin-bottom: 12px;">
        {{ message }}
      </p>

      <div class="grid two">
        <div class="card">
          <h3>公开风采展示</h3>
          <div class="list">
            <article class="card" v-for="item in showcase" :key="item.id">
              <div class="stack" style="justify-content: space-between;">
                <strong>{{ item.userDisplayName }} · {{ item.title }}</strong>
                <span class="tag">{{ formatHonorType(item.honorType) }}</span>
              </div>
              <p class="muted">{{ item.showcaseText || item.reason }}</p>
              <p class="muted">奖励积分：{{ item.pointsAwarded }} | 发放时间：{{ formatDate(item.awardedAt) }}</p>
            </article>
            <p v-if="!showcase.length" class="notice">暂无公开风采。</p>
          </div>
        </div>

        <div class="card">
          <h3>我的荣誉记录</h3>
          <div class="list">
            <article class="card" v-for="item in myHonors" :key="item.id">
              <div class="stack" style="justify-content: space-between;">
                <strong>{{ item.title }}</strong>
                <span class="tag">+{{ item.pointsAwarded }} 分</span>
              </div>
              <p class="muted">{{ item.reason }}</p>
              <p class="muted">类型：{{ formatHonorType(item.honorType) }} | 时间：{{ formatDate(item.awardedAt) }}</p>
            </article>
            <p v-if="!myHonors.length" class="notice">暂无个人荣誉记录。</p>
          </div>
        </div>
      </div>

      <div class="card" v-if="isAdmin" style="margin-top: 14px;">
        <h3>优秀志愿者候选评定</h3>
        <div class="list">
          <article class="card" v-for="candidate in candidates" :key="candidate.userId">
            <div class="stack" style="justify-content: space-between;">
              <strong>{{ candidate.displayName }}</strong>
              <span class="tag">综合分 {{ candidate.score }}</span>
            </div>
            <p class="muted">
              服务时长：{{ candidate.totalHours }} 小时 |
              参与次数：{{ candidate.serviceCount }} |
              平均评价：{{ candidate.averageRating }} |
              当前积分：{{ candidate.points }}
            </p>
            <p class="muted">{{ candidate.reason }}</p>
            <div class="grid two">
              <div class="field">
                <label>荣誉类型</label>
                <select v-model="awardForms[candidate.userId].honorType">
                  <option value="EXCELLENT_VOLUNTEER">{{ formatHonorType("EXCELLENT_VOLUNTEER") }}</option>
                  <option value="SERVICE_STAR">{{ formatHonorType("SERVICE_STAR") }}</option>
                  <option value="SPECIAL_CONTRIBUTION">{{ formatHonorType("SPECIAL_CONTRIBUTION") }}</option>
                </select>
              </div>
              <div class="field">
                <label>奖励积分</label>
                <input v-model.number="awardForms[candidate.userId].pointsAwarded" type="number" min="0" />
              </div>
            </div>
            <div class="field" style="margin-top: 10px;">
              <label>荣誉标题</label>
              <input v-model.trim="awardForms[candidate.userId].title" />
            </div>
            <div class="field" style="margin-top: 10px;">
              <label>评定原因</label>
              <textarea v-model.trim="awardForms[candidate.userId].reason"></textarea>
            </div>
            <div class="field" style="margin-top: 10px;">
              <label>风采展示文案</label>
              <textarea v-model.trim="awardForms[candidate.userId].showcaseText"></textarea>
            </div>
            <div class="stack" style="margin-top: 10px;">
              <button class="btn primary" :disabled="awardingId === candidate.userId" @click="award(candidate)">
                {{ awardingId === candidate.userId ? "确认中..." : "确认授予荣誉" }}
              </button>
              <label class="muted">
                <input v-model="awardForms[candidate.userId].publicVisible" type="checkbox" />
                公开展示
              </label>
            </div>
          </article>
          <p v-if="!candidates.length" class="notice">暂无候选数据。</p>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { honorApi } from "../api";
import { authState } from "../stores/auth";
import { formatHonorType } from "../utils/labels";

const message = ref("");
const ok = ref(false);
const awardingId = ref(null);
const showcase = ref([]);
const myHonors = ref([]);
const candidates = ref([]);
const awardForms = reactive({});

const isAdmin = computed(() => authState.user?.role === "ADMIN");

function formatDate(raw) {
  if (!raw) return "-";
  return raw.replace("T", " ").slice(0, 16);
}

function ensureAwardForm(candidate) {
  if (awardForms[candidate.userId]) {
    return;
  }
  awardForms[candidate.userId] = {
    honorType: "EXCELLENT_VOLUNTEER",
    title: "优秀志愿者",
    reason: candidate.reason,
    showcaseText: `${candidate.displayName}在志愿服务中表现稳定，${candidate.reason}`,
    pointsAwarded: 30,
    publicVisible: true
  };
}

async function loadData() {
  const tasks = [honorApi.showcase(), honorApi.my()];
  if (isAdmin.value) {
    tasks.push(honorApi.candidates());
  }
  const [showcaseData, myData, candidateData = []] = await Promise.all(tasks);
  showcase.value = showcaseData;
  myHonors.value = myData;
  candidates.value = candidateData;
  candidateData.forEach(ensureAwardForm);
}

async function award(candidate) {
  const form = awardForms[candidate.userId];
  message.value = "";
  ok.value = false;
  if (!form.title || !form.reason) {
    message.value = "请填写荣誉标题和评定原因";
    return;
  }
  awardingId.value = candidate.userId;
  try {
    await honorApi.award({
      userId: candidate.userId,
      honorType: form.honorType,
      title: form.title,
      reason: form.reason,
      showcaseText: form.showcaseText || null,
      relatedActivityId: null,
      pointsAwarded: Number(form.pointsAwarded || 0),
      publicVisible: Boolean(form.publicVisible)
    });
    ok.value = true;
    message.value = "荣誉已授予，积分和通知已同步处理";
    await loadData();
  } catch (err) {
    message.value = err.message;
  } finally {
    awardingId.value = null;
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
