<script setup lang="ts">
import { computed, ref, shallowRef, watch } from 'vue'
import { useRouter } from 'vue-router'
import { NButton, NCheckbox, NEmpty, NTag, useDialog, useMessage } from 'naive-ui'
import {
  useDeleteMyComments,
  useUpdateMyMovieCommentContent
} from '@/api/endpoints/comment-management/comment-management'
import type { Comment } from '@/api/model'
import CommentComposerModal from '@/components/comment/CommentComposerModal.vue'
import { getCommentPreviewText, getCommentTypeLabel, isLongReview } from '@/utils/comment'
import { formatDateTimeLabel } from '@/utils/profile'

const props = withDefaults(defineProps<{
  items: Comment[]
  total: number
  loading?: boolean
}>(), {
  loading: false
})

const emit = defineEmits<{
  refresh: []
}>()

const router = useRouter()
const dialog = useDialog()
const message = useMessage()
const editingShortComment = shallowRef<Comment | null>(null)
const draftResetToken = shallowRef(0)
const bulkDeleting = shallowRef(false)
const selectedCommentIds = ref<number[]>([])
const deletingCommentIds = ref<number[]>([])

const updateMyMovieCommentContentMutation = useUpdateMyMovieCommentContent()
const deleteMyCommentsMutation = useDeleteMyComments()

const showShortCommentEditor = computed({
  get: () => editingShortComment.value !== null,
  set: (value: boolean) => {
    if (!value) {
      closeShortCommentEditor()
    }
  }
})
const shortReviewInitial = computed(() => {
  if (isLongReview(editingShortComment.value?.type)) {
    return ''
  }

  return editingShortComment.value?.content?.trim() ?? ''
})
const shortDraftStorageKeyBase = computed(() => {
  const movieId = editingShortComment.value?.movieId
  return movieId ? `movie-review-draft:${movieId}` : ''
})
const selectableCommentIds = computed(() => {
  return props.items
    .map((item) => getCommentId(item))
    .filter((commentId): commentId is number => typeof commentId === 'number')
})
const selectedCount = computed(() => selectedCommentIds.value.length)
const allVisibleSelected = computed(() => {
  return selectableCommentIds.value.length > 0 && selectedCount.value === selectableCommentIds.value.length
})
const partiallySelected = computed(() => {
  return selectedCount.value > 0 && !allVisibleSelected.value
})
const isPreviewTruncated = computed(() => {
  return props.total > props.items.length && props.items.length > 0
})
const isDeletingComments = computed(() => {
  return bulkDeleting.value || deletingCommentIds.value.length > 0
})

watch(
  selectableCommentIds,
  (nextIds) => {
    const nextIdSet = new Set(nextIds)
    selectedCommentIds.value = selectedCommentIds.value.filter((commentId) => nextIdSet.has(commentId))
  },
  { immediate: true }
)

function extractErrorMessage(error: unknown): string {
  if (!error || typeof error !== 'object') {
    return ''
  }

  const record = error as {
    message?: unknown
    response?: {
      data?: {
        message?: unknown
      }
    }
  }

  const responseMessage = record.response?.data?.message
  if (typeof responseMessage === 'string') {
    return responseMessage
  }

  return typeof record.message === 'string' ? record.message : ''
}

function getCommentId(item?: Comment | null): number | null {
  const commentId = item?.id
  return typeof commentId === 'number' && commentId > 0 ? commentId : null
}

function getMovieId(item?: Comment | null): number | null {
  const movieId = item?.movieId
  return typeof movieId === 'number' && movieId > 0 ? movieId : null
}

function isDeletingComment(commentId?: number): boolean {
  return typeof commentId === 'number' && deletingCommentIds.value.includes(commentId)
}

function isCommentSelected(commentId?: number): boolean {
  return typeof commentId === 'number' && selectedCommentIds.value.includes(commentId)
}

