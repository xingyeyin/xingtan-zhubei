import { createRouter, createWebHistory } from "vue-router";
import Dashboard from "../views/Dashboard.vue";
import Users from "../views/Users.vue";
import KnowledgeBase from "../views/KnowledgeBase.vue";
import Lessons from "../views/Lessons.vue";
import Review from "../views/Review.vue";
import Stats from "../views/Stats.vue";
import Settings from "../views/Settings.vue";
import Login from "../views/Login.vue";

const routes = [
  { path: "/login", component: Login },
  { path: "/", redirect: "/dashboard" },
  { path: "/dashboard", component: Dashboard },
  { path: "/users", component: Users },
  { path: "/kb", component: KnowledgeBase },
  { path: "/lessons", component: Lessons },
  { path: "/review", component: Review },
  { path: "/stats", component: Stats },
  { path: "/settings", component: Settings }
];

const router = createRouter({
  history: createWebHistory(),
  routes
});

router.beforeEach((to) => {
  const token = localStorage.getItem("xingtan_token");
  if (!token && to.path !== "/login") {
    return "/login";
  }
  if (token && to.path === "/login") {
    return "/dashboard";
  }
  return true;
});

export default router;
