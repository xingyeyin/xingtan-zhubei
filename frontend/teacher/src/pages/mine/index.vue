<template>
  <view class="page">
    <view class="profile" @tap="go('/pages/mine/profile')">
      <view class="avatar">👩‍🏫</view>
      <view class="profile-body">
        <text class="name">{{ nickname }}</text>
        <text class="school">{{ school || "未填写学校" }}</text>
        <text class="school" v-if="subjects">任教：{{ subjects }}</text>
      </view>
      <text class="arrow">›</text>
    </view>

    <view class="card">
      <view class="card-title">我的数据</view>
      <view class="stats-row">
        <view class="stat">
          <text class="stat-num">{{ stats.lessonCount }}</text>
          <text class="stat-label">我的教案</text>
        </view>
        <view class="stat">
          <text class="stat-num">{{ stats.totalGenerations }}</text>
          <text class="stat-label">AI 生成</text>
        </view>
        <view class="stat">
          <text class="stat-num">{{ stats.savedHours }}h</text>
          <text class="stat-label">节省备课</text>
        </view>
        <view class="stat">
          <text class="stat-num">{{ stats.avgQuality }}</text>
          <text class="stat-label">平均质量分</text>
        </view>
      </view>
    </view>

    <view class="card menu">
      <view class="menu-item" @tap="go('/pages/mine/profile')"><text>👤 我的资料</text><text class="arrow">›</text></view>
      <view class="menu-item" @tap="go('/pages/mine/report')"><text>📈 我的使用报告</text><text class="arrow">›</text></view>
      <view class="menu-item" @tap="go('/pages/mine/feedback')"><text>💬 意见反馈</text><text class="arrow">›</text></view>
      <view class="menu-item" @tap="go('/pages/mine/settings')"><text>⚙️ 设置（弱网/语音/密码）</text><text class="arrow">›</text></view>
      <view class="menu-item" @tap="go('/pages/mine/help')"><text>❓ 帮助中心</text><text class="arrow">›</text></view>
      <view class="menu-item" @tap="logout"><text>🚪 退出登录</text><text class="arrow">›</text></view>
    </view>

    <view class="card about">
      <view class="card-title">关于「杏坛智备」</view>
      <view class="about-item"><text class="about-dot">▍</text><text class="about-text">面向乡村教师的 AI 备课助手：新课标对齐、教材适配、即问即得。</text></view>
      <view class="about-item"><text class="about-dot">▍</text><text class="about-text">2025 年全国教育事业发展统计公报：义务教育阶段学校 18.00 万所，专任教师 1069.76 万人。</text></view>
      <view class="about-item"><text class="about-dot">▍</text><text class="about-text">特岗计划实施 20 年，累计为 3 万多所农村学校选聘教师 118 万人。</text></view>
      <view class="version">杏坛智备 v0.2.0 · 青年红色筑梦之旅赛道项目</view>
    </view>
  </view>
</template>

<script setup>
import { ref } from "vue";
import { onShow } from "@dcloudio/uni-app";
import { request } from "../../utils/request";
import { getProfile, setProfile, clearAuth } from "../../utils/auth";

const nickname = ref((getProfile() && getProfile().nickname) || "教师");
const school = ref((getProfile() && getProfile().school) || "");
const subjects = ref("");
const stats = ref({ lessonCount: 0, totalGenerations: 0, savedHours: 0, avgQuality: 0 });

function go(url) {
  uni.navigateTo({ url });
}

onShow(async () => {
  try {
    const profile = await request({ url: "/api/auth/profile" });
    setProfile(profile);
    nickname.value = profile.nickname || "教师";
    school.value = profile.school || "";
    subjects.value = profile.subjects || "";
  } catch (e) {}
  try {
    stats.value = await request({ url: "/api/stats/my" });
  } catch (e) {}
});

async function logout() {
  try {
    await request({ url: "/api/auth/logout", method: "POST" });
  } catch (e) {}
  clearAuth();
  uni.reLaunch({ url: "/pages/auth/login" });
}
</script>

<style scoped>
.page { padding: 24rpx 24rpx 40rpx; }
.profile { display: flex; align-items: center; background: linear-gradient(135deg, #2e6b4f, #3e8a63); border-radius: 24rpx; padding: 36rpx 32rpx; color: #fff; box-shadow: 0 12rpx 32rpx rgba(46, 107, 79, 0.25); }
.avatar { font-size: 84rpx; margin-right: 24rpx; }
.profile-body { flex: 1; }
.name { display: block; font-size: 36rpx; font-weight: 700; }
.school { display: block; margin-top: 8rpx; font-size: 25rpx; opacity: 0.85; }
.arrow { font-size: 44rpx; opacity: 0.8; }
.card { background: #fff; border-radius: 22rpx; padding: 28rpx; margin-top: 24rpx; box-shadow: 0 4rpx 14rpx rgba(44, 44, 44, 0.04); }
.card-title { font-size: 30rpx; font-weight: 700; margin-bottom: 20rpx; }
.stats-row { display: flex; }
.stat { flex: 1; text-align: center; }
.stat-num { display: block; font-size: 36rpx; font-weight: 700; color: #2e6b4f; }
.stat-label { display: block; margin-top: 8rpx; font-size: 22rpx; color: #8a8a8a; }
.menu { padding: 8rpx 28rpx; }
.menu-item { display: flex; justify-content: space-between; align-items: center; padding: 30rpx 0; font-size: 29rpx; border-bottom: 1rpx solid #f3f0e8; }
.menu-item:last-child { border-bottom: none; }
.about-item { display: flex; margin-bottom: 16rpx; }
.about-dot { color: #e3a03c; margin-right: 12rpx; }
.about-text { flex: 1; font-size: 25rpx; line-height: 1.6; color: #555; }
.version { margin-top: 16rpx; text-align: center; font-size: 22rpx; color: #a0a0a0; }
</style>
