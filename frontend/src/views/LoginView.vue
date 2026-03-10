<template>
  <section class="panel" style="max-width: 520px; margin: 26px auto;">
    <div class="panel-head">
      <h2>欢迎登录</h2>
      <p>使用管理员、组织方或志愿者账号进入系统。</p>
    </div>
    <div class="panel-body">
      <div class="grid">
        <div class="field">
          <label>用户名</label>
          <input v-model.trim="form.username" placeholder="例如：admin" />
        </div>
        <div class="field">
          <label>密码</label>
          <input v-model="form.password" type="password" placeholder="请输入密码" />
        </div>
      </div>

      <p v-if="error" class="notice error" style="margin-top: 12px;">{{ error }}</p>

      <div class="stack" style="margin-top: 14px;">
        <button class="btn primary" :disabled="loading" @click="submit">
          {{ loading ? "登录中..." : "登录" }}
        </button>
        <RouterLink class="btn ghost" to="/register">去注册</RouterLink>
      </div>
    </div>
  </section>
</template>

<script setup>
import { reactive, ref } from "vue";
import { RouterLink, useRouter } from "vue-router";
import { authApi } from "../api";
import { setAuth } from "../stores/auth";

const router = useRouter();
const loading = ref(false);
const error = ref("");
const form = reactive({
  username: "admin",
  password: "admin123"
});

async function submit() {
  error.value = "";
  if (!form.username || !form.password) {
    error.value = "请输入用户名和密码";
    return;
  }
  loading.value = true;
  try {
    const data = await authApi.login({
      username: form.username,
      password: form.password
    });
    setAuth(data.token, data.user);
    router.push("/activities");
  } catch (err) {
    error.value = err.message;
  } finally {
    loading.value = false;
  }
}
</script>
