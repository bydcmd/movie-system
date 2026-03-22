import type { AxiosError, AxiosRequestConfig } from "axios";
import axiosInstance from "@/api/axios";

type ResultEnvelope<T> = {
  code?: number;
  data?: T;
};

type AbortSignalLike = {
  aborted: boolean;
  addEventListener?: (
    type: "abort",
    listener: () => void,
    options?: { once?: boolean },
  ) => void;
  onabort?: ((event: Event) => void) | null;
  reason?: unknown;
};

function normalizeHeaders(
  headers?: HeadersInit,
): AxiosRequestConfig["headers"] {
  if (!headers) {
    return undefined;
  }
  if (headers instanceof Headers) {
    return Object.fromEntries(headers.entries());
  }
  if (Array.isArray(headers)) {
    return Object.fromEntries(headers);
  }
  return headers;
}

function mergeAbortSignal(
  controller: AbortController,
  signal?: AbortSignalLike | null,
): void {
  if (!signal) {
    return;
  }
  const reason = "reason" in signal ? signal.reason : undefined;
  if (signal.aborted) {
    controller.abort(reason);
    return;
  }
  const forwardAbort = () => {
    const nextReason = "reason" in signal ? signal.reason : undefined;
    controller.abort(nextReason);
  };

  if (typeof signal.addEventListener === "function") {
    signal.addEventListener("abort", forwardAbort, { once: true });
    return;
  }

  const previousOnAbort = signal.onabort;
  signal.onabort = (event: Event) => {
    previousOnAbort?.call(signal, event);
    forwardAbort();
  };
}

function buildLegacyConfig(
  url: string,
  options?: RequestInit,
): AxiosRequestConfig {
  return {
    url,
    method: options?.method as AxiosRequestConfig["method"],
    data: options?.body,
    headers: normalizeHeaders(options?.headers),
    withCredentials: options?.credentials === "include",
  };
}

export const customInstance = <T>(
  configOrUrl: string | AxiosRequestConfig,
  options?: RequestInit | AxiosRequestConfig,
): Promise<T> & { cancel?: () => void } => {
  const controller = new AbortController();
  let config: AxiosRequestConfig;

  if (typeof configOrUrl === "string") {
    const requestOptions = options as RequestInit | undefined;
    mergeAbortSignal(controller, requestOptions?.signal);
    config = {
      ...buildLegacyConfig(configOrUrl, requestOptions),
      signal: controller.signal,
    };
  } else {
    const axiosOptions = options as AxiosRequestConfig | undefined;
    mergeAbortSignal(controller, configOrUrl.signal);
    mergeAbortSignal(controller, axiosOptions?.signal);
    config = {
      ...configOrUrl,
      ...axiosOptions,
      headers: {
        ...(configOrUrl.headers || {}),
        ...(axiosOptions?.headers || {}),
      },
      signal: controller.signal,
    };
  }

  const promise = axiosInstance(config).then(({ data }) => {
    // data 是后端返回的完整 Result 对象
    // 如果业务成功，返回 data.data
    if (
      data &&
      typeof data === "object" &&
      "code" in data &&
      (data as ResultEnvelope<T>).code === 200
    ) {
      return (data as ResultEnvelope<T>).data as T;
    }
    // 如果业务失败，Axios 拦截器已经 reject，不会走到这里
    return data as T; // 理论上不会执行到这里，但保留类型安全
  }) as Promise<T> & { cancel?: () => void };

  promise.cancel = () => controller.abort("Query was cancelled");
  return promise;
};

export type ErrorType<T> = AxiosError<T>;
export type BodyType<T> = T;
