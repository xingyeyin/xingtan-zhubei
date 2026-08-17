<template>
  <view class="page">
    <view v-if="!plan" class="empty">加载中…</view>

    <block v-else>
      <view class="head">
        <view>
          <text class="title">{{ plan.title }}</text>
          <view class="tags">
            <text class="tag">{{ plan.subject }}</text>
            <text class="tag">{{ plan.grade }}</text>
            <text class="tag">{{ plan.textbook }}</text>
            <text class="tag">{{ plan.lessonType }}</text>
          </view>
        </view>
        <view class="score" v-if="content.qualityScore">
          <text class="score-num">{{ content.qualityScore }}</text>
          <text class="score-label">质量分</text>
        </view>
      </view>

      <view class="section">
        <view class="section-name">🎯 教学目标</view>
        <view v-for="(obj, i) in content.objectives" :key="i" class="obj-item">
          <text class="obj-type">{{ obj.type }}</text>
          <text class="obj-content">{{ obj.content }}</text>
        </view>
      </view>

      <view class="section">
        <view class="section-name">📍 教学重难点</view>
        <text class="line">重点：{{ joinList(content.keyPoints) }}</text>
        <text class="line">难点：{{ joinList(content.difficultPoints) }}</text>
      </view>

      <view class="section">
        <view class="section-name">🕐 教学过程</view>
        <view v-for="(p, i) in content.process" :key="i" class="process-item">
          <view class="process-head">
            <text class="process-stage">{{ p.stage }}</text>
            <text class="process-time">{{ p.duration }} 分钟</text>
          </view>
          <text class="process-body">{{ p.activities }}</text>
        </view>
      </view>

      <view class="section">
        <view class="section-name">🖊 板书设计</view>
        <text class="board">{{ content.boardDesign }}</text>
      </view>

      <view class="section">
        <view class="section-name">📚 分层作业</view>
        <view v-for="(hw, i) in content.homework" :key="i" class="hw-item">
          <text class="hw-level">{{ hw.level }}</text>
          <text v-for="(it, j) in hw.items" :key="j" class="hw-text">· {{ it }}</text>
        </view>
      </view>

      <view class="section standard">
        <view class="section-name">📜 课标依据卡片</view>
        <view v-for="(c, i) in content.standardCards" :key="i" class="standard-card">
          <text class="standard-ref">{{ c.ref }}</text>
          <text class="standard-content">{{ c.content }}</text>
        </view>
      </view>

      <view class="ai-badge">🤖 本教案由 AI 辅助生成，请教师审核后使用</view>
      <view class="copy-btn" @tap="copyText">复制教案全文</view>
      <view class="from-tip">来自「杏坛智备」乡村教师 AI 备课助手 · 教研广场分享</view>
    </block>
  </view>
</template>

<script setup>
import { ref } from "vue";
import { onLoad } from "@dcloudio/uni-app";
import { request } from "../../utils/request";

const plan = ref(null);
const content = ref({ objectives: [], keyPoints: [], difficultPoints: [], process: [], boardDesign: "", homework: [], standardCards: [] });

onLoad(async (options) => {
  try {
    const id = options.id;
    plan.value = await request({ url: "/api/public/lessons/" + id, timeout: 15000 });
    if (plan.value.content) {
      try {
        content.value = JSON.parse(plan.value.content);
      } catch (e) {
        content.value = { ...content.value };
      }
    }
  } catch (e) {
    uni.showToast({ title: "教案不存在或未公开", icon: "none" });
  }
});

function joinList(list) {
  return (list || []).join("；") || "-";
}
function copyText() {
  let text = plan.value.title + "\n";
  text += plan.value.subject + " · " + plan.value.grade + " · " + plan.value.textbook + "\n\n";
  text += "【教学目标】\n";
  content.value.objectives.forEach((o) => (text += o.type + "：" + o.content + "\n"));
  text += "\n【教学重难点】\n重点：" + joinList(content.value.keyPoints) + "\n难点：" + joinList(content.value.difficultPoints) + "\n\n";
  text += "【教学过程】\n";
  content.value.process.forEach((p) => (text += p.stage + "（" + p.duration + "分钟）：" + p.activities + "\n"));
  text += "\n【板书设计】\n" + content.value.boardDesign + "\n\n【分层作业】\n";
  content.value.homework.forEach((h) => h.items.forEach((it) => (text += h.level + "：" + it + "\n")));
  uni.setClipboardData({ data: text, success: () => uni.showToast({ title: "已复制", icon: "success" }) });
}
</script>

