<template>
  <view class="page">
    <view class="head-bar" v-if="data.className">
      <view>
        <text class="head-title">{{ data.grade }}{{ data.className }}</text>
        <text class="head-sub">{{ data.students.length }} 名学生</text>
      </view>
      <view class="analyze-btn" @tap="analyze">📊 一键学情分析</view>
    </view>

    <!-- 添加学生 -->
    <view class="card">
      <view class="card-title">添加学生</view>
      <input class="input" v-model="newStudent.name" placeholder="学生姓名" />
      <view class="score-row">
        <view v-for="s in subjects" :key="s" class="score-field">
          <text class="score-label">{{ s }}</text>
          <input class="input small" v-model="newStudent.scores[s]" type="digit" placeholder="分数" />
        </view>
      </view>
      <view class="primary-btn" @tap="addStudent">添加</view>
    </view>

    <!-- 学生列表 -->
    <view class="card">
      <view class="card-title">学生成绩（可修改）</view>
      <view v-if="data.students.length === 0" class="empty">暂无学生，先添加吧</view>
      <view v-for="stu in data.students" :key="stu.id" class="student-card">
        <input class="input name" v-model="stu.name" placeholder="姓名" />
        <view class="score-row">
          <view v-for="s in subjects" :key="s" class="score-field">
            <text class="score-label">{{ s }}</text>
            <input class="input small" v-model="stu.scores[s]" type="digit" placeholder="-" />
          </view>
        </view>
        <view class="student-ops">
          <text class="op" @tap="saveStudent(stu)">保存</text>
          <text class="op danger" @tap="removeStudent(stu)">删除</text>
        </view>
      </view>
    </view>

    <!-- 学情分析结果 -->
    <view v-if="analysis" class="card">
      <view class="card-title">🤖 学情分析结果</view>
      <view v-if="stats" class="stats-box">
        <view v-for="(v, k) in stats" :key="k" class="stat-item">
          <text class="stat-subject">{{ k }}</text>
          <text class="stat-line">平均 {{ v.avg }} · 及格率 {{ v.passRate }}% · 优秀率 {{ v.excellentRate }}%</text>
        </view>
      </view>
      <text class="result-text">{{ analysis }}</text>
      <view class="ghost-btn" @tap="copy">复制分析结果</view>
    </view>
  </view>
</template>

<script setup>
import { reactive, ref } from "vue";
import { onLoad } from "@dcloudio/uni-app";
import { request } from "../../utils/request";

const subjects = ["语文", "数学", "英语"];
const data = ref({ grade: "", className: "", students: [] });
const stats = ref(null);
const analysis = ref("");
const newStudent = reactive({ name: "", scores: { 语文: "", 数学: "", 英语: "" } });

async function load(id) {
  try {
    data.value = await request({ url: "/api/classes/" + id + "/students" });
  } catch (e) {}
}

async function addStudent() {
  if (!newStudent.name.trim()) return uni.showToast({ title: "请输入学生姓名", icon: "none" });
  try {
    await request({
      url: "/api/classes/" + data.value.classId + "/students",
      method: "POST",
      data: { name: newStudent.name, scores: normalize(newStudent.scores) }
    });
    uni.showToast({ title: "已添加", icon: "success" });
    newStudent.name = "";
    newStudent.scores = { 语文: "", 数学: "", 英语: "" };
    load(data.value.classId);
  } catch (e) {}
}

async function saveStudent(stu) {
  try {
    await request({
      url: "/api/classes/students/" + stu.id,
      method: "PUT",
      data: { name: stu.name, scores: normalize(stu.scores) }
    });
    uni.showToast({ title: "已保存", icon: "success" });
  } catch (e) {}
}

function removeStudent(stu) {
  uni.showModal({
    title: "删除学生",
    content: "确定删除 " + stu.name + " 及其成绩？",
    confirmColor: "#c0392b",
    success: async (res) => {
      if (!res.confirm) return;
      try {
        await request({ url: "/api/classes/students/" + stu.id, method: "DELETE" });
        uni.showToast({ title: "已删除", icon: "success" });
        load(data.value.classId);
      } catch (e) {}
    }
  });
}

