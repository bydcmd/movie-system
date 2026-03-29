import type {
  AdminDashboardOverviewVO,
  AdminTrendPointVO
} from '@/api/model'

export const DEFAULT_ADMIN_PAGE_SIZE = 6

export type NormalizedPage<T> = {
  list: T[]
  total: number
  pageNum: number
  pageSize: number
}

export function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object'
}

export function toNumber(value: unknown, fallback = 0): number {
  return typeof value === 'number' && Number.isFinite(value) ? value : fallback
}

export function formatAdminCount(value?: number | null): string {
  return new Intl.NumberFormat('zh-CN').format(value ?? 0)
}

export function extractAdminErrorMessage(error: unknown): string {
  if (!isRecord(error)) {
    return ''
  }

  const response = isRecord(error.response) ? error.response : null
  const responseData = response && isRecord(response.data) ? response.data : null
  const responseMessage = responseData?.message
  if (typeof responseMessage === 'string') {
    return responseMessage
  }

  return typeof error.message === 'string' ? error.message : ''
}

export function normalizePage<T>(value: unknown): NormalizedPage<T> {
  if (!isRecord(value)) {
    return {
      list: [],
      total: 0,
      pageNum: 1,
      pageSize: DEFAULT_ADMIN_PAGE_SIZE
    }
  }

  const list = Array.isArray(value.list) ? (value.list as T[]) : []
  const pageSize = toNumber(value.pageSize, DEFAULT_ADMIN_PAGE_SIZE)

  return {
    list,
    total: toNumber(value.total, list.length),
    pageNum: toNumber(value.pageNum, 1),
    pageSize
  }
}

export function normalizeAdminOverview(
  value: unknown,
  fallback?: { userCount?: number; movieCount?: number; commentCount?: number }
): AdminDashboardOverviewVO {
  const record = isRecord(value) ? value : {}

  return {
    totalUsers: toNumber(record.totalUsers, fallback?.userCount ?? 0),
    activeUsers: toNumber(record.activeUsers),
    frozenUsers: toNumber(record.frozenUsers),
    cancelledUsers: toNumber(record.cancelledUsers),
    adminUsers: toNumber(record.adminUsers),
    totalMovies: toNumber(record.totalMovies, fallback?.movieCount ?? 0),
    totalPeople: toNumber(record.totalPeople),
    totalGenres: toNumber(record.totalGenres),
    totalRegions: toNumber(record.totalRegions),
    totalComments: toNumber(record.totalComments),
    publishedCommentCount: toNumber(record.publishedCommentCount, fallback?.commentCount ?? 0),
    draftCommentCount: toNumber(record.draftCommentCount),
    shortCommentCount: toNumber(record.shortCommentCount),
    longReviewCount: toNumber(record.longReviewCount),
    totalCommentLikes: toNumber(record.totalCommentLikes),
    totalRatings: toNumber(record.totalRatings),
    totalFavorites: toNumber(record.totalFavorites),
    totalViewHistories: toNumber(record.totalViewHistories),
    totalWatchedMovies: toNumber(record.totalWatchedMovies)
  }
}

export function normalizeAdminTrendPoints(value: unknown): AdminTrendPointVO[] {
  if (!Array.isArray(value)) {
    return []
  }

  return value.map((item) => {
    const record = isRecord(item) ? item : {}
    return {
      date: typeof record.date === 'string' ? record.date : '',
      value: toNumber(record.value)
    }
  })
}

export function formatAdminTrendDate(value?: string): string {
  if (!value) {
    return '--'
  }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value.slice(5)
  }

  return new Intl.DateTimeFormat('zh-CN', {
    month: 'numeric',
    day: 'numeric'
  }).format(date)
}

export async function refetchOrThrow<T>(
  query: { refetch: () => Promise<{ data?: T; error?: unknown }> }
): Promise<T | undefined> {
  const result = await query.refetch()
  if (result.error) {
    throw result.error
  }
  return result.data
}