<style scoped>
.page { padding: 24rpx 24rpx 60rpx; }
.empty { text-align: center; padding: 200rpx 0; color: #999; }
.head { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 24rpx; }
.title { font-size: 40rpx; font-weight: 800; line-height: 1.35; display: block; }
.tags { display: flex; flex-wrap: wrap; margin-top: 14rpx; }
.tag { background: #f1efe7; color: #6b6b6b; font-size: 22rpx; padding: 6rpx 16rpx; border-radius: 8rpx; margin-right: 12rpx; }
.score { background: linear-gradient(135deg, #2e6b4f, #3e8a63); color: #fff; border-radius: 18rpx; padding: 16rpx 22rpx; text-align: center; min-width: 110rpx; }
.score-num { display: block; font-size: 40rpx; font-weight: 800; }
.score-label { font-size: 20rpx; opacity: 0.85; }
.section { background: #fff; border-radius: 22rpx; padding: 28rpx; margin-bottom: 20rpx; box-shadow: 0 4rpx 14rpx rgba(44, 44, 44, 0.04); }
.section-name { font-size: 30rpx; font-weight: 700; margin-bottom: 18rpx; }
.obj-item { display: flex; margin-bottom: 14rpx; }
.obj-type { flex-shrink: 0; background: #e9f2ec; color: #2e6b4f; font-size: 22rpx; padding: 6rpx 14rpx; border-radius: 8rpx; margin-right: 14rpx; height: fit-content; }
.obj-content { font-size: 27rpx; line-height: 1.6; }
.line { display: block; font-size: 27rpx; line-height: 1.6; margin-bottom: 10rpx; }
.process-item { border-left: 6rpx solid #2e6b4f; background: #f8faf7; border-radius: 0 14rpx 14rpx 0; padding: 20rpx 22rpx; margin-bottom: 16rpx; }
.process-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10rpx; }
.process-stage { font-size: 28rpx; font-weight: 700; color: #2e6b4f; }
.process-time { font-size: 22rpx; color: #8a8a8a; background: #e9f2ec; padding: 4rpx 14rpx; border-radius: 999rpx; }
.process-body { font-size: 26rpx; line-height: 1.65; }
.board { font-size: 27rpx; line-height: 1.7; white-space: pre-line; }
.hw-item { background: #f8faf7; border-radius: 14rpx; padding: 20rpx; margin-bottom: 14rpx; }
.hw-level { font-size: 24rpx; color: #2e6b4f; font-weight: 700; margin-right: 14rpx; }
.hw-text { display: block; font-size: 26rpx; line-height: 1.6; margin-top: 6rpx; }
.standard { background: #f2f7f2; }
.standard-card { background: #fff; border-left: 6rpx solid #e3a03c; border-radius: 0 14rpx 14rpx 0; padding: 20rpx; margin-bottom: 14rpx; }
.standard-ref { display: block; font-size: 24rpx; color: #b57a1f; font-weight: 700; margin-bottom: 8rpx; }
.standard-content { font-size: 25rpx; line-height: 1.6; color: #557755; }
.ai-badge { background: #fdf3e3; border: 2rpx solid #e3a03c; border-radius: 14rpx; padding: 18rpx 22rpx; font-size: 23rpx; color: #8a6d2f; margin-bottom: 20rpx; }
.copy-btn { background: #2e6b4f; color: #fff; text-align: center; padding: 24rpx 0; border-radius: 16rpx; font-size: 29rpx; font-weight: 700; }
.from-tip { margin-top: 20rpx; text-align: center; font-size: 22rpx; color: #a0a0a0; }
</style>
