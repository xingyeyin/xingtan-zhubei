<template>
  <Login v-if="!isAuthed" />
  <el-container v-else class="layout">
    <el-aside width="230px" class="aside">
      <div class="logo">
        <span class="logo-icon">🌾</span>
        <div>
          <div class="logo-name">杏坛智备</div>
          <div class="logo-sub">乡村教师 AI 备课助手</div>
        </div>
      </div>
      <el-menu router :default-active="$route.path" class="menu">
        <el-menu-item index="/dashboard"><span class="mi">📊</span>仪表盘</el-menu-item>
        <el-menu-item index="/users"><span class="mi">👥</span>用户管理</el-menu-item>
        <el-menu-item index="/kb"><span class="mi">📚</span>知识库管理</el-menu-item>
        <el-menu-item index="/lessons"><span class="mi">📄</span>教案管理</el-menu-item>
        <el-menu-item index="/review"><span class="mi">🛡️</span>内容审核</el-menu-item>
        <el-menu-item index="/stats"><span class="mi">📈</span>运营分析</el-menu-item>
        <el-menu-item index="/settings"><span class="mi">⚙️</span>系统设置</el-menu-item>
      </el-menu>
      <div class="aside-foot">青年红色筑梦之旅 · 创意组</div>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="header-title">管理后台</div>
        <div class="header-right">
          <span class="env-tag">演示模式</span>
          <span class="user-chip">👩‍💼 管理员</span>
        </div>
      </el-header>
      <el-main><router-view /></el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref, watch } from "vue";
import { useRoute } from "vue-router";
import Login from "./views/Login.vue";

const isAuthed = ref(!!localStorage.getItem("xingtan_token"));
const route = useRoute();
watch(
  () => route.path,
  () => {
    isAuthed.value = !!localStorage.getItem("xingtan_token");
  }
);
</script>

<style>
:root {
  --el-color-primary: #2e6b4f;
  --el-color-primary-light-3: #5c9176;
  --el-color-primary-light-5: #8db3a0;
  --el-color-primary-light-7: #bdd5c9;
  --el-color-primary-light-8: #d3e4dc;
  --el-color-primary-light-9: #e9f2ec;
  --el-color-primary-dark-2: #255640;
}
* { margin: 0; padding: 0; box-sizing: border-box; }
html, body, #app { height: 100%; }
body { font-family: "PingFang SC", "Microsoft YaHei", sans-serif; background: #f5f7f4; }
.layout { height: 100%; }
.aside {
  background: linear-gradient(180deg, #244c38 0%, #2e6b4f 100%);
  display: flex; flex-direction: column;
}
.logo { display: flex; align-items: center; gap: 12px; padding: 24px 20px; color: #fff; }
.logo-icon { font-size: 30px; }
.logo-name { font-size: 19px; font-weight: 700; }
.logo-sub { font-size: 11px; opacity: 0.7; margin-top: 3px; }
.menu { border-right: none; background: transparent; flex: 1; }
.menu .el-menu-item { color: #cfe0d6; height: 52px; line-height: 52px; }
.menu .el-menu-item .mi { margin-right: 10px; }
.menu .el-menu-item:hover { background: rgba(255, 255, 255, 0.08); color: #fff; }
.menu .el-menu-item.is-active { background: rgba(255, 255, 255, 0.14); color: #f4c26b; font-weight: 600; }
.aside-foot { padding: 18px 20px; font-size: 11px; color: rgba(255, 255, 255, 0.5); }
.header {
  background: #fff; border-bottom: 1px solid #eef0ea; display: flex; justify-content: space-between;
  align-items: center; height: 60px;
}
.header-title { font-size: 17px; font-weight: 700; }
.header-right { display: flex; align-items: center; gap: 14px; }
.env-tag { background: #e9f2ec; color: #2e6b4f; font-size: 12px; padding: 4px 12px; border-radius: 999px; }
.user-chip { font-size: 13px; color: #555; }
.el-main { background: #f5f7f4; padding: 20px; }
.el-card { border-radius: 14px; border: none; box-shadow: 0 4px 16px rgba(36, 76, 56, 0.06); }
.el-card__header { font-weight: 700; color: #2c2c2c; }
</style>