function markCommentDeleting(commentId: number, pending: boolean) {
  if (pending) {
    if (!deletingCommentIds.value.includes(commentId)) {
      deletingCommentIds.value = [...deletingCommentIds.value, commentId]
    }
    return
  }

  deletingCommentIds.value = deletingCommentIds.value.filter((id) => id !== commentId)
}

function openMovie(movieId?: number) {
  if (!movieId) {
    return
  }

  void router.push(`/movie/${movieId}`)
}

function openCommentEditor(item: Comment) {
  const movieId = getMovieId(item)
  if (!movieId) {
    message.error('当前评论缺少关联电影，暂时无法编辑')
    return
  }

  if (isLongReview(item.type)) {
    const commentId = getCommentId(item)
    if (!commentId) {
      message.error('当前长评缺少评论标识，暂时无法编辑')
      return
    }

    void router.push({
      name: 'long-review-editor-edit',
      params: {
        movieId: String(movieId),
        commentId: String(commentId)
      }
    })
    return
  }

  editingShortComment.value = item
}

function closeShortCommentEditor() {
  if (updateMyMovieCommentContentMutation.isPending.value) {
    return
  }

  editingShortComment.value = null
}

async function handleShortCommentSubmit(payload: { type: 'short'; content: string }) {
  const movieId = getMovieId(editingShortComment.value)
  if (!movieId) {
    message.error('当前短评缺少关联电影，暂时无法保存')
    return
  }

  try {
    await updateMyMovieCommentContentMutation.mutateAsync({
      movieId,
      data: {
        content: payload.content
      }
    })
    draftResetToken.value += 1
    editingShortComment.value = null
    message.success('短评已更新')
    emit('refresh')
  } catch (error) {
    console.error('Failed to update short comment:', error)
    message.error(extractErrorMessage(error) || '更新短评失败，请稍后再试')
  }
}

function handleToggleAllComments(checked: boolean) {
  selectedCommentIds.value = checked ? [...selectableCommentIds.value] : []
}

function handleToggleComment(commentId: number, checked: boolean) {
  if (checked) {
    if (!selectedCommentIds.value.includes(commentId)) {
      selectedCommentIds.value = [...selectedCommentIds.value, commentId]
    }
    return
  }

  selectedCommentIds.value = selectedCommentIds.value.filter((id) => id !== commentId)
}

async function deleteComments(commentIds: number[], options?: { bulk?: boolean }) {
  const uniqueIds = Array.from(new Set(commentIds))
  if (!uniqueIds.length) {
    return
  }

  if (options?.bulk) {
    bulkDeleting.value = true
  }

  uniqueIds.forEach((commentId) => {
    markCommentDeleting(commentId, true)
  })

  try {
    await deleteMyCommentsMutation.mutateAsync({
      data: {
        ids: uniqueIds
      }
    })

    const editingCommentId = getCommentId(editingShortComment.value)
    if (editingCommentId && uniqueIds.includes(editingCommentId)) {
      draftResetToken.value += 1
      editingShortComment.value = null
    }

    selectedCommentIds.value = selectedCommentIds.value.filter((commentId) => !uniqueIds.includes(commentId))
    message.success(uniqueIds.length > 1 ? `已删除 ${uniqueIds.length} 条评论` : '评论已删除')
    emit('refresh')
  } catch (error) {
    console.error('Failed to delete comment:', error)
    message.error(extractErrorMessage(error) || '删除评论失败，请稍后再试')
  } finally {
    uniqueIds.forEach((commentId) => {
      markCommentDeleting(commentId, false)
    })
    bulkDeleting.value = false
  }
}

async function deleteComment(item: Comment) {
  const commentId = getCommentId(item)
  if (!commentId) {
    message.error('当前评论缺少评论标识，暂时无法删除')
    return
  }

  await deleteComments([commentId])
}

function confirmDeleteComment(item: Comment) {
  const commentId = getCommentId(item)
  if (!commentId) {
    message.error('当前评论缺少评论标识，暂时无法删除')
    return
  }

  dialog.warning({
    title: '删除评论',
    content: `确定删除这条${isLongReview(item.type) ? '长评' : '短评'}吗？删除后无法恢复。`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      await deleteComment(item)
    }
  })
}

