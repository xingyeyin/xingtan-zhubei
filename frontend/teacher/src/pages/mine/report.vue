<template>
  <view class="page">
    <view class="card">
      <view class="stats-row">
        <view class="stat">
          <text class="stat-num">{{ stats.lessonCount }}</text>
          <text class="stat-label">我的教案</text>
        </view>
        <view class="stat">
          <text class="stat-num">{{ stats.totalGenerations }}</text>
          <text class="stat-label">AI 生成次数</text>
        </view>
        <view class="stat">
          <text class="stat-num">{{ stats.savedHours }}h</text>
          <text class="stat-label">估算节省备课</text>
        </view>
        <view class="stat">
          <text class="stat-num">{{ stats.avgQuality }}</text>
          <text class="stat-label">平均质量分</text>
        </view>
      </view>
    </view>

    <view class="card">
      <view class="card-title">最近教案</view>
      <view v-if="!stats.recentLessons || stats.recentLessons.length === 0" class="empty">还没有教案，去备课吧</view>
      <view v-for="item in stats.recentLessons" :key="item.id" class="lesson-row" @tap="openLesson(item)">
        <view class="lesson-body">
          <text class="lesson-title">{{ item.title }}</text>
          <text class="lesson-meta">{{ item.subject }} · {{ item.grade }} · 质量分 {{ item.qualityScore || "-" }}</text>
        </view>
        <text class="arrow">›</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from "vue";
import { onShow } from "@dcloudio/uni-app";
import { request } from "../../utils/request";

const stats = ref({ lessonCount: 0, totalGenerations: 0, savedHours: 0, avgQuality: 0, recentLessons: [] });

function openLesson(item) {
  uni.navigateTo({ url: "/pages/lesson/editor?id=" + item.id });
}

onShow(async () => {
  try {
    stats.value = await request({ url: "/api/stats/my" });
  } catch (e) {}
});
</script>

<style scoped>
.page { padding: 24rpx; }
.card { background: #fff; border-radius: 22rpx; padding: 28rpx; margin-bottom: 20rpx; box-shadow: 0 4rpx 14rpx rgba(44, 44, 44, 0.04); }
.stats-row { display: flex; }
.stat { flex: 1; text-align: center; }
.stat-num { display: block; font-size: 38rpx; font-weight: 700; color: #2e6b4f; }
.stat-label { display: block; margin-top: 8rpx; font-size: 22rpx; color: #8a8a8a; }
.card-title { font-size: 30rpx; font-weight: 700; margin-bottom: 18rpx; }
.empty { text-align: center; color: #a0a0a0; padding: 40rpx 0; font-size: 26rpx; }
.lesson-row { display: flex; align-items: center; padding: 20rpx 0; border-bottom: 1rpx solid #f3f0e8; }
.lesson-row:last-child { border-bottom: none; }
.lesson-body { flex: 1; }
.lesson-title { display: block; font-size: 28rpx; font-weight: 600; }
.lesson-meta { display: block; margin-top: 6rpx; font-size: 23rpx; color: #a0a0a0; }
.arrow { color: #ccc; font-size: 40rpx; }
</style>
