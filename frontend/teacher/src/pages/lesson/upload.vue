<template>
  <view class="page">
    <view class="card">
      <view class="card-title">📁 导入课件，AI 帮你分析备课与 PPT</view>
      <view class="file-box" @tap="choose">
        <text class="file-icon">📄</text>
        <text class="file-name">{{ fileName || "点击选择文件（PPT / PDF / Word / TXT / MD）" }}</text>
      </view>
      <input ref="fileInput" type="file" accept=".pptx,.pdf,.docx,.txt,.md" style="display:none" @change="onFile" />
      <text class="label">课件标题</text>
      <input class="input" v-model="title" placeholder="如：《秋天的雨》课件" />
      <text class="label">学科</text>
      <input class="input" v-model="subject" placeholder="如：语文" />
      <view class="primary-btn" :class="{ disabled: loading }" @tap="upload">
        {{ loading ? "分析中，请稍候（约 30 秒）…" : "上传并 AI 分析" }}
      </view>
      <view class="tip">支持 pptx / pdf / docx / txt / md，文件不超过 20MB。上传后系统会抽取课件内容，生成教学目标、教案建议与 PPT 大纲。</view>
    </view>

    <view v-if="result" class="card">
      <view class="card-title">🤖 AI 分析结果</view>
      <text class="result-text">{{ result }}</text>
      <view class="btn-row">
        <view class="ghost-btn" @tap="copy">复制结果</view>
        <view class="primary-btn small" @tap="useResult">用结果生成教案</view>
      </view>
      <view class="ppt-btn" :class="{ disabled: downloading }" @tap="downloadPpt">
        {{ downloading ? "🎨 AI 课件生成中…" : "🎨 生成美化课件（AI 约 30 秒）" }}
      </view>
      <view class="demo-btn" :class="{ disabled: downloading }" @tap="simulateDemo">🎬 演示模式：测试加载遮罩（无需后端）</view>
    </view>

    <!-- 导出加载遮罩 -->
    <view v-if="downloading" class="dl-mask" @tap.stop>
      <view class="dl-card">
        <view class="dl-spinner"></view>
        <text class="dl-text">{{ downloadingText }}</text>
        <text class="dl-sub">请耐心等待，不要离开页面</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, watch, onBeforeUnmount } from "vue";
import { onLoad, onUnload } from "@dcloudio/uni-app";
import { getToken } from "../../utils/auth";
import { request } from "../../utils/request";

const fileInput = ref(null);
const file = ref(null);
const fileName = ref("");
const title = ref("");
const subject = ref("");
const loading = ref(false);
const result = ref("");
const lastDocId = ref(null);
const downloading = ref(false);
const downloadingText = ref("");
const DL_KEY = "xt_dl_state_upload";
let simulateTimer = null;
let simulateRemaining = 0;

/**
 * 持久化：遮罩状态写入 sessionStorage
 */
watch([downloading, downloadingText], ([dl, text]) => {
  try {
    if (dl) {
      sessionStorage.setItem(DL_KEY, JSON.stringify({
        downloading: true,
        text: text || "",
        remaining: simulateRemaining,
        ts: Date.now()
      }));
    } else {
      sessionStorage.removeItem(DL_KEY);
    }
  } catch (e) {}
});

function startSimulateCountdown(seconds) {
  if (simulateTimer) clearInterval(simulateTimer);
  simulateRemaining = seconds;
  simulateTimer = setInterval(() => {
    simulateRemaining--;
    if (simulateRemaining <= 0) {
      clearInterval(simulateTimer);
      simulateTimer = null;
      return;
    }
    try {
      const raw = sessionStorage.getItem(DL_KEY);
      if (raw) {
        const s = JSON.parse(raw);
        s.remaining = simulateRemaining;
        sessionStorage.setItem(DL_KEY, JSON.stringify(s));
      }
    } catch (e) {}
  }, 1000);
}
function stopSimulateCountdown() {
  if (simulateTimer) {
    clearInterval(simulateTimer);
    simulateTimer = null;
  }
}