function confirmDeleteSelectedComments() {
  if (!selectedCount.value) {
    return
  }

  const count = selectedCount.value
  const content = isPreviewTruncated.value
    ? `确认删除已选 ${count} 条评论？当前页面只展示最近 ${props.items.length} / ${props.total} 条记录。`
    : `确认删除已选 ${count} 条评论？此操作不可撤销。`

  dialog.warning({
    title: '删除所选评论',
    content,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      await deleteComments(selectedCommentIds.value, { bulk: true })
    }
  })
}
</script>

<template>
  <div class="profile-comment-list">
    <div class="profile-comment-header">
      <div>
        <h3 class="profile-comment-title">评论历史</h3>
        <p class="profile-comment-description">保留你参与讨论的痕迹，方便回看每一次表达。</p>
      </div>
      <div class="profile-comment-total">
        共 {{ props.total }} 条
      </div>
    </div>

    <div v-if="props.loading" class="profile-comment-loading">
      <div
        v-for="index in 4"
        :key="index"
        class="profile-comment-skeleton"
      />
    </div>

    <template v-else>
      <section
        v-if="props.total > 0"
        class="profile-comment-toolbar"
      >
        <div class="profile-comment-toolbar-main">
          <n-checkbox
            :checked="allVisibleSelected"
            :indeterminate="partiallySelected"
            :disabled="selectableCommentIds.length === 0 || isDeletingComments"
            @update:checked="handleToggleAllComments"
          >
            全选当前列表
          </n-checkbox>

          <span class="profile-comment-selection-count">
            已选 {{ selectedCount }} 条
          </span>

          <span class="profile-comment-toolbar-hint">
            <template v-if="isPreviewTruncated">
              当前展示最近 {{ props.items.length }} / {{ props.total }} 条，删除所选仅作用于当前可见记录。
            </template>
            <template v-else>
              删除所选仅影响勾选记录。
            </template>
          </span>
        </div>

        <div class="profile-comment-toolbar-actions">
          <n-button
            type="error"
            secondary
            class="profile-comment-toolbar-button"
            :disabled="selectedCount === 0 || isDeletingComments"
            :loading="bulkDeleting"
            @click="confirmDeleteSelectedComments"
          >
            删除所选
          </n-button>
        </div>
      </section>

      <n-empty
        v-if="props.items.length === 0"
        description="你还没有发过评论，去留下一句观后感吧。"
        class="profile-comment-empty"
      />

      <div v-else class="profile-comment-rows">
        <article
          v-for="item in props.items"
          :key="item.id"
          class="profile-comment-row"
        >
          <div class="profile-comment-top">
            <div class="profile-comment-heading">
              <div class="profile-comment-meta-row">
                <n-checkbox
                  v-if="item.id"
                  class="profile-comment-checkbox"
                  :checked="isCommentSelected(item.id)"
                  :disabled="isDeletingComments"
                  @update:checked="(checked) => handleToggleComment(item.id as number, checked)"
                />
                <div class="profile-comment-meta">
                  <n-tag round :type="item.type === 2 ? 'warning' : 'default'">
                    {{ getCommentTypeLabel(item.type) }}
                  </n-tag>
                  <span class="profile-comment-time">
                    发布于 {{ formatDateTimeLabel(item.commentTime) }}
                  </span>
                </div>
              </div>
              <h4 class="profile-comment-name">
                {{ item.title || (isLongReview(item.type) ? '未命名长评' : '我的短评') }}
              </h4>
            </div>
            <div class="profile-comment-votes">
              获赞 {{ item.votes || 0 }}
            </div>
          </div>

          <p class="profile-comment-body">
            {{ getCommentPreviewText(item, 180) }}
          </p>

          <div class="profile-comment-foot">
            <span class="profile-comment-movie">
              所属电影 {{ item.movieId ? `#${item.movieId}` : '暂不可用' }}
            </span>
            <div class="profile-comment-actions">
              <n-button text type="primary" :disabled="!item.movieId || isDeletingComments" @click="openMovie(item.movieId)">
                查看对应电影
              </n-button>
              <n-button
                text
                type="primary"
                :disabled="!item.movieId || !item.id || isDeletingComments"
                @click="openCommentEditor(item)"
              >
                {{ isLongReview(item.type) ? '修改长评' : '修改短评' }}
              </n-button>
              <n-button
                text
                type="error"
                :disabled="!item.id || bulkDeleting"
                :loading="isDeletingComment(item.id)"
                @click="confirmDeleteComment(item)"
              >
                删除评论
              </n-button>
            </div>
          </div>
        </article>
      </div>
    </template>
  </div>

  <CommentComposerModal
    v-model:show="showShortCommentEditor"
    :short-initial="shortReviewInitial"
    :draft-storage-key-base="shortDraftStorageKeyBase"
    :draft-reset-token="draftResetToken"
    :saving="updateMyMovieCommentContentMutation.isPending.value"
    @submit="handleShortCommentSubmit"
  />
