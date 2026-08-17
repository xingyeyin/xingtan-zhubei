<template>
  <view class="page">
    <view class="tabs">
      <text v-for="t in tabs" :key="t.value" class="tab" :class="{ on: tab === t.value }" @tap="switchTab(t.value)">{{ t.name }}</text>
    </view>

    <view v-if="loading" class="empty">加载中…</view>
    <view v-else-if="docs.length === 0" class="empty">
      <text class="empty-icon">📚</text>
      <text class="empty-text">暂无资源</text>
    </view>

    <view v-for="doc in docs" :key="doc.id" class="doc-card">
      <view class="doc-head">
        <view class="doc-icon" :class="'ic-' + doc.docType.toLowerCase()">{{ docIcon(doc.docType) }}</view>
        <view class="doc-body">
          <text class="doc-title">{{ doc.title }}</text>
          <view class="doc-meta">
            <text class="doc-tag">{{ typeName(doc.docType) }}</text>
            <text v-if="doc.subject" class="doc-tag">{{ doc.subject }}</text>
            <text v-if="doc.grade" class="doc-tag">{{ doc.grade }}</text>
          </view>
        </view>
      </view>
      <view class="doc-foot">
        <text class="doc-date">{{ formatTime(doc.createdAt) }}</text>
        <view class="use-btn" @tap="useDoc(doc)">去备课</view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from "vue";
import { onShow } from "@dcloudio/uni-app";
import { request } from "../../utils/request";

const tabs = [
  { name: "全部", value: "" },
  { name: "课标", value: "STANDARD" },
  { name: "教材", value: "TEXTBOOK" },
  { name: "模板", value: "TEMPLATE" },
  { name: "乡土案例", value: "CASE" }
];
const tab = ref("");
const docs = ref([]);
const loading = ref(true);

function typeName(t) {
  return { STANDARD: "课标", TEXTBOOK: "教材", TEMPLATE: "模板", CASE: "乡土案例" }[t] || "资源";
}
function docIcon(t) {
  return { STANDARD: "📜", TEXTBOOK: "📖", TEMPLATE: "📋", CASE: "🌾" }[t] || "📁";
}
function formatTime(t) {
  return t ? String(t).replace("T", " ").slice(0, 16) : "";
}
function switchTab(v) {
  tab.value = v;
  load();
}
async function load() {
  loading.value = true;
  try {
    const url = "/api/kb/documents?page=1&size=200" + (tab.value ? "&docType=" + tab.value : "");
    const page = await request({ url });
    docs.value = page.records || [];
  } catch (e) {
  } finally {
    loading.value = false;
  }
}
function useDoc(doc) {
  uni.navigateTo({
    url: "/pages/lesson/wizard?subject=" + (doc.subject || "") + "&title=" + encodeURIComponent(doc.title)
  });
}

onShow(load);
</script>

<style scoped>
.page { padding: 24rpx 24rpx 40rpx; }
.tabs { display: flex; background: #fff; border-radius: 16rpx; padding: 8rpx; margin-bottom: 24rpx; box-shadow: 0 4rpx 14rpx rgba(44, 44, 44, 0.04); }
.tab { flex: 1; text-align: center; padding: 16rpx 0; font-size: 26rpx; color: #8a8a8a; border-radius: 12rpx; }
.tab.on { background: #2e6b4f; color: #fff; font-weight: 600; }
.empty { text-align: center; padding: 120rpx 0; color: #999; }
.empty-icon { font-size: 80rpx; display: block; }
.empty-text { display: block; margin-top: 20rpx; font-size: 26rpx; }
.doc-card { background: #fff; border-radius: 22rpx; padding: 26rpx; margin-bottom: 20rpx; box-shadow: 0 4rpx 14rpx rgba(44, 44, 44, 0.04); }
.doc-head { display: flex; }
.doc-icon { width: 84rpx; height: 84rpx; border-radius: 20rpx; display: flex; align-items: center; justify-content: center; font-size: 40rpx; flex-shrink: 0; }
.ic-standard { background: #e9f2ec; }
.ic-textbook { background: #eef0fa; }
.ic-template { background: #fdf3e3; }
.ic-case { background: #fbeceb; }
.doc-body { flex: 1; margin-left: 20rpx; }
.doc-title { font-size: 28rpx; font-weight: 600; line-height: 1.45; }
.doc-meta { display: flex; flex-wrap: wrap; margin-top: 12rpx; }
.doc-tag { background: #f1efe7; color: #6b6b6b; font-size: 22rpx; padding: 4rpx 14rpx; border-radius: 8rpx; margin-right: 10rpx; }
.doc-foot { display: flex; justify-content: space-between; align-items: center; margin-top: 18rpx; padding-top: 18rpx; border-top: 1rpx solid #f3f0e8; }
.doc-date { font-size: 22rpx; color: #a0a0a0; }
.use-btn { background: #e9f2ec; color: #2e6b4f; font-size: 24rpx; padding: 10rpx 26rpx; border-radius: 999rpx; }
</style>
