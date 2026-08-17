import App from "./App.vue";
import { createSSRApp } from "vue";

// uni-app 入口：由 createSSRApp 创建应用
export function createApp() {
  const app = createSSRApp(App);
  return { app };
}
