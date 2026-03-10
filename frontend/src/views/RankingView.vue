<template>
  <section class="panel">
    <div class="panel-head">
      <h2>志愿者积分排行</h2>
      <p>按积分从高到低展示前 20 名。</p>
    </div>
    <div class="panel-body">
      <p v-if="error" class="notice error">{{ error }}</p>
      <div v-else class="list">
        <article v-for="(item, idx) in rankings" :key="item.userId" class="card">
          <div class="stack" style="justify-content: space-between; align-items: center;">
            <h3>#{{ idx + 1 }} {{ item.displayName }}</h3>
            <span class="tag">{{ item.points }} 分</span>
          </div>
        </article>
        <p v-if="!rankings.length" class="notice">暂无排行数据。</p>
      </div>
    </div>
  </section>
</template>

<script setup>
import { onMounted, ref } from "vue";
import { userApi } from "../api";

const rankings = ref([]);
const error = ref("");

onMounted(async () => {
  try {
    rankings.value = await userApi.ranking();
  } catch (err) {
    error.value = err.message;
  }
});
</script>
