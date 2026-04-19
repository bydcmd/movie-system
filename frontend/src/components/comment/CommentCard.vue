<script setup lang="ts">
import { computed } from 'vue'
import { NAvatar, NButton, NRate, NTag } from 'naive-ui'
import type { CommentVO } from '@/api/model'
import {
  countReadableCharacters,
  estimateReadingMinutes,
  extractTiptapText,
  getCommentPreviewText,
  normalizeCommentId,
  getCommentTypeLabel,
  isLongReview
} from '@/utils/comment'
import { formatDateTimeLabel, getNameInitial } from '@/utils/profile'

const props = withDefaults(
  defineProps<{
    comment: CommentVO
    isOwner?: boolean
    likeLoading?: boolean
    deleteLoading?: boolean
    readingMode?: boolean
    isAuthenticated?: boolean
  }>(),
  {
    isOwner: false,
    likeLoading: false,
    deleteLoading: false,
    readingMode: false,
    isAuthenticated: false
  }
)

const emit = defineEmits<{
  toggleLike: [commentId: string]
  delete: [comment: CommentVO]
}>()

const isLong = computed(() => isLongReview(props.comment.type))
const fullText = computed(() => extractTiptapText(props.comment.content))
const previewText = computed(() => getCommentPreviewText(props.comment, isLong.value ? 240 : 180))
const publishedAt = computed(() => formatDateTimeLabel(props.comment.commentTime))
const readableCharacters = computed(() => countReadableCharacters(fullText.value))
const readingMinutes = computed(() => estimateReadingMinutes(fullText.value))
const reviewTitle = computed(() => {
  if (!isLong.value) {
    return props.comment.title?.trim() ?? ''
  }

  return props.comment.title?.trim() || '未命名长评'
})
const longReviewMeta = computed(() => {
  if (!isLong.value) {
    return []
  }

  const items = [publishedAt.value]
  if (readableCharacters.value > 0) {
    items.push(`${readableCharacters.value} 字`)
  }
  if (readingMinutes.value > 0) {
    items.push(`约 ${readingMinutes.value} 分钟读完`)
  }
  return items
})
const longReviewRoute = computed(() => {
  const commentId = normalizeCommentId(props.comment.id)
  if (!isLong.value || !props.comment.movieId || !commentId) {
    return null
  }

  return {
    name: 'movie-review-detail',
    params: {
      id: props.comment.movieId,
      commentId
    }
  }
})
const likeActionLabel = computed(() => {
  const votes = props.comment.votes || 0

  if (!props.isAuthenticated) {
    return `登录后点赞 · ${votes}`
  }

  return `${props.comment.isLiked ? '已赞' : '点赞'} · ${votes}`
})

function handleToggleLike() {
  const commentId = normalizeCommentId(props.comment.id)
  if (!commentId) {
    return
  }

  emit('toggleLike', commentId)
}

function handleDelete() {
  emit('delete', props.comment)
}
</script>

<template>
  <article
    class="comment-card"
    :class="{
      'comment-card--long': isLong,
      'comment-card--reading': isLong && props.readingMode
    }"
  >
    <div class="comment-header">
      <div class="comment-author">
        <n-avatar :src="props.comment.userAvatar || undefined" :fallback-src="undefined" round>
          {{ getNameInitial(props.comment.userNickname) }}
        </n-avatar>
        <div class="comment-meta">
          <div class="comment-meta-row">
            <span class="comment-name">{{ props.comment.userNickname || '匿名用户' }}</span>
            <n-tag size="small" :type="isLong ? 'warning' : 'default'">
              {{ getCommentTypeLabel(props.comment.type) }}
            </n-tag>
            <n-tag v-if="props.isOwner" size="small" type="info">我的评论</n-tag>
          </div>
          <div class="comment-meta-row">
            <span class="comment-time">{{ publishedAt }}</span>
            <n-rate v-if="props.comment.rating" :value="props.comment.rating" readonly size="small" />
          </div>
        </div>
      </div>

      <div v-if="!isLong" class="comment-votes">
        <span class="comment-votes-label">{{ props.comment.votes || 0 }} 赞</span>
      </div>
    </div>

    <div class="comment-body" :class="{ 'comment-body--long': isLong }">
      <div v-if="isLong && longReviewMeta.length > 0" class="comment-reading-meta" aria-label="长评元信息">
        <span v-for="item in longReviewMeta" :key="item" class="comment-reading-meta-item">
          {{ item }}
        </span>
      </div>

      <h4 v-if="reviewTitle" class="comment-title" :class="{ 'comment-title--long': isLong }">
        <RouterLink
          v-if="longReviewRoute"
          :to="longReviewRoute"
          class="comment-title-link"
        >
          {{ reviewTitle }}
        </RouterLink>
        <template v-else>
          {{ reviewTitle }}
        </template>
      </h4>

      <p
        class="comment-preview"
        :class="{
          'comment-preview--long': isLong,
          'comment-preview--excerpt': isLong
        }"
      >
        {{ previewText }}
      </p>
    </div>

    <div class="comment-actions" :class="{ 'comment-actions--long': isLong }">
      <div class="comment-actions-left">
        <n-button
          quaternary
          :type="props.isAuthenticated && props.comment.isLiked ? 'primary' : 'default'"
          :loading="props.likeLoading"
          @click="handleToggleLike"
        >
          {{ likeActionLabel }}
        </n-button>

        <RouterLink
          v-if="longReviewRoute"
          :to="longReviewRoute"
          class="comment-read-link"
        >
          阅读全文
        </RouterLink>
      </div>

      <div v-if="isLong || props.isOwner" class="comment-actions-right">
        <span v-if="isLong" class="comment-actions-note">
          {{ props.comment.votes || 0 }} 人觉得这篇长评有帮助
        </span>
        <n-button v-if="props.isOwner" text type="error" :loading="props.deleteLoading" @click="handleDelete">
          删除
        </n-button>
      </div>
    </div>
  </article>
