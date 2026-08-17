<script>
export default {
  onLaunch() {
    const token = uni.getStorageSync("xingtan_token");
    if (!token) {
      uni.reLaunch({ url: "/pages/auth/login" });
    }
  }
};
</script>

<style>
page {
  --color-primary: #2e6b4f;
  --color-primary-light: #e9f2ec;
  --color-accent: #e3a03c;
  --color-bg: #f7f4ec;
  --color-card: #ffffff;
  --color-text: #2c2c2c;
  --color-sub: #8a8a8a;
  --radius: 20rpx;
  background-color: #f7f4ec;
  font-size: 15px;
  color: #2c2c2c;
  font-family: "PingFang SC", "Microsoft YaHei", sans-serif;
}
</style>
