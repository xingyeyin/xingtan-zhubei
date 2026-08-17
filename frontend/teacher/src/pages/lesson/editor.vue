<template>
  <view class="page">
    <view v-if="!plan" class="empty">加载教案中…</view>

    <block v-else>
      <view class="head">
        <view>
          <text class="title">{{ plan.title }}</text>
          <view class="tags">
            <text class="tag">{{ plan.subject }}</text>
            <text class="tag">{{ plan.grade }}</text>
            <text class="tag">{{ plan.textbook }}</text>
            <text class="tag">{{ plan.lessonType }}</text>
          </view>
        </view>
        <view class="score" v-if="content.qualityScore">
          <text class="score-num">{{ content.qualityScore }}</text>
          <text class="score-label">质量分</text>
        </view>
        <view class="share-badge" :class="{ on: plan.isPublic === 1 }" @tap="toggleShare">
          {{ plan.isPublic === 1 ? "已公开" : "私密" }}
        </view>
        <view class="share-badge" @tap="feedback">反馈</view>
      </view>

      <!-- 教学目标 -->
      <view class="section">
        <view class="section-head">
          <text class="section-name">🎯 教学目标</text>
          <view class="section-ops">
            <text class="section-op" @tap="regenerateObjectives">重新生成</text>
            <text class="section-op" @tap="aiAsk('把教学目标改得更具体可操作')">AI 优化</text>
          </view>
        </view>
        <view v-for="(obj, i) in content.objectives" :key="i" class="obj-item">
          <text class="obj-type">{{ obj.type }}</text>
          <text class="obj-content">{{ obj.content }}</text>
        </view>
      </view>

      <!-- 重难点 -->
      <view class="section">
        <view class="section-head">
          <text class="section-name">📍 教学重难点</text>
          <view class="section-ops">
            <text class="section-op" @tap="regenerateKeyPoints">重新生成</text>
          </view>
        </view>
        <view class="kp-row">
          <text class="kp-label">重点</text>
          <text class="kp-text">{{ joinList(content.keyPoints) }}</text>
        </view>
        <view class="kp-row">
          <text class="kp-label diff">难点</text>
          <text class="kp-text">{{ joinList(content.difficultPoints) }}</text>
        </view>
      </view>

      <!-- 教学过程 -->
      <view class="section">
        <view class="section-head"><text class="section-name">🕐 教学过程</text></view>
        <view v-for="(p, i) in content.process" :key="i" class="process-item">
          <view class="process-head">
            <text class="process-stage">{{ p.stage }}</text>
            <text class="process-time">{{ p.duration }} 分钟</text>
          </view>
          <text class="process-body">{{ p.activities }}</text>
          <view class="process-ops">
            <text @tap="moveStage(i, -1)">↑ 上移</text>
            <text @tap="moveStage(i, 1)">↓ 下移</text>
            <text @tap="rewriteStage(i)">AI 重写</text>
            <text class="danger" @tap="removeStage(i)">删除</text>
          </view>
        </view>
        <view class="add-stage" @tap="insertStage(content.process.length - 1)">＋ 在末尾插入环节</view>
      </view>

      <!-- 板书设计 -->
      <view class="section">
        <view class="section-head">
          <text class="section-name">🖊 板书设计</text>
          <view class="section-ops">
            <text class="section-op" @tap="regenerateBoard">重新生成</text>
          </view>
        </view>
        <text class="board">{{ content.boardDesign }}</text>
      </view>

      <!-- 作业设计 -->
      <view class="section">
        <view class="section-head">
          <text class="section-name">📚 分层作业</text>
          <view class="section-ops">
            <text class="section-op" @tap="regenerateHomework">重新生成</text>
          </view>
        </view>
        <view v-for="(hw, i) in content.homework" :key="i" class="hw-item">
          <text class="hw-level">{{ hw.level }}</text>
          <view class="hw-items">
            <text v-for="(it, j) in hw.items" :key="j" class="hw-text">· {{ it }}</text>
          </view>
        </view>
      </view>

      <!-- 课标依据 -->
      <view class="section standard">
        <view class="section-head">
          <text class="section-name">📜 课标依据卡片</text>
          <view class="section-ops">
            <text class="section-op" @tap="regenerateCards">重新生成</text>
          </view>
        </view>
        <view v-for="(c, i) in content.standardCards" :key="i" class="standard-card">
          <text class="standard-ref">{{ c.ref }}</text>
          <text class="standard-content">{{ c.content }}</text>
        </view>
      </view>

      <!-- AI 生成标识（合规要求） -->
      <view class="ai-badge">
        <text>🤖 本教案由 AI 辅助生成，请教师审核后使用（符合生成式 AI 内容标识要求）</text>
      </view>

      <view class="bottom-space"></view>

      <!-- 底部操作栏 -->
      <view class="float-bar">
        <view class="float-btn" :class="{ 'fb-disabled': downloading }" @tap="openAi"><text class="fb-icon">💬</text><text>AI 对话</text></view>
        <view class="float-btn" :class="{ 'fb-disabled': downloading }" @tap="savePlan"><text class="fb-icon">💾</text><text>保存</text></view>
        <view class="float-btn" :class="{ 'fb-disabled': downloading }" @tap="exportPpt"><text class="fb-icon">📊</text><text>导出PPT</text></view>
        <view class="float-btn" :class="{ 'fb-disabled': downloading }" @tap="exportDocx"><text class="fb-icon">📝</text><text>导出Word</text></view>
        <view class="float-btn" :class="{ 'fb-disabled': downloading }" @tap="exportPlan"><text class="fb-icon">📄</text><text>导出MD</text></view>
        <view class="float-btn" :class="{ 'fb-disabled': downloading }" @tap="shareLesson"><text class="fb-icon">🔗</text><text>分享</text></view>
        <view class="float-btn float-demo" :class="{ 'fb-disabled': downloading }" @tap="simulateDemo"><text class="fb-icon">🎬</text><text>演示</text></view>
      </view>

      <!-- 导出加载遮罩 -->
      <view v-if="downloading" class="dl-mask" @tap.stop>
        <view class="dl-card">
          <view class="dl-spinner"></view>
          <text class="dl-text">{{ downloadingText }}</text>
          <text class="dl-sub">请耐心等待，不要离开页面</text>
        </view>
      </view>

      <!-- AI 对话面板 -->
      <view v-if="showAi" class="ai-mask" @tap="showAi = false">
        <view class="ai-panel" @tap.stop>
          <view class="ai-head">
            <text class="ai-title">AI 备课助手</text>
            <text class="ai-close" @tap="showAi = false">✕</text>
          </view>
          <view class="ai-suggests">
            <text v-for="s in aiSuggests" :key="s" class="ai-chip" @tap="aiAsk(s)">{{ s }}</text>
          </view>
          <scroll-view scroll-y class="ai-result">
            <text v-if="!aiResult" class="ai-placeholder">选择一个建议或输入你的问题，AI 会结合教案上下文回答。</text>
            <text v-else class="ai-text">{{ aiResult }}</text>
          </scroll-view>
          <view class="ai-input-row">
            <input class="ai-input" v-model="aiInput" placeholder="例如：加入乡土案例导入" />
            <view class="ai-send" @tap="sendAi">发送</view>
          </view>
        </view>
      </view>
    </block>
  </view>
