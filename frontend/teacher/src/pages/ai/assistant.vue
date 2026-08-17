<template>
  <view class="page">
    <view class="tabs">
      <text v-for="t in tabs" :key="t.value" class="tab" :class="{ on: tab === t.value }" @tap="switchTab(t.value)">{{ t.name }}</text>
    </view>

    <view class="card">
      <text class="label">{{ placeholder }}</text>
      <textarea class="textarea" v-model="input" :placeholder="placeholder" />
      <view class="chips">
        <text v-for="s in suggests" :key="s" class="chip" @tap="input = s">{{ s }}</text>
      </view>
      <view class="primary-btn" :class="{ disabled: loading }" @tap="ask">{{ loading ? "AI 思考中…" : "开始" }}</view>
    </view>

    <view v-if="result" class="card">
      <view class="card-title">结果</view>
      <text class="result-text">{{ result }}</text>
      <view class="ghost-btn" @tap="copy">复制结果</view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from "vue";
import { onLoad } from "@dcloudio/uni-app";
import { request } from "../../utils/request";

const tabs = [
  { name: "AI 问答", value: "qa", system: "你是乡村教师的 AI 备课助手。回答要简洁、实用、可操作，符合义务教育课程标准理念。", placeholder: "请输入教学问题，例如：如何讲好《背影》的细节描写？", suggests: ["如何设计课堂导入？", "新课标下的教案怎么写？", "怎么处理学生上课走神？"] },
  { name: "生成习题", value: "exercise", system: "你是中小学命题专家。请围绕用户给出的知识点，生成 10 道练习题（含答案与解析），题型包含填空、选择、判断、解答，难度分层，题目原创。", placeholder: "输入知识点，例如：三年级语文 比喻句；五年级数学 分数加减法", suggests: ["三年级语文：比喻句", "五年级数学：分数加减法", "七年级英语：一般现在时"] },
  { name: "一键组卷", value: "paper", system: "你是中小学试卷命题专家。请为用户生成一份单元测试卷：包含题型、题量、分值分布、每道题目内容与参考答案，难度符合课程标准要求。", placeholder: "输入范围，例如：六年级数学 第一单元 分数乘法 单元测试卷", suggests: ["六年级数学：分数乘法单元卷", "八年级语文：第三单元古诗文测试", "四年级英语：Unit 3 单元卷"] },
  { name: "学情分析", value: "study", system: "你是教育数据分析专家。请基于教师描述的班级学情，分析学生薄弱点，并给出备课调整建议与课堂应对策略。", placeholder: "粘贴班级学情，例如：五年级(2)班期中数学平均72分，分数应用题失分严重…", suggests: ["班级计算正确率低", "学生阅读理解失分多", "英语听说基础薄弱"] }
];

const tab = ref("qa");
const input = ref("");
const result = ref("");
const loading = ref(false);

const current = computed(() => tabs.find((t) => t.value === tab.value));
const placeholder = computed(() => current.value.placeholder);
const suggests = computed(() => current.value.suggests);

function switchTab(v) {
  tab.value = v;
  result.value = "";
}

async function ask() {
  if (!input.value.trim()) return uni.showToast({ title: "请输入内容", icon: "none" });
  loading.value = true;
  result.value = "";
  try {
    result.value = await request({
      url: "/api/ai/chat",
      method: "POST",
      data: { message: current.value.system + "\n用户输入：" + input.value },
      timeout: 120000
    });
  } catch (e) {
    result.value = "调用失败，请稍后重试";
  } finally {
    loading.value = false;
  }
}

function copy() {
  uni.setClipboardData({ data: result.value });
}

onLoad((options) => {
  if (options && options.tab && tabs.some((t) => t.value === options.tab)) {
    tab.value = options.tab;
  }
});
</script>

<style scoped>
.page { padding: 24rpx; }
.tabs { display: flex; background: #fff; border-radius: 16rpx; padding: 8rpx; margin-bottom: 20rpx; box-shadow: 0 4rpx 14rpx rgba(44, 44, 44, 0.04); }
.tab { flex: 1; text-align: center; padding: 16rpx 0; font-size: 25rpx; color: #8a8a8a; border-radius: 12rpx; }
.tab.on { background: #2e6b4f; color: #fff; font-weight: 600; }
.card { background: #fff; border-radius: 22rpx; padding: 28rpx; margin-bottom: 20rpx; box-shadow: 0 4rpx 14rpx rgba(44, 44, 44, 0.04); }
.label { display: block; font-size: 26rpx; color: #555; margin-bottom: 12rpx; }
.textarea { background: #f7f4ec; border-radius: 14rpx; padding: 22rpx; width: auto; height: 220rpx; font-size: 28rpx; }
.chips { display: flex; flex-wrap: wrap; margin-top: 16rpx; }
.chip { background: #e9f2ec; color: #2e6b4f; font-size: 23rpx; padding: 10rpx 20rpx; border-radius: 999rpx; margin: 0 12rpx 12rpx 0; }
.primary-btn { background: #2e6b4f; color: #fff; text-align: center; padding: 24rpx 0; border-radius: 16rpx; font-size: 30rpx; font-weight: 700; margin-top: 24rpx; }
.disabled { opacity: 0.6; }
.card-title { font-size: 30rpx; font-weight: 700; margin-bottom: 16rpx; }
.result-text { font-size: 26rpx; line-height: 1.7; white-space: pre-wrap; }
.ghost-btn { margin-top: 20rpx; background: #e9f2ec; color: #2e6b4f; text-align: center; padding: 18rpx 0; border-radius: 14rpx; font-size: 27rpx; }
</style>
