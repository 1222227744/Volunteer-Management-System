<template>
  <section class="panel">
    <div class="panel-head">
      <h2>用户与权限管理</h2>
      <p>仅管理员可访问。可查看用户资料、调整角色权限、维护账号状态与实名审核状态。</p>
    </div>
    <div class="panel-body">
      <p v-if="message" :class="['notice', ok ? 'success' : 'error']" style="margin-bottom: 12px;">
        {{ message }}
      </p>

      <div class="card" style="margin-bottom: 14px;">
        <div class="grid two">
          <div class="field">
            <label>关键字筛选（用户名/昵称）</label>
            <input v-model.trim="keyword" placeholder="输入关键字过滤用户" />
          </div>
          <div class="field">
            <label>统计</label>
            <p class="notice">共 {{ filteredUsers.length }} / {{ users.length }} 个用户</p>
          </div>
        </div>
      </div>

      <div class="list">
        <article class="card" v-for="user in filteredUsers" :key="user.userId">
          <div class="grid three">
            <div>
              <strong>{{ user.displayName }}</strong>
              <p class="muted">用户名：{{ user.username }}</p>
              <p class="muted">用户ID：{{ user.userId }}</p>
              <p class="muted">联系电话：{{ user.phone || "-" }}</p>
              <p class="muted">服务意向：{{ user.serviceIntention || "-" }}</p>
            </div>
            <div>
              <strong>积分</strong>
              <p class="muted">{{ user.points }}</p>
              <p class="muted">账号状态：{{ formatAccountStatus(user.accountStatus) }}</p>
              <p class="muted">实名状态：{{ formatVerificationStatus(user.verificationStatus) }}</p>
              <p v-if="user.verificationComment" class="muted">审核说明：{{ user.verificationComment }}</p>
            </div>
            <div>
              <strong>角色</strong>
              <div class="stack" style="margin-top: 6px;">
                <select v-model="roles[user.userId]">
                  <option value="VOLUNTEER">{{ formatRole("VOLUNTEER") }}</option>
                  <option value="ORGANIZER">{{ formatRole("ORGANIZER") }}</option>
                  <option value="ADMIN">{{ formatRole("ADMIN") }}</option>
                </select>
                <button
                  class="btn primary"
                  :disabled="String(user.userId) === String(authState.user?.id)"
                  @click="saveRole(user)"
                >
                  保存角色
                </button>
              </div>
              <p class="muted" style="margin-top: 6px;">当前角色：{{ formatRole(user.role) }}</p>
              <small class="muted" v-if="String(user.userId) === String(authState.user?.id)">
                当前登录管理员不允许在此页面修改自己的角色
              </small>
              <div class="stack" style="margin-top: 10px;">
                <select v-model="accountStatuses[user.userId]">
                  <option value="ENABLED">{{ formatAccountStatus("ENABLED") }}</option>
                  <option value="DISABLED">{{ formatAccountStatus("DISABLED") }}</option>
                  <option value="LOCKED">{{ formatAccountStatus("LOCKED") }}</option>
                </select>
                <button
                  class="btn warn"
                  :disabled="String(user.userId) === String(authState.user?.id)"
                  @click="saveAccountStatus(user)"
                >
                  保存账号状态
                </button>
              </div>
              <div class="stack" style="margin-top: 10px;">
                <select v-model="verificationStatuses[user.userId]">
                  <option value="UNVERIFIED">{{ formatVerificationStatus("UNVERIFIED") }}</option>
                  <option value="PENDING">{{ formatVerificationStatus("PENDING") }}</option>
                  <option value="VERIFIED">{{ formatVerificationStatus("VERIFIED") }}</option>
                  <option value="REJECTED">{{ formatVerificationStatus("REJECTED") }}</option>
                </select>
                <button class="btn ghost" @click="saveVerification(user)">
                  保存实名状态
                </button>
              </div>
              <div class="field" style="margin-top: 10px;">
                <label>实名审核说明</label>
                <input v-model.trim="verificationComments[user.userId]" placeholder="可填写认证通过或驳回说明" />
              </div>
            </div>
          </div>
        </article>
        <p v-if="!filteredUsers.length" class="notice">没有匹配的用户。</p>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { userApi } from "../api";
import { authState } from "../stores/auth";
import { formatAccountStatus, formatRole, formatVerificationStatus } from "../utils/labels";

const users = ref([]);
const roles = reactive({});
const accountStatuses = reactive({});
const verificationStatuses = reactive({});
const verificationComments = reactive({});
const keyword = ref("");
const message = ref("");
const ok = ref(false);

const filteredUsers = computed(() => {
  const k = keyword.value.toLowerCase();
  if (!k) {
    return users.value;
  }
  return users.value.filter((item) => {
    return (
      item.username.toLowerCase().includes(k) ||
      item.displayName.toLowerCase().includes(k) ||
      String(item.userId).includes(k)
    );
  });
});

async function loadUsers() {
  const data = await userApi.list();
  users.value = data;
  for (const user of data) {
    roles[user.userId] = user.role;
    accountStatuses[user.userId] = user.accountStatus;
    verificationStatuses[user.userId] = user.verificationStatus;
    verificationComments[user.userId] = user.verificationComment || "";
  }
}

async function saveRole(user) {
  message.value = "";
  ok.value = false;
  try {
    await userApi.updateRole(user.userId, roles[user.userId]);
    ok.value = true;
    message.value = `已更新 ${user.displayName} 的角色`;
    await loadUsers();
  } catch (err) {
    message.value = err.message;
  }
}

async function saveAccountStatus(user) {
  message.value = "";
  ok.value = false;
  try {
    await userApi.updateAccountStatus(user.userId, accountStatuses[user.userId]);
    ok.value = true;
    message.value = `已更新 ${user.displayName} 的账号状态`;
    await loadUsers();
  } catch (err) {
    message.value = err.message;
  }
}

async function saveVerification(user) {
  message.value = "";
  ok.value = false;
  try {
    await userApi.updateVerification(
      user.userId,
      verificationStatuses[user.userId],
      verificationComments[user.userId]
    );
    ok.value = true;
    message.value = `已更新 ${user.displayName} 的实名状态`;
    await loadUsers();
  } catch (err) {
    message.value = err.message;
  }
}

onMounted(async () => {
  try {
    await loadUsers();
  } catch (err) {
    message.value = err.message;
  }
});
</script>
