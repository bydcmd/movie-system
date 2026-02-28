type Serializable =
  | string
  | number
  | boolean
  | Date
  | null
  | undefined
  | Serializable[]
  | Record<string, unknown>;

function appendValue(
  params: URLSearchParams,
  key: string,
  value: Serializable,
) {
  if (value === null || value === undefined) {
    return;
  }

  if (Array.isArray(value)) {
    value.forEach((item) => appendValue(params, key, item));
    return;
  }

  if (value instanceof Date) {
    params.append(key, value.toISOString());
    return;
  }

  if (typeof value === "object") {
    try {
      params.append(key, JSON.stringify(value));
    } catch {
      params.append(key, String(value));
    }
    return;
  }

  params.append(key, String(value));
}

export const customParamsSerializer = (
  rawParams: Record<string, unknown>,
): string => {
  const params = new URLSearchParams();

  Object.entries(rawParams || {}).forEach(([key, value]) => {
    appendValue(params, key, value as Serializable);
  });

  return params.toString();
};
