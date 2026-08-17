<template>
  <view class="page">
    <view class="steps">
      <view v-for="(s, i) in steps" :key="i" class="step" :class="{ active: i <= step }">
        <view class="step-dot" :class="{ done: i < step }">{{ i < step ? "✓" : i + 1 }}</view>
        <text class="step-label">{{ s }}</text>
      </view>
    </view>

    <!-- 第一步：学科年级 -->
    <view v-if="step === 0" class="panel">
      <text class="panel-label">选择学科</text>
      <view class="subject-grid">
        <view v-for="s in subjects" :key="s.name" class="subject-card" :class="{ on: form.subject === s.name }" @tap="form.subject = s.name">
          <text class="subject-icon">{{ s.icon }}</text>
          <text class="subject-name">{{ s.name }}</text>
          <text class="subject-desc">{{ s.desc }}</text>
        </view>
      </view>
      <text class="panel-label">选择年级</text>
      <view class="chip-wrap">
        <text v-for="g in grades" :key="g" class="chip" :class="{ on: form.grade === g }" @tap="form.grade = g">{{ g }}</text>
      </view>
    </view>

    <!-- 第二步：教材课题 -->
    <view v-else-if="step === 1" class="panel">
      <text class="panel-label">教材版本</text>
      <view class="chip-wrap">
        <text v-for="t in versionList" :key="t" class="chip" :class="{ on: form.textbook === t }" @tap="form.textbook = t">{{ t }}</text>
      </view>
      <text class="panel-label">课题名称</text>
      <input class="input" v-model="form.title" placeholder="如：《背影》第一课时" />
      <text class="panel-label">快捷选题（来自教材目录）<text v-if="loaded" class="panel-hint">已匹配 {{ topics.length }} 条</text></text>
      <view class="chip-wrap">
        <text v-for="t in topics" :key="t" class="chip" :class="{ on: form.title === t }" @tap="form.title = t">{{ t }}</text>
      </view>
    </view>

    <!-- 第三步：课型要求 -->
    <view v-else-if="step === 2" class="panel">
      <text class="panel-label">课型</text>
      <view class="chip-wrap">
        <text v-for="t in lessonTypes" :key="t" class="chip" :class="{ on: form.lessonType === t }" @tap="form.lessonType = t">{{ t }}</text>
      </view>
      <text class="panel-label">补充要求（可选）</text>
      <textarea class="textarea" v-model="form.extra" placeholder="如：加入乡土案例导入、设计小组活动、生成分层作业…" />
      <view class="feature-row">
        <text>生成课标依据卡片</text>
        <switch :checked="true" color="#2e6b4f" disabled />
      </view>
      <view class="feature-row">
        <text>弱网模式（低带宽适配）</text>
        <switch :checked="true" color="#2e6b4f" disabled />
      </view>
    </view>

    <!-- 生成中 -->
    <view v-else class="generating">
      <view class="gen-ring"></view>
      <text class="gen-title">{{ progressTitle }}</text>
      <text class="gen-sub">{{ form.title }}</text>
      <view class="gen-steps">
        <view v-for="(s, i) in genSteps" :key="i" class="gen-step" :class="{ done: genIndex > i, now: genIndex === i }">
          <text class="gen-step-icon">{{ genIndex > i ? "✓" : genIndex === i ? "◐" : "○" }}</text>
          <text class="gen-step-text">{{ s }}</text>
        </view>
      </view>
      <view v-if="genError" class="gen-error">{{ genError }}</view>
    </view>

    <view v-if="step < 3" class="btn-row">
      <view v-if="step > 0" class="ghost-btn" @tap="step--">上一步</view>
      <view v-if="step < 2" class="primary-btn" @tap="next">下一步</view>
      <view v-else class="primary-btn" @tap="generate">开始生成</view>
    </view>
  </view>
</template>

<script setup>
import { computed, reactive, ref, watch } from "vue";
import { onLoad } from "@dcloudio/uni-app";
import { request } from "../../utils/request";

