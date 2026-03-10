<template>
  <section class="panel" style="max-width: 620px; margin: 26px auto;">
    <div class="panel-head">
      <h2>新志愿者注册</h2>
      <p>注册后默认角色为志愿者，可参与报名、服务记录与反馈。</p>
    </div>
    <div class="panel-body">
      <div class="grid two">
        <div class="field">
          <label>用户名</label>
          <input v-model.trim="form.username" placeholder="3-30位字母或数字" />
        </div>
        <div class="field">
          <label>昵称</label>
          <input v-model.trim="form.displayName" placeholder="展示名称" />
        </div>
      </div>
      <div class="field" style="margin-top: 12px;">
        <label>密码</label>
        <input v-model="form.password" type="password" placeholder="至少6位" />
      </div>

      <p v-if="message" :class="['notice', ok ? 'success' : 'error']" style="margin-top: 12px;">
        {{ message }}
      </p>

      <div class="stack" style="margin-top: 14px;">
        <button class="btn primary" :disabled="loading" @click="submit">
          {{ loading ? "提交中..." : "注册" }}
        </button>
        <RouterLink class="btn ghost" to="/login">返回登录</RouterLink>
      </div>
    </div>
  </section>
</template>

<script setup>
import { reactive, ref } from "vue";
import { RouterLink, useRouter } from "vue-router";
import { authApi } from "../api";

const router = useRouter();
const loading = ref(false);
const message = ref("");
const ok = ref(false);

const form = reactive({
  username: "",
  displayName: "",
  password: ""
});

async function submit() {
  message.value = "";
  ok.value = false;
  if (!form.username || !form.displayName || !form.password) {
    message.value = "请完整填写注册信息";
    return;
  }
  loading.value = true;
  try {
    await authApi.register({ ...form });
    ok.value = true;
    message.value = "注册成功，即将跳转登录页";
    setTimeout(() => router.push("/login"), 900);
  } catch (err) {
    message.value = err.message;
  } finally {
    loading.value = false;
  }
}
</script>
