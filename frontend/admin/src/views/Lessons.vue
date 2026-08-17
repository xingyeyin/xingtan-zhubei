<template>
  <el-card>
    <template #header>
      <div class="head">
        <span>教案管理（全部教师）</span>
        <div class="head-ops">
          <el-input v-model="keyword" placeholder="搜索教案标题" clearable style="width: 220px" @keyup.enter="load(1)" />
          <el-button type="primary" @click="load(1)">查询</el-button>
        </div>
      </div>
    </template>

    <el-table :data="records" v-loading="loading">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
      <el-table-column prop="teacher" label="教师" width="110" />
      <el-table-column prop="school" label="学校" width="180" show-overflow-tooltip />
      <el-table-column prop="subject" label="学科" width="80" />
      <el-table-column prop="grade" label="年级" width="110" />
      <el-table-column prop="qualityScore" label="质量分" width="80" />
      <el-table-column label="公开" width="70">
        <template #default="{ row }">
          <el-tag size="small" :type="row.isPublic === 1 ? 'success' : 'info'">{{ row.isPublic === 1 ? "是" : "否" }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="170">
        <template #default="{ row }">{{ fmt(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="90">
        <template #default="{ row }">
          <el-button size="small" type="danger" link @click="removeLesson(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination layout="total, prev, pager, next" :total="total" :page-size="size" :current-page="page" @current-change="load" />
    </div>
  </el-card>
</template>

<script setup>
import { onMounted, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import request from "../api/request";

const records = ref([]);
const total = ref(0);
const page = ref(1);
const size = 20;
const loading = ref(false);
const keyword = ref("");

function fmt(t) {
  return t ? String(t).replace("T", " ").slice(0, 16) : "-";
}

async function load(p) {
  page.value = p || 1;
  loading.value = true;
  try {
    const params = { page: page.value, size };
    if (keyword.value) params.keyword = keyword.value;
    const res = await request.get("/admin/lessons", { params });
    if (res && res.data) {
      records.value = res.data.records || [];
      total.value = Number(res.data.total || 0);
    }
  } catch (e) {
    ElMessage.error("加载失败");
  } finally {
    loading.value = false;
  }
}

async function removeLesson(row) {
  try {
    await ElMessageBox.confirm("确定删除教案「" + row.title + "」？删除后不可恢复。", "提示", { type: "warning" });
    await request.delete("/admin/lessons/" + row.id);
    ElMessage.success("已删除");
    load(page.value);
  } catch (e) {}
}

onMounted(() => load(1));
</script>

<style scoped>
.head { display: flex; justify-content: space-between; align-items: center; }
.head-ops { display: flex; gap: 10px; }
.pager { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