const steps = ["学科年级", "教材课题", "课型要求"];
const step = ref(0);
const subjects = ref([
  { name: "语文", icon: "📖", desc: "阅读写作" },
  { name: "数学", icon: "🧮", desc: "数与图形" },
  { name: "英语", icon: "🔤", desc: "听说读写" },
  { name: "物理", icon: "⚛️", desc: "声光力热电" },
  { name: "化学", icon: "🧪", desc: "物质与变化" },
  { name: "生物", icon: "🧬", desc: "生命与自然" },
  { name: "道德与法治", icon: "⚖️", desc: "品德与法治" },
  { name: "历史", icon: "🏛️", desc: "文明与传承" },
  { name: "地理", icon: "🌏", desc: "山川与人文" }
]);
const grades = ["一年级", "二年级", "三年级", "四年级", "五年级", "六年级", "七年级", "八年级", "九年级"];
const fallbackVersions = ["人教版", "北师大版", "苏教版"];
const fallbackTopics = ["《背影》第一课时", "《三峡》", "《苏州园林》", "《白杨礼赞》", "一元二次方程", "分数乘法", "Unit 1 My name's Gina", "三角形的内角和"];
const lessonTypes = ["新授课", "复习课", "公开课", "习题课"];
const form = reactive({ subject: "", grade: "", textbook: "人教版", title: "", lessonType: "新授课", extra: "" });
const catalog = ref([]);
const loaded = ref(false);

// 教材目录动态化：从知识库接口加载 75 册目录（公开接口，无需登录）
onLoad(async (options) => {
  if (options && options.subject) form.subject = decodeURIComponent(options.subject);
  if (options && options.title) form.title = decodeURIComponent(options.title);
  if (options && options.extra) form.extra = decodeURIComponent(options.extra);
  try {
    const res = await request({ url: "/api/kb/textbooks", timeout: 20000 });
    catalog.value = Array.isArray(res) ? res : [];
    loaded.value = true;
  } catch (e) {
    loaded.value = false;
  }
});

const matchedDocs = computed(() => {
  if (!catalog.value.length || !form.subject || !form.grade) return [];
  return catalog.value.filter(
    (t) => t.subject === form.subject && (t.grade || "").startsWith(form.grade)
  );
});

const versionList = computed(() => {
  const versions = [...new Set(matchedDocs.value.map((t) => t.version).filter(Boolean))];
  if (versions.length) return versions;
  return loaded.value ? ["人教版"] : fallbackVersions;
});

const topics = computed(() => {
  if (!loaded.value || !matchedDocs.value.length) return fallbackTopics;
  const list = [];
  const seen = new Set();
  for (const doc of matchedDocs.value) {
    if (form.textbook && doc.version !== form.textbook) continue;
    for (const unit of doc.units || []) {
      const cleaned = String(unit).replace(doc.title || "", "").replace(/^[：:\s]+/, "").trim();
      const chapter = cleaned.split(/[：:]/)[0] || "";
      const chapterName = chapter.replace(/^第[一二三四五六七八九十0-9]+[章节单元课]+\s*/, "").trim();
      if (chapterName && chapterName.length <= 20) {
        if (!seen.has(chapterName)) { seen.add(chapterName); list.push(chapterName); }
      }
      const after = cleaned.includes("：") ? cleaned.split("：")[1] : cleaned.includes(":") ? cleaned.split(":")[1] : "";
      if (after) {
        for (const part of after.split(/[、，,;；]/)) {
          const p = part.trim().replace(/^[0-9.、\s]+/, "");
          if (p && p.length <= 20 && !seen.has(p)) { seen.add(p); list.push(p); }
        }
      }
    }
  }
  return list.slice(0, 18);
});

watch(
  () => [form.subject, form.grade],
  () => {
    const v = versionList.value;
    if (!v.includes(form.textbook)) form.textbook = v[0] || "人教版";
  }
);

const genSteps = ["定位课标条目", "检索教材与模板", "生成教学目标", "设计教学过程", "质量校验"];
const genIndex = ref(0);
const genError = ref("");
let timer = null;

function next() {
  if (step.value === 0 && !form.subject) return tip("请选择学科");
  if (step.value === 0 && !form.grade) return tip("请选择年级");
  if (step.value === 1 && !form.title) return tip("请输入课题名称");
  step.value++;
}
function tip(title) {
  uni.showToast({ title, icon: "none" });
}

async function generate() {
  step.value = 3;
  genIndex.value = 0;
  genError.value = "";
  try {
    const task = await request({
      url: "/api/generations",
      method: "POST",
      data: { scene: "LESSON_PLAN", params: { ...form } }
    });
    timer = setInterval(() => poll(task.id), 1500);
  } catch (e) {
    genError.value = "创建任务失败，请检查后端服务";
    step.value = 2;
  }
}

async function poll(taskId) {
  genIndex.value = Math.min(genIndex.value + 1, genSteps.length - 1);
  try {
    const res = await request({ url: "/api/generations/" + taskId });
    const status = res.task.status;
    if (status === "SUCCESS") {
      clearInterval(timer);
      uni.redirectTo({ url: "/pages/lesson/editor?id=" + res.lessonPlan.id });
    } else if (status === "FAILED" || status === "TIMEOUT") {
      clearInterval(timer);
      genError.value = "生成失败：" + (res.task.errorMessage || "未知错误") + "，可返回重试";
    }
  } catch (e) {
    clearInterval(timer);
    genError.value = "连接中断，请稍后重试";
  }
}

