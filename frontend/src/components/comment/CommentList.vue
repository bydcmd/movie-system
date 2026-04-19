<script setup lang="ts">
import { computed } from 'vue'
import { NButton, NEmpty, NPagination, NSpin } from 'naive-ui'
import type { CommentVO } from '@/api/model'
import CommentCard from '@/components/comment/CommentCard.vue'
import { normalizeCommentId, type CommentFilter } from '@/utils/comment'

const page = defineModel<number>('page', { required: true })
const filter = defineModel<CommentFilter>('filter', { default: 'short' })

const props = withDefaults(
  defineProps<{
    items: CommentVO[]
    total: number
    pageSize: number
    loading?: boolean
    currentUserId?: string | null
    isAuthenticated?: boolean
    pendingLikeIds?: string[]
    pendingDeleteIds?: string[]
  }>(),
  {
    loading: false,
    currentUserId: null,
    isAuthenticated: false,
    pendingLikeIds: () => [],
    pendingDeleteIds: () => []
  }
)

const emit = defineEmits<{
  toggleLike: [commentId: string]
  delete: [comment: CommentVO]
}>()

const filterOptions: Array<{ label: string; value: CommentFilter }> = [
  { label: '短评', value: 'short' },
  { label: '长评', value: 'long' }
]

const emptyDescription = computed(() => {
  if (filter.value === 'long') {
    return '还没有人发布长评。'
  }

  return '还没有人留下短评。'
})

const pageCount = computed(() => {
  return Math.max(1, Math.ceil(props.total / props.pageSize))
})

function isLikePending(commentId?: unknown): boolean {
  const normalizedCommentId = normalizeCommentId(commentId)
  if (!normalizedCommentId) {
    return false
  }

  return props.pendingLikeIds.includes(normalizedCommentId)
}

function isDeletePending(commentId?: unknown): boolean {
  const normalizedCommentId = normalizeCommentId(commentId)
  if (!normalizedCommentId) {
    return false
  }

  return props.pendingDeleteIds.includes(normalizedCommentId)
}
</script>

<template>
  <div class="comment-list" :class="{ 'comment-list--reading': filter === 'long' }">
    <div class="comment-toolbar">
      <div class="comment-filter-group">
        <n-button
          v-for="option in filterOptions"
          :key="option.value"
          quaternary
          size="small"
          :type="filter === option.value ? 'primary' : 'default'"
          @click="filter = option.value"
        >
          {{ option.label }}
        </n-button>
      </div>
    </div>

    <div v-if="props.loading" class="comment-loading">
      <n-spin size="medium" />
    </div>

    <n-empty
      v-else-if="props.items.length === 0"
      :description="emptyDescription"
      class="comment-empty"
    />

    <div v-else class="comment-stack" :class="{ 'comment-stack--reading': filter === 'long' }">
      <CommentCard
        v-for="comment in props.items"
        :key="comment.id"
        :comment="comment"
        :is-owner="Boolean(props.currentUserId) && comment.userId === props.currentUserId"
        :like-loading="isLikePending(comment.id)"
        :delete-loading="isDeletePending(comment.id)"
        :reading-mode="filter === 'long'"
        :is-authenticated="props.isAuthenticated"
        @toggle-like="emit('toggleLike', $event)"
        @delete="emit('delete', $event)"
      />
    </div>

    <div v-if="props.total > props.pageSize" class="comment-pagination">
      <n-pagination v-model:page="page" :page-count="pageCount" :page-size="props.pageSize" />
    </div>
  </div>
</template>

<style scoped>
.comment-list {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.comment-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
}

.comment-filter-group {
  display: flex;
  flex-wrap: wrap;
  gap: 0.6rem;
}

.comment-loading {
  display: flex;
  justify-content: center;
  padding: 3rem 0;
}

.comment-empty {
  padding: 2rem 0;
}

.comment-stack {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.comment-stack--reading {
  gap: 0;
}

.comment-pagination {
  display: flex;
  justify-content: center;
  padding-top: 0.5rem;
}
</style>
