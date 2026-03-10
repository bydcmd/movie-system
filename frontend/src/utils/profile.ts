const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'

export function resolveAssetUrl(path?: string | null): string | null {
  if (!path) {
    return null
  }

  if (path.startsWith('http://') || path.startsWith('https://')) {
    return path
  }

  if (path.startsWith('/')) {
    return `${API_BASE_URL}${path}`
  }

  return path
}

export function formatDateLabel(value?: string | null): string {
  if (!value) {
    return '暂无记录'
  }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }

  return new Intl.DateTimeFormat('zh-CN', {
    month: 'short',
    day: 'numeric'
  }).format(date)
}

export function formatDateTimeLabel(value?: string | null): string {
  if (!value) {
    return '尚未同步'
  }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }

  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  }).format(date)
}

export function truncateText(value?: string | null, maxLength = 120): string {
  if (!value) {
    return '暂无内容'
  }

  const normalized = value.replace(/\s+/g, ' ').trim()
  if (normalized.length <= maxLength) {
    return normalized
  }

  return `${normalized.slice(0, maxLength)}...`
}

export function splitCsvLike(value?: string | null): string[] {
  if (!value) {
    return []
  }

  return value
    .split(/[，,/|]/)
    .map((item) => item.trim())
    .filter(Boolean)
}

export function getNameInitial(value?: string | null): string {
  return value?.trim().charAt(0).toUpperCase() || 'U'
}
