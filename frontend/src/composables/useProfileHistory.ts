import { computed, ref, shallowRef } from 'vue'
import { useMessage } from 'naive-ui'
import {
  useClearHistory,
  useDeleteHistory,
  useDeleteHistoryBatch,
  useGetMyHistory,
  useGetHistoryCount
} from '@/api/endpoints/view-history-management/view-history-management'
import type { MovieItemVO, BatchIdsDTO } from '@/api/model'

const PREVIEW_SIZE = 4

type PageResult<T> = {
  list: T[]
  total: number
}

function normalizePage<T>(value: unknown): PageResult<T> {
  if (!value || typeof value !== 'object') {
    return { list: [], total: 0 }
  }

  const record = value as Record<string, unknown>
  const list = Array.isArray(record.list) ? (record.list as T[]) : []
  const total = typeof record.total === 'number' ? record.total : list.length

  return { list, total }
}

async function refetchOrThrow<T>(query: { refetch: () => Promise<{ data?: T; error?: unknown }> }) {
  const result = await query.refetch()
  if (result.error) {
    throw result.error
  }
  return result.data
}

export function useProfileHistory() {
  const message = useMessage()
  const previewParams = computed(() => ({ page: 1, size: PREVIEW_SIZE }))

  const historyQuery = useGetMyHistory(previewParams, {
    query: {
      enabled: false,
      retry: false
    }
  })

  const historyCountQuery = useGetHistoryCount({
    query: {
      enabled: false,
      retry: false
    }
  })

  const deleteHistoryMutation = useDeleteHistory()
  const deleteHistoryBatchMutation = useDeleteHistoryBatch()
  const clearHistoryMutation = useClearHistory()

  const historyMovies = shallowRef<MovieItemVO[]>([])
  const historyTotal = shallowRef(0)

  const loading = shallowRef(false)
  const refreshing = shallowRef(false)
  const loadError = shallowRef('')
  const lastUpdatedAt = shallowRef<string>('')

  async function loadHistory(options?: { silent?: boolean }) {
    const silent = Boolean(options?.silent)

    if (silent) {
      refreshing.value = true
    } else {
      loading.value = true
    }

    loadError.value = ''

    try {
      const [historyResult, countResult] = await Promise.allSettled([
        refetchOrThrow(historyQuery),
        refetchOrThrow(historyCountQuery)
      ])

      if (historyResult.status === 'fulfilled') {
        const page = normalizePage<MovieItemVO>(historyResult.value)
        historyMovies.value = page.list
      }

      if (countResult.status === 'fulfilled') {
        const countData = countResult.value
        if (countData && typeof countData === 'object') {
          const record = countData as Record<string, unknown>
          historyTotal.value = typeof record.data === 'number' ? record.data : 0
        }
      }

      const failures = [historyResult, countResult].filter(
        (result) => result.status === 'rejected'
      )

      if (failures.length > 0) {
        loadError.value = '浏览历史加载失败'
        if (!silent) {
          message.warning(loadError.value)
        }
      }

      lastUpdatedAt.value = new Date().toISOString()
    } catch (error) {
      console.error('Failed to load history:', error)
      loadError.value = '浏览历史加载失败'
      if (!silent) {
        message.error(loadError.value)
      }
    } finally {
      loading.value = false
      refreshing.value = false
    }
  }

  async function deleteHistory(historyId: number) {
    try {
      await deleteHistoryMutation.mutateAsync({ historyId })
      message.success('已删除该浏览记录')
      await loadHistory({ silent: true })
    } catch (error) {
      console.error('Failed to delete history:', error)
      message.error('删除失败，请稍后再试')
      throw error
    }
  }

  async function deleteHistoryBatch(ids: number[]) {
    if (!ids.length) {
      message.warning('请选择要删除的记录')
      return
    }

    try {
      const payload: BatchIdsDTO = { ids }
      await deleteHistoryBatchMutation.mutateAsync({ data: payload })
      message.success(`已删除 ${ids.length} 条浏览记录`)
      await loadHistory({ silent: true })
    } catch (error) {
      console.error('Failed to batch delete history:', error)
      message.error('批量删除失败，请稍后再试')
      throw error
    }
  }

  async function clearAllHistory() {
    try {
      await clearHistoryMutation.mutateAsync()
      message.success('浏览历史已清空')
      historyMovies.value = []
      historyTotal.value = 0
      lastUpdatedAt.value = new Date().toISOString()
    } catch (error) {
      console.error('Failed to clear history:', error)
      message.error('清空失败，请稍后再试')
      throw error
    }
  }

  const hasHistory = computed(() => historyTotal.value > 0)

  return {
    historyMovies,
    historyTotal,
    loading,
    refreshing,
    loadError,
    lastUpdatedAt,
    hasHistory,
    loadHistory,
    deleteHistory,
    deleteHistoryBatch,
    clearAllHistory
  }
}