</template>

<style scoped>
.profile-comment-list {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.profile-comment-header {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}

.profile-comment-title {
  margin: 0;
  font-family: var(--font-display);
  font-size: 1.15rem;
  font-weight: 700;
  color: #0f172a;
}

.profile-comment-description {
  margin: 0.35rem 0 0;
  font-size: 0.92rem;
  line-height: 1.6;
  color: #64748b;
}

.profile-comment-total {
  font-size: 0.92rem;
  font-weight: 600;
  color: #475569;
}

.profile-comment-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 0.9rem;
  border-radius: 1rem;
  border: 1px solid rgba(148, 163, 184, 0.2);
  background: rgba(248, 250, 252, 0.72);
  padding: 0.9rem 1rem;
}

.profile-comment-toolbar-main {
  display: flex;
  flex: 1;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.85rem;
}

.profile-comment-selection-count {
  font-size: 0.9rem;
  font-weight: 600;
  color: #334155;
}

.profile-comment-toolbar-hint {
  font-size: 0.88rem;
  color: #64748b;
}

.profile-comment-toolbar-actions {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.profile-comment-toolbar-button {
  min-width: 6rem;
}

.profile-comment-loading {
  display: grid;
  gap: 0.75rem;
}

.profile-comment-skeleton {
  height: 7rem;
  border-radius: 1rem;
  background: rgba(226, 232, 240, 0.72);
}

.profile-comment-empty {
  border-radius: 1rem;
  border: 1px dashed rgba(148, 163, 184, 0.36);
  padding: 3.5rem 0;
}

.profile-comment-rows {
  border-top: 1px solid rgba(148, 163, 184, 0.2);
}

.profile-comment-row {
  border-bottom: 1px solid rgba(148, 163, 184, 0.2);
  padding: 1rem 0;
}

.profile-comment-top {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 0.75rem;
}

.profile-comment-heading {
  min-width: 0;
}

.profile-comment-meta-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.75rem;
}

.profile-comment-checkbox {
  flex-shrink: 0;
}

.profile-comment-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem;
}

.profile-comment-time {
  font-size: 0.9rem;
  color: #64748b;
}

.profile-comment-name {
  margin: 0.65rem 0 0;
  color: #0f172a;
  font-size: 1.05rem;
  font-weight: 700;
}

.profile-comment-votes {
  font-size: 0.9rem;
  color: #475569;
}

.profile-comment-body {
  margin: 0.9rem 0 0;
  font-size: 0.94rem;
  line-height: 1.8;
  color: #475569;
}

.profile-comment-foot {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  margin-top: 0.9rem;
}

.profile-comment-movie {
  font-size: 0.88rem;
  color: #94a3b8;
}

.profile-comment-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 0.75rem;
}
</style>
