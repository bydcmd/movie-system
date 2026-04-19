import { computed, reactive, shallowRef } from 'vue'
import { useDialog, useMessage } from 'naive-ui'
import {
  useDeleteCommentAdmin,
  useGetCommentListAdmin
} from '@/api/endpoints/admin-comment-management/admin-comment-management'
import type {
  Comment,
  GetCommentListAdminParams,
  PageInfoComment
} from '@/api/model'
import { normalizeCommentId } from '@/utils/comment'
import { formatDateTimeLabel, truncateText } from '@/utils/profile'
import {
  DEFAULT_ADMIN_PAGE_SIZE,
  extractAdminErrorMessage,
  formatAdminCount,
  normalizePage,
  refetchOrThrow
} from '@/utils/admin'

type AdminCommentListState = {
  keywordInput: string
  keyword: string
  page: number
  size: number
}

export function useAdminComments() {
  const dialog = useDialog()
  const message = useMessage()
  const pendingDeletedCommentId = shallowRef<string | null>(null)

  const state = reactive<AdminCommentListState>({
    keywordInput: '',
    keyword: '',
    page: 1,
    size: DEFAULT_ADMIN_PAGE_SIZE
  })

  const params = computed<GetCommentListAdminParams>(() => ({
    keyword: state.keyword || undefined,
    page: state.page,
    size: state.size
  }))

  const commentQuery = useGetCommentListAdmin<PageInfoComment>(params, {
    query: {
      retry: false
    }
  })
  const deleteCommentMutation = useDeleteCommentAdmin()

  const page = computed(() => normalizePage<Comment>(commentQuery.data.value))
  const comments = computed(() => page.value.list)
  const total = computed(() => page.value.total)
  const loading = computed(() => commentQuery.isLoading.value || commentQuery.isFetching.value)
  const hasLoadError = computed(() => commentQuery.isError.value)
  const lastUpdatedText = computed(() => {
    if (!commentQuery.dataUpdatedAt.value) {
      return '尚未同步'
    }

    return formatDateTimeLabel(new Date(commentQuery.dataUpdatedAt.value).toISOString())
  })

  function getCommentTypeLabel(type?: number | null): string {
    return type === 2 ? '长评' : '短评'
  }

  function getCommentStatusLabel(status?: number | null): string {
    return status === 1 ? '草稿' : '已发布'
  }

  function getCommentStatusType(status?: number | null): 'warning' | 'success' {
    return status === 1 ? 'warning' : 'success'
  }

  function summarizeComment(comment: Comment): string {
    const rawContent = comment.content?.trim()
    if (!rawContent) {
      return comment.title?.trim() || '暂无内容'
    }

    if (comment.type === 2 && (rawContent.startsWith('{') || rawContent.startsWith('['))) {
      return comment.title?.trim() || '结构化长评内容'
    }

    return truncateText(rawContent, 160)
  }

  function applyKeyword() {
    state.keyword = state.keywordInput.trim()
    state.page = 1
  }

  function resetFilters() {
    state.keywordInput = ''
    state.keyword = ''
    state.page = 1
  }

  async function refreshComments() {
    try {
      await refetchOrThrow(commentQuery)
      message.success('评论列表已刷新')
    } catch (error) {
      console.error('Failed to refresh comments:', error)
      if (!extractAdminErrorMessage(error)) {
        message.error('刷新评论列表失败，请稍后再试')
      }
    }
  }

  function isDeletingComment(commentId?: unknown): boolean {
    const normalizedCommentId = normalizeCommentId(commentId)
    return Boolean(normalizedCommentId && pendingDeletedCommentId.value === normalizedCommentId)
  }

  async function deleteComment(comment: Comment) {
    const commentId = normalizeCommentId(comment.id)
    if (!commentId) {
      message.warning('评论 ID 无效，无法删除')
      return
    }

    pendingDeletedCommentId.value = commentId

    try {
      const shouldFallbackToPreviousPage = comments.value.length === 1 && state.page > 1

      await deleteCommentMutation.mutateAsync({ id: commentId as unknown as number })

      if (shouldFallbackToPreviousPage) {
        state.page -= 1
      }

      await refetchOrThrow(commentQuery)
      message.success('评论已删除')
    } catch (error) {
      console.error('Failed to delete comment:', error)
      if (!extractAdminErrorMessage(error)) {
        message.error('删除评论失败，请稍后再试')
      }
    } finally {
      pendingDeletedCommentId.value = null
    }
  }

  function requestDeleteComment(comment: Comment) {
    const commentId = normalizeCommentId(comment.id)
    if (!commentId) {
      message.warning('评论 ID 无效，无法删除')
      return
    }

    dialog.warning({
      title: '删除评论',
      content: `确定删除这条${getCommentTypeLabel(comment.type)}吗？删除后无法恢复。`,
      positiveText: '确认删除',
      negativeText: '取消',
      onPositiveClick: () => deleteComment(comment)
    })
  }

  return {
    state,
    comments,
    total,
    loading,
    hasLoadError,
    lastUpdatedText,
    formatCount: formatAdminCount,
    getCommentTypeLabel,
    getCommentStatusLabel,
    getCommentStatusType,
    summarizeComment,
    applyKeyword,
    resetFilters,
    refreshComments,
    requestDeleteComment,
    isDeletingComment
  }
}
