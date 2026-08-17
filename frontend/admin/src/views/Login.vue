<template>
  <div class="login-page">
    <div class="login-card">
      <div class="brand">
        <div class="logo">🌾</div>
        <div class="name">杏坛智备</div>
        <div class="sub">乡村教师 AI 备课助手 · 管理后台</div>
      </div>
      <el-form label-position="top" @keyup.enter="login">
        <el-form-item label="管理员手机号">
          <el-input v-model="phone" size="large" placeholder="管理员手机号" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="password" size="large" type="password" show-password placeholder="登录密码" />
        </el-form-item>
        <el-button type="primary" size="large" class="login-btn" :loading="loading" @click="login">登 录</el-button>
      </el-form>
      <div class="tip">演示管理员账号：13000000000 / admin123</div>
    </div>
  </div>
</template>

<script setup>
import { ref } from "vue";
import { ElMessage } from "element-plus";
import request from "../api/request";

const phone = ref("13000000000");
const password = ref("admin123");
const loading = ref(false);

async function login() {
  loading.value = true;
  try {
    const res = await request.post("/auth/login", { phone: phone.value, password: password.value });
    const data = res && res.data;
    if (!data || data.role !== "ADMIN") {
      ElMessage.error("该账号不是管理员");
      return;
    }
    localStorage.setItem("xingtan_token", data.token);
    localStorage.setItem("xingtan_admin_name", data.nickname || "管理员");
    ElMessage.success("登录成功");
    window.location.href = "/dashboard";
  } catch (e) {
    ElMessage.error("登录失败，请检查账号密码");
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(150deg, #244c38 0%, #2e6b4f 55%, #f7f4ec 130%);
}
.login-card {
  width: 380px;
  background: #fff;
  border-radius: 18px;
  padding: 42px 40px 30px;
  box-shadow: 0 20px 60px rgba(20, 50, 35, 0.25);
}
.brand { text-align: center; margin-bottom: 28px; }
.logo { font-size: 44px; }
.name { font-size: 26px; font-weight: 800; color: #2e6b4f; margin-top: 6px; }
.sub { font-size: 13px; color: #8a8a8a; margin-top: 6px; }
.login-btn { width: 100%; margin-top: 6px; }
.tip { margin-top: 20px; font-size: 12px; color: #a0a0a0; text-align: center; }
</style>
