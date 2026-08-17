<template>
  <view class="page">
    <view class="card">
      <view class="card-title">📖 快速上手（5 步）</view>
      <view v-for="(s, i) in steps" :key="i" class="step">
        <text class="step-num">{{ i + 1 }}</text>
        <text class="step-text">{{ s }}</text>
      </view>
    </view>

    <view class="card">
      <view class="card-title">❓ 常见问题</view>
      <view v-for="(f, i) in faqs" :key="i" class="faq">
        <text class="faq-q">Q：{{ f.q }}</text>
        <text class="faq-a">A：{{ f.a }}</text>
      </view>
    </view>
  </view>
</template>

<script setup>
const steps = [
  "登录后进入首页，点击「新建教案」开始备课",
  "选择学科、年级、教材版本，输入课题",
  "等待 AI 生成（约 20~40 秒），查看课标依据卡片",
  "不满意？点「AI 对话」提出修改要求",
  "完成教案后点「保存」，需要展示时点「导出」或「导出 PPT」"
];

const faqs = [
  { q: "生成失败或超时怎么办？", a: "先检查网络，点击重试；仍失败可降低课型复杂度或使用 AI 问答手动获取建议。" },
  { q: "生成的教案不满意？", a: "在编辑器中使用「AI 对话」提出具体修改（如：换成乡土案例导入、加小组活动），或点「重新生成」。" },
  { q: "我的教案会不会被别人看到？", a: "教案默认仅自己可见；只有在教研广场手动开启分享后才会公开。" },
  { q: "可以导入自己的课件吗？", a: "支持 PPT/PDF/Word/文本，导入后系统会自动分析并给出教案建议与 PPT 大纲。" },
  { q: "导出格式有哪些？", a: "支持 Markdown 文档与 PPT 两种格式，均可直接下载使用。" },
  { q: "网络不好能用吗？", a: "请开启「弱网模式」（我的-设置），系统会自动压缩请求，生成过程支持完成后查看。" },
  { q: "忘记密码怎么办？", a: "在「设置-修改密码」中修改；若忘记原密码，请联系学校管理员重置账号。" },
  { q: "支持哪些学科？", a: "当前已覆盖小学/初中语数英全学段教材目录，其他学科陆续扩展中。" }
];
</script>

<style scoped>
.page { padding: 24rpx; }
.card { background: #fff; border-radius: 22rpx; padding: 28rpx; margin-bottom: 20rpx; box-shadow: 0 4rpx 14rpx rgba(44, 44, 44, 0.04); }
.card-title { font-size: 30rpx; font-weight: 700; margin-bottom: 18rpx; }
.step { display: flex; margin-bottom: 18rpx; }
.step-num { width: 44rpx; height: 44rpx; border-radius: 50%; background: #2e6b4f; color: #fff; display: flex; align-items: center; justify-content: center; font-size: 24rpx; font-weight: 700; margin-right: 16rpx; flex-shrink: 0; }
.step-text { flex: 1; font-size: 27rpx; line-height: 1.6; }
.faq { padding: 18rpx 0; border-bottom: 1rpx solid #f3f0e8; }
.faq:last-child { border-bottom: none; }
.faq-q { display: block; font-size: 27rpx; font-weight: 600; }
.faq-a { display: block; margin-top: 8rpx; font-size: 25rpx; color: #666; line-height: 1.6; }
</style>
