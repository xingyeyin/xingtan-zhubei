import axios from "axios";

const request = axios.create({
  baseURL: "/api",
  timeout: 60000
});

request.interceptors.request.use((config) => {
  const token = localStorage.getItem("xingtan_token");
  if (token) config.headers.Authorization = "Bearer " + token;
  return config;
});

request.interceptors.response.use(
  (res) => res.data,
  (err) => {
    if (err.response && err.response.status === 401) {
      localStorage.removeItem("xingtan_token");
      window.location.href = "/login";
    }
    return Promise.reject(err);
  }
);

export default request;
