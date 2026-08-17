<template>
  <div>
    <el-row :gutter="16">
      <el-col :span="8" v-for="card in cards" :key="card.label">
        <el-card class="metric" shadow="hover">
          <div class="metric-label">{{ card.label }}</div>
          <div class="metric-value" :style="{ color: card.color }">{{ card.value }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card style="margin-top: 16px">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="审核记录" name="review">
          <el-table :data="records" size="small">
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="taskId" label="任务ID" width="100">
              <template #default="{ row }">{{ row.taskId || "-" }}</template>
            </el-table-column>
            <el-table-column prop="contentType" label="内容类型" width="130" />
            <el-table-column label="结果" width="110">
              <template #default="{ row }">
                <el-tag size="small" :type="row.result === 'PASS' ? 'success' : 'danger'">{{ row.result === "PASS" ? "通过" : "拦截" }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="detail" label="说明" min-width="260" />
            <el-table-column label="时间" width="180">
              <template #default="{ row }">{{ fmt(row.createdAt) }}</template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="用户反馈" name="feedback">
          <el-table :data="feedbacks" size="small">
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="nickname" label="教师" width="120" />
            <el-table-column label="类型" width="110">
              <template #default="{ row }">
                <el-tag size="small" :type="typeTag(row.type)">{{ typeName(row.type) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="content" label="内容" min-width="300" show-overflow-tooltip />
            <el-table-column prop="lessonPlanId" label="教案ID" width="90">
              <template #default="{ row }">{{ row.lessonPlanId || "-" }}</template>
            </el-table-column>
            <el-table-column label="时间" width="180">
              <template #default="{ row }">{{ fmt(row.createdAt) }}</template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-card style="margin-top: 16px">
      <template #header>审核机制（四层防线）</template>
      <el-row :gutter="16">
        <el-col :span="6" v-for="layer in layers" :key="layer.name">
          <div class="layer">
            <div class="layer-icon">{{ layer.icon }}</div>
            <div class="layer-name">{{ layer.name }}</div>
            <div class="layer-desc">{{ layer.desc }}</div>
          </div>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from "vue";
import request from "../api/request";

const activeTab = ref("review");
const records = ref([]);
const feedbacks = ref([]);
const cards = ref([
  { label: "待人工审核", value: 0, color: "#e3a03c" },
  { label: "自动通过", value: 0, color: "#2e6b4f" },
  { label: "拦截记录", value: 0, color: "#c0392b" }
]);
const layers = [
  { icon: "📝", name: "敏感词规则", desc: "内置教育领域敏感词库，命中即拦截" },
  { icon: "🔍", name: "规则校验", desc: "JSON 结构、字段完整性与时长合理性校验" },
  { icon: "🤖", name: "模型复核", desc: "对高风险内容调用大模型二次判断" },
  { icon: "👨‍🏫", name: "人工抽检", desc: "管理端人工抽检与申诉处理" }
];

function typeName(t) {
  return { USEFUL: "用得上", NEED_FIX: "需修改", REPORT: "问题反馈", SUGGEST: "建议" }[t] || t;
}
function typeTag(t) {
  return { USEFUL: "success", NEED_FIX: "warning", REPORT: "danger", SUGGEST: "primary" }[t] || "info";
}
function fmt(t) {
  return t ? String(t).replace("T", " ").slice(0, 16) : "-";
}

onMounted(async () => {
  try {
    const res = await request.get("/stats/reviews");
    if (res && res.data) {
      records.value = res.data.records || [];
      cards.value[0].value = res.data.pending ?? 0;
      cards.value[1].value = res.data.pass ?? 0;
      cards.value[2].value = res.data.blocked ?? 0;
    }
  } catch (e) {}
  try {
    const fb = await request.get("/admin/feedbacks");
    feedbacks.value = (fb && fb.data && fb.data.records) || [];
  } catch (e) {}
});
</script>

<style scoped>
.metric { text-align: center; padding: 8px 0; }
.metric-label { color: #8a8a8a; font-size: 13px; }
.metric-value { font-size: 34px; font-weight: 800; margin-top: 6px; }
.layer { text-align: center; background: #f8faf7; border-radius: 12px; padding: 24px 12px; }
.layer-icon { font-size: 32px; }
.layer-name { font-weight: 700; margin-top: 10px; font-size: 14px; }
.layer-desc { font-size: 12px; color: #8a8a8a; margin-top: 6px; line-height: 1.5; }
</style>
