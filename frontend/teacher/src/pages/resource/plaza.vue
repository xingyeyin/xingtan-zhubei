<template>
  <view class="page">
    <view class="head-bar">
      <text class="head-title">教研广场</text>
      <text class="head-sub">教师共享教案 · 跨校同课异构</text>
    </view>

    <view v-if="loading" class="empty">加载中…</view>
    <view v-else-if="lessons.length === 0" class="empty">
      <text class="empty-icon">👥</text>
      <text class="empty-text">还没有公开教案，去「我的备课」把你的好教案分享出来吧</text>
    </view>

    <view v-for="item in lessons" :key="item.id" class="lesson-card" @tap="open(item)">
      <view class="lesson-top">
        <text class="lesson-title">{{ item.title }}</text>
        <text class="score">{{ item.qualityScore || "-" }}分</text>
      </view>
      <view class="tags">
        <text class="tag">{{ item.subject }}</text>
        <text class="tag">{{ item.grade }}</text>
        <text class="tag">{{ item.textbook }}</text>
      </view>
      <view class="lesson-foot">
        <text class="time">{{ fmt(item.createdAt) }}</text>
        <text class="view">点击查看详情 ›</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from "vue";
import { onShow } from "@dcloudio/uni-app";
import { request } from "../../utils/request";

const lessons = ref([]);
const loading = ref(true);

function fmt(t) {
  return t ? String(t).replace("T", " ").slice(0, 16) : "";
}
function open(item) {
  uni.navigateTo({ url: "/pages/lesson/editor?id=" + item.id });
}

onShow(async () => {
  loading.value = true;
  try {
    const list = await request({ url: "/api/lessons/public?limit=50" });
    lessons.value = list || [];
  } catch (e) {
  } finally {
    loading.value = false;
  }
});
</script>

<style scoped>
.page { padding: 24rpx 24rpx 40rpx; }
.head-bar { margin-bottom: 22rpx; }
.head-title { font-size: 38rpx; font-weight: 700; display: block; }
.head-sub { display: block; margin-top: 8rpx; font-size: 24rpx; color: #8a8a8a; }
.empty { text-align: center; padding: 120rpx 40rpx; color: #999; }
.empty-icon { font-size: 80rpx; display: block; }
.empty-text { display: block; margin-top: 20rpx; font-size: 26rpx; line-height: 1.6; }
.lesson-card { background: #fff; border-radius: 22rpx; padding: 28rpx; margin-bottom: 20rpx; box-shadow: 0 4rpx 14rpx rgba(44, 44, 44, 0.04); }
.lesson-top { display: flex; justify-content: space-between; align-items: center; }
.lesson-title { font-size: 31rpx; font-weight: 700; flex: 1; }
.score { background: #fdf3e3; color: #b57a1f; font-size: 23rpx; padding: 6rpx 16rpx; border-radius: 999rpx; }
.tags { display: flex; flex-wrap: wrap; margin-top: 14rpx; }
.tag { background: #f1efe7; color: #6b6b6b; font-size: 22rpx; padding: 6rpx 16rpx; border-radius: 8rpx; margin-right: 12rpx; }
.lesson-foot { display: flex; justify-content: space-between; margin-top: 16rpx; }
.time { font-size: 23rpx; color: #a0a0a0; }
.view { font-size: 23rpx; color: #2e6b4f; }
</style>
