import axios, { AxiosError, type InternalAxiosRequestConfig } from "axios";
import { createDiscreteApi } from "naive-ui";
import { useAuthStore } from "@/stores/auth";

// 创建独立的消息 API（用于组件外部）
const { message: messageApi } = createDiscreteApi(["message"]);

const instance = axios.create({
  // 使用 Vite 环境变量，若无则回退至本地地址
  baseURL: import.meta.env.VITE_API_BASE_URL || "http://localhost:9090",
  timeout: 10000,
  withCredentials: true,
});

// 安全的获取 auth store，避免在 pinia 未初始化时出错
function getAuthStore() {
  try {
    return useAuthStore();
  } catch {
    return null;
  }
}

type RetriableRequestConfig = InternalAxiosRequestConfig & {
  _retry?: boolean;
};

const REFRESH_BYPASS_URLS = [
  "/auth/login",
  "/auth/register",
  "/auth/token/refresh",
  "/auth/logout",
];

let refreshPromise: Promise<string | null> | null = null;

function shouldBypassRefresh(url?: string): boolean {
  if (!url) {
    return false;
  }
  return REFRESH_BYPASS_URLS.some((path) => url.includes(path));
}

async function refreshTokenSilently(): Promise<string | null> {
  const authStore = getAuthStore();
  if (!authStore) {
    return null;
  }

  if (!refreshPromise) {
    refreshPromise = authStore.refreshAccessToken().finally(() => {
      refreshPromise = null;
    });
  }
  return refreshPromise;
}

// 401 重登录防抖锁，避免并发请求导致多次弹出和重复路由跳转
let isRelogin = false;

// 统一的 401 未授权处理逻辑
const handleUnauthorized = (message?: string) => {
  if (!isRelogin) {
    isRelogin = true;
    console.error("[Unauthorized]", message);
    messageApi.warning(message || "登录已过期，请重新登录");

    const authStore = getAuthStore();
    authStore?.logout();

    // 动态导入 router 避免由于组件内引入 API 导致的顶层循环依赖
    import("@/router")
      .then(({ default: router }) => {
        if (router.currentRoute.value.path !== "/login") {
          return router.push({
            name: "login",
            query: { redirect: router.currentRoute.value.fullPath },
          });
        }
        return undefined;
      })
      .catch((error: unknown) => {
        console.error("[Router Redirect Error]", error);
      })
      .finally(() => {
        // 无论导入/跳转成功与否，都释放锁，避免 401 处理被永久阻塞
        isRelogin = false;
      });
  }
};

// 递归删除对象中的指定 key
function deleteKey(obj: any, keyToDelete: string): void {
  if (!obj || typeof obj !== "object") return;
  if (Array.isArray(obj)) {
    obj.forEach((item) => deleteKey(item, keyToDelete));
  } else {
    Object.keys(obj).forEach((key) => {
      if (key === keyToDelete) {
        delete obj[key];
      } else if (typeof obj[key] === "object") {
        deleteKey(obj[key], keyToDelete);
      }
    });
  }
}

instance.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const authStore = getAuthStore();
    if (authStore?.token) {
      config.headers.set("Authorization", `Bearer ${authStore.token}`);
    }
    // 删除 user 参数（同时处理 query 和 body）
    if (config.params) {
      deleteKey(config.params, "user");
    }
    if (config.data) {
      deleteKey(config.data, "user");
    }
    return config;
  },
  (error: AxiosError) => {
    console.error("[Request Error]", error.message);
    return Promise.reject(error);
  },
);

// 统一 API 响应结构类型
export interface ApiResponse<T = unknown> {
  code?: number;
  message?: string;
  data?: T;
  success?: boolean;
  failed?: boolean;
}

