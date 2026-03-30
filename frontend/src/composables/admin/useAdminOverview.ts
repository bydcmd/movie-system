import { computed, shallowRef } from 'vue'
import { useDialog, useMessage } from 'naive-ui'
import {
  useGetDashboardStats
} from '@/api/endpoints/admin-dashboard-management/admin-dashboard-management'
import {
  useRefreshPersonalizedRecommendationsAllAdmin
} from '@/api/endpoints/admin-recommendation-cache-management/admin-recommendation-cache-management'
import type {
  AdminDashboardVO
} from '@/api/model'
import { formatDateTimeLabel } from '@/utils/profile'
import {
  extractAdminErrorMessage,
  formatAdminCount,
  formatAdminTrendDate,
  isRecord,
  normalizeAdminOverview,
  normalizeAdminTrendPoints,
  refetchOrThrow,
  toNumber
} from '@/utils/admin'

type TrendPanel = {
  key: string
  label: string
  description: string
  tone: 'amber' | 'emerald' | 'sky' | 'rose' | 'slate' | 'indigo'
  total: number
  points: Array<{
    date: string
    shortDate: string
    value: number
    ratio: number
  }>
}

type OverviewCard = {
  key: string
  label: string
  tone: 'amber' | 'emerald' | 'sky' | 'rose' | 'slate' | 'indigo'
  value: number
  description: string
}

