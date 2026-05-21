<template>
  <section class="panel">
    <div class="panel-head">
      <h2>捐赠与支持</h2>
      <p>记录社会支持行为，透明展示捐赠信息。</p>
    </div>
    <div class="panel-body">
      <p v-if="message" :class="['notice', ok ? 'success' : 'error']" style="margin-bottom: 12px;">
        {{ message }}
      </p>

      <div class="card" style="margin-bottom: 14px;">
        <h3>发起捐赠</h3>
        <div class="grid two">
          <div class="field">
            <label>捐赠人（留空则默认当前昵称）</label>
            <input v-model.trim="form.donorName" placeholder="例如：张三" />
          </div>
          <div class="field">
            <label>金额</label>
            <input v-model.number="form.amount" type="number" min="0.01" step="0.01" />
          </div>
        </div>
        <div class="field" style="margin-top: 10px;">
          <label>留言</label>
          <textarea v-model.trim="form.message" placeholder="可选，最多500字"></textarea>
        </div>
        <div class="stack" style="margin-top: 12px;">
          <button class="btn primary" :disabled="submitting" @click="donate">
            {{ submitting ? "提交中..." : "确认捐赠" }}
          </button>
        </div>
      </div>

      <div class="grid two">
        <div class="card">
          <h3>我的捐赠记录</h3>
          <p class="muted">累计金额：{{ myTotal }}</p>
          <div class="list">
            <article class="card" v-for="item in myItems" :key="item.id">
              <div class="stack" style="justify-content: space-between;">
                <strong>{{ item.donorName }}</strong>
                <span class="tag">¥ {{ item.amount }}</span>
              </div>
              <p v-if="item.message" class="muted">{{ item.message }}</p>
              <p class="muted">时间：{{ formatDate(item.createdAt) }}</p>
            </article>
            <p v-if="!myItems.length" class="notice">暂无捐赠记录。</p>
          </div>
        </div>

        <div class="card" v-if="canManage">
          <h3>平台捐赠总览</h3>
          <p class="muted">总金额：{{ allTotal }}</p>
          <div class="list">
            <article class="card" v-for="item in allItems" :key="item.id">
              <div class="stack" style="justify-content: space-between;">
                <strong>{{ item.donorName }}（用户ID: {{ item.userId }}）</strong>
                <span class="tag">¥ {{ item.amount }}</span>
              </div>
              <p v-if="item.message" class="muted">{{ item.message }}</p>
              <p class="muted">时间：{{ formatDate(item.createdAt) }}</p>
            </article>
            <p v-if="!allItems.length" class="notice">暂无平台捐赠记录。</p>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { donationApi } from "../api";
import { authState } from "../stores/auth";

const submitting = ref(false);
const message = ref("");
const ok = ref(false);

const form = reactive({
  donorName: "",
  amount: 10,
  message: ""
});

const myItems = ref([]);
const allItems = ref([]);
const myTotal = ref(0);
const allTotal = ref(0);

const canManage = computed(
  () => authState.user?.role === "ADMIN"
);

function formatDate(raw) {
  if (!raw) return "-";
  return raw.replace("T", " ").slice(0, 16);
}

function calcTotal(items) {
  return items.reduce((sum, item) => sum + Number(item.amount || 0), 0).toFixed(2);
}

async function loadData() {
  const tasks = [donationApi.my()];
  if (canManage.value) {
    tasks.push(donationApi.all());
  }
  const [mine, all = { totalAmount: 0, items: [] }] = await Promise.all(tasks);
  myItems.value = mine;
  myTotal.value = calcTotal(mine);
  allItems.value = all.items || [];
  allTotal.value = Number(all.totalAmount || 0).toFixed(2);
}

async function donate() {
  message.value = "";
  ok.value = false;
  if (!form.amount || Number(form.amount) <= 0) {
    message.value = "请输入有效的捐赠金额";
    return;
  }
  submitting.value = true;
  try {
    await donationApi.donate({
      donorName: form.donorName || null,
      amount: Number(form.amount),
      message: form.message || null
    });
    ok.value = true;
    message.value = "捐赠记录提交成功";
    form.donorName = "";
    form.amount = 10;
    form.message = "";
    await loadData();
  } catch (err) {
    message.value = err.message;
  } finally {
    submitting.value = false;
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