// 业务码处理（HTTP 200 但业务失败的情况）
function handleBusinessCode(response: ApiResponse): boolean {
  const { code, message, success, failed } = response;

  // 兼容 success / failed / code 三种业务状态表达
  if (
    failed === true ||
    success === false ||
    (code !== undefined && code !== 200)
  ) {
    switch (code) {
      case 200:
        // success === false 但 code 为 200，可能是特殊业务逻辑
        console.warn("[Business Warning]", message);
        messageApi.warning(message || "操作未成功");
        break;
      case 401:
        handleUnauthorized(message);
        break;
      case 403:
        console.error("[Business Forbidden]", message);
        messageApi.error(message || "权限不足，无法访问");
        break;
      case 404:
        console.error("[Business Not Found]", message);
        messageApi.error(message || "请求的资源不存在");
        break;
      case 400:
      case 422:
        console.error("[Business Bad Request]", message);
        messageApi.error(message || "请求参数错误");
        break;
      case 429:
        console.error("[Business Rate Limit]", message);
        messageApi.warning(message || "请求过于频繁，请稍后再试");
        break;
      case 500:
      case 502:
      case 503:
        console.error("[Business Server Error]", message);
        messageApi.error(message || "服务器内部错误，请稍后再试");
        break;
      default:
        // 兼容 code 为 undefined 的情况
        console.error(`[Business Error ${code ?? "UNKNOWN"}]`, message);
        messageApi.error(
          message || `操作失败 ${code ? `(code: ${code})` : ""}`,
        );
    }
    return false;
  }
  return true;
}

instance.interceptors.response.use(
  async (response) => {
    const contentType = (response.headers?.["content-type"] || "") as string;

    // 处理文件下载失败时，后端返回 JSON 错误信息的情况
    if (
      response.data instanceof Blob &&
      contentType.includes("application/json")
    ) {
      const text = await response.data.text();
      try {
        response.data = text ? JSON.parse(text) : null;
      } catch {
        response.data = text;
      }
    }

    // 处理 HTTP 200 但业务码不为 200 的情况
    const data = response.data as ApiResponse;
    if (
      data &&
      typeof data === "object" &&
      ("code" in data || "success" in data || "failed" in data)
    ) {
      const isSuccess = handleBusinessCode(data);
      if (!isSuccess) {
        // 业务失败时，构造标准 AxiosError 抛出，而不是普通 Error
        const error = new AxiosError(
          data.message || "业务错误",
          data.code?.toString() || "BUSINESS_ERROR",
          response.config,
          response.request,
          response,
        );
        Object.assign(error, { isBusinessError: true }); // 附加业务错误标识
        return Promise.reject(error);
      }
    }
    return response;
  },
  async (error: AxiosError) => {
    const status = error.response?.status;
    const originalConfig = error.config as RetriableRequestConfig | undefined;

    if (
      status === 401 &&
      originalConfig &&
      !originalConfig._retry &&
      !shouldBypassRefresh(originalConfig.url)
    ) {
      originalConfig._retry = true;
      const refreshedToken = await refreshTokenSilently();
      if (refreshedToken) {
        if (
          originalConfig.headers &&
          typeof originalConfig.headers.set === "function"
        ) {
          originalConfig.headers.set(
            "Authorization",
            `Bearer ${refreshedToken}`,
          );
        } else {
          originalConfig.headers = {
            ...(originalConfig.headers || {}),
            Authorization: `Bearer ${refreshedToken}`,
          };
        }
        return instance(originalConfig);
      }
      handleUnauthorized(error.message);
      return Promise.reject(error);
    }

    // 处理 HTTP 错误状态
    if (error.response) {
      const { status, data } = error.response;
      const message = (data as any)?.message || error.message;

      switch (status) {
        case 400:
          console.error("[Bad Request]", message);
          messageApi.error(message || "请求参数错误");
          break;
        case 401:
          if (!shouldBypassRefresh(error.config?.url)) {
            handleUnauthorized(message);
          } else {
            console.error("[Unauthorized]", message);
            messageApi.error(message || "认证失败，请重新登录");
          }
          break;
        case 403:
          console.error("[Forbidden]", message);
          messageApi.error("权限不足，无法访问");
          break;
        case 404:
          console.error("[Not Found]", message);
          messageApi.error("请求的资源不存在");
          break;
        case 500:
          console.error("[Server Error]", message);
          messageApi.error("服务器内部错误，请稍后再试");
          break;
        default:
          console.error(`[HTTP ${status}]`, message);
          messageApi.error(message || `请求失败 (HTTP ${status})`);
      }
    } else if (error.request) {
      // 请求已发出但没有收到响应
      console.error("[Network Error]", "无法连接到服务器，请检查网络");
      messageApi.error("网络连接失败，请检查网络设置");
    } else {
      // 请求配置出错
      console.error("[Request Config Error]", error.message);
      messageApi.error("请求配置错误");
    }

    return Promise.reject(error);
  },
);

export default instance;
