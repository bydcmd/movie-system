<script setup lang="ts">
import { computed, shallowRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NButton, NEmpty, NSpin, useMessage } from 'naive-ui'
import type { CommentVO, Movie } from '@/api/model'
import LongReviewArticle from '@/components/comment/LongReviewArticle.vue'
import NavBar from '@/components/layout/NavBar.vue'
import {
  useGetMovieLongReviewDetail,
  useLikeComment,
  useUnlikeComment
} from '@/api/endpoints/comment-management/comment-management'
import { useGetMovieDetail } from '@/api/endpoints/movie-management/movie-management'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const authStore = useAuthStore()

const movieId = computed(() => Number(route.params.id))
const commentId = computed(() => Number(route.params.commentId))
const currentUserId = computed(() => authStore.user?.id ?? null)

const movie = shallowRef<Movie | null>(null)
const review = shallowRef<CommentVO | null>(null)
const loading = shallowRef(false)
const likeLoading = shallowRef(false)
const errorMessage = shallowRef('')

const movieDetailQuery = useGetMovieDetail(movieId, {
  query: {
    enabled: false,
    retry: false
  },
  request: {
    timeout: 60000
  }
})
const longReviewDetailQuery = useGetMovieLongReviewDetail(movieId, commentId, {
  query: {
    enabled: false,
    retry: false
  }
})
const likeCommentMutation = useLikeComment()
const unlikeCommentMutation = useUnlikeComment()

async function refetchOrThrow<T>(query: { refetch: () => Promise<{ data?: T; error?: unknown }> }) {
  const result = await query.refetch()
  if (result.error) {
    throw result.error
  }
  return result.data
}

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

function ensureAuthenticated(): boolean {
  if (!authStore.isAuthenticated) {
    message.warning('请先登录')
    void router.push({
      name: 'login',
      query: { redirect: route.fullPath }
    })
    return false
  }

  return true
}

async function fetchMovieDetail() {
  if (!movieId.value) {
    movie.value = null
    return
  }

  try {
    const data = (await refetchOrThrow(movieDetailQuery)) as Movie | null
    movie.value = data ?? null
  } catch (error) {
    console.error('Failed to fetch movie detail:', error)
    movie.value = null
  }
}

async function fetchLongReview() {
  if (!movieId.value || !commentId.value) {
    review.value = null
    errorMessage.value = '长评不存在或链接无效'
    return
  }

  try {
    const data = (await refetchOrThrow(longReviewDetailQuery)) as CommentVO | null
    if (!data) {
      review.value = null
      errorMessage.value = '长评不存在或已被删除'
      return
    }

    review.value = data
    errorMessage.value = ''
  } catch (error) {
    console.error('Failed to fetch long review detail:', error)
    review.value = null
    errorMessage.value = extractErrorMessage(error) || '长评加载失败，请稍后再试'
  }
}

async function fetchPage() {
  loading.value = true
  await Promise.all([fetchLongReview(), fetchMovieDetail()])
  loading.value = false
}

async function handleToggleLike() {
  if (!review.value?.id || !ensureAuthenticated()) {
    return
  }

  const targetCommentId = review.value.id
  const previousLiked = Boolean(review.value.isLiked)
  const previousVotes = review.value.votes || 0
  const nextLiked = !previousLiked
  const nextVotes = Math.max(0, previousVotes + (nextLiked ? 1 : -1))

  review.value = {
    ...review.value,
    isLiked: nextLiked,
    votes: nextVotes
  }
  likeLoading.value = true

  try {
    if (nextLiked) {
      await likeCommentMutation.mutateAsync({ commentId: targetCommentId })
    } else {
      await unlikeCommentMutation.mutateAsync({ commentId: targetCommentId })
    }
  } catch (error) {
    console.error('Failed to toggle long review like:', error)
    review.value = {
      ...review.value,
      isLiked: previousLiked,
      votes: previousVotes
    }
    if (!extractErrorMessage(error)) {
      message.error('点赞操作失败，请稍后再试')
    }
  } finally {
    likeLoading.value = false
  }
}

watch([movieId, commentId], () => {
  void fetchPage()
}, { immediate: true })

watch(currentUserId, (nextUserId, previousUserId) => {
  if (nextUserId !== previousUserId && movieId.value && commentId.value) {
    void fetchLongReview()
  }
})
</script>

<template>
  <div class="long-review-page">
    <NavBar />

    <main class="long-review-page-main">
      <div v-if="loading && !review" class="long-review-page-state">
        <n-spin size="large" />
      </div>

      <div v-else-if="errorMessage" class="long-review-page-state">
        <n-empty :description="errorMessage">
          <template #extra>
            <div class="long-review-page-actions">
              <n-button @click="void fetchPage()">重新加载</n-button>
              <n-button secondary @click="void router.push({ name: 'movie-detail', params: { id: movieId } })">
                返回电影页
              </n-button>
            </div>
          </template>
        </n-empty>
      </div>

      <LongReviewArticle
        v-else-if="review"
        :review="review"
        :movie="movie"
        :like-loading="likeLoading"
        :is-authenticated="authStore.isAuthenticated"
        @toggle-like="handleToggleLike"
      />
    </main>

    <footer class="long-review-page-footer">
      <p>&copy; 2026 MovieReviews. 保留所有权利</p>
    </footer>
  </div>
</template>

<style scoped>
.long-review-page {
  display: flex;
  min-height: 100vh;
  flex-direction: column;
  background:
    radial-gradient(circle at top, rgba(251, 191, 36, 0.15), transparent 30%),
    linear-gradient(180deg, rgb(255 251 235), rgb(248 250 252) 16rem);
}

.long-review-page-main {
  flex: 1;
}

.long-review-page-state {
  display: flex;
  min-height: 60vh;
  align-items: center;
  justify-content: center;
  padding: 2rem 1rem;
}

.long-review-page-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 0.75rem;
}

.long-review-page-footer {
  padding: 2.5rem 1rem;
  color: rgb(100 116 139);
  text-align: center;
}
</style>
