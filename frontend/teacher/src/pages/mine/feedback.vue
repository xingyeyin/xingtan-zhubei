<template>
  <view class="page">
    <view class="card">
      <text class="label">反馈类型</text>
      <view class="chip-wrap">
        <text v-for="t in types" :key="t.value" class="chip" :class="{ on: type === t.value }" @tap="type = t.value">{{ t.name }}</text>
      </view>
      <text class="label">反馈内容</text>
      <textarea class="textarea" v-model="content" placeholder="请告诉我们您的使用感受、遇到的问题或建议…（例如：希望增加 XX 学科的教案模板）" />
      <view class="primary-btn" @tap="submit">提交反馈</view>
    </view>
  </view>
</template>

<script setup>
import { ref } from "vue";
import { request } from "../../utils/request";

const types = [
  { name: "用得上", value: "USEFUL" },
  { name: "需修改", value: "NEED_FIX" },
  { name: "问题反馈", value: "REPORT" },
  { name: "建议", value: "SUGGEST" }
];
const type = ref("USEFUL");
const content = ref("");

async function submit() {
  if (!content.value.trim()) return uni.showToast({ title: "请填写反馈内容", icon: "none" });
  try {
    await request({
      url: "/api/feedbacks",
      method: "POST",
      data: { type: type.value, content: content.value }
    });
    uni.showToast({ title: "感谢反馈", icon: "success" });
    setTimeout(() => uni.navigateBack(), 600);
  } catch (e) {}
}
</script>

<style scoped>
.page { padding: 24rpx; }
.card { background: #fff; border-radius: 22rpx; padding: 32rpx; box-shadow: 0 4rpx 14rpx rgba(44, 44, 44, 0.04); }
.label { display: block; font-size: 26rpx; color: #555; margin: 20rpx 0 12rpx; }
.label:first-child { margin-top: 0; }
.chip-wrap { display: flex; flex-wrap: wrap; }
.chip { background: #f1efe7; border-radius: 999rpx; padding: 14rpx 30rpx; margin: 0 16rpx 16rpx 0; font-size: 26rpx; }
.chip.on { background: #2e6b4f; color: #fff; }
.textarea { background: #f7f4ec; border-radius: 14rpx; padding: 22rpx; width: auto; height: 280rpx; font-size: 28rpx; }
.primary-btn { background: #2e6b4f; color: #fff; text-align: center; padding: 24rpx 0; border-radius: 16rpx; font-size: 30rpx; font-weight: 700; margin-top: 36rpx; }
</style>
