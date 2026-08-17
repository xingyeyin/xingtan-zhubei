<template>
  <view class="page">
    <!-- 顶部问候 -->
    <view class="hero">
      <view class="hero-deco deco-1"></view>
      <view class="hero-deco deco-2"></view>
      <text class="hero-hello">你好，{{ nickname }}</text>
      <text class="hero-school" v-if="schoolName">{{ schoolName }}</text>
      <text class="hero-title">让每一节乡村课\n备得起、备得好</text>
      <text class="hero-sub">新课标对齐 · 教材适配 · 即问即得</text>
      <view class="hero-btn" @tap="goWizard">＋ 开始备课</view>
    </view>

    <!-- 快捷入口 -->
    <view class="grid">
      <view class="grid-item" v-for="item in entries" :key="item.name" @tap="entryTap(item)">
        <view class="grid-icon" :style="{ background: item.bg }">{{ item.icon }}</view>
        <text class="grid-name">{{ item.name }}</text>
        <text class="grid-desc">{{ item.desc }}</text>
      </view>
    </view>

    <!-- 使用统计 -->
    <view class="card">
      <view class="card-head">
        <text class="card-title">使用统计</text>
        <text class="card-link" @tap="goList">查看全部 ›</text>
      </view>
      <view class="stats-row">
        <view class="stat">
          <text class="stat-num">{{ stats.totalGenerations }}</text>
          <text class="stat-label">累计生成教案</text>
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

    <!-- 备课灵感 -->
    <view class="card">
      <view class="card-head">
        <text class="card-title">备课灵感</text>
        <text class="card-link" @tap="goResource">更多资源 ›</text>
      </view>
      <view class="idea" v-for="idea in ideas" :key="idea.id" @tap="useIdea(idea)">
        <view class="idea-tag" :class="'tag-' + idea.docType.toLowerCase()">{{ typeName(idea.docType) }}</view>
        <view class="idea-body">
          <text class="idea-title">{{ idea.title }}</text>
          <text class="idea-meta">{{ idea.subject || "全学科" }} · 2022版课标对齐</text>
        </view>
        <text class="idea-arrow">›</text>
      </view>
    </view>

    <!-- 教育动态 -->
    <view class="card">
      <view class="card-head">
        <text class="card-title">教育动态</text>
        <text class="card-link">真实数据 · 可溯源</text>
      </view>
      <view class="news" v-for="(n, i) in news" :key="i">
        <view class="news-dot"></view>
        <view class="news-body">
          <text class="news-title">{{ n.title }}</text>
          <text class="news-source">{{ n.source }}</text>
        </view>
      </view>
    </view>

    <view class="tip">🌾 弱网模式已开启：低带宽下自动压缩图片与请求，乡村网络也能流畅使用</view>
  </view>
</template>

<script setup>
import { ref } from "vue";
import { onShow } from "@dcloudio/uni-app";
import { request } from "../../utils/request";
import { getProfile, setProfile } from "../../utils/auth";

const nickname = ref((getProfile() && getProfile().nickname) || "教师");
const schoolName = ref((getProfile() && getProfile().school) || "");
const stats = ref({ totalGenerations: 0, savedHours: 0, avgQuality: 0 });
const ideas = ref([]);

const entries = [
  { name: "新建教案", icon: "📝", desc: "AI 一键生成", bg: "#e9f2ec", go: "wizard" },
  { name: "生成习题", icon: "🧮", desc: "按知识点出题", bg: "#fdf3e3", go: "assistant", tab: "exercise" },
  { name: "一键组卷", icon: "📄", desc: "单元测试卷", bg: "#eef0fa", go: "assistant", tab: "paper" },
  { name: "AI 问答", icon: "💬", desc: "随时请教", bg: "#fbeceb", go: "assistant", tab: "qa" },
  { name: "学情分析", icon: "📊", desc: "班级数据管理", bg: "#e8f4f4", go: "classes" },
  { name: "教研广场", icon: "👥", desc: "跨校同课异构", bg: "#f4edf8", go: "plaza" }
];

const news = [
  { title: "2025年全国教育事业发展统计公报：义务教育阶段学校 18.00 万所，专任教师 1069.76 万人", source: "教育部 · 2026-07" },
  { title: "特岗计划实施 20 年：累计为中西部 3 万多所农村学校选聘教师 118 万人", source: "人民日报 · 2025-09" },
  { title: "研究显示：多学科教学教师备课门数更多、时间更长，是主要压力来源之一", source: "乡村教育研究数据库" }
];

function typeName(t) {
  return { STANDARD: "课标", TEXTBOOK: "教材", TEMPLATE: "模板", CASE: "案例" }[t] || "资源";
}

function goWizard() {
  uni.navigateTo({ url: "/pages/lesson/wizard" });
}
function goList() {
  uni.switchTab({ url: "/pages/lesson/list" });
}
function goResource() {
  uni.switchTab({ url: "/pages/resource/index" });
}
function entryTap(item) {
  if (item.go === "wizard") return goWizard();
  if (item.go === "assistant") return uni.navigateTo({ url: "/pages/ai/assistant?tab=" + item.tab });
  if (item.go === "plaza") return uni.navigateTo({ url: "/pages/resource/plaza" });
  if (item.go === "classes") return uni.navigateTo({ url: "/pages/class/index" });
}
function useIdea(idea) {
  uni.navigateTo({
    url: "/pages/lesson/wizard?subject=" + (idea.subject || "") + "&title=" + encodeURIComponent(idea.title)
  });
}

