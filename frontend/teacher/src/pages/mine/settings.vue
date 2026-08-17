<template>
  <view class="page">
    <view class="card">
      <view class="card-title">使用偏好</view>
      <view class="switch-row">
        <view><text class="sw-name">弱网模式</text><text class="sw-desc">低带宽下压缩请求，乡村网络更流畅</text></view>
        <switch :checked="weak" color="#2e6b4f" @change="e => save('weak', e.detail.value)" />
      </view>
      <view class="switch-row">
        <view><text class="sw-name">语音输入</text><text class="sw-desc">备课输入支持语音（浏览器需支持）</text></view>
        <switch :checked="voice" color="#2e6b4f" @change="e => save('voice', e.detail.value)" />
      </view>
      <view class="switch-row">
        <view><text class="sw-name">消息通知</text><text class="sw-desc">生成完成、系统公告提醒</text></view>
        <switch :checked="notify" color="#2e6b4f" @change="e => save('notify', e.detail.value)" />
      </view>
    </view>

    <view class="card">
      <view class="card-title">修改密码</view>
      <input class="input" v-model="pwd.oldPassword" password placeholder="原密码" />
      <input class="input" v-model="pwd.newPassword" password placeholder="新密码（至少 6 位）" />
      <input class="input" v-model="pwd.confirm" password placeholder="确认新密码" />
      <view class="primary-btn" @tap="changePassword">修改密码</view>
    </view>
  </view>
</template>

<script setup>
import { ref } from "vue";
import { onLoad } from "@dcloudio/uni-app";
import { request } from "../../utils/request";

const weak = ref(true);
const voice = ref(false);
const notify = ref(true);
const pwd = ref({ oldPassword: "", newPassword: "", confirm: "" });

onLoad(() => {
  weak.value = uni.getStorageSync("setting_weak") !== "0";
  voice.value = uni.getStorageSync("setting_voice") === "1";
  notify.value = uni.getStorageSync("setting_notify") !== "0";
});

function save(key, val) {
  uni.setStorageSync("setting_" + key, val ? "1" : "0");
  uni.showToast({ title: "已保存", icon: "success" });
}

async function changePassword() {
  if (pwd.value.newPassword.length < 6) return uni.showToast({ title: "新密码至少 6 位", icon: "none" });
  if (pwd.value.newPassword !== pwd.value.confirm) return uni.showToast({ title: "两次输入不一致", icon: "none" });
  try {
    await request({
      url: "/api/auth/password",
      method: "POST",
      data: { oldPassword: pwd.value.oldPassword, newPassword: pwd.value.newPassword }
    });
    uni.showToast({ title: "密码已修改", icon: "success" });
    pwd.value = { oldPassword: "", newPassword: "", confirm: "" };
  } catch (e) {}
}
</script>

<style scoped>
.page { padding: 24rpx; }
.card { background: #fff; border-radius: 22rpx; padding: 28rpx 32rpx; margin-bottom: 20rpx; box-shadow: 0 4rpx 14rpx rgba(44, 44, 44, 0.04); }
.card-title { font-size: 30rpx; font-weight: 700; margin-bottom: 18rpx; }
.switch-row { display: flex; justify-content: space-between; align-items: center; padding: 20rpx 0; border-bottom: 1rpx solid #f3f0e8; }
.switch-row:last-child { border-bottom: none; }
.sw-name { display: block; font-size: 28rpx; }
.sw-desc { display: block; margin-top: 6rpx; font-size: 23rpx; color: #a0a0a0; }
.input { background: #f7f4ec; border-radius: 14rpx; padding: 20rpx 24rpx; font-size: 28rpx; margin-bottom: 18rpx; }
.primary-btn { background: #2e6b4f; color: #fff; text-align: center; padding: 24rpx 0; border-radius: 16rpx; font-size: 30rpx; font-weight: 700; margin-top: 8rpx; }
</style>
