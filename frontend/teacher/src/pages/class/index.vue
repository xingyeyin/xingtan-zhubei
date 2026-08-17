<template>
  <view class="page">
    <view class="head-bar">
      <text class="head-title">我的班级</text>
      <view class="add-btn" @tap="openCreate">＋ 新建班级</view>
    </view>

    <!-- 新建/编辑表单 -->
    <view v-if="showForm" class="card">
      <text class="card-title">{{ editing ? "编辑班级" : "新建班级" }}</text>
      <text class="label">年级</text>
      <view class="chip-wrap">
        <text v-for="g in grades" :key="g" class="chip" :class="{ on: form.grade === g }" @tap="form.grade = g">{{ g }}</text>
      </view>
      <text class="label">班级名称</text>
      <input class="input" v-model="form.className" placeholder="如：三（1）班" />
      <view class="btn-row">
        <view class="ghost-btn" @tap="showForm = false">取消</view>
        <view class="primary-btn" @tap="saveClass">保存</view>
      </view>
    </view>

    <view v-if="loading" class="empty">加载中…</view>
    <view v-else-if="classes.length === 0" class="empty">
      <text class="empty-icon">🏫</text>
      <text class="empty-text">还没有班级，点右上角新建班级，然后录入学生成绩</text>
    </view>

    <view v-for="c in classes" :key="c.id" class="class-card" @tap="openDetail(c)">
      <view class="class-body">
        <text class="class-name">{{ c.grade }}{{ c.className }}</text>
        <text class="class-meta">{{ c.studentCount }} 名学生</text>
      </view>
      <view class="class-ops">
        <text class="op" @tap.stop="openEdit(c)">编辑</text>
        <text class="op danger" @tap.stop="removeClass(c)">删除</text>
        <text class="arrow">›</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { reactive, ref } from "vue";
import { onShow } from "@dcloudio/uni-app";
import { request } from "../../utils/request";

const grades = ["一年级", "二年级", "三年级", "四年级", "五年级", "六年级", "七年级", "八年级", "九年级"];
const classes = ref([]);
const loading = ref(true);
const showForm = ref(false);
const editing = ref(null);
const form = reactive({ grade: "三年级", className: "" });

async function load() {
  loading.value = true;
  try {
    classes.value = await request({ url: "/api/classes" });
  } catch (e) {
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  editing.value = null;
  form.grade = "三年级";
  form.className = "";
  showForm.value = true;
}
function openEdit(c) {
  editing.value = c;
  form.grade = c.grade || "三年级";
  form.className = c.className || "";
  showForm.value = true;
}
async function saveClass() {
  if (!form.className.trim()) return uni.showToast({ title: "请输入班级名称", icon: "none" });
  try {
    if (editing.value) {
      await request({ url: "/api/classes/" + editing.value.id, method: "PUT", data: { ...form } });
    } else {
      await request({ url: "/api/classes", method: "POST", data: { ...form } });
    }
    uni.showToast({ title: "已保存", icon: "success" });
    showForm.value = false;
    load();
  } catch (e) {}
}
function removeClass(c) {
  uni.showModal({
    title: "删除班级",
    content: "删除「" + c.grade + c.className + "」将同时删除学生与成绩，确定？",
    confirmColor: "#c0392b",
    success: async (res) => {
      if (!res.confirm) return;
      try {
        await request({ url: "/api/classes/" + c.id, method: "DELETE" });
        uni.showToast({ title: "已删除", icon: "success" });
        load();
      } catch (e) {}
    }
  });
}
function openDetail(c) {
  uni.navigateTo({ url: "/pages/class/detail?id=" + c.id });
}

onShow(load);
</script>

<style scoped>
.page { padding: 24rpx 24rpx 40rpx; }
.head-bar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 22rpx; }
.head-title { font-size: 38rpx; font-weight: 700; }
.add-btn { background: #2e6b4f; color: #fff; font-size: 26rpx; padding: 14rpx 30rpx; border-radius: 999rpx; }
.card { background: #fff; border-radius: 22rpx; padding: 28rpx; margin-bottom: 20rpx; box-shadow: 0 4rpx 14rpx rgba(44, 44, 44, 0.04); }
.card-title { font-size: 30rpx; font-weight: 700; margin-bottom: 14rpx; }
.label { display: block; font-size: 26rpx; color: #555; margin: 16rpx 0 10rpx; }
.chip-wrap { display: flex; flex-wrap: wrap; }
.chip { background: #f1efe7; border-radius: 999rpx; padding: 12rpx 24rpx; margin: 0 12rpx 12rpx 0; font-size: 24rpx; }
.chip.on { background: #2e6b4f; color: #fff; }
.input { background: #f7f4ec; border-radius: 14rpx; padding: 20rpx 24rpx; font-size: 28rpx; }
.btn-row { display: flex; gap: 20rpx; margin-top: 24rpx; }
.ghost-btn { flex: 1; background: #f1efe7; color: #555; text-align: center; padding: 20rpx 0; border-radius: 14rpx; font-size: 28rpx; }
.primary-btn { flex: 1; background: #2e6b4f; color: #fff; text-align: center; padding: 20rpx 0; border-radius: 14rpx; font-size: 28rpx; font-weight: 700; }
.empty { text-align: center; padding: 120rpx 40rpx; color: #999; }
.empty-icon { font-size: 80rpx; display: block; }
.empty-text { display: block; margin-top: 20rpx; font-size: 26rpx; line-height: 1.6; }
.class-card { background: #fff; border-radius: 22rpx; padding: 28rpx; margin-bottom: 18rpx; display: flex; justify-content: space-between; align-items: center; box-shadow: 0 4rpx 14rpx rgba(44, 44, 44, 0.04); }
.class-name { display: block; font-size: 32rpx; font-weight: 700; }
.class-meta { display: block; margin-top: 8rpx; font-size: 24rpx; color: #8a8a8a; }
.class-ops { display: flex; align-items: center; gap: 20rpx; }
.op { font-size: 24rpx; color: #2e6b4f; }
.op.danger { color: #c0392b; }
.arrow { color: #ccc; font-size: 40rpx; }
</style>
