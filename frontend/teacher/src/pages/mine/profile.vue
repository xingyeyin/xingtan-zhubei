<template>
  <view class="page">
    <view class="card">
      <text class="label">昵称</text>
      <input class="input" v-model="form.nickname" placeholder="您的称呼" />
      <text class="label">学校</text>
      <input class="input" v-model="form.schoolName" placeholder="如：新乡某乡村小学" />
      <text class="label">任教学科</text>
      <input class="input" v-model="form.subjects" placeholder="如：语文、数学" />
      <text class="label">任教年级</text>
      <input class="input" v-model="form.grades" placeholder="如：三年级、五年级" />
      <view class="primary-btn" @tap="save">保存资料</view>
    </view>
  </view>
</template>

<script setup>
import { reactive } from "vue";
import { onLoad } from "@dcloudio/uni-app";
import { request } from "../../utils/request";
import { setProfile } from "../../utils/auth";

const form = reactive({ nickname: "", schoolName: "", subjects: "", grades: "" });

onLoad(async () => {
  try {
    const p = await request({ url: "/api/auth/profile" });
    form.nickname = p.nickname || "";
    form.schoolName = p.school || "";
    form.subjects = p.subjects || "";
    form.grades = p.grades || "";
  } catch (e) {}
});

async function save() {
  if (!form.nickname) return uni.showToast({ title: "昵称不能为空", icon: "none" });
  try {
    const p = await request({
      url: "/api/auth/profile",
      method: "PUT",
      data: { ...form }
    });
    setProfile({ nickname: p.nickname, school: p.school });
    uni.showToast({ title: "已保存", icon: "success" });
    setTimeout(() => uni.navigateBack(), 600);
  } catch (e) {}
}
</script>

<style scoped>
.page { padding: 24rpx; }
.card { background: #fff; border-radius: 22rpx; padding: 32rpx; box-shadow: 0 4rpx 14rpx rgba(44, 44, 44, 0.04); }
.label { display: block; font-size: 26rpx; color: #555; margin: 20rpx 0 10rpx; }
.label:first-child { margin-top: 0; }
.input { background: #f7f4ec; border-radius: 14rpx; padding: 20rpx 24rpx; font-size: 29rpx; }
.primary-btn { background: #2e6b4f; color: #fff; text-align: center; padding: 24rpx 0; border-radius: 16rpx; font-size: 30rpx; font-weight: 700; margin-top: 36rpx; }
</style>
