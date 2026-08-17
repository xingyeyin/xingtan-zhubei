// 统一的请求封装（H5 / 小程序通用），自动携带登录令牌
import { getToken, clearAuth } from "./auth";

export function request({ url, method = "GET", data = {}, header = {}, timeout = 60000 }) {
  return new Promise((resolve, reject) => {
    uni.request({
      url,
      method,
      data,
      timeout,
      header: {
        "Content-Type": "application/json",
        Authorization: getToken() ? "Bearer " + getToken() : "",
        ...header
      },
      success: (res) => {
        if (res.statusCode === 401) {
          clearAuth();
          uni.reLaunch({ url: "/pages/auth/login" });
          uni.showToast({ title: "请先登录", icon: "none" });
          reject(new Error("未登录"));
          return;
        }
        if (res.statusCode >= 200 && res.statusCode < 300 && res.data && res.data.code === 200) {
          resolve(res.data.data);
        } else {
          const msg = res.data && res.data.message ? res.data.message : "请求失败";
          uni.showToast({ title: msg, icon: "none" });
          reject(new Error(msg));
        }
      },
      fail: (err) => {
        uni.showToast({ title: "网络异常，请确认后端服务已启动", icon: "none" });
        reject(err);
      }
    });
  });
}