</template>

<style scoped>
.comment-card {
  border-bottom: 1px solid rgb(226 232 240);
  padding: 1.15rem 0;
}

.comment-card--long {
  padding-top: 1.5rem;
  padding-bottom: 1.6rem;
}

.comment-card:last-child {
  border-bottom: 0;
  padding-bottom: 0;
}

.comment-header {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 1rem;
}

.comment-author {
  display: flex;
  gap: 0.9rem;
  min-width: 0;
}

.comment-meta {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 0.35rem;
}

.comment-meta-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem;
}

.comment-name {
  color: rgb(15 23 42);
  font-weight: 700;
}

.comment-time {
  color: rgb(100 116 139);
  font-size: 0.9rem;
}

.comment-votes {
  color: rgb(100 116 139);
  font-size: 0.85rem;
}

.comment-votes-label {
  white-space: nowrap;
}

.comment-body {
  margin-top: 1rem;
}

.comment-body--long {
  max-width: 46rem;
}

.comment-reading-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 0.55rem;
  margin-bottom: 0.8rem;
}

.comment-reading-meta-item {
  color: rgb(100 116 139);
  font-size: 0.82rem;
  line-height: 1.4;
}

.comment-title {
  margin: 0 0 0.7rem;
  color: rgb(15 23 42);
  font-size: 1.05rem;
  font-weight: 700;
}

.comment-title--long {
  margin-bottom: 0.85rem;
  font-family: 'Iowan Old Style', 'Palatino Linotype', 'Noto Serif SC', serif;
  font-size: clamp(1.22rem, 1.1vw + 1rem, 1.55rem);
  font-weight: 600;
  line-height: 1.55;
  letter-spacing: 0.01em;
}

.comment-title-link {
  color: inherit;
  text-decoration: none;
  transition: color 180ms ease;
}

.comment-title-link:hover {
  color: rgb(180 83 9);
}

.comment-preview {
  margin: 0;
  color: rgb(71 85 105);
  line-height: 1.8;
  white-space: pre-line;
}

.comment-preview--long {
  color: rgb(51 65 85);
  font-size: 0.98rem;
  line-height: 1.9;
}

.comment-preview--excerpt {
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 4;
}

.comment-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 0.75rem;
  margin-top: 1rem;
}

.comment-actions--long {
  align-items: center;
}

.comment-actions-left {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.75rem;
}

.comment-actions-right {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 0.75rem;
}

.comment-actions-note {
  color: rgb(100 116 139);
  font-size: 0.83rem;
  line-height: 1.4;
}

.comment-read-link {
  color: rgb(180 83 9);
  font-size: 0.92rem;
  font-weight: 600;
  text-decoration: none;
  transition: color 180ms ease;
}

.comment-read-link:hover {
  color: rgb(146 64 14);
}

@media (max-width: 700px) {
  .comment-body--long {
    max-width: none;
  }

  .comment-actions-right {
    justify-content: flex-start;
  }
}
</style>