onLoad(() => {
  try {
    const raw = sessionStorage.getItem(DL_KEY);
    if (raw) {
      const state = JSON.parse(raw);
      if (state && state.downloading) {
        const remaining = state.remaining || 0;
        if (remaining > 0 && remaining < 30) {
          downloading.value = true;
          downloadingText.value = state.text || "正在生成…";
          startSimulateCountdown(remaining);
          setTimeout(() => {
            stopSimulateCountdown();
            downloading.value = false;
            uni.showToast({ title: "演示完成 ✓", icon: "success" });
          }, remaining * 1000);
          uni.showToast({ title: "🔄 已恢复遮罩状态", icon: "none", duration: 1500 });
        } else {
          sessionStorage.removeItem(DL_KEY);
          uni.showModal({
            title: "下载被中断",
            content: "检测到页面被刷新，上一次下载已终止。请重新点击生成课件。",
            showCancel: false,
            confirmText: "知道了"
          });
        }
      }
    }
  } catch (e) {}
});
onUnload(() => stopSimulateCountdown());
onBeforeUnmount(() => stopSimulateCountdown());

function choose() {
  fileInput.value.$el && fileInput.value.$el.click();
}
function onFile(e) {
  const f = e.target.files && e.target.files[0];
  if (f) {
    file.value = f;
    fileName.value = f.name;
    if (!title.value) title.value = f.name.replace(/\.[^.]+$/, "");
  }
}

function upload() {
  if (!file.value) return uni.showToast({ title: "请先选择文件", icon: "none" });
  loading.value = true;
  result.value = "";
  const fd = new FormData();
  fd.append("file", file.value);
  fd.append("title", title.value || fileName.value);
  fd.append("subject", subject.value || "");
  const xhr = new XMLHttpRequest();
  xhr.open("POST", "/api/kb/upload");
  xhr.setRequestHeader("Authorization", "Bearer " + getToken());
  xhr.onload = () => {
    try {
      const res = JSON.parse(xhr.responseText);
      if (res.code === 200) {
        analyze(res.data.id);
      } else {
        loading.value = false;
        uni.showToast({ title: res.message || "上传失败", icon: "none" });
      }
    } catch (err) {
      loading.value = false;
      uni.showToast({ title: "上传失败", icon: "none" });
    }
  };
  xhr.onerror = () => {
    loading.value = false;
    uni.showToast({ title: "网络异常", icon: "none" });
  };
  xhr.send(fd);
}

async function analyze(docId) {
  lastDocId.value = docId;
  try {
    result.value = await request({
      url: "/api/ai/analyze-courseware",
      method: "POST",
      data: { docId },
      timeout: 180000
    });
  } catch (e) {
    result.value = "分析失败，请重试";
  } finally {
    loading.value = false;
  }
}

function copy() {
  uni.setClipboardData({ data: result.value });
}

function useResult() {
  uni.navigateTo({
    url: "/pages/lesson/wizard?subject=" + encodeURIComponent(subject.value) + "&extra=" + encodeURIComponent(result.value)
  });
}

function downloadPpt() {
  if (!lastDocId.value) return uni.showToast({ title: "请先上传课件", icon: "none" });
  if (downloading.value) return;
  downloading.value = true;
  downloadingText.value = "正在生成 AI 精美课件，约 10-30 秒…";
  const url = "/api/ai/courseware/" + lastDocId.value + "/export-ppt";
  const filename = (title.value || "课件") + "（AI美化课件）.pptx";
  fetch(url, { headers: { Authorization: "Bearer " + getToken() } })
    .then((res) => res.blob())
    .then((blob) => {
      const objUrl = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = objUrl;
      a.download = filename;
      document.body.appendChild(a);
      a.click();
      setTimeout(() => {
        URL.revokeObjectURL(objUrl);
        a.remove();
      }, 1000);
      stopSimulateCountdown();
      downloading.value = false;
      uni.showToast({ title: "美化课件已保存到本地", icon: "success" });
    })
    .catch(() => {
      stopSimulateCountdown();
      downloading.value = false;
      sessionStorage.removeItem(DL_KEY);
      uni.showToast({ title: "生成失败，请重试", icon: "none" });
    });
}

/**
 * 演示模式：模拟 PPT 生成全过程，测试加载遮罩（含刷新恢复）
 */
