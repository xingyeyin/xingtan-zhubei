<template>
  <view class="page">
    <view class="head-bar">
      <text class="head-title">我的备课</text>
      <view class="head-actions">
        <view class="add-btn ghost" @tap="goUpload">导入课件</view>
        <view class="add-btn" @tap="goWizard">＋ 新建</view>
      </view>
    </view>

    <view class="search-row">
      <input class="search-input" v-model="keyword" placeholder="搜索教案标题" confirm-type="search" @confirm="load(1)" />
      <view class="search-btn" @tap="load(1)">搜索</view>
    </view>

    <view v-if="loading" class="empty">加载中…</view>
    <view v-else-if="lessons.length === 0" class="empty">
      <text class="empty-icon">📭</text>
      <text class="empty-text">还没有教案，点右上角开始第一份备课</text>
      <view class="empty-btn" @tap="goWizard">开始备课</view>
    </view>

    <view v-for="item in lessons" :key="item.id" class="lesson-card" @tap="openLesson(item)">
      <view class="lesson-top">
        <text class="lesson-title">{{ item.title }}</text>
        <text class="score">{{ item.qualityScore || "-" }}分</text>
      </view>
      <view class="tags">
        <text class="tag">{{ item.subject }}</text>
        <text class="tag">{{ item.grade }}</text>
        <text class="tag">{{ item.textbook }}</text>
        <text class="tag">{{ item.lessonType }}</text>
      </view>
      <text class="lesson-time">{{ formatTime(item.createdAt) }}</text>
      <view class="lesson-ops">
        <text class="op" @tap.stop="duplicateLesson(item)">复制</text>
        <text class="op danger" @tap.stop="removeLesson(item)">删除</text>
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
const keyword = ref("");

function goWizard() {
  uni.navigateTo({ url: "/pages/lesson/wizard" });
}
function goUpload() {
  uni.navigateTo({ url: "/pages/lesson/upload" });
}
function openLesson(item) {
  uni.navigateTo({ url: "/pages/lesson/editor?id=" + item.id });
}
function formatTime(t) {
  return t ? String(t).replace("T", " ").slice(0, 16) : "";
}

onShow(async () => {
  loading.value = true;
  try {
    const url = "/api/lessons?page=1&size=50" + (keyword.value ? "&keyword=" + encodeURIComponent(keyword.value) : "");
    const page = await request({ url });
    lessons.value = page.records || [];
  } catch (e) {
  } finally {
    loading.value = false;
  }
});

async function duplicateLesson(item) {
  try {
    await request({ url: "/api/lessons/" + item.id + "/duplicate", method: "POST" });
    uni.showToast({ title: "已复制", icon: "success" });
    load(1);
  } catch (e) {}
}

function removeLesson(item) {
  uni.showModal({
    title: "删除教案",
    content: "确定删除「" + item.title + "」？删除后不可恢复。",
    confirmColor: "#c0392b",
    success: async (res) => {
      if (!res.confirm) return;
      try {
        await request({ url: "/api/lessons/" + item.id, method: "DELETE" });
        uni.showToast({ title: "已删除", icon: "success" });
        load(1);
      } catch (e) {}
    }
  });
}
</script>

<style scoped>
.page { padding: 24rpx 24rpx 40rpx; }
.head-bar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24rpx; }
.head-title { font-size: 38rpx; font-weight: 700; }
.head-actions { display: flex; gap: 14rpx; }
.add-btn { background: #2e6b4f; color: #fff; font-size: 26rpx; padding: 14rpx 30rpx; border-radius: 999rpx; }
.add-btn.ghost { background: #fff; color: #2e6b4f; border: 2rpx solid #2e6b4f; }
.search-row { display: flex; gap: 14rpx; margin-bottom: 20rpx; }
.search-input { flex: 1; background: #fff; border-radius: 999rpx; padding: 18rpx 28rpx; font-size: 27rpx; }
.search-btn { background: #2e6b4f; color: #fff; border-radius: 999rpx; padding: 18rpx 34rpx; font-size: 26rpx; }
.empty { text-align: center; padding: 140rpx 40rpx; color: #999; }
.empty-icon { font-size: 90rpx; display: block; }
.empty-text { display: block; margin-top: 24rpx; font-size: 27rpx; }
.empty-btn { display: inline-block; margin-top: 32rpx; background: #2e6b4f; color: #fff; padding: 16rpx 44rpx; border-radius: 999rpx; font-size: 27rpx; }
.lesson-card { background: #fff; border-radius: 22rpx; padding: 28rpx; margin-bottom: 20rpx; box-shadow: 0 4rpx 14rpx rgba(44, 44, 44, 0.04); }
.lesson-top { display: flex; justify-content: space-between; align-items: center; }
.lesson-title { font-size: 32rpx; font-weight: 700; flex: 1; }
.score { background: #fdf3e3; color: #b57a1f; font-size: 23rpx; padding: 6rpx 16rpx; border-radius: 999rpx; }
.tags { display: flex; flex-wrap: wrap; margin-top: 16rpx; }
.tag { background: #f1efe7; color: #6b6b6b; font-size: 22rpx; padding: 6rpx 16rpx; border-radius: 8rpx; margin-right: 12rpx; margin-bottom: 8rpx; }
.lesson-time { display: block; margin-top: 14rpx; font-size: 23rpx; color: #a0a0a0; }
.lesson-ops { display: flex; justify-content: flex-end; gap: 30rpx; margin-top: 14rpx; padding-top: 14rpx; border-top: 1rpx solid #f3f0e8; }
.op { font-size: 25rpx; color: #2e6b4f; }
.op.danger { color: #c0392b; }
</style>
