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
            <p>{{ roleLabel }} · 积分 {{ authState.user?.points ?? 0 }}</p>
          </div>
          <button class="btn ghost" @click="logout">退出</button>
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
import { computed, onMounted } from "vue";
import { useRouter } from "vue-router";
import { RouterLink, RouterView } from "vue-router";
import { authApi } from "./api";
import { setUnauthorizedHandler } from "./api/http";
import { authState, clearAuth, isLoggedIn, setAuth } from "./stores/auth";

const router = useRouter();

const loggedIn = computed(() => isLoggedIn());
const canManage = computed(
  () => authState.user?.role === "ADMIN" || authState.user?.role === "ORGANIZER"
);
const isAdmin = computed(() => authState.user?.role === "ADMIN");
const roleLabel = computed(() => {
  if (authState.user?.role === "ADMIN") {
    return "管理员";
  }
  if (authState.user?.role === "ORGANIZER") {
    return "组织方";
  }
  return "志愿者";
});

async function bootstrapMe() {
  if (!isLoggedIn()) {
    return;
  }
  try {
    const user = await authApi.me();
    setAuth(authState.token, user);
  } catch {
    clearAuth();
    router.push("/login");
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
