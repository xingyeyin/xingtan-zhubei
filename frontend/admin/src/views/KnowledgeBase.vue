<template>
  <el-card>
    <template #header>
      <div class="head">
        <span>知识库管理</span>
        <div class="head-ops">
          <el-radio-group v-model="docType" @change="load(1)">
            <el-radio-button label="">全部</el-radio-button>
            <el-radio-button label="STANDARD">课标</el-radio-button>
            <el-radio-button label="TEXTBOOK">教材</el-radio-button>
            <el-radio-button label="TEMPLATE">模板</el-radio-button>
            <el-radio-button label="CASE">乡土案例</el-radio-button>
          </el-radio-group>
          <el-button type="primary" @click="openUpload">＋ 上传文档</el-button>
        </div>
      </div>
    </template>

    <el-table :data="records" v-loading="loading">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="title" label="标题" min-width="260" show-overflow-tooltip />
      <el-table-column label="类型" width="110">
        <template #default="{ row }">
          <el-tag size="small" :type="typeTag(row.docType)">{{ typeName(row.docType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="subject" label="学科" width="90">
        <template #default="{ row }">{{ row.subject || "全学科" }}</template>
      </el-table-column>
      <el-table-column prop="grade" label="年级" width="90">
        <template #default="{ row }">{{ row.grade || "-" }}</template>
      </el-table-column>
      <el-table-column label="创建时间" width="170">
        <template #default="{ row }">{{ fmt(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="140">
        <template #default="{ row }">
          <el-button size="small" type="primary" link @click="detail(row)">详情</el-button>
          <el-button size="small" link @click="toast">编辑</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination layout="total, prev, pager, next" :total="total" :page-size="size" :current-page="page" @current-change="load" />
    </div>

    <el-dialog v-model="showUpload" title="上传知识文档" width="520" @closed="resetUpload">
      <el-form label-width="90px">
        <el-form-item label="文档类型">
          <el-select v-model="uploadForm.docType" style="width: 100%">
            <el-option label="课标" value="STANDARD" />
            <el-option label="教材目录" value="TEXTBOOK" />
            <el-option label="教案模板" value="TEMPLATE" />
            <el-option label="乡土案例" value="CASE" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题"><el-input v-model="uploadForm.title" placeholder="文档标题（留空则用文件名）" /></el-form-item>
        <el-form-item label="学科">
          <el-select v-model="uploadForm.subject" clearable style="width: 100%">
            <el-option v-for="s in subjects" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>
        <el-form-item label="文件">
          <el-upload
            drag
            :auto-upload="false"
            :limit="1"
            :on-change="onFileChange"
            :on-remove="onFileRemove"
            :file-list="fileList"
            accept=".pptx,.pdf,.docx,.txt,.md"
          >
            <div>将文件拖到此处，或<em>点击上传</em>（PPTX / PDF / DOCX / TXT / MD）</div>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showUpload = false">取消</el-button>
        <el-button type="primary" :loading="uploading" @click="submitUpload">提交入库</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showDetail" title="文档详情" width="640">
      <template v-if="detailData">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="标题">{{ detailData.title }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ typeName(detailData.docType) }}</el-descriptions-item>
          <el-descriptions-item label="学科">{{ detailData.subject || "全学科" }}</el-descriptions-item>
          <el-descriptions-item label="年级">{{ detailData.grade || "-" }}</el-descriptions-item>
          <el-descriptions-item label="教材版本">{{ detailData.textbookVersion || "-" }}</el-descriptions-item>
          <el-descriptions-item label="分块数">{{ chunks.length }}</el-descriptions-item>
        </el-descriptions>
        <div class="sub-title">内容分块（已解析入库，供 RAG 检索）</div>
        <div class="chunk-list" v-loading="chunkLoading">
          <div v-for="(c, i) in chunks" :key="c.id" class="chunk-item">
            <div class="chunk-seq">#{{ i + 1 }}</div>
            <div class="chunk-text">{{ c.content }}</div>
          </div>
          <el-empty v-if="!chunkLoading && chunks.length === 0" description="该文档暂无内容分块" :image-size="70" />
        </div>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import request from "../api/request";

const records = ref([]);
const total = ref(0);
const page = ref(1);
const size = 20;
const loading = ref(false);
const docType = ref("");
const showUpload = ref(false);
const showDetail = ref(false);
const uploading = ref(false);
const chunkLoading = ref(false);
const uploadForm = reactive({ docType: "STANDARD", title: "", subject: "" });
const fileList = ref([]);
const detailData = ref(null);
const chunks = ref([]);
const subjects = ["语文", "数学", "英语", "物理", "化学", "生物", "道德与法治", "历史", "地理"];

function typeName(t) {
  return { STANDARD: "课标", TEXTBOOK: "教材", TEMPLATE: "模板", CASE: "乡土案例", COURSEWARE: "课件" }[t] || "资源";
}
function typeTag(t) {
  return { STANDARD: "success", TEXTBOOK: "primary", TEMPLATE: "warning", CASE: "danger", COURSEWARE: "info" }[t] || "info";
}
function fmt(t) {
  return t ? String(t).replace("T", " ").slice(0, 16) : "-";
}
function toast() {
  ElMessage.info("编辑功能将在内容管理模块中提供");
}
function openUpload() {
  showUpload.value = true;
}
function resetUpload() {
  uploadForm.docType = "STANDARD";
  uploadForm.title = "";
  uploadForm.subject = "";
  fileList.value = [];
}
function onFileChange(file) {
  fileList.value = [file.raw];
}
function onFileRemove() {
  fileList.value = [];
}

async function submitUpload() {
  if (!fileList.value.length) return ElMessage.warning("请先选择文件");
  const fd = new FormData();
  fd.append("file", fileList.value[0]);
  fd.append("docType", uploadForm.docType);
  if (uploadForm.title) fd.append("title", uploadForm.title);
  if (uploadForm.subject) fd.append("subject", uploadForm.subject);
  uploading.value = true;
  try {
    const res = await request.post("/kb/upload", fd, { headers: { "Content-Type": "multipart/form-data" } });
    if (res && res.code === 200) {
      ElMessage.success("上传并解析入库成功");
      showUpload.value = false;
      load(1);
    } else {
      ElMessage.error((res && res.message) || "上传失败");
    }
  } catch (e) {
    ElMessage.error((e.response && e.response.data && e.response.data.message) || "上传失败，请检查文件格式");
  } finally {
    uploading.value = false;
  }
}

async function detail(row) {
  detailData.value = row;
  chunks.value = [];
  showDetail.value = true;
  chunkLoading.value = true;
  try {
    const res = await request.get(`/kb/documents/${row.id}/chunks`);
    chunks.value = (res && res.data) || [];
  } catch (e) {
    ElMessage.error("分块加载失败");
  } finally {
    chunkLoading.value = false;
  }
}

async function load(p) {
  page.value = p || 1;
  loading.value = true;
  try {
    const params = { page: page.value, size };
    if (docType.value) params.docType = docType.value;
    const res = await request.get("/kb/documents", { params });
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

onMounted(() => load(1));
</script>

<style scoped>
.head { display: flex; justify-content: space-between; align-items: center; }
.head-ops { display: flex; gap: 14px; align-items: center; }
.pager { display: flex; justify-content: flex-end; margin-top: 16px; }
.sub-title { margin: 14px 0 8px; font-weight: 600; color: #2e6b4f; }
.chunk-list { max-height: 360px; overflow: auto; border: 1px solid #e4e7ed; border-radius: 6px; padding: 8px; }
.chunk-item { display: flex; gap: 10px; padding: 8px; border-bottom: 1px dashed #ebeef5; }
.chunk-item:last-child { border-bottom: none; }
.chunk-seq { flex: 0 0 34px; height: 22px; line-height: 22px; text-align: center; background: #eaf3ee; color: #2e6b4f; border-radius: 4px; font-size: 12px; }
.chunk-text { flex: 1; font-size: 13px; color: #333; line-height: 1.6; white-space: pre-wrap; word-break: break-all; }
</style>
