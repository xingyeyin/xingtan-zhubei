<template>
  <div>
    <el-card>
      <template #header>模型供应商配置</template>
      <el-form label-width="140px">
        <el-form-item label="默认供应商">
          <el-select v-model="defaultProvider">
            <el-option label="DeepSeek" value="deepseek" />
            <el-option label="通义千问" value="qwen" />
          </el-select>
        </el-form-item>
      </el-form>
      <el-row :gutter="16">
        <el-col :span="12" v-for="(info, name) in providers" :key="name">
          <div class="provider-card">
            <div class="provider-head">
              <span class="provider-name">{{ name === "qwen" ? "通义千问" : "DeepSeek" }}</span>
              <el-tag size="small" :type="info.configured ? 'success' : 'info'">{{ info.configured ? "已配置" : "未配置" }}</el-tag>
            </div>
            <div class="provider-row">接口：{{ info.baseUrl }}</div>
            <div class="provider-row">模型：{{ info.model }}</div>
            <div class="provider-row">Key：{{ info.apiKeyMasked || "未填写（在 application-local.yml 配置）" }}</div>
          </div>
        </el-col>
      </el-row>
    </el-card>

    <el-card style="margin-top: 16px">
      <template #header>平台参数</template>
      <el-form label-width="140px">
        <el-form-item label="单用户日配额">
          <el-input-number :model-value="50" :min="1" :max="500" />
          <span class="hint">次/天（防滥用）</span>
        </el-form-item>
        <el-form-item label="生成超时时间">
          <el-input-number :model-value="120" :min="30" :max="600" />
          <span class="hint">秒</span>
        </el-form-item>
        <el-form-item label="结果缓存">
          <el-switch :model-value="true" />
          <span class="hint">相同任务参数直接返回缓存，节省成本</span>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="save">保存配置</el-button>
          <el-button @click="reload">刷新</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import request from "../api/request";

const defaultProvider = ref("qwen");
const providers = ref({});

async function load() {
  try {
    const res = await request.get("/admin/config");
    if (res && res.data) {
      defaultProvider.value = res.data.defaultProvider || "qwen";
      providers.value = res.data.providers || {};
    }
  } catch (e) {
    ElMessage.error("配置读取失败");
  }
}
function reload() {
  load();
}
function save() {
  ElMessage.success("已保存（当前为前端演示，服务端持久化开发中）");
}

onMounted(load);
</script>

<style scoped>
.provider-card { border: 1px solid #edf0ea; border-radius: 12px; padding: 20px; margin-bottom: 16px; }
.provider-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.provider-name { font-weight: 700; font-size: 15px; }
.provider-row { font-size: 13px; color: #666; margin-top: 8px; }
.hint { margin-left: 12px; font-size: 12px; color: #a0a0a0; }
</style>