onShow(async () => {
  try {
    const profile = await request({ url: "/api/auth/profile" });
    setProfile(profile);
    nickname.value = profile.nickname || "教师";
    schoolName.value = profile.school || "";
  } catch (e) {}
  try {
    stats.value = await request({ url: "/api/stats/overview" });
  } catch (e) {}
  try {
    const page = await request({ url: "/api/kb/documents?page=1&size=4" });
    ideas.value = (page.records || []).slice(0, 3);
  } catch (e) {}
});
</script>

<style scoped>
.page { padding: 24rpx 24rpx 40rpx; }
.hero {
  position: relative; overflow: hidden;
  background: linear-gradient(135deg, #2e6b4f 0%, #3e8a63 100%);
  border-radius: 28rpx; padding: 44rpx 40rpx 40rpx; color: #fff;
  box-shadow: 0 12rpx 32rpx rgba(46, 107, 79, 0.25);
}
.hero-deco { position: absolute; border-radius: 50%; background: rgba(255, 255, 255, 0.08); }
.deco-1 { width: 260rpx; height: 260rpx; right: -60rpx; top: -70rpx; }
.deco-2 { width: 160rpx; height: 160rpx; right: 90rpx; bottom: -50rpx; }
.hero-hello { display: block; font-size: 26rpx; opacity: 0.9; }
.hero-school { display: block; font-size: 23rpx; opacity: 0.75; margin-top: 6rpx; }
.hero-title { display: block; font-size: 42rpx; font-weight: 700; line-height: 1.45; margin-top: 14rpx; white-space: pre-line; }
.hero-sub { display: block; margin-top: 14rpx; font-size: 24rpx; opacity: 0.85; }
.hero-btn {
  display: inline-flex; margin-top: 28rpx; background: #fff; color: #2e6b4f;
  font-size: 28rpx; font-weight: 600; padding: 16rpx 36rpx; border-radius: 999rpx;
}
.grid { display: flex; flex-wrap: wrap; justify-content: space-between; margin-top: 24rpx; }
.grid-item {
  width: 31.5%; background: #fff; border-radius: 20rpx; padding: 26rpx 0 22rpx;
  display: flex; flex-direction: column; align-items: center; margin-bottom: 18rpx;
  box-shadow: 0 4rpx 14rpx rgba(44, 44, 44, 0.04);
}
.grid-icon {
  width: 76rpx; height: 76rpx; border-radius: 22rpx; display: flex; align-items: center;
  justify-content: center; font-size: 40rpx;
}
.grid-name { margin-top: 14rpx; font-size: 28rpx; font-weight: 600; }
.grid-desc { margin-top: 6rpx; font-size: 22rpx; color: #a0a0a0; }
.card { background: #fff; border-radius: 24rpx; padding: 28rpx; margin-top: 24rpx; box-shadow: 0 4rpx 14rpx rgba(44, 44, 44, 0.04); }
.card-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20rpx; }
.card-title { font-size: 30rpx; font-weight: 700; }
.card-link { font-size: 24rpx; color: #2e6b4f; }
.stats-row { display: flex; }
.stat { flex: 1; text-align: center; position: relative; }
.stat:not(:last-child)::after { content: ""; position: absolute; right: 0; top: 10%; height: 80%; width: 1rpx; background: #f0ede4; }
.stat-num { display: block; font-size: 40rpx; font-weight: 700; color: #2e6b4f; }
.stat-label { display: block; margin-top: 8rpx; font-size: 23rpx; color: #8a8a8a; }
.idea { display: flex; align-items: center; padding: 20rpx 0; border-bottom: 1rpx solid #f3f0e8; }
.idea:last-child { border-bottom: none; }
.idea-tag { font-size: 22rpx; padding: 8rpx 16rpx; border-radius: 8rpx; margin-right: 18rpx; white-space: nowrap; }
.tag-standard { background: #e9f2ec; color: #2e6b4f; }
.tag-textbook { background: #eef0fa; color: #4a5aa0; }
.tag-template { background: #fdf3e3; color: #b57a1f; }
.tag-case { background: #fbeceb; color: #b0553f; }
.idea-body { flex: 1; }
.idea-title { display: block; font-size: 27rpx; font-weight: 600; line-height: 1.4; }
.idea-meta { display: block; margin-top: 6rpx; font-size: 22rpx; color: #a0a0a0; }
.idea-arrow { color: #ccc; font-size: 40rpx; margin-left: 10rpx; }
.news { display: flex; padding: 16rpx 0; }
.news-dot { width: 12rpx; height: 12rpx; border-radius: 50%; background: #e3a03c; margin-top: 12rpx; margin-right: 18rpx; flex-shrink: 0; }
.news-body { flex: 1; }
.news-title { display: block; font-size: 25rpx; line-height: 1.5; }
.news-source { display: block; margin-top: 6rpx; font-size: 22rpx; color: #a0a0a0; }
.tip { margin-top: 24rpx; font-size: 23rpx; color: #8a8a8a; text-align: center; line-height: 1.6; }
</style>
