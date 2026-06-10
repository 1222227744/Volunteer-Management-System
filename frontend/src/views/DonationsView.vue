<template>
  <section class="panel">
    <div class="panel-head">
      <h2>捐赠与支持</h2>
      <p>按 v3 支付接口流程创建捐赠订单，通过模拟支付回调生成正式捐赠记录。</p>
    </div>
    <div class="panel-body">
      <p v-if="message" :class="['notice', ok ? 'success' : 'error']" style="margin-bottom: 12px;">
        {{ message }}
      </p>

      <div class="card" style="margin-bottom: 14px;">
        <h3>发起捐赠订单</h3>
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
          <button class="btn primary" :disabled="submitting" @click="createOrder">
            {{ submitting ? "创建中..." : "创建捐赠订单" }}
          </button>
        </div>
      </div>

      <div class="grid two">
        <div class="card">
          <h3>我的捐赠订单</h3>
          <p class="muted">待支付订单可直接模拟支付回调，成功后自动生成正式捐赠记录。</p>
          <div class="list">
            <article class="card" v-for="item in myOrders" :key="item.id">
              <div class="stack" style="justify-content: space-between;">
                <strong>{{ item.donorName }}</strong>
                <span class="tag">{{ formatDonationOrderStatus(item.status) }}</span>
              </div>
              <p class="muted">金额：¥ {{ formatAmount(item.amount) }}</p>
              <p v-if="item.message" class="muted">留言：{{ item.message }}</p>
              <p class="muted">回调令牌：{{ item.callbackToken }}</p>
              <p v-if="item.paymentNote" class="muted">支付说明：{{ item.paymentNote }}</p>
              <p class="muted">
                创建时间：{{ formatDate(item.createdAt) }}
                <span v-if="item.paidAt"> | 支付时间：{{ formatDate(item.paidAt) }}</span>
              </p>
              <div v-if="item.status === 'PENDING'" class="stack">
                <button class="btn primary" :disabled="processingId === item.id" @click="simulatePayment(item, 'PAID')">
                  模拟支付成功
                </button>
                <button class="btn warn" :disabled="processingId === item.id" @click="simulatePayment(item, 'FAILED')">
                  模拟支付失败
                </button>
                <button class="btn danger" :disabled="processingId === item.id" @click="simulatePayment(item, 'CANCELLED')">
                  模拟取消支付
                </button>
              </div>
            </article>
            <p v-if="!myOrders.length" class="notice">暂无捐赠订单。</p>
          </div>
        </div>

        <div class="card">
          <h3>我的捐赠记录</h3>
          <p class="muted">累计金额：{{ myTotal }}</p>
          <div class="list">
            <article class="card" v-for="item in myItems" :key="item.id">
              <div class="stack" style="justify-content: space-between;">
                <strong>{{ item.donorName }}</strong>
                <span class="tag">¥ {{ formatAmount(item.amount) }}</span>
              </div>
              <p v-if="item.message" class="muted">{{ item.message }}</p>
              <p class="muted">订单ID：{{ item.orderId || "-" }} | 时间：{{ formatDate(item.createdAt) }}</p>
            </article>
            <p v-if="!myItems.length" class="notice">暂无正式捐赠记录。</p>
          </div>
        </div>
      </div>

      <div class="card" v-if="canManage" style="margin-top: 14px;">
        <h3>平台捐赠总览</h3>
        <p class="muted">总金额：{{ allTotal }}，订单数：{{ allOrders.length }}，正式记录数：{{ allItems.length }}</p>
        <div class="grid two">
          <div>
            <h3>平台订单</h3>
            <div class="list">
              <article class="card" v-for="item in allOrders" :key="item.id">
                <div class="stack" style="justify-content: space-between;">
                  <strong>{{ item.donorName }}（用户ID: {{ item.userId }}）</strong>
                  <span class="tag">{{ formatDonationOrderStatus(item.status) }}</span>
                </div>
                <p class="muted">金额：¥ {{ formatAmount(item.amount) }} | 创建时间：{{ formatDate(item.createdAt) }}</p>
                <p v-if="item.paymentNote" class="muted">支付说明：{{ item.paymentNote }}</p>
              </article>
              <p v-if="!allOrders.length" class="notice">暂无平台捐赠订单。</p>
            </div>
          </div>
          <div>
            <h3>正式捐赠记录</h3>
            <div class="list">
              <article class="card" v-for="item in allItems" :key="item.id">
                <div class="stack" style="justify-content: space-between;">
                  <strong>{{ item.donorName }}（用户ID: {{ item.userId }}）</strong>
                  <span class="tag">¥ {{ formatAmount(item.amount) }}</span>
                </div>
                <p v-if="item.message" class="muted">{{ item.message }}</p>
                <p class="muted">订单ID：{{ item.orderId || "-" }} | 时间：{{ formatDate(item.createdAt) }}</p>
              </article>
              <p v-if="!allItems.length" class="notice">暂无平台捐赠记录。</p>
            </div>
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
import { formatDonationOrderStatus } from "../utils/labels";

const submitting = ref(false);
const processingId = ref(null);
const message = ref("");
const ok = ref(false);

const form = reactive({
  donorName: "",
  amount: 10,
  message: ""
});

const myItems = ref([]);
const myOrders = ref([]);
const allItems = ref([]);
const allOrders = ref([]);
const myTotal = ref(0);
const allTotal = ref(0);

const canManage = computed(
  () => authState.user?.role === "ADMIN"
);

function formatDate(raw) {
  if (!raw) return "-";
  return raw.replace("T", " ").slice(0, 16);
}

function formatAmount(value) {
  return Number(value || 0).toFixed(2);
}

function calcTotal(items) {
  return items.reduce((sum, item) => sum + Number(item.amount || 0), 0).toFixed(2);
}

async function loadData() {
  const tasks = [donationApi.my(), donationApi.myOrders()];
  if (canManage.value) {
    tasks.push(donationApi.all());
  }
  const [mine, orders, all = { totalAmount: 0, items: [], orders: [] }] = await Promise.all(tasks);
  myItems.value = mine;
  myOrders.value = orders;
  myTotal.value = calcTotal(mine);
  allItems.value = all.items || [];
  allOrders.value = all.orders || [];
  allTotal.value = Number(all.totalAmount || 0).toFixed(2);
}

async function createOrder() {
  message.value = "";
  ok.value = false;
  if (!form.amount || Number(form.amount) <= 0) {
    message.value = "请输入有效的捐赠金额";
    return;
  }
  submitting.value = true;
  try {
    const order = await donationApi.createOrder({
      donorName: form.donorName || null,
      amount: Number(form.amount),
      message: form.message || null
    });
    ok.value = true;
    message.value = `捐赠订单创建成功，请使用订单 ${order.id} 的模拟支付按钮完成回调`;
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

async function simulatePayment(order, status) {
  message.value = "";
  ok.value = false;
  processingId.value = order.id;
  const noteMap = {
    PAID: "演示支付成功，生成正式捐赠记录",
    FAILED: "演示支付失败，不生成捐赠记录",
    CANCELLED: "演示取消支付，不生成捐赠记录"
  };
  try {
    await donationApi.simulatePayment(order.id, {
      status,
      callbackToken: order.callbackToken,
      note: noteMap[status]
    });
    ok.value = true;
    message.value = `${formatDonationOrderStatus(status)}，订单状态已更新`;
    await loadData();
  } catch (err) {
    message.value = err.message;
  } finally {
    processingId.value = null;
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
