import axios, {
  AxiosError,
  AxiosHeaders,
  type AxiosRequestConfig,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from "axios";
import { createDiscreteApi } from "naive-ui";
import { useAuthStore } from "@/stores/auth";

// 创建独立的消息 API（用于组件外部）
const { message: messageApi } = createDiscreteApi(["message"]);

const BIGINT_JSON_VALUE_REGEX = /(:\s*)(-?\d{16,})(?=\s*[,}\]])/g;

function parseJsonPreservingLargeIntegers(data: string): unknown {
  const trimmed = data.trim();
  if (!trimmed || (!trimmed.startsWith("{") && !trimmed.startsWith("["))) {
    return data;
  }

  try {
    return JSON.parse(trimmed.replace(BIGINT_JSON_VALUE_REGEX, '$1"$2"'));
  } catch {
    return data;
  }
}

const instance = axios.create({
  // 使用 Vite 环境变量，若无则回退至本地地址
  baseURL: import.meta.env.VITE_API_BASE_URL || "http://localhost:8080",
  timeout: 10000,
  withCredentials: true,
  transformResponse: [
    (data) => {
      if (typeof data !== "string") {
        return data;
      }
      return parseJsonPreservingLargeIntegers(data);
    },
  ],
});

declare module "axios" {
  interface AxiosRequestConfig {
    skipUnauthorizedRedirect?: boolean;
  }
}

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

function shouldSkipUnauthorizedRedirect(
  config?: Pick<AxiosRequestConfig, "skipUnauthorizedRedirect"> | null,
): boolean {
  return Boolean(config?.skipUnauthorizedRedirect);
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
    authStore?.clearAuth();

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

function handleAuthEndpointUnauthorized(url: string | undefined, message?: string) {
  if (!url) {
    return;
  }

  if (url.includes("/auth/token/refresh") || url.includes("/auth/logout")) {
    return;
  }

  console.error("[Auth Endpoint Unauthorized]", message);
  messageApi.error(message || "认证失败，请重新登录");
}

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

function setAuthorizationHeader(
  config: RetriableRequestConfig,
  token: string,
): void {
  if (config.headers && typeof config.headers.set === "function") {
    config.headers.set("Authorization", `Bearer ${token}`);
    return;
  }

  const headers = new AxiosHeaders(config.headers);
  headers.set("Authorization", `Bearer ${token}`);
  config.headers = headers;
}

function createBusinessError(
  response: AxiosResponse<ApiResponse>,
  data: ApiResponse,
): AxiosError {
  const businessStatus =
    typeof data.code === "number" ? data.code : response.status;
  const businessResponse = {
    ...response,
    status: businessStatus,
    statusText: response.statusText || String(businessStatus),
  };
  const error = new AxiosError(
    data.message || "业务错误",
    data.code?.toString() || "BUSINESS_ERROR",
    response.config,
    response.request,
    businessResponse,
  );

  Object.assign(error, { isBusinessError: true });
  return error;
}

async function handleUnauthorizedResponse(
  error: AxiosError,
  originalConfig: RetriableRequestConfig | undefined,
  message?: string,
) {
  const shouldSkipUnauthorized = shouldSkipUnauthorizedRedirect(originalConfig);

  if (
    originalConfig &&
    !originalConfig._retry &&
    !shouldBypassRefresh(originalConfig.url)
  ) {
    // 部分页面会发起“可选登录态”请求来补充收藏/评分等信息。
    // 这类请求收到 401 时不应触发刷新或清空全局登录态，否则会造成进入公共页面时被动掉线。
    if (shouldSkipUnauthorized) {
      console.warn("[Optional Unauthorized]", message || error.message);
      return Promise.reject(error);
    }

    originalConfig._retry = true;

    try {
      const refreshedToken = await refreshTokenSilently();
      if (refreshedToken) {
        setAuthorizationHeader(originalConfig, refreshedToken);
        return instance(originalConfig);
      }
    } catch (refreshError) {
      return Promise.reject(refreshError);
    }
  }

  if (!shouldBypassRefresh(originalConfig?.url)) {
    if (shouldSkipUnauthorized) {
      console.warn("[Optional Unauthorized]", message || error.message);
    } else {
      handleUnauthorized(message || error.message);
    }
  } else {
    handleAuthEndpointUnauthorized(originalConfig?.url, message || error.message);
  }

  return Promise.reject(error);
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
      if (data.code === 401) {
        const businessError = createBusinessError(response, data);
        return handleUnauthorizedResponse(
          businessError,
          response.config as RetriableRequestConfig | undefined,
          data.message,
        );
      }

      const isSuccess = handleBusinessCode(data);
      if (!isSuccess) {
        return Promise.reject(createBusinessError(response, data));
      }
    }
    return response;
  },
  async (error: AxiosError) => {
    const status = error.response?.status;
    const originalConfig = error.config as RetriableRequestConfig | undefined;

    if (status === 401) {
      return handleUnauthorizedResponse(
        error,
        originalConfig,
        (error.response?.data as any)?.message || error.message,
      );
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
      // 请求已发出但没有收到响应（可能是取消、超时或真正的网络错误）
      // ERR_CANCELED 表示请求被取消（页面切换时 Vue Query 会取消进行中的请求）
      if (error.code === 'ERR_CANCELED' || error.message === 'canceled') {
        console.warn("[Request Canceled]", error.message);
        // 取消的请求不显示错误提示，静默忽略
      } else {
        console.error("[Network Error]", "无法连接到服务器，请检查网络");
        messageApi.error("网络连接失败，请检查网络设置");
      }
    } else {
      // 请求配置出错
      console.error("[Request Config Error]", error.message);
      messageApi.error("请求配置错误");
    }

    return Promise.reject(error);
  },
);

export default instance;
