<template>
  <view class="page">
    <view class="brand">
      <text class="name">注册账号</text>
      <text class="slogan">加入杏坛智备，开启 AI 备课</text>
    </view>

    <view class="form">
      <view class="field">
        <text class="field-label">手机号</text>
        <input class="input" v-model="form.phone" type="number" maxlength="11" placeholder="11 位手机号" />
      </view>
      <view class="field">
        <text class="field-label">昵称</text>
        <input class="input" v-model="form.nickname" placeholder="怎么称呼您" />
      </view>
      <view class="field">
        <text class="field-label">学校</text>
        <input class="input" v-model="form.schoolName" placeholder="如：新乡某乡村小学" />
      </view>
      <view class="field">
        <text class="field-label">任教学科</text>
        <input class="input" v-model="form.subjects" placeholder="如：语文、数学" />
      </view>
      <view class="field">
        <text class="field-label">密码（至少 6 位）</text>
        <input class="input" v-model="form.password" password placeholder="设置登录密码" />
      </view>
      <view class="primary-btn" @tap="register">注 册</view>
      <view class="link-row">
        <text class="link" @tap="goLogin">已有账号？去登录</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { reactive } from "vue";
import { request } from "../../utils/request";
import { setToken, setProfile } from "../../utils/auth";

const form = reactive({ phone: "", nickname: "", schoolName: "", subjects: "", password: "" });

async function register() {
  if (!/^1\d{10}$/.test(form.phone)) return uni.showToast({ title: "请输入正确的手机号", icon: "none" });
  if (!form.nickname) return uni.showToast({ title: "请输入昵称", icon: "none" });
  if (!form.password || form.password.length < 6) return uni.showToast({ title: "密码至少 6 位", icon: "none" });
  try {
    const data = await request({
      url: "/api/auth/register",
      method: "POST",
      data: { ...form }
    });
    setToken(data.token);
    setProfile({ nickname: data.nickname, school: data.schoolName });
    uni.showToast({ title: "注册成功", icon: "success" });
    setTimeout(() => uni.reLaunch({ url: "/pages/index/index" }), 600);
  } catch (e) {}
}

function goLogin() {
  uni.navigateBack({ delta: 1 });
}
</script>

<style scoped>
.page { min-height: 100vh; padding: 90rpx 48rpx; background: linear-gradient(160deg, #e9f2ec 0%, #f7f4ec 55%); }
.brand { text-align: center; margin-bottom: 50rpx; }
.name { font-size: 44rpx; font-weight: 800; color: #2e6b4f; }
.slogan { display: block; margin-top: 12rpx; font-size: 26rpx; color: #8a8a8a; }
.form { background: #fff; border-radius: 28rpx; padding: 40rpx 36rpx; box-shadow: 0 12rpx 36rpx rgba(46, 107, 79, 0.10); }
.field { margin-bottom: 24rpx; }
.field-label { display: block; font-size: 26rpx; color: #555; margin-bottom: 12rpx; }
.input { background: #f7f4ec; border-radius: 14rpx; padding: 22rpx 26rpx; font-size: 30rpx; }
.primary-btn {
  background: #2e6b4f; color: #fff; text-align: center; padding: 26rpx 0;
  border-radius: 16rpx; font-size: 32rpx; font-weight: 700; margin-top: 16rpx;
}
.link-row { text-align: center; margin-top: 26rpx; }
.link { color: #2e6b4f; font-size: 26rpx; }
</style>