export function useAdminOverview() {
  const dialog = useDialog()
  const message = useMessage()
  const isRefreshingOverview = shallowRef(false)

  const dashboardQuery = useGetDashboardStats<AdminDashboardVO>({
    query: {
      retry: false
    }
  })
  const refreshAllRecommendationsMutation = useRefreshPersonalizedRecommendationsAllAdmin()

  const overview = computed(() => {
    const dashboard = dashboardQuery.data.value
    const fallback = isRecord(dashboard)
      ? {
          userCount: toNumber(dashboard.userCount),
          movieCount: toNumber(dashboard.movieCount),
          commentCount: toNumber(dashboard.commentCount)
        }
      : undefined

    return normalizeAdminOverview(isRecord(dashboard) ? dashboard.overview : undefined, fallback)
  })

  const overviewCards = computed<OverviewCard[]>(() => {
    const stats = overview.value

    return [
      {
        key: 'users',
        label: '注册用户',
        tone: 'amber',
        value: stats.totalUsers || 0,
        description: `活跃 ${stats.activeUsers || 0} · 冻结 ${stats.frozenUsers || 0} · 注销 ${stats.cancelledUsers || 0}`
      },
      {
        key: 'movies',
        label: '电影资产',
        tone: 'sky',
        value: stats.totalMovies || 0,
        description: `影人 ${stats.totalPeople || 0} · 类型 ${stats.totalGenres || 0} · 地区 ${stats.totalRegions || 0}`
      },
      {
        key: 'comments',
        label: '已发布评论',
        tone: 'rose',
        value: stats.publishedCommentCount || 0,
        description: `草稿 ${stats.draftCommentCount || 0} · 短评 ${stats.shortCommentCount || 0} · 长评 ${stats.longReviewCount || 0}`
      },
      {
        key: 'ratings',
        label: '评分沉淀',
        tone: 'emerald',
        value: stats.totalRatings || 0,
        description: `收藏 ${stats.totalFavorites || 0} · 看过 ${stats.totalWatchedMovies || 0}`
      },
      {
        key: 'engagement',
        label: '社区热度',
        tone: 'indigo',
        value: stats.totalCommentLikes || 0,
        description: `总评论 ${stats.totalComments || 0} · 浏览 ${stats.totalViewHistories || 0}`
      },
      {
        key: 'admins',
        label: '管理员人数',
        tone: 'slate',
        value: stats.adminUsers || 0,
        description: '拥有后台管理权限的账户数量'
      }
    ]
  })

  const trendPanels = computed<TrendPanel[]>(() => {
    const dashboard = dashboardQuery.data.value
    const trendRecord = isRecord(dashboard) && isRecord(dashboard.trends) ? dashboard.trends : {}

    const trendDefinitions: Array<{
      key: 'userRegistrations' | 'publishedComments' | 'favorites' | 'ratings' | 'views' | 'watchedMovies'
      label: string
      description: string
      tone: TrendPanel['tone']
    }> = [
      { key: 'userRegistrations', label: '新增用户', description: '近 7 天注册趋势', tone: 'amber' },
      { key: 'publishedComments', label: '发布评论', description: '近 7 天内容发布', tone: 'rose' },
      { key: 'favorites', label: '新增收藏', description: '近 7 天收藏记录', tone: 'emerald' },
      { key: 'ratings', label: '新增评分', description: '近 7 天评分写入', tone: 'sky' },
      { key: 'views', label: '浏览次数', description: '近 7 天详情访问', tone: 'indigo' },
      { key: 'watchedMovies', label: '看过标记', description: '近 7 天观影沉淀', tone: 'slate' }
    ]

    return trendDefinitions.map((definition) => {
      const rawPoints = normalizeAdminTrendPoints(trendRecord[definition.key])
      const peak = Math.max(1, ...rawPoints.map((point) => point.value || 0))
      const total = rawPoints.reduce((sum, point) => sum + (point.value || 0), 0)

      return {
        key: definition.key,
        label: definition.label,
        description: definition.description,
        tone: definition.tone,
        total,
        points: rawPoints.map((point) => {
          const value = point.value || 0
          return {
            date: point.date || '',
            shortDate: formatAdminTrendDate(point.date),
            value,
            ratio: value > 0 ? value / peak : 0
          }
        })
      }
    })
  })

  const loading = computed(() => dashboardQuery.isLoading.value || dashboardQuery.isFetching.value)
  const hasLoadError = computed(() => dashboardQuery.isError.value)
  const isRefreshingAllRecommendations = computed(() => refreshAllRecommendationsMutation.isPending.value)
  const lastUpdatedText = computed(() => {
    if (!dashboardQuery.dataUpdatedAt.value) {
      return '尚未同步'
    }

    return formatDateTimeLabel(new Date(dashboardQuery.dataUpdatedAt.value).toISOString())
  })

  async function refreshOverview() {
    isRefreshingOverview.value = true

    try {
      await refetchOrThrow(dashboardQuery)
      message.success('仪表盘已刷新')
    } catch (error) {
      console.error('Failed to refresh dashboard overview:', error)
      if (!extractAdminErrorMessage(error)) {
        message.error('刷新仪表盘失败，请稍后再试')
      }
    } finally {
      isRefreshingOverview.value = false
    }
  }

  async function refreshAllRecommendations() {
    try {
      await refreshAllRecommendationsMutation.mutateAsync({
        params: { confirmAll: true }
      })
      message.success('已清除全部用户的猜你喜欢缓存')
    } catch (error) {
      console.error('Failed to refresh all personalized recommendations:', error)
      if (!extractAdminErrorMessage(error)) {
        message.error('清空全量推荐缓存失败，请稍后再试')
      }
    }
  }

  function requestRefreshAllRecommendations() {
    dialog.warning({
      title: '清空全量猜你喜欢缓存',
      content: '该操作会清除全部用户的猜你喜欢缓存，下一次请求会回源重建推荐结果。',
      positiveText: '确认清除',
      negativeText: '取消',
      onPositiveClick: refreshAllRecommendations
    })
  }

  return {
    overview,
    overviewCards,
    trendPanels,
    loading,
    hasLoadError,
    lastUpdatedText,
    formatCount: formatAdminCount,
    isRefreshingOverview,
    isRefreshingAllRecommendations,
    refreshOverview,
    requestRefreshAllRecommendations
  }
}