</template>

<script setup>
import { computed, ref, watch, onBeforeUnmount } from "vue";
import { onLoad, onUnload } from "@dcloudio/uni-app";
import { request } from "../../utils/request";
import { getToken } from "../../utils/auth";

const plan = ref(null);
const content = ref({ objectives: [], keyPoints: [], difficultPoints: [], process: [], boardDesign: "", homework: [], standardCards: [] });
const showAi = ref(false);
const aiInput = ref("");
const aiResult = ref("");
const aiSuggests = ["加入乡土案例导入", "设计小组合作活动", "把语言改得适合乡村学生", "生成课堂提问清单", "补充分层作业", "生成板书设计"];
const downloading = ref(false);
const downloadingText = ref("");
const DL_KEY = "xt_dl_state_editor";
let simulateTimer = null;
let simulateRemaining = 0;

/**
 * 持久化：遮罩状态变化时写入 sessionStorage
 * 刷新页面后可恢复，演示不中断
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

/**
 * 模拟倒计时：每秒写入 remaining，用于刷新后恢复
 */
function startSimulateCountdown(seconds) {
  stopSimulateCountdown();
  simulateRemaining = seconds;
  simulateTimer = setInterval(() => {
    simulateRemaining--;
    if (simulateRemaining <= 0) {
      stopSimulateCountdown();
    } else {
      try {
        const raw = sessionStorage.getItem(DL_KEY);
        if (raw) {
          const s = JSON.parse(raw);
          s.remaining = simulateRemaining;
          sessionStorage.setItem(DL_KEY, JSON.stringify(s));
        }
      } catch (e) {}
    }
  }, 1000);
}
function stopSimulateCountdown() {
  if (simulateTimer) {
    clearInterval(simulateTimer);
    simulateTimer = null;
  }
}

