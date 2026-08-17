// 登录态管理
const TOKEN_KEY = "xingtan_token";
const PROFILE_KEY = "xingtan_profile";

export function getToken() {
  return uni.getStorageSync(TOKEN_KEY) || "";
}
export function setToken(token) {
  uni.setStorageSync(TOKEN_KEY, token);
}
export function clearAuth() {
  uni.removeStorageSync(TOKEN_KEY);
  uni.removeStorageSync(PROFILE_KEY);
}
export function getProfile() {
  return uni.getStorageSync(PROFILE_KEY) || null;
}
export function setProfile(profile) {
  uni.setStorageSync(PROFILE_KEY, profile);
}
