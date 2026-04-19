import { computed, ref } from 'vue'
import { useMessage, useDialog } from 'naive-ui'
import {
  useDeleteWatchedBatch,
  useClearWatched
} from '@/api/endpoints/watched-management/watched-management'
import type { MovieItemVO } from '@/api/model'
import { getMovieIdKey, normalizeMovieId, normalizeMovieIdList, type MovieId } from '@/utils/movie'

export interface UseWatchedManagementOptions {
  onSuccess?: () => void
}

export function useWatchedManagement(options: UseWatchedManagementOptions = {}) {
  const message = useMessage()
  const dialog = useDialog()
  const { onSuccess } = options

  // Selection state - using ref for array (similar to ProfileCommentList)
  const selectedIds = ref<MovieId[]>([])

  // Mutations
  const deleteBatchMutation = useDeleteWatchedBatch()
  const clearMutation = useClearWatched()

  // Computed derived state
  const selectedCount = computed(() => selectedIds.value.length)
  const hasSelection = computed(() => selectedIds.value.length > 0)
  const isDeleting = computed(() => deleteBatchMutation.isPending.value)
  const isClearing = computed(() => clearMutation.isPending.value)
  const isProcessing = computed(() => isDeleting.value || isClearing.value)

  /**
   * Toggle selection of a single movie
   */
  function toggleSelection(movieId: MovieId) {
    const movieIdKey = getMovieIdKey(movieId)
    if (!movieIdKey) {
      return
    }

    const existingIndex = selectedIds.value.findIndex((id) => getMovieIdKey(id) === movieIdKey)
    if (existingIndex > -1) {
      selectedIds.value = selectedIds.value.filter((id) => getMovieIdKey(id) !== movieIdKey)
    } else {
      const normalizedMovieId = normalizeMovieId(movieId)
      if (normalizedMovieId) {
        selectedIds.value = [...selectedIds.value, normalizedMovieId]
      }
    }
  }

  /**
   * Select all movies in the list
   */
  function selectAll(movies: MovieItemVO[]) {
    selectedIds.value = normalizeMovieIdList(movies.map((movie) => movie.movieId))
  }

  /**
   * Clear all selections
   */
  function clearSelection() {
    selectedIds.value = []
  }

  /**
   * Check if a movie is selected
   */
  function isSelected(movieId: MovieId): boolean {
    const movieIdKey = getMovieIdKey(movieId)
    return Boolean(movieIdKey && selectedIds.value.some((id) => getMovieIdKey(id) === movieIdKey))
  }

  /**
   * Delete selected movies in batch
   */
  async function deleteSelected() {
    if (!hasSelection.value) {
      message.warning('请先选择要删除的电影')
      return
    }

    const ids = selectedIds.value

    try {
      await deleteBatchMutation.mutateAsync({
        data: { ids }
      } as { data: { ids: number[] } })
      message.success(`已成功删除 ${ids.length} 部影片`)
      selectedIds.value = []
      onSuccess?.()
    } catch (error) {
      console.error('Failed to delete watched movies:', error)
      message.error('删除失败，请稍后再试')
    }
  }

  /**
   * Confirm and delete selected movies
   */
  function confirmDeleteSelected() {
    if (!hasSelection.value) {
      message.warning('请先选择要删除的电影')
      return
    }

    const count = selectedCount.value

    dialog.warning({
      title: '确认删除',
      content: `确定要删除选中的 ${count} 部影片吗？此操作不可恢复。`,
      positiveText: '删除',
      negativeText: '取消',
      onPositiveClick: () => deleteSelected()
    })
  }

  /**
   * Clear all watched records
   */
  async function clearAll() {
    try {
      await clearMutation.mutateAsync()
      message.success('已清空所有看过记录')
      selectedIds.value = []
      onSuccess?.()
    } catch (error) {
      console.error('Failed to clear watched movies:', error)
      message.error('清空失败，请稍后再试')
    }
  }

  /**
   * Confirm and clear all watched records
   */
  function confirmClearAll() {
    dialog.warning({
      title: '确认清空',
      content: '确定要清空所有看过记录吗？此操作不可恢复。',
      positiveText: '清空',
      negativeText: '取消',
      onPositiveClick: () => clearAll()
    })
  }

  return {
    // State
    selectedIds,

    // Computed
    selectedCount,
    hasSelection,
    isDeleting,
    isClearing,
    isProcessing,

    // Actions
    toggleSelection,
    selectAll,
    clearSelection,
    isSelected,
    deleteSelected,
    confirmDeleteSelected,
    clearAll,
    confirmClearAll
  }
}