onLoad(async (options) => {
  // —— 刷新后状态恢复 ——
  try {
    const raw = sessionStorage.getItem(DL_KEY);
    if (raw) {
      const state = JSON.parse(raw);
      if (state && state.downloading) {
        const remaining = state.remaining || 0;
        if (remaining > 0 && remaining < 30) {
          // 模拟模式：恢复遮罩，按剩余秒数倒计时
          downloading.value = true;
          downloadingText.value = state.text || "正在生成…";
          startSimulateCountdown(remaining);
          setTimeout(() => {
            downloading.value = false;
            uni.showToast({ title: "演示完成 ✓", icon: "success" });
          }, remaining * 1000);
          uni.showToast({ title: "🔄 已恢复遮罩状态", icon: "none", duration: 1500 });
        } else {
          // 真实下载模式：刷新 = 下载已中断
          sessionStorage.removeItem(DL_KEY);
          uni.showModal({
            title: "下载被中断",
            content: "检测到页面被刷新，上一次下载已终止。请重新点击导出按钮。",
            showCancel: false,
            confirmText: "知道了"
          });
        }
      }
    }
  } catch (e) {}

  try {
    const id = options.id || 1;
    plan.value = await request({ url: "/api/lessons/" + id });
    if (plan.value.content) {
      try {
        content.value = JSON.parse(plan.value.content);
      } catch (e) {
        content.value = { ...content.value };
      }
    }
  } catch (e) {
    uni.showToast({ title: "教案加载失败", icon: "none" });
  }
});

onUnload(() => stopSimulateCountdown());
onBeforeUnmount(() => stopSimulateCountdown());

function joinList(list) {
  return (list || []).join("；") || "-";
}
function openAi() {
  showAi.value = true;
  aiResult.value = "";
}
function aiAsk(s) {
  aiInput.value = s;
  sendAi();
}
async function sendAi() {
  const msg = aiInput.value.trim();
  if (!msg) return;
  aiResult.value = "思考中…";
  try {
    aiResult.value = await request({
      url: "/api/ai/chat",
      method: "POST",
      data: { message: "我在备《" + (plan.value.title || "") + "》，请：" + msg }
    });
  } catch (e) {
    aiResult.value = "调用失败，请稍后重试";
  }
}
function downloadViaFetch(url, filename, loadingText, simulateSeconds) {
  if (downloading.value) return;
  downloading.value = true;
  downloadingText.value = loadingText || "正在生成…";
  // 模拟模式：直接延迟 simulateSeconds 秒后成功（无需后端，用于演示遮罩效果）
  if (simulateSeconds && simulateSeconds > 0) {
    startSimulateCountdown(simulateSeconds);
    setTimeout(() => {
      stopSimulateCountdown();
      downloading.value = false;
      uni.showToast({ title: "演示完成：已保存到本地", icon: "success" });
    }, simulateSeconds * 1000);
    return;
  }
  // #ifdef H5
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
      uni.showToast({ title: "已保存到本地", icon: "success" });
    })
    .catch(() => {
      stopSimulateCountdown();
      downloading.value = false;
      sessionStorage.removeItem(DL_KEY);
      uni.showToast({ title: "下载失败，请重试", icon: "none" });
    });
  // #endif
  // #ifndef H5
  uni.downloadFile({
    url,
    success() {
      downloading.value = false;
      uni.showToast({ title: "已开始下载", icon: "none" });
    },
    fail() {
      downloading.value = false;
      uni.showToast({ title: "下载失败", icon: "none" });
    }
  });
  // #endif
}

