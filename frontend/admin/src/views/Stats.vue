<template>
  <div>
    <el-row :gutter="16">
      <el-col :span="12">
        <el-card>
          <template #header>学科分布（教案）</template>
          <div ref="pieRef" style="height: 300px"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <template #header>近 7 日生成趋势</template>
          <div ref="lineRef" style="height: 300px"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="12">
        <el-card>
          <template #header>模型成本监控</template>
          <el-table :data="costRows" size="small">
            <el-table-column prop="provider" label="供应商" />
            <el-table-column prop="model" label="模型" />
            <el-table-column label="状态">
              <template #default="{ row }">
                <el-tag size="small" :type="row.configured ? 'success' : 'info'">{{ row.configured ? "已配置" : "未配置" }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="apiKeyMasked" label="Key" width="150" />
          </el-table>
          <div class="cost-note">调用费用与缓存命中率将随埋点接入后展示；当前为供应商配置状态。</div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <template #header>核心指标口径</template>
          <div class="formula">
            <div class="f-item"><b>节省备课时长</b>= 生成教案数 × 1.5 小时（基于基线调研的备课耗时差，试点后以实测为准）</div>
            <div class="f-item"><b>教案质量分</b>= 结构完整度 40% + 课标覆盖 30% + 情境贴合 20% + 可操作性 10%</div>
            <div class="f-item"><b>教师活跃</b>= 7 日内发生任意使用行为的教师数</div>
            <div class="f-item"><b>数据来源</b>= 埋点 usage_log 实时统计，试点月度报告可直接导出</div>
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

const pieRef = ref(null);
const lineRef = ref(null);
const costRows = ref([]);
let pie = null;
let line = null;

onMounted(async () => {
  pie = echarts.init(pieRef.value);
  line = echarts.init(lineRef.value);
  pie.setOption({ tooltip: { trigger: "item" }, legend: { bottom: 0 }, series: [{ type: "pie", radius: ["40%", "66%"], data: [] }] });
  line.setOption({
    tooltip: { trigger: "axis" },
    grid: { left: 40, right: 20, top: 30, bottom: 30 },
    xAxis: { type: "category", data: [] },
    yAxis: { type: "value", minInterval: 1 },
    series: [{ type: "bar", data: [], itemStyle: { color: "#2e6b4f", borderRadius: [6, 6, 0, 0] } }]
  });

  try {
    const p = await request.get("/stats/distribution");
    const entries = Object.entries((p && p.data) || {}).map(([name, value]) => ({ name, value }));
    pie.setOption({ series: [{ data: entries }] });
  } catch (e) {}
  try {
    const t = await request.get("/stats/trend?days=7");
    const rows = (t && t.data) || [];
    line.setOption({
      xAxis: { data: rows.map((r) => String(r.day).slice(5)) },
      series: [{ data: rows.map((r) => Number(r.cnt)) }]
    });
  } catch (e) {}
  try {
    const c = await request.get("/admin/config");
    const cfg = (c && c.data) || {};
    costRows.value = Object.entries(cfg.providers || {}).map(([provider, info]) => ({ provider, ...info }));
  } catch (e) {}

  window.addEventListener("resize", resize);
});
function resize() {
  pie && pie.resize();
  line && line.resize();
}
onUnmounted(() => {
  window.removeEventListener("resize", resize);
  pie && pie.dispose();
  line && line.dispose();
});
</script>

<style scoped>
.cost-note { margin-top: 14px; font-size: 12px; color: #a0a0a0; line-height: 1.6; }
.formula .f-item { padding: 14px 0; border-bottom: 1px solid #f2f0ea; font-size: 13px; color: #555; line-height: 1.6; }
.formula .f-item:last-child { border-bottom: none; }
</style>
