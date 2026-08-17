<template>
  <el-card>
    <template #header>
      <div class="head">
        <span>用户管理</span>
        <div class="head-ops">
          <el-input v-model="keyword" placeholder="搜索姓名/手机号" clearable style="width: 200px" @keyup.enter="load(1)" />
          <el-select v-model="role" placeholder="角色" clearable style="width: 130px">
            <el-option label="教师" value="TEACHER" />
            <el-option label="管理员" value="ADMIN" />
            <el-option label="运营" value="OPERATOR" />
          </el-select>
          <el-button type="primary" @click="load(1)">查询</el-button>
          <el-button @click="reset">重置</el-button>
        </div>
      </div>
    </template>

    <el-table :data="records" v-loading="loading">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="姓名" width="140">
        <template #default="{ row }">
          <div class="user-cell">
            <span class="avatar">{{ row.nickname ? row.nickname.charAt(0) : "?" }}</span>
            <span>{{ row.nickname }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="phone" label="手机号" width="150" />
      <el-table-column prop="school" label="学校" />
      <el-table-column label="教案数" width="90">
        <template #default="{ row }">{{ row.lessonCount ?? 0 }}</template>
      </el-table-column>
      <el-table-column label="角色" width="110">
        <template #default="{ row }">
          <el-tag size="small" :type="row.role === 'ADMIN' ? 'warning' : row.role === 'OPERATOR' ? 'info' : 'success'">
            {{ roleName(row.role) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="row.status === 1 ? 'success' : 'danger'">{{ row.status === 1 ? "正常" : "禁用" }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="注册时间" width="170">
        <template #default="{ row }">{{ fmt(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="190">
        <template #default="{ row }">
          <el-button size="small" type="primary" link @click="openDetail(row)">详情</el-button>
          <el-button size="small" :type="row.status === 1 ? 'danger' : 'success'" link @click="toggleStatus(row)">
            {{ row.status === 1 ? "禁用" : "启用" }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        layout="total, prev, pager, next"
        :total="total"
        :page-size="size"
        :current-page="page"
        @current-change="load"
      />
    </div>

    <!-- 用户详情 -->
    <el-dialog v-model="showDetail" :title="'用户详情 - ' + (detail.nickname || '')" width="760px">
      <template v-if="detail.id">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="ID">{{ detail.id }}</el-descriptions-item>
          <el-descriptions-item label="手机号">{{ detail.phone }}</el-descriptions-item>
          <el-descriptions-item label="学校">{{ detail.school || "-" }}</el-descriptions-item>
          <el-descriptions-item label="任教学科">{{ detail.subjects || "-" }}</el-descriptions-item>
          <el-descriptions-item label="年级">{{ detail.grades || "-" }}</el-descriptions-item>
          <el-descriptions-item label="角色">{{ roleName(detail.role) }}</el-descriptions-item>
          <el-descriptions-item label="教案数">
            <el-tag size="small" type="success">{{ detail.lessonCount ?? 0 }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="使用记录数">
            <el-tag size="small">{{ detail.usageCount ?? 0 }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="注册时间" :span="2">{{ fmt(detail.createdAt) }}</el-descriptions-item>
        </el-descriptions>

        <div class="sub-title">最近使用记录</div>
        <el-table :data="detail.recentLogs || []" size="small" border>
          <el-table-column prop="action" label="动作" width="130" />
          <el-table-column prop="scene" label="场景" width="140" />
          <el-table-column prop="duration_sec" label="耗时(s)" width="90" />
          <el-table-column label="时间">
            <template #default="{ row }">{{ fmt(row.created_at) }}</template>
          </el-table-column>
        </el-table>

        <div class="sub-title">教案列表（最近 10 份）</div>
        <el-table :data="detail.lessons || []" size="small" border>
          <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
          <el-table-column prop="subject" label="学科" width="80" />
          <el-table-column prop="grade" label="年级" width="110" />
          <el-table-column prop="qualityScore" label="质量分" width="90" />
          <el-table-column label="公开" width="80">
            <template #default="{ row }">{{ row.isPublic === 1 ? "是" : "否" }}</template>
          </el-table-column>
        </el-table>
      </template>
    </el-dialog>
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
const role = ref("");
const showDetail = ref(false);
const detail = ref({});

function roleName(r) {
  return { TEACHER: "教师", ADMIN: "管理员", OPERATOR: "运营" }[r] || r;
}
function fmt(t) {
  return t ? String(t).replace("T", " ").slice(0, 16) : "-";
}
function reset() {
  keyword.value = "";
  role.value = "";
  load(1);
}

async function load(p) {
  page.value = p || 1;
  loading.value = true;
  try {
    const params = { page: page.value, size };
    if (keyword.value) params.keyword = keyword.value;
    if (role.value) params.role = role.value;
    const res = await request.get("/users", { params });
    if (res && res.data) {
      records.value = res.data.records || [];
      total.value = Number(res.data.total || 0);
    }
  } catch (e) {
    ElMessage.error("加载失败，请确认后端已启动");
  } finally {
    loading.value = false;
  }
}

async function openDetail(row) {
  try {
    const res = await request.get("/admin/users/" + row.id);
    detail.value = (res && res.data) || {};
    showDetail.value = true;
  } catch (e) {
    ElMessage.error("详情加载失败");
  }
}

async function toggleStatus(row) {
  const target = row.status === 1 ? 0 : 1;
  try {
    await ElMessageBox.confirm(target === 0 ? "确定禁用该账号？" : "确定启用该账号？", "提示", {
      type: "warning"
    });
    await request.put("/admin/users/" + row.id + "/status", { status: target });
    ElMessage.success(target === 0 ? "已禁用" : "已启用");
    load(page.value);
  } catch (e) {}
}

onMounted(() => load(1));
</script>

<style scoped>
.head { display: flex; justify-content: space-between; align-items: center; }
.head-ops { display: flex; gap: 10px; }
.user-cell { display: flex; align-items: center; gap: 8px; }
.avatar { width: 30px; height: 30px; border-radius: 50%; background: #e9f2ec; color: #2e6b4f; display: inline-flex; align-items: center; justify-content: center; font-size: 13px; font-weight: 700; }
.pager { display: flex; justify-content: flex-end; margin-top: 16px; }
.sub-title { font-weight: 700; margin: 18px 0 10px; }
</style>