function normalize(scores) {
  const out = {};
  for (const s of subjects) {
    if (scores[s] !== "" && scores[s] !== undefined && scores[s] !== null) {
      out[s] = Number(scores[s]);
    }
  }
  return out;
}

async function analyze() {
  if (data.value.students.length === 0) return uni.showToast({ title: "请先添加学生和成绩", icon: "none" });
  analysis.value = "分析中，请稍候（约 30 秒）…";
  try {
    const s = await request({ url: "/api/classes/" + data.value.classId + "/analysis" });
    stats.value = s.stats;
    let msg = "班级：" + s.className + "，共 " + s.studentCount + " 名学生。\n成绩概况：\n";
    for (const [k, v] of Object.entries(s.stats)) {
      msg += k + "：平均 " + v.avg + " 分，及格率 " + v.passRate + "%，优秀率 " + v.excellentRate + "%。\n";
    }
    msg += "\n请分析该班薄弱点，并给出备课调整建议、课堂应对策略和分层教学方案。";
    analysis.value = await request({
      url: "/api/ai/chat",
      method: "POST",
      data: { message: msg },
      timeout: 180000
    });
  } catch (e) {
    analysis.value = "分析失败，请重试";
  }
}

function copy() {
  uni.setClipboardData({ data: analysis.value });
}

onLoad((options) => {
  if (options && options.id) load(options.id);
});
</script>

<style scoped>
.page { padding: 24rpx; }
.head-bar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20rpx; }
.head-title { font-size: 38rpx; font-weight: 700; display: block; }
.head-sub { display: block; margin-top: 6rpx; font-size: 24rpx; color: #8a8a8a; }
.analyze-btn { background: #2e6b4f; color: #fff; font-size: 25rpx; padding: 16rpx 28rpx; border-radius: 999rpx; }
.card { background: #fff; border-radius: 22rpx; padding: 28rpx; margin-bottom: 20rpx; box-shadow: 0 4rpx 14rpx rgba(44, 44, 44, 0.04); }
.card-title { font-size: 30rpx; font-weight: 700; margin-bottom: 16rpx; }
.input { background: #f7f4ec; border-radius: 14rpx; padding: 18rpx 22rpx; font-size: 27rpx; }
.input.name { margin-bottom: 14rpx; }
.input.small { text-align: center; }
.score-row { display: flex; gap: 14rpx; margin-top: 14rpx; }
.score-field { flex: 1; }
.score-label { display: block; font-size: 23rpx; color: #888; text-align: center; margin-bottom: 8rpx; }
.primary-btn { background: #2e6b4f; color: #fff; text-align: center; padding: 20rpx 0; border-radius: 14rpx; font-size: 28rpx; font-weight: 700; margin-top: 20rpx; }
.empty { text-align: center; color: #a0a0a0; padding: 40rpx 0; font-size: 26rpx; }
.student-card { background: #f8faf7; border-radius: 16rpx; padding: 20rpx; margin-bottom: 16rpx; }
.student-ops { display: flex; justify-content: flex-end; gap: 28rpx; margin-top: 14rpx; }
.op { font-size: 25rpx; color: #2e6b4f; }
.op.danger { color: #c0392b; }
.stats-box { background: #e9f2ec; border-radius: 14rpx; padding: 18rpx; margin-bottom: 16rpx; }
.stat-item { padding: 8rpx 0; }
.stat-subject { font-weight: 700; color: #2e6b4f; margin-right: 12rpx; }
.stat-line { font-size: 25rpx; color: #555; }
.result-text { font-size: 25rpx; line-height: 1.7; white-space: pre-wrap; }
.ghost-btn { margin-top: 20rpx; background: #e9f2ec; color: #2e6b4f; text-align: center; padding: 18rpx 0; border-radius: 14rpx; font-size: 27rpx; }
</style>
