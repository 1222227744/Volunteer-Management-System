<template>
  <div class="app-shell">
    <header v-if="loggedIn" class="topbar">
      <div class="topbar-main">
        <div class="brand">
          <div class="brand-dot"></div>
          <div>
            <h1>志愿者服务管理系统</h1>
            <p>Volunteer Service Platform</p>
          </div>
        </div>
        <div class="user-box">
          <div>
            <strong>{{ authState.user?.displayName }}</strong>
            <p>{{ roleLabel }} · 积分 {{ authState.user?.points ?? 0 }} · {{ verificationLabel }}</p>
          </div>
          <button class="btn ghost" @click="toggleProfilePanel">资料</button>
          <button class="btn ghost" @click="logout">退出</button>
        </div>
      </div>
      <div v-if="showProfilePanel" class="card profile-card">
        <h3>我的资料</h3>
        <p v-if="profileMessage" :class="['notice', profileOk ? 'success' : 'error']">{{ profileMessage }}</p>
        <div class="grid three">
          <div class="field">
            <label>昵称</label>
            <input v-model.trim="profileForm.displayName" />
          </div>
          <div class="field">
            <label>联系电话</label>
            <input v-model.trim="profileForm.phone" placeholder="用于活动联系" />
          </div>
          <div class="field">
            <label>服务意向</label>
            <input v-model.trim="profileForm.serviceIntention" placeholder="例如：环保清洁、社区陪伴" />
          </div>
        </div>
        <div class="stack" style="margin-top: 12px;">
          <button class="btn primary" @click="saveProfile">保存资料</button>
          <span class="muted">账号状态：{{ accountStatusLabel }}</span>
        </div>
      </div>
      <nav class="nav">
        <RouterLink to="/activities">活动</RouterLink>
        <RouterLink v-if="canManage" to="/activity-ops">活动执行</RouterLink>
        <RouterLink to="/my-registrations">我的报名</RouterLink>
        <RouterLink to="/service-records">服务记录</RouterLink>
        <RouterLink to="/contents">内容发布</RouterLink>
        <RouterLink to="/donations">捐赠</RouterLink>
        <RouterLink to="/feedbacks">反馈</RouterLink>
        <RouterLink v-if="canManage" to="/resources">资源对接</RouterLink>
        <RouterLink to="/ranking">排行榜</RouterLink>
        <RouterLink to="/announcements">公告</RouterLink>
        <RouterLink to="/notifications">通知</RouterLink>
        <RouterLink v-if="canManage" to="/dashboard">统计</RouterLink>
        <RouterLink v-if="isAdmin" to="/users-admin">用户管理</RouterLink>
        <RouterLink v-if="isAdmin" to="/audit-logs">审计日志</RouterLink>
      </nav>
    </header>

    <main class="page-wrap">
      <RouterView />
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { RouterLink, RouterView } from "vue-router";
import { authApi, userApi } from "./api";
import { setUnauthorizedHandler } from "./api/http";
import { authState, clearAuth, isLoggedIn, setAuth } from "./stores/auth";
import { formatAccountStatus, formatVerificationStatus } from "./utils/labels";

const router = useRouter();

const loggedIn = computed(() => isLoggedIn());
const showProfilePanel = ref(false);
const profileMessage = ref("");
const profileOk = ref(false);
const profileForm = reactive({
  displayName: "",
  phone: "",
  serviceIntention: ""
});
const canManage = computed(
  () => authState.user?.role === "ADMIN" || authState.user?.role === "ORGANIZER"
);
const isAdmin = computed(() => authState.user?.role === "ADMIN");
const accountStatusLabel = computed(() => formatAccountStatus(authState.user?.accountStatus));
const verificationLabel = computed(() => formatVerificationStatus(authState.user?.verificationStatus));
const roleLabel = computed(() => {
  if (authState.user?.role === "ADMIN") {
    return "管理员";
  }
  if (authState.user?.role === "ORGANIZER") {
    return "组织方";
  }
  return "志愿者";
});

function syncProfileForm() {
  profileForm.displayName = authState.user?.displayName || "";
  profileForm.phone = authState.user?.phone || "";
  profileForm.serviceIntention = authState.user?.serviceIntention || "";
}

function toggleProfilePanel() {
  showProfilePanel.value = !showProfilePanel.value;
  profileMessage.value = "";
  profileOk.value = false;
  if (showProfilePanel.value) {
    syncProfileForm();
  }
}

async function bootstrapMe() {
  if (!isLoggedIn()) {
    return;
  }
  try {
    const user = await authApi.me();
    setAuth(authState.token, user);
    syncProfileForm();
  } catch {
    clearAuth();
    router.push("/login");
  }
}

async function saveProfile() {
  profileMessage.value = "";
  profileOk.value = false;
  if (!profileForm.displayName) {
    profileMessage.value = "昵称不能为空";
    return;
  }
  try {
    const user = await userApi.updateMyProfile({ ...profileForm });
    setAuth(authState.token, user);
    syncProfileForm();
    profileOk.value = true;
    profileMessage.value = "资料已更新";
  } catch (err) {
    profileMessage.value = err.message;
  }
}

async function logout() {
  try {
    await authApi.logout();
  } catch {
    // token may already be invalid, just clear local state
  }
  clearAuth();
  router.push("/login");
}

onMounted(() => {
  setUnauthorizedHandler(() => {
    router.push("/login");
  });
  bootstrapMe();
});
</script>
