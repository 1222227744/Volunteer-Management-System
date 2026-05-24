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
        <div v-else>
          <div class="stack" style="justify-content: space-between; margin-bottom: 12px;">
            <p class="notice" style="margin: 0;">统计范围：{{ stats.scopeLabel || "未定义" }}</p>
            <button class="btn ghost" :disabled="exporting" @click="exportStats">
              {{ exporting ? "导出中..." : "导出统计 CSV" }}
            </button>
          </div>
          <div class="stats">
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
              <strong>{{ stats.canViewFeedbackMetrics ? (stats.feedbackOpenCount ?? 0) : "-" }}</strong>
            </article>
            <article class="stat">
              <h4>捐赠总金额</h4>
              <strong>{{ stats.canViewDonationMetrics ? (stats.donationTotalAmount ?? 0) : "-" }}</strong>
            </article>
            <article class="stat">
              <h4>待处理故障</h4>
              <strong>{{ stats.incidentOpenCount ?? 0 }}</strong>
            </article>
          </div>
          <div class="grid two" style="margin-top: 14px;">
            <article class="card">
              <h3>活动状态分布</h3>
              <p v-for="item in activityStatusRows" :key="item.key" class="muted">
                {{ item.label }}：{{ item.value }}
              </p>
            </article>
            <article class="card">
              <h3>报名状态分布</h3>
              <p v-for="item in registrationStatusRows" :key="item.key" class="muted">
                {{ item.label }}：{{ item.value }}
              </p>
            </article>
          </div>
          <div class="card" style="margin-top: 14px;">
            <h3>近 6 个月活动趋势</h3>
            <div class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>月份</th>
                    <th>活动数量</th>
                    <th>报名数量</th>
                    <th>完成数量</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="item in stats.activityTrendStats || []" :key="item.month">
                    <td>{{ item.month }}</td>
                    <td>{{ item.activityCount }}</td>
                    <td>{{ item.registrationCount }}</td>
                    <td>{{ item.completedRegistrationCount }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
          <div class="card" style="margin-top: 14px;">
            <h3>资源对接统计</h3>
            <div class="grid three">
              <p class="muted">资源总数：{{ stats.resourceStats?.resourceCount ?? 0 }}</p>
              <p class="muted">待匹配需求：{{ stats.resourceStats?.openNeedCount ?? 0 }}</p>
              <p class="muted">完成匹配：{{ stats.resourceStats?.completedMatchCount ?? 0 }}</p>
            </div>
          </div>
        </div>
      </template>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { dashboardApi } from "../api";
import { authState } from "../stores/auth";
import { formatActivityStatus, formatRegistrationStatus } from "../utils/labels";

// 展示层：对应 SRS FR-08 统计分析看板。
const stats = ref({});
const error = ref("");
const exporting = ref(false);

const canManage = computed(
  () => authState.user?.role === "ADMIN" || authState.user?.role === "ORGANIZER"
);

const activityStatusRows = computed(() =>
  Object.entries(stats.value.activityStatusStats || {}).map(([key, value]) => ({
    key,
    value,
    label: formatActivityStatus(key)
  }))
);

const registrationStatusRows = computed(() =>
  Object.entries(stats.value.registrationStatusStats || {}).map(([key, value]) => ({
    key,
    value,
    label: formatRegistrationStatus(key)
  }))
);

async function exportStats() {
  error.value = "";
  exporting.value = true;
  try {
    await dashboardApi.exportStats();
  } catch (err) {
    error.value = err.message;
  } finally {
    exporting.value = false;
  }
}

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