let simulateIdx = 0;
function simulateDemo() {
  simulateIdx++;
  if (downloading.value) return;
  const scenarios = [
    { text: "正在生成 AI 精美课件，约 10-30 秒…", seconds: 4 },
    { text: "正在分析课件结构…", seconds: 2 },
    { text: "正在渲染 PPTX 文件…", seconds: 3 }
  ];
  const s = scenarios[(simulateIdx - 1) % scenarios.length];
  uni.showToast({ title: "▶ 第" + ((simulateIdx - 1) % scenarios.length + 1) + "组演示", icon: "none", duration: 1200 });
  setTimeout(() => {
    downloading.value = true;
    downloadingText.value = s.text;
    startSimulateCountdown(s.seconds);
    setTimeout(() => {
      stopSimulateCountdown();
      downloading.value = false;
      uni.showToast({ title: "演示完成 ✓", icon: "success" });
    }, s.seconds * 1000);
  }, 400);
}
</script>

<style scoped>
.page { padding: 24rpx; }
.card { background: #fff; border-radius: 22rpx; padding: 28rpx; margin-bottom: 20rpx; box-shadow: 0 4rpx 14rpx rgba(44, 44, 44, 0.04); }
.card-title { font-size: 30rpx; font-weight: 700; margin-bottom: 18rpx; }
.file-box { background: #f7f4ec; border: 3rpx dashed #cfd8cf; border-radius: 18rpx; padding: 40rpx 20rpx; text-align: center; }
.file-icon { display: block; font-size: 60rpx; }
.file-name { display: block; margin-top: 14rpx; font-size: 26rpx; color: #666; }
.label { display: block; font-size: 26rpx; color: #555; margin: 22rpx 0 10rpx; }
.input { background: #f7f4ec; border-radius: 14rpx; padding: 20rpx 24rpx; font-size: 28rpx; }
.primary-btn { background: #2e6b4f; color: #fff; text-align: center; padding: 24rpx 0; border-radius: 16rpx; font-size: 30rpx; font-weight: 700; margin-top: 26rpx; }
.disabled { opacity: 0.6; }
.small { margin-top: 0; font-size: 27rpx; padding: 20rpx 0; }
.tip { margin-top: 20rpx; font-size: 23rpx; color: #a0a0a0; line-height: 1.6; }
.result-text { font-size: 25rpx; line-height: 1.7; white-space: pre-wrap; }
.btn-row { display: flex; gap: 20rpx; margin-top: 22rpx; }
.ghost-btn { flex: 1; background: #e9f2ec; color: #2e6b4f; text-align: center; padding: 20rpx 0; border-radius: 14rpx; font-size: 27rpx; }
.btn-row .primary-btn { flex: 1; }
.ppt-btn { margin-top: 18rpx; background: linear-gradient(135deg, #2e6b4f, #3e8a63); color: #fff; text-align: center; padding: 22rpx 0; border-radius: 14rpx; font-size: 28rpx; font-weight: 700; }
.demo-btn { margin-top: 16rpx; background: linear-gradient(135deg, #fdf3e3, #fff8e7); color: #a26b20; text-align: center; padding: 20rpx 0; border-radius: 14rpx; font-size: 26rpx; font-weight: 600; border: 2rpx dashed #e3a03c; }
.disabled { opacity: 0.6; pointer-events: none; }

/* 导出加载遮罩 */
.dl-mask { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.45); z-index: 9999; display: flex; align-items: center; justify-content: center; }
.dl-card { background: #fff; border-radius: 24rpx; padding: 50rpx 60rpx; display: flex; flex-direction: column; align-items: center; box-shadow: 0 8rpx 30rpx rgba(0,0,0,0.15); }
.dl-spinner { width: 56rpx; height: 56rpx; border: 6rpx solid #e9f2ec; border-top-color: #2e6b4f; border-radius: 50%; animation: dl-spin 0.8s linear infinite; }
@keyframes dl-spin { to { transform: rotate(360deg); } }
.dl-text { font-size: 28rpx; font-weight: 600; color: #333; margin-top: 26rpx; }
.dl-sub { font-size: 23rpx; color: #999; margin-top: 10rpx; }
</style>
