<template>
  <section class="panel">
    <div class="panel-head">
      <h2>运营统计看板</h2>
      <p>用于组织方与管理员监控平台运行情况。</p>
    </div>
    <div class="panel-body">
      <p v-if="!canManage" class="notice error">当前账号无权限访问统计看板。</p>
      <template v-else>
        <p v-if="error" class="notice error">{{ error }}</p>
        <div v-else class="stats">
          <article class="stat">
            <h4>用户总数</h4>
            <strong>{{ stats.userCount ?? 0 }}</strong>
          </article>
          <article class="stat">
            <h4>活动总数</h4>
            <strong>{{ stats.activityCount ?? 0 }}</strong>
          </article>
          <article class="stat">
            <h4>报名总数</h4>
            <strong>{{ stats.registrationCount ?? 0 }}</strong>
          </article>
          <article class="stat">
            <h4>完成报名</h4>
            <strong>{{ stats.completedRegistrationCount ?? 0 }}</strong>
          </article>
          <article class="stat">
            <h4>累计服务时长</h4>
            <strong>{{ stats.totalServiceHours ?? 0 }}</strong>
          </article>
          <article class="stat">
            <h4>待审核内容</h4>
            <strong>{{ stats.pendingContentCount ?? 0 }}</strong>
          </article>
          <article class="stat">
            <h4>待处理反馈</h4>
            <strong>{{ stats.feedbackOpenCount ?? 0 }}</strong>
          </article>
          <article class="stat">
            <h4>捐赠总金额</h4>
            <strong>{{ stats.donationTotalAmount ?? 0 }}</strong>
          </article>
        </div>
      </template>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { dashboardApi } from "../api";
import { authState } from "../stores/auth";

// 展示层：对应 SRS FR-08 统计分析看板。
const stats = ref({});
const error = ref("");

const canManage = computed(
  () => authState.user?.role === "ADMIN" || authState.user?.role === "ORGANIZER"
);

onMounted(async () => {
  if (!canManage.value) {
    return;
  }
  try {
    stats.value = await dashboardApi.stats();
  } catch (err) {
    error.value = err.message;
  }
});
</script>