/**
 * 演示模式：模拟三种导出场景的遮罩效果，用于测试和路演
 */
let simulateStep = 0;
function simulateDemo() {
  simulateStep++;
  const demos = [
    { text: "正在生成 AI 精美课件，约 10-30 秒…", seconds: 3, name: "PPT 导出" },
    { text: "正在生成 Word 文档…", seconds: 2, name: "Word 导出" },
    { text: "正在导出 Markdown…", seconds: 1, name: "MD 导出" }
  ];
  const item = demos[(simulateStep - 1) % demos.length];
  uni.showToast({ title: "▶ 演示：" + item.name, icon: "none", duration: 1500 });
  setTimeout(() => {
    downloadViaFetch(null, "模拟_" + item.name + ".txt", item.text, item.seconds);
  }, 300);
}
function exportPlan() {
  downloadViaFetch("/api/lessons/" + plan.value.id + "/export", (plan.value.title || "教案") + ".md", "正在导出 Markdown…");
}
function exportDocx() {
  downloadViaFetch("/api/lessons/" + plan.value.id + "/export-docx", (plan.value.title || "教案") + ".docx", "正在生成 Word 文档…");
}
async function savePlan() {
  try {
    await request({
      url: "/api/lessons/" + plan.value.id,
      method: "PUT",
      data: {
        title: plan.value.title,
        subject: plan.value.subject,
        grade: plan.value.grade,
        textbook: plan.value.textbook,
        lessonType: plan.value.lessonType,
        content: JSON.stringify(content.value)
      }
    });
    downloadViaFetch("/api/lessons/" + plan.value.id + "/export", (plan.value.title || "教案") + ".md", "正在保存并导出…");
    uni.showToast({ title: "已保存（云端+本地）", icon: "success" });
  } catch (e) {}
}
function exportPpt() {
  downloadViaFetch("/api/ai/lessons/" + plan.value.id + "/export-ppt", (plan.value.title || "教案") + "（AI美化课件）.pptx", "正在生成 AI 精美课件，约 10-30 秒…");
}
async function toggleShare() {
  const next = plan.value.isPublic === 1 ? 0 : 1;
  try {
    await request({
      url: "/api/lessons/" + plan.value.id,
      method: "PUT",
      data: { isPublic: next }
    });
    plan.value.isPublic = next;
    uni.showToast({ title: next === 1 ? "已公开到教研广场" : "已设为私密", icon: "none" });
  } catch (e) {}
}
function shareLesson() {
  uni.showModal({
    title: "分享教案",
    content: "将教案设为公开并生成分享链接，复制后其他老师用浏览器打开即可查看。",
    confirmText: "生成链接",
    success: async (res) => {
      if (!res.confirm) return;
      try {
        if (plan.value.isPublic !== 1) {
          await request({ url: "/api/lessons/" + plan.value.id, method: "PUT", data: { isPublic: 1 } });
          plan.value.isPublic = 1;
        }
        const origin = typeof window !== "undefined" ? window.location.origin : "";
        const link = origin + "/#/pages/lesson/share?id=" + plan.value.id;
        uni.setClipboardData({
          data: link,
          success: () => uni.showToast({ title: "分享链接已复制", icon: "success" })
        });
      } catch (e) {}
    }
  });
}
async function aiJson(prompt) {
  const text = await request({
    url: "/api/ai/chat",
    method: "POST",
    data: { message: "请只输出 JSON，不要任何其他文字或解释。\n" + prompt },
    timeout: 120000
  });
  const t = String(text).trim().replace(/^```[a-zA-Z]*/, "").replace(/```$/, "").trim();
  return JSON.parse(t);
}
async function aiText(prompt) {
  return request({
    url: "/api/ai/chat",
    method: "POST",
    data: { message: prompt },
    timeout: 120000
  });
}
async function regenerateObjectives() {
  try {
    const arr = await aiJson("重新生成《" + plan.value.title + "》的教学目标，返回 JSON 数组，每项为 {\"type\":\"素养类型\",\"content\":\"具体可操作的目标\",\"standardRef\":\"课标条目\"}，3~4 项。");
    if (Array.isArray(arr) && arr.length) {
      content.value.objectives = arr;
      await savePlan();
      uni.showToast({ title: "教学目标已更新", icon: "success" });
    }
  } catch (e) {
    uni.showToast({ title: "生成失败，请重试", icon: "none" });
  }
}
async function regenerateKeyPoints() {
  try {
    const obj = await aiJson("重新生成《" + plan.value.title + "》的教学重难点，返回 JSON：{\"keyPoints\":[\"...\"],\"difficultPoints\":[\"...\"]}，各 1~3 条。");
    if (obj && obj.keyPoints) {
      content.value.keyPoints = obj.keyPoints;
      content.value.difficultPoints = obj.difficultPoints || [];
      await savePlan();
      uni.showToast({ title: "重难点已更新", icon: "success" });
    }
  } catch (e) {
    uni.showToast({ title: "生成失败，请重试", icon: "none" });
  }
}
async function rewriteStage(i) {
  try {
    const obj = await aiJson("重写《" + plan.value.title + "》教学过程第 " + (i + 1) + " 个环节「" + content.value.process[i].stage + "」，返回 JSON：{\"stage\":\"环节名\",\"duration\":分钟数(数字),\"activities\":\"师生活动描述\"}。");
    if (obj && obj.stage) {
      content.value.process[i] = obj;
      await savePlan();
      uni.showToast({ title: "环节已重写", icon: "success" });
    }
  } catch (e) {
    uni.showToast({ title: "生成失败，请重试", icon: "none" });
  }
}
async function regenerateBoard() {
  try {
    const text = await aiText("为《" + plan.value.title + "》重新设计板书，返回纯文本，可用 └ ├ 等符号组织层级，不要输出 JSON 或多余解释。");
    if (text && text.trim()) {
      content.value.boardDesign = text.trim();
      await savePlan();
      uni.showToast({ title: "板书已更新", icon: "success" });
    }
  } catch (e) {
    uni.showToast({ title: "生成失败，请重试", icon: "none" });
  }
}
async function regenerateHomework() {
  try {
    const arr = await aiJson("为《" + plan.value.title + "》重新生成分层作业，返回 JSON 数组，每项为 {\"level\":\"基础/提高/拓展\",\"items\":[\"...\"]}。");
    if (Array.isArray(arr) && arr.length) {
      content.value.homework = arr;
      await savePlan();
      uni.showToast({ title: "作业已更新", icon: "success" });
    }
  } catch (e) {
    uni.showToast({ title: "生成失败，请重试", icon: "none" });
  }
}
async function regenerateCards() {
  try {
    const arr = await aiJson("根据 2022 版义务教育课程标准，为《" + plan.value.title + "》生成 3 条课标依据，返回 JSON 数组，每项为 {\"ref\":\"课标出处\",\"content\":\"课标原文要点\"}。");
    if (Array.isArray(arr) && arr.length) {
      content.value.standardCards = arr;
      await savePlan();
      uni.showToast({ title: "课标依据已更新", icon: "success" });
    }
  } catch (e) {
    uni.showToast({ title: "生成失败，请重试", icon: "none" });
  }
}
function moveStage(i, dir) {
  const arr = content.value.process;
  const j = i + dir;
  if (j < 0 || j >= arr.length) return;
  const tmp = arr[i];
  arr[i] = arr[j];
  arr[j] = tmp;
}
function removeStage(i) {
  content.value.process.splice(i, 1);
  savePlan();
}
function insertStage(i) {
  content.value.process.splice(i + 1, 0, { stage: "新环节", duration: 5, activities: "请填写该环节的师生活动" });
}
function feedback() {
  uni.showActionSheet({
    itemList: ["用得上", "需修改"],
    success: async (res) => {
      try {
        await request({
          url: "/api/feedbacks",
          method: "POST",
          data: {
            type: res.tapIndex === 0 ? "USEFUL" : "NEED_FIX",
            content: res.tapIndex === 0 ? "教案用得上" : "教案需修改",
            lessonPlanId: plan.value.id
          }
        });
        uni.showToast({ title: "感谢反馈", icon: "success" });
      } catch (e) {}
    }
  });
}
</script>