const progressTitle = "正在生成教案…";
</script>

<style scoped>
.page { padding: 24rpx 24rpx 60rpx; }
.steps { display: flex; justify-content: space-between; margin-bottom: 28rpx; }
.step { display: flex; flex-direction: column; align-items: center; width: 30%; }
.step-dot {
  width: 52rpx; height: 52rpx; border-radius: 50%; background: #ece9df; color: #a0a0a0;
  display: flex; align-items: center; justify-content: center; font-size: 26rpx; font-weight: 700;
}
.step.active .step-dot { background: #2e6b4f; color: #fff; }
.step.done .step-dot { background: #e3a03c; color: #fff; }
.step-label { margin-top: 10rpx; font-size: 24rpx; color: #a0a0a0; }
.step.active .step-label { color: #2e6b4f; font-weight: 600; }
.panel { background: #fff; border-radius: 24rpx; padding: 32rpx; box-shadow: 0 4rpx 14rpx rgba(44, 44, 44, 0.04); }
.panel-label { display: block; font-size: 28rpx; font-weight: 700; margin: 24rpx 0 16rpx; }
.panel-label:first-child { margin-top: 0; }
.subject-grid { display: flex; flex-wrap: wrap; gap: 16rpx; }
.subject-card {
  width: calc((100% - 32rpx) / 3); flex: none; background: #f7f4ec; border: 3rpx solid transparent; border-radius: 18rpx;
  padding: 24rpx 0; text-align: center; box-sizing: border-box;
}
.subject-card.on { border-color: #2e6b4f; background: #e9f2ec; }
.subject-icon { display: block; font-size: 48rpx; }
.subject-name { display: block; margin-top: 10rpx; font-size: 28rpx; font-weight: 700; }
.subject-desc { display: block; margin-top: 6rpx; font-size: 22rpx; color: #a0a0a0; }
.chip-wrap { display: flex; flex-wrap: wrap; }
.chip {
  background: #f1efe7; border-radius: 999rpx; padding: 14rpx 30rpx; margin: 0 16rpx 16rpx 0; font-size: 26rpx;
}
.chip.on { background: #2e6b4f; color: #fff; }
.panel-hint { font-size: 22rpx; color: #2e6b4f; margin-left: 12rpx; font-weight: 400; }
.input { background: #f7f4ec; border-radius: 14rpx; padding: 22rpx; font-size: 28rpx; }
.textarea { background: #f7f4ec; border-radius: 14rpx; padding: 22rpx; width: auto; height: 180rpx; font-size: 28rpx; }
.feature-row { display: flex; justify-content: space-between; align-items: center; padding: 18rpx 0; font-size: 27rpx; border-bottom: 1rpx solid #f3f0e8; }
.btn-row { display: flex; gap: 20rpx; margin-top: 32rpx; }
.primary-btn { flex: 1; background: #2e6b4f; color: #fff; text-align: center; padding: 24rpx 0; border-radius: 16rpx; font-size: 30rpx; font-weight: 600; }
.ghost-btn { flex: 1; background: #fff; color: #2e6b4f; text-align: center; padding: 24rpx 0; border-radius: 16rpx; font-size: 30rpx; border: 2rpx solid #2e6b4f; }
.generating { text-align: center; padding: 60rpx 40rpx 20rpx; }
.gen-ring {
  width: 120rpx; height: 120rpx; margin: 0 auto; border-radius: 50%;
  border: 10rpx solid #e9f2ec; border-top-color: #2e6b4f;
  animation: spin 1s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }
.gen-title { display: block; margin-top: 36rpx; font-size: 34rpx; font-weight: 700; }
.gen-sub { display: block; margin-top: 10rpx; font-size: 26rpx; color: #8a8a8a; }
.gen-steps { margin-top: 44rpx; text-align: left; }
.gen-step { display: flex; align-items: center; padding: 14rpx 24rpx; }
.gen-step-icon { width: 44rpx; height: 44rpx; border-radius: 50%; display: flex; align-items: center; justify-content: center; background: #f1efe7; color: #a0a0a0; margin-right: 20rpx; }
.gen-step.done .gen-step-icon { background: #e9f2ec; color: #2e6b4f; }
.gen-step.now .gen-step-icon { background: #2e6b4f; color: #fff; }
.gen-step-text { font-size: 27rpx; color: #8a8a8a; }
.gen-step.now .gen-step-text { color: #2e6b4f; font-weight: 600; }
.gen-error { margin-top: 30rpx; color: #c0392b; font-size: 26rpx; }
</style>
