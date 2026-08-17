<template>
  <view class="page">
    <view class="brand">
      <text class="logo">🌾</text>
      <text class="name">杏坛智备</text>
      <text class="slogan">让每一节乡村课都备得起、备得好</text>
    </view>

    <view class="form">
      <view class="field">
        <text class="field-label">手机号</text>
        <input class="input" v-model="phone" type="number" maxlength="11" placeholder="请输入手机号" />
      </view>
      <view class="field">
        <text class="field-label">密码</text>
        <input class="input" v-model="password" password placeholder="请输入密码" />
      </view>
      <view class="primary-btn" @tap="login">登 录</view>
      <view class="link-row">
        <text class="link" @tap="goRegister">还没有账号？立即注册</text>
      </view>
      <view class="demo-tip">演示账号：13800000000 / 123456（张老师 13900000000、李老师 13700000000）</view>
    </view>
  </view>
</template>

<script setup>
import { ref } from "vue";
import { request } from "../../utils/request";
import { setToken, setProfile } from "../../utils/auth";

const phone = ref("13800000000");
const password = ref("123456");

async function login() {
  if (!/^1\d{10}$/.test(phone.value)) return uni.showToast({ title: "请输入正确的手机号", icon: "none" });
  if (!password.value) return uni.showToast({ title: "请输入密码", icon: "none" });
  try {
    const data = await request({
      url: "/api/auth/login",
      method: "POST",
      data: { phone: phone.value, password: password.value }
    });
    setToken(data.token);
    setProfile({ nickname: data.nickname, school: data.schoolName });
    uni.showToast({ title: "登录成功", icon: "success" });
    setTimeout(() => uni.reLaunch({ url: "/pages/index/index" }), 600);
  } catch (e) {}
}

function goRegister() {
  uni.navigateTo({ url: "/pages/auth/register" });
}
</script>

<style scoped>
.page { min-height: 100vh; padding: 100rpx 48rpx; background: linear-gradient(160deg, #e9f2ec 0%, #f7f4ec 55%); }
.brand { text-align: center; margin-bottom: 70rpx; }
.logo { font-size: 90rpx; display: block; }
.name { display: block; margin-top: 18rpx; font-size: 52rpx; font-weight: 800; color: #2e6b4f; }
.slogan { display: block; margin-top: 14rpx; font-size: 26rpx; color: #8a8a8a; }
.form { background: #fff; border-radius: 28rpx; padding: 44rpx 36rpx; box-shadow: 0 12rpx 36rpx rgba(46, 107, 79, 0.10); }
.field { margin-bottom: 28rpx; }
.field-label { display: block; font-size: 26rpx; color: #555; margin-bottom: 12rpx; }
.input { background: #f7f4ec; border-radius: 14rpx; padding: 22rpx 26rpx; font-size: 30rpx; }
.primary-btn {
  background: #2e6b4f; color: #fff; text-align: center; padding: 26rpx 0;
  border-radius: 16rpx; font-size: 32rpx; font-weight: 700; margin-top: 16rpx;
}
.link-row { text-align: center; margin-top: 26rpx; }
.link { color: #2e6b4f; font-size: 26rpx; }
.demo-tip { margin-top: 30rpx; font-size: 22rpx; color: #a0a0a0; text-align: center; line-height: 1.6; }
</style>