<style scoped>
.page { padding: 24rpx 24rpx 0; }
.empty { text-align: center; padding: 200rpx 0; color: #999; }
.head { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 24rpx; }
.title { font-size: 40rpx; font-weight: 800; line-height: 1.35; display: block; }
.tags { display: flex; flex-wrap: wrap; margin-top: 14rpx; }
.tag { background: #f1efe7; color: #6b6b6b; font-size: 22rpx; padding: 6rpx 16rpx; border-radius: 8rpx; margin-right: 12rpx; }
.score { background: linear-gradient(135deg, #2e6b4f, #3e8a63); color: #fff; border-radius: 18rpx; padding: 16rpx 22rpx; text-align: center; min-width: 110rpx; }
.score-num { display: block; font-size: 40rpx; font-weight: 800; }
.score-label { font-size: 20rpx; opacity: 0.85; }
.share-badge { background: #f1efe7; color: #8a8a8a; font-size: 21rpx; padding: 8rpx 18rpx; border-radius: 999rpx; margin-left: 14rpx; }
.share-badge.on { background: #e9f2ec; color: #2e6b4f; }
.section { background: #fff; border-radius: 22rpx; padding: 28rpx; margin-bottom: 20rpx; box-shadow: 0 4rpx 14rpx rgba(44, 44, 44, 0.04); }
.section-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 18rpx; }
.section-name { font-size: 30rpx; font-weight: 700; }
.section-ops { display: flex; gap: 18rpx; }
.section-op { font-size: 24rpx; color: #2e6b4f; }
.obj-item { display: flex; margin-bottom: 14rpx; }
.obj-type { flex-shrink: 0; background: #e9f2ec; color: #2e6b4f; font-size: 22rpx; padding: 6rpx 14rpx; border-radius: 8rpx; margin-right: 14rpx; height: fit-content; }
.obj-content { font-size: 27rpx; line-height: 1.6; }
.kp-row { display: flex; margin-bottom: 12rpx; }
.kp-label { flex-shrink: 0; background: #fdf3e3; color: #b57a1f; font-size: 22rpx; padding: 6rpx 14rpx; border-radius: 8rpx; margin-right: 14rpx; }
.kp-label.diff { background: #fbeceb; color: #b0553f; }
.kp-text { font-size: 27rpx; line-height: 1.6; }
.process-item { border-left: 6rpx solid #2e6b4f; background: #f8faf7; border-radius: 0 14rpx 14rpx 0; padding: 20rpx 22rpx; margin-bottom: 16rpx; }
.process-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10rpx; }
.process-stage { font-size: 28rpx; font-weight: 700; color: #2e6b4f; }
.process-time { font-size: 22rpx; color: #8a8a8a; background: #e9f2ec; padding: 4rpx 14rpx; border-radius: 999rpx; }
.process-body { font-size: 26rpx; line-height: 1.65; }
.process-ops { display: flex; gap: 24rpx; margin-top: 14rpx; font-size: 23rpx; color: #2e6b4f; }
.process-ops .danger { color: #c0392b; }
.add-stage { margin-top: 16rpx; text-align: center; font-size: 25rpx; color: #2e6b4f; background: #e9f2ec; border-radius: 12rpx; padding: 16rpx 0; }
.board { font-size: 27rpx; line-height: 1.7; white-space: pre-line; font-family: "Kaiti SC", "STKaiti", serif; }
.hw-item { background: #f8faf7; border-radius: 14rpx; padding: 20rpx; margin-bottom: 14rpx; }
.hw-level { font-size: 24rpx; color: #2e6b4f; font-weight: 700; }
.hw-items { margin-top: 10rpx; }
.hw-text { display: block; font-size: 26rpx; line-height: 1.6; }
.standard { background: #f2f7f2; }
.standard-card { background: #fff; border-left: 6rpx solid #e3a03c; border-radius: 0 14rpx 14rpx 0; padding: 20rpx; margin-bottom: 14rpx; }
.standard-ref { display: block; font-size: 24rpx; color: #b57a1f; font-weight: 700; margin-bottom: 8rpx; }
.standard-content { font-size: 25rpx; line-height: 1.6; color: #557755; }
.ai-badge {
  background: #fdf3e3; border: 2rpx solid #e3a03c; border-radius: 14rpx;
  padding: 18rpx 22rpx; font-size: 23rpx; color: #8a6d2f; line-height: 1.5;
  margin-bottom: 20rpx;
}
.bottom-space { height: 140rpx; }
.float-bar {
  position: fixed; left: 24rpx; right: 24rpx; bottom: 36rpx; background: #fff; border-radius: 999rpx;
  box-shadow: 0 10rpx 30rpx rgba(44, 44, 44, 0.14); display: flex; padding: 16rpx 0;
}
.float-btn { flex: 1; display: flex; flex-direction: column; align-items: center; font-size: 22rpx; color: #555; }
.fb-icon { font-size: 34rpx; margin-bottom: 4rpx; }
.ai-mask { position: fixed; inset: 0; background: rgba(0, 0, 0, 0.4); z-index: 100; display: flex; align-items: flex-end; }
.ai-panel { width: 100%; background: #fff; border-radius: 28rpx 28rpx 0 0; padding: 30rpx 28rpx 60rpx; }
.ai-head { display: flex; justify-content: space-between; align-items: center; }
.ai-title { font-size: 32rpx; font-weight: 700; }
.ai-close { font-size: 34rpx; color: #999; padding: 0 8rpx; }
.ai-suggests { display: flex; flex-wrap: wrap; margin: 20rpx 0; }
.ai-chip { background: #e9f2ec; color: #2e6b4f; font-size: 24rpx; padding: 12rpx 22rpx; border-radius: 999rpx; margin: 0 14rpx 14rpx 0; }
.ai-result { height: 320rpx; background: #f8faf7; border-radius: 14rpx; padding: 22rpx; }
.ai-placeholder { color: #a0a0a0; font-size: 25rpx; }
.ai-text { font-size: 26rpx; line-height: 1.7; white-space: pre-wrap; }
.ai-input-row { display: flex; gap: 16rpx; margin-top: 20rpx; }
.ai-input { flex: 1; background: #f1efe7; border-radius: 999rpx; padding: 18rpx 26rpx; font-size: 27rpx; }
.ai-send { background: #2e6b4f; color: #fff; border-radius: 999rpx; padding: 18rpx 36rpx; font-size: 27rpx; }

/* 导出按钮禁用态 */
.fb-disabled { opacity: 0.45; pointer-events: none; }
.float-demo { background: linear-gradient(135deg, #fdf3e3, #fff8e7) !important; color: #a26b20 !important; }

/* 导出加载遮罩 */
.dl-mask { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.45); z-index: 9999; display: flex; align-items: center; justify-content: center; }
.dl-card { background: #fff; border-radius: 24rpx; padding: 50rpx 60rpx; display: flex; flex-direction: column; align-items: center; box-shadow: 0 8rpx 30rpx rgba(0,0,0,0.15); }
.dl-spinner { width: 56rpx; height: 56rpx; border: 6rpx solid #e9f2ec; border-top-color: #2e6b4f; border-radius: 50%; animation: dl-spin 0.8s linear infinite; }
@keyframes dl-spin { to { transform: rotate(360deg); } }
.dl-text { font-size: 28rpx; font-weight: 600; color: #333; margin-top: 26rpx; }
.dl-sub { font-size: 23rpx; color: #999; margin-top: 10rpx; }
</style>
