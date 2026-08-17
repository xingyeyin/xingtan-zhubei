<template>
  <div>
    <!-- 指标卡 -->
    <el-row :gutter="16">
      <el-col :span="6" v-for="card in cards" :key="card.label">
        <el-card class="metric" shadow="hover">
          <div class="metric-icon" :style="{ background: card.bg }">{{ card.icon }}</div>
          <div>
            <div class="metric-label">{{ card.label }}</div>
            <div class="metric-value">{{ card.value }}</div>
            <div class="metric-desc">{{ card.desc }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="14">
        <el-card>
          <template #header>生成量趋势（近 7 日）</template>
          <div ref="trendRef" style="height: 300px"></div>
          <div v-if="trendEmpty" class="chart-empty">暂无可视化数据，接入真实使用后将自动展示</div>
        </el-card>
      </el-col>
      <el-col :span="10">
        <el-card>
          <template #header>教案学科分布</template>
          <div ref="pieRef" style="height: 300px"></div>
          <div v-if="pieEmpty" class="chart-empty">暂无教案数据</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="14">
        <el-card>
          <template #header>最近动态</template>
          <el-table :data="recent" size="small">
            <el-table-column prop="nickname" label="教师" width="110" />
            <el-table-column prop="action" label="动作" width="120">
              <template #default="{ row }">
                <el-tag size="small" :type="actionType(row.action)">{{ actionName(row.action) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="scene" label="场景" width="130" />
            <el-table-column prop="duration_sec" label="耗时(s)" width="90" />
            <el-table-column label="时间">
              <template #default="{ row }">{{ fmt(row.created_at) }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="10">
        <el-card>
          <template #header>教育洞察</template>
          <div v-for="(n, i) in insights" :key="i" class="insight">
            <div class="insight-dot"></div>
            <div>
              <div class="insight-text">{{ n.text }}</div>
              <div class="insight-src">{{ n.src }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, ref } from "vue";
import * as echarts from "echarts";
import request from "../api/request";

const cards = ref([
  { label: "教师数", value: "-", desc: "已注册乡村教师", icon: "👩‍🏫", bg: "#e9f2ec" },
  { label: "累计生成教案", value: "-", desc: "AI 备课任务", icon: "📝", bg: "#fdf3e3" },
  { label: "知识库条目", value: "-", desc: "课标/教材/案例", icon: "📚", bg: "#eef0fa" },
  { label: "估算节省备课", value: "-", desc: "按每份约1.5h测算", icon: "⏱️", bg: "#fbeceb" },
  { label: "好评率", value: "-", desc: "教师反馈满意度", icon: "⭐", bg: "#f2ecf8" },
  { label: "试点学校", value: "-", desc: "已落地合作学校", icon: "🏫", bg: "#e9f5f7" },
  { label: "公开教案", value: "-", desc: "教研广场共享", icon: "🔓", bg: "#f8f0e5" },
  { label: "平均质量分", value: "-", desc: "AI教案质量评估", icon: "💯", bg: "#ecf8ee" }
]);
const recent = ref([]);
const trendEmpty = ref(true);
const pieEmpty = ref(true);
const trendRef = ref(null);
const pieRef = ref(null);
let trendChart = null;
let pieChart = null;

const insights = [
  { text: "2025 年全国教育事业发展统计公报：义务教育阶段学校 18.00 万所，专任教师 1069.76 万人", src: "教育部 · 2026-07" },
  { text: "特岗计划实施 20 年：累计为中西部 22 省 3 万多所农村学校选聘教师 118 万人", src: "人民日报 · 2025-09" },
  { text: "乡村教育研究显示：多学科教学教师备课门数更多、时间更长，是首要压力来源", src: "乡村教育研究数据库" },
  { text: "2022 版义务教育课程标准全面实施，教案设计需对齐核心素养与学习任务群", src: "教育部 · 2022" }
];

function actionName(a) {
  return { GENERATE: "生成教案", EDIT: "编辑教案", EXPORT: "导出教案", LOGIN: "登录", COLLECT: "收藏", FEEDBACK: "反馈" }[a] || a;
}
function actionType(a) {
  return { GENERATE: "success", EDIT: "primary", EXPORT: "warning", LOGIN: "info", COLLECT: "", FEEDBACK: "danger" }[a] || "info";
}
function fmt(t) {
  return t ? String(t).replace("T", " ").slice(0, 16) : "-";
}

onMounted(async () => {
  try {
    const data = await request.get("/stats/overview");
    if (data && data.data) {
      const d = data.data;
      cards.value[0].value = d.totalTeachers ?? "-";
      cards.value[1].value = d.totalGenerations ?? "-";
      cards.value[2].value = d.totalDocs ?? "-";
      cards.value[3].value = (d.savedHours ?? 0) + "h";
      cards.value[4].value = (d.goodRate ?? 0) + "%";
      cards.value[5].value = d.schoolCount ?? "-";
      cards.value[6].value = d.publicLessons ?? "-";
      cards.value[7].value = d.avgQuality ?? "-";
    }
  } catch (e) {}

  try {
    const r = await request.get("/stats/recent?limit=8");
    recent.value = (r && r.data) || [];
  } catch (e) {}

  trendChart = echarts.init(trendRef.value);
  pieChart = echarts.init(pieRef.value);
  trendChart.setOption({
    tooltip: { trigger: "axis" },
    grid: { left: 40, right: 20, top: 30, bottom: 30 },
    xAxis: { type: "category", data: [] },
    yAxis: { type: "value", minInterval: 1 },
    series: [{ type: "line", smooth: true, areaStyle: { opacity: 0.15 }, itemStyle: { color: "#2e6b4f" }, lineStyle: { color: "#2e6b4f", width: 3 }, data: [] }]
  });
  pieChart.setOption({
    tooltip: { trigger: "item" },
    legend: { bottom: 0 },
    series: [{ type: "pie", radius: ["42%", "68%"], data: [], itemStyle: { borderRadius: 6 } }]
  });

  try {
    const t = await request.get("/stats/trend?days=7");
    const rows = (t && t.data) || [];
    if (rows.length) {
      trendEmpty.value = false;
      trendChart.setOption({
        xAxis: { type: "category", data: rows.map((r) => String(r.day).slice(5)) },
        series: [{ data: rows.map((r) => Number(r.cnt)) }]
      });
    }
  } catch (e) {}

  try {
    const p = await request.get("/stats/distribution");
    const map = (p && p.data) || {};
    const entries = Object.entries(map).map(([name, value]) => ({ name, value }));
    if (entries.length) {
      pieEmpty.value = false;
      pieChart.setOption({ series: [{ data: entries }] });
    }
  } catch (e) {}

  window.addEventListener("resize", resize);
});

function resize() {
  trendChart && trendChart.resize();
  pieChart && pieChart.resize();
}
onUnmounted(() => {
  window.removeEventListener("resize", resize);
  trendChart && trendChart.dispose();
  pieChart && pieChart.dispose();
});
</script>

<style scoped>
.metric { display: flex; align-items: center; gap: 16px; }
.metric-icon { width: 54px; height: 54px; border-radius: 14px; display: flex; align-items: center; justify-content: center; font-size: 26px; flex-shrink: 0; }
.metric-label { font-size: 13px; color: #8a8a8a; }
.metric-value { font-size: 28px; font-weight: 800; color: #2c2c2c; margin-top: 2px; }
.metric-desc { font-size: 12px; color: #b0b0b0; margin-top: 2px; }
.chart-empty { position: relative; margin-top: -280px; text-align: center; color: #b0b0b0; font-size: 13px; }
.insight { display: flex; gap: 12px; padding: 12px 0; border-bottom: 1px solid #f2f0ea; }
.insight:last-child { border-bottom: none; }
.insight-dot { width: 10px; height: 10px; border-radius: 50%; background: #e3a03c; margin-top: 7px; flex-shrink: 0; }
.insight-text { font-size: 13px; line-height: 1.6; color: #444; }
.insight-src { font-size: 12px; color: #a8a8a8; margin-top: 4px; }
</style>
