<template>
  <section class="panel">
    <div class="panel-head">
      <h2>用户与权限管理</h2>
      <p>仅管理员可访问。可查看用户列表并调整角色权限。</p>
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
            </div>
            <div>
              <strong>积分</strong>
              <p class="muted">{{ user.points }}</p>
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
import { formatRole } from "../utils/labels";

const users = ref([]);
const roles = reactive({});
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

onMounted(async () => {
  try {
    await loadUsers();
  } catch (err) {
    message.value = err.message;
  }
});
</script>
