<script setup lang="ts">
import { ref, onMounted, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  NButton,
  NSpin,
  NEmpty,
  NTag,
  NRate,
  NTabs,
  NTabPane,
  NList,
  NListItem,
  NThing,
  NAvatar,
  NPagination,
  NSpace,
  NDivider,
  NIcon,
  NModal,
  NForm,
  NFormItem,
  NInput,
  useMessage
} from 'naive-ui'
import { ArrowBack, Heart, CheckmarkCircle } from '@vicons/ionicons5'
import NavBar from '@/components/layout/NavBar.vue'
import MoviePlaceholder from '@/components/movie/MoviePlaceholder.vue'
import { getMovieDetail } from '@/api/endpoints/movie-management/movie-management'
import {
  getMovieComments,
  submitMovieComment,
  submitMovieLongReview
} from '@/api/endpoints/comment-management/comment-management'
import {
  addFavorite,
  isFavorited as fetchFavoritedStatusApi,
  removeFavorite
} from '@/api/endpoints/favorite-management/favorite-management'
import { getUserRating, updateRating } from '@/api/endpoints/rating-management/rating-management'
import {
  addWatched,
  isWatched as fetchWatchedStatusApi,
  removeWatched
} from '@/api/endpoints/watched-management/watched-management'
import { useAuthStore } from '@/stores/auth'
import type { Movie, CommentVO, PageInfoCommentVO } from '@/api/model'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const authStore = useAuthStore()
const movieId = computed(() => Number(route.params.id))

// 状态
const movie = ref<Movie | null>(null)
const loading = ref(false)
const favoriteLoading = ref(false)
const watchedLoading = ref(false)
const isFavorited = ref(false)
const isWatched = ref(false)
const comments = ref<CommentVO[]>([])
const commentsTotal = ref(0)
const commentsPage = ref(1)
const commentsPageSize = ref(10)
const commentsLoading = ref(false)
const showReviewModal = ref(false)
const reviewTab = ref<'short' | 'long'>('short')
const shortReview = ref('')
const longReviewTitle = ref('')
const longReviewContent = ref('')
const submitReviewLoading = ref(false)
const userRating = ref<number | null>(null)
const ratingLoading = ref(false)

const resolveStatus = (value: unknown): boolean => {
  if (typeof value === 'boolean') return value
  if (typeof value === 'number') return value > 0
  if (value && typeof value === 'object') {
    const record = value as Record<string, unknown>
    if (typeof record.isFavorited === 'boolean') return record.isFavorited
    if (typeof record.isWatched === 'boolean') return record.isWatched
    if (typeof record.favorited === 'boolean') return record.favorited
    if (typeof record.watched === 'boolean') return record.watched
    if (typeof record.status === 'number') return record.status > 0
  }
  return !!value
}

const resolveRating = (value: unknown): number | null => {
  if (typeof value === 'number') return value
  if (value && typeof value === 'object') {
    const record = value as Record<string, unknown>
    if (typeof record.rating === 'number') return record.rating
  }
  return null
}

const ensureAuthenticated = () => {
  if (!authStore.isAuthenticated) {
    message.warning('请先登录')
    router.push({
      name: 'login',
      query: { redirect: route.fullPath }
    })
    return false
  }
  return true
}

const openReviewModal = (tab: 'short' | 'long') => {
  if (!ensureAuthenticated()) return
  reviewTab.value = tab
  showReviewModal.value = true
}

const resetReviewForm = () => {
  shortReview.value = ''
  longReviewTitle.value = ''
  longReviewContent.value = ''
}

const toTiptapJson = (plainText: string): string => {
  const lines = plainText.replace(/\r\n/g, '\n').split('\n')
  const content = lines.map(line =>
    line.length > 0
      ? { type: 'paragraph', content: [{ type: 'text', text: line }] }
      : { type: 'paragraph' }
  )
  return JSON.stringify({ type: 'doc', content })
}

const submitReview = async () => {
  if (!ensureAuthenticated() || !movieId.value) return
  submitReviewLoading.value = true
  try {
    if (reviewTab.value === 'short') {
      if (!shortReview.value.trim()) {
        message.warning('请输入短评内容')
        return
      }
      await submitMovieComment(movieId.value, {
        content: shortReview.value.trim()
      })
      message.success('短评已发布')
    } else {
      if (!longReviewTitle.value.trim() || !longReviewContent.value.trim()) {
        message.warning('请输入长评标题和内容')
        return
      }
      await submitMovieLongReview(movieId.value, {
        title: longReviewTitle.value.trim(),
        content: toTiptapJson(longReviewContent.value)
      })
      message.success('长评已发布')
    }
    showReviewModal.value = false
    resetReviewForm()
    fetchComments()
  } catch (error) {
    console.error('Failed to submit review:', error)
    message.error('发布失败，请稍后再试')
  } finally {
    submitReviewLoading.value = false
  }
}

const fetchUserRating = async () => {
  if (!authStore.isAuthenticated || !movieId.value) {
    userRating.value = null
    return
  }
  try {
    const rating = await getUserRating(movieId.value)
    userRating.value = resolveRating(rating)
  } catch (error) {
    console.error('Failed to fetch user rating:', error)
  }
}

const submitUserRating = async (value: number) => {
  if (!ensureAuthenticated() || !movieId.value) return
  ratingLoading.value = true
  try {
    await updateRating(movieId.value, { rating: value })
    userRating.value = value
    message.success('评分已提交')
  } catch (error) {
    console.error('Failed to submit rating:', error)
    message.error('评分失败')
  } finally {
    ratingLoading.value = false
  }
}
const activeTab = ref('overview')

// 图片加载状态
const imageLoaded = ref(false)
const imageError = ref(false)

// 处理图片 URL
const imageUrl = computed(() => {
  const cover = movie.value?.cover
  if (!cover) return null
  if (cover.startsWith('http://') || cover.startsWith('https://')) {
    return cover
  }
  if (cover.startsWith('/')) {
    return `${import.meta.env.VITE_API_BASE_URL || 'http://localhost:9090'}${cover}`
  }
  return cover
})

// 计算显示分数
const displayDoubanScore = computed(() => {
  return movie.value?.doubanScore ? movie.value.doubanScore.toFixed(1) : '-'
})

const displaySiteScore = computed(() => {
  return movie.value?.score ? (movie.value.score / 2).toFixed(1) : '-'
})

// 解析类型
const genresList = computed(() => {
  return movie.value?.genres?.split(',').map(g => g.trim()).filter(Boolean) || []
})

// 解析地区
const regionsList = computed(() => {
  return movie.value?.regions?.split(',').map(r => r.trim()).filter(Boolean) || []
})

// 解析语言
const languagesList = computed(() => {
  return movie.value?.languages?.split(',').map(l => l.trim()).filter(Boolean) || []
})

// 解析演员列表
const getName = (person: any): string => {
  return (person?.name || person?.NAME || '').trim()
}

const getRole = (person: any): string => {
  return (person?.role || person?.ROLE || '').trim()
}

const actorsList = computed(() => {
  return (movie.value?.actors || [])
    .map((actor: any) => ({
      name: getName(actor),
      role: getRole(actor)
    }))
    .filter(a => a.name)
})

// 解析导演列表
const directorsList = computed(() => {
  return (movie.value?.directors || [])
    .map((director: any) => getName(director))
    .filter(Boolean)
})

// 解析编剧列表
const writersList = computed(() => {
  return (movie.value?.writers || [])
    .map((writer: any) => getName(writer))
    .filter(Boolean)
})

// 获取电影详情
const fetchMovieDetail = async () => {
  if (!movieId.value) return
  loading.value = true
  try {
    // user 参数是可选的，用于记录登录用户的浏览历史
    const data = await getMovieDetail(movieId.value, {
      // 详情聚合查询可能较慢，避免被全局 10s axios 超时提前中断
      timeout: 60000
    }) as Movie | null
    movie.value = data ?? null
  } catch (error) {
    console.error('Failed to fetch movie detail:', error)
    movie.value = null
  } finally {
    loading.value = false
  }
}

const fetchFavoriteStatus = async () => {
  if (!authStore.isAuthenticated || !movieId.value) {
    isFavorited.value = false
    return
  }
  try {
    const status = await fetchFavoritedStatusApi(movieId.value)
    isFavorited.value = resolveStatus(status)
  } catch (error) {
    console.error('Failed to fetch favorite status:', error)
  }
}

const fetchWatchedStatus = async () => {
  if (!authStore.isAuthenticated || !movieId.value) {
    isWatched.value = false
    return
  }
  try {
    const status = await fetchWatchedStatusApi(movieId.value)
    isWatched.value = resolveStatus(status)
  } catch (error) {
    console.error('Failed to fetch watched status:', error)
  }
}

const toggleFavorite = async () => {
  if (!ensureAuthenticated() || !movieId.value) return
  favoriteLoading.value = true
  try {
    if (isFavorited.value) {
      await removeFavorite(movieId.value)
      isFavorited.value = false
      message.success('已取消收藏')
    } else {
      await addFavorite(movieId.value)
      isFavorited.value = true
      message.success('已收藏')
    }
  } catch (error) {
    console.error('Failed to toggle favorite:', error)
    message.error('收藏操作失败')
  } finally {
    favoriteLoading.value = false
  }
}

const toggleWatched = async () => {
  if (!ensureAuthenticated() || !movieId.value) return
  watchedLoading.value = true
  try {
    if (isWatched.value) {
      await removeWatched(movieId.value)
      isWatched.value = false
      message.success('已取消看过')
    } else {
      await addWatched(movieId.value)
      isWatched.value = true
      message.success('已标记看过')
    }
  } catch (error) {
    console.error('Failed to toggle watched:', error)
    message.error('看过操作失败')
  } finally {
    watchedLoading.value = false
  }
}

// 获取评论列表
const fetchComments = async () => {
  if (!movieId.value) return
  commentsLoading.value = true
  try {
    const pageInfo = await getMovieComments(movieId.value, {
      page: commentsPage.value,
      size: commentsPageSize.value
    }) as PageInfoCommentVO | null
    comments.value = pageInfo?.list || []
    commentsTotal.value = pageInfo?.total || 0
  } catch (error) {
    console.error('Failed to fetch comments:', error)
    comments.value = []
    commentsTotal.value = 0
  } finally {
    commentsLoading.value = false
  }
}

// 处理评论分页
const handleCommentsPageChange = (page: number) => {
  commentsPage.value = page
  fetchComments()
}

// 格式化日期
const formatDate = (dateStr: string) => {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  return date.toLocaleDateString('zh-CN')
}

// 返回上一页
const goBack = () => {
  router.back()
}

// 图片加载处理
const handleImageLoad = () => {
  imageLoaded.value = true
}

const handleImageError = () => {
  imageError.value = true
}

onMounted(() => {
  fetchMovieDetail()
  fetchComments()
  fetchFavoriteStatus()
  fetchWatchedStatus()
  fetchUserRating()
})

watch(
  () => authStore.isAuthenticated,
  () => {
    fetchFavoriteStatus()
    fetchWatchedStatus()
    fetchUserRating()
  }
)

watch(
  () => movieId.value,
  () => {
    fetchMovieDetail()
    fetchComments()
    fetchFavoriteStatus()
    fetchWatchedStatus()
    fetchUserRating()
  }
)
</script>

<template>
  <div class="min-h-screen bg-slate-50 flex flex-col">
    <NavBar />

    <!-- Loading State -->
    <div v-if="loading" class="flex-1 flex justify-center items-center">
      <n-spin size="large" />
    </div>

    <!-- Error State -->
    <div v-else-if="!movie" class="flex-1 flex justify-center items-center">
      <n-empty description="电影不存在或已被删除">
        <template #extra>
          <n-button @click="$router.push('/movies')">返回电影列表</n-button>
        </template>
      </n-empty>
    </div>

    <!-- Movie Detail Content -->
    <template v-else>
      <!-- Hero Section with Backdrop -->
      <section class="relative bg-slate-900 text-white overflow-hidden">
        <div class="absolute inset-0 bg-gradient-to-r from-slate-900 via-slate-900/95 to-slate-900/80"></div>
        
        <div class="container mx-auto px-4 py-8 relative z-10">
          <!-- Back Button -->
          <n-button text class="text-slate-400 hover:text-white mb-6" @click="goBack">
            <template #icon>
              <n-icon><ArrowBack /></n-icon>
            </template>
            返回
          </n-button>

          <div class="flex flex-col md:flex-row gap-8">
            <!-- Poster -->
            <div class="w-full md:w-72 flex-shrink-0">
              <div class="aspect-[2/3] rounded-xl overflow-hidden shadow-2xl bg-slate-800 relative">
                <MoviePlaceholder 
                  v-if="!imageUrl || imageError" 
                  :title="movie.name || ''" 
                  class="absolute inset-0"
                />
                <img 
                  v-if="imageUrl && !imageError"
                  :src="imageUrl" 
                  :alt="movie.name"
                  class="w-full h-full object-cover"
                  :class="{ 'opacity-0': !imageLoaded, 'opacity-100': imageLoaded }"
                  @load="handleImageLoad"
                  @error="handleImageError"
                />
              </div>
            </div>

            <!-- Info -->
            <div class="flex-1 py-4">
              <h1 class="text-4xl md:text-5xl font-display font-bold mb-2">
                {{ movie.name }}
              </h1>
              <p v-if="movie.alias" class="text-slate-400 text-lg mb-4">{{ movie.alias }}</p>

              <!-- Meta Info -->
              <div class="flex flex-wrap items-center gap-4 text-sm text-slate-300 mb-6">
                <span v-if="movie.year">{{ movie.year }}年</span>
                <span v-if="movie.mins">{{ movie.mins }}</span>
                <span v-if="movie.releaseDate">{{ movie.releaseDate }}上映</span>
                <span v-if="movie.imdbId" class="text-slate-500">IMDb: {{ movie.imdbId }}</span>
              </div>

              <!-- Tags -->
              <div class="flex flex-wrap gap-2 mb-6">
                <n-tag v-for="genre in genresList" :key="genre" type="primary" size="small">
                  {{ genre }}
                </n-tag>
                <n-tag v-for="region in regionsList" :key="region" size="small">
                  {{ region }}
                </n-tag>
              </div>

              <!-- Ratings -->
              <div class="flex items-center gap-8 mb-6">
                <div class="text-center">
                  <div class="text-3xl font-bold text-accent">{{ displayDoubanScore }}</div>
                  <div class="text-sm text-slate-400">豆瓣评分</div>
                  <div v-if="movie.doubanVotes" class="text-xs text-slate-500 mt-1">
                    {{ movie.doubanVotes.toLocaleString() }}人评价
                  </div>
                </div>
                <n-divider vertical class="h-16 bg-slate-700" />
                <div class="text-center">
                  <div class="text-3xl font-bold text-primary">{{ displaySiteScore }}</div>
                  <div class="text-sm text-slate-400">本站评分</div>
                  <div v-if="movie.votes" class="text-xs text-slate-500 mt-1">
                    {{ movie.votes.toLocaleString() }}人评价
                  </div>
                </div>
                <n-divider vertical class="h-16 bg-slate-700" />
                <div class="text-center">
                  <div class="text-sm text-slate-400 mb-2">我的评分</div>
                  <n-rate
                    :value="userRating || 0"
                    allow-half
                    size="large"
                    :readonly="ratingLoading"
                    @update:value="submitUserRating"
                  />
                </div>
              </div>

              <!-- Actions -->
              <n-space>
                <n-button
                  :type="isFavorited ? 'success' : 'primary'"
                  :secondary="isFavorited"
                  size="large"
                  :loading="favoriteLoading"
                  @click="toggleFavorite"
                >
                  <template #icon>
                    <n-icon><Heart /></n-icon>
                  </template>
                  {{ isFavorited ? '已收藏' : '收藏' }}
                </n-button>
                <n-button
                  :type="isWatched ? 'success' : 'info'"
                  :secondary="isWatched"
                  size="large"
                  :loading="watchedLoading"
                  @click="toggleWatched"
                >
                  <template #icon>
                    <n-icon><CheckmarkCircle /></n-icon>
                  </template>
                  {{ isWatched ? '已看过' : '看过' }}
                </n-button>
              </n-space>
            </div>
          </div>
        </div>
      </section>

      <!-- Main Content -->
      <main class="container mx-auto px-4 py-8 flex-1">
        <n-tabs v-model:value="activeTab" type="line" animated>
          <!-- Overview Tab -->
          <n-tab-pane name="overview" tab="概览">
            <div class="grid grid-cols-1 lg:grid-cols-3 gap-8">
              <!-- Left: Storyline & Info -->
              <div class="lg:col-span-2 space-y-8">
                <!-- Storyline -->
                <section v-if="movie.storyline" class="bg-white rounded-xl p-6 shadow-sm border border-slate-200">
                  <h2 class="text-xl font-bold text-slate-900 mb-4">剧情简介</h2>
                  <p class="text-slate-600 leading-relaxed whitespace-pre-line">{{ movie.storyline }}</p>
                </section>

                <!-- Cast & Crew -->
                <section class="bg-white rounded-xl p-6 shadow-sm border border-slate-200">
                  <h2 class="text-xl font-bold text-slate-900 mb-4">演职员</h2>
                  
                  <!-- Directors -->
                  <div v-if="directorsList.length > 0" class="mb-4">
                    <h3 class="text-sm font-medium text-slate-500 mb-2">导演</h3>
                    <div class="flex flex-wrap gap-2">
                      <n-tag v-for="director in directorsList" :key="director" size="medium">
                        {{ director }}
                      </n-tag>
                    </div>
                  </div>

                  <!-- Writers -->
                  <div v-if="writersList.length > 0" class="mb-4">
                    <h3 class="text-sm font-medium text-slate-500 mb-2">编剧</h3>
                    <div class="flex flex-wrap gap-2">
                      <n-tag v-for="writer in writersList" :key="writer" size="medium">
                        {{ writer }}
                      </n-tag>
                    </div>
                  </div>

                  <!-- Actors -->
                  <div v-if="actorsList.length > 0">
                    <h3 class="text-sm font-medium text-slate-500 mb-2">主演</h3>
                    <div class="flex flex-wrap gap-2">
                      <n-tag v-for="actor in actorsList.slice(0, 10)" :key="actor.name" size="medium">
                        {{ actor.name }}
                        <span v-if="actor.role" class="text-slate-400 ml-1">饰 {{ actor.role }}</span>
                      </n-tag>
                    </div>
                  </div>
                </section>
              </div>

              <!-- Right: More Info -->
              <div class="space-y-6">
                <section class="bg-white rounded-xl p-6 shadow-sm border border-slate-200">
                  <h2 class="text-lg font-bold text-slate-900 mb-4">更多信息</h2>
                  
                  <div class="space-y-4">
                    <div v-if="languagesList.length > 0">
                      <div class="text-sm text-slate-500">语言</div>
                      <div class="text-slate-900">{{ languagesList.join(' / ') }}</div>
                    </div>
                    
                    <div v-if="movie.regions">
                      <div class="text-sm text-slate-500">制片国家/地区</div>
                      <div class="text-slate-900">{{ movie.regions }}</div>
                    </div>
                    
                    <div v-if="movie.mins">
                      <div class="text-sm text-slate-500">片长</div>
                      <div class="text-slate-900">{{ movie.mins }}</div>
                    </div>
                    
                    <div v-if="movie.releaseDate">
                      <div class="text-sm text-slate-500">上映日期</div>
                      <div class="text-slate-900">{{ movie.releaseDate }}</div>
                    </div>
                    
                    <div v-if="movie.imdbId">
                      <div class="text-sm text-slate-500">IMDb</div>
                      <div class="text-slate-900">tt{{ movie.imdbId }}</div>
                    </div>
                  </div>
                </section>
              </div>
            </div>
          </n-tab-pane>

          <!-- Comments Tab -->
          <n-tab-pane name="comments" :tab="`评论 (${commentsTotal})`">
            <div class="bg-white rounded-xl p-6 shadow-sm border border-slate-200">
              <div class="flex items-center justify-between mb-6">
                <h2 class="text-xl font-bold text-slate-900">观众评论</h2>
                <n-space>
                  <n-button type="primary" @click="openReviewModal('short')">写短评</n-button>
                  <n-button type="info" @click="openReviewModal('long')">写长评</n-button>
                </n-space>
              </div>

              <!-- Comments Loading -->
              <div v-if="commentsLoading" class="flex justify-center py-10">
                <n-spin size="medium" />
              </div>

              <!-- Empty Comments -->
              <n-empty v-else-if="comments.length === 0" description="暂无评论，快来发表第一条评论吧！" />

              <!-- Comments List -->
              <n-list v-else>
                <n-list-item v-for="comment in comments" :key="comment.id">
                  <n-thing>
                    <template #avatar>
                      <n-avatar :src="comment.userAvatar || undefined" :fallback-src="undefined">
                        {{ comment.userNickname?.charAt(0) || '?' }}
                      </n-avatar>
                    </template>
                    <template #header>
                      <div class="flex items-center gap-2">
                        <span class="font-medium">{{ comment.userNickname }}</span>
                        <n-rate v-if="comment.rating" :value="comment.rating" readonly size="small" />
                      </div>
                    </template>
                    <template #header-extra>
                      <span class="text-slate-400 text-sm">{{ formatDate(comment.commentTime || '') }}</span>
                    </template>
                    <template #description>
                      <div class="mt-2">
                        <h4 v-if="comment.title" class="font-bold text-slate-900 mb-2">{{ comment.title }}</h4>
                        <p class="text-slate-600">{{ comment.contentSummary || comment.content }}</p>
                      </div>
                    </template>
                  </n-thing>
                </n-list-item>
              </n-list>

              <!-- Comments Pagination -->
              <div v-if="commentsTotal > commentsPageSize" class="flex justify-center mt-6">
                <n-pagination
                  v-model:page="commentsPage"
                  :page-count="Math.ceil(commentsTotal / commentsPageSize)"
                  :page-size="commentsPageSize"
                  @update:page="handleCommentsPageChange"
                />
              </div>
            </div>
          </n-tab-pane>
        </n-tabs>
      </main>
    </template>

    <footer class="bg-slate-900 text-slate-400 py-12 text-center mt-auto">
      <p>&copy; 2026 MovieReviews. 保留所有权利</p>
    </footer>

    <n-modal v-model:show="showReviewModal" preset="card" title="发布评论" class="max-w-2xl w-full">
      <n-tabs v-model:value="reviewTab" type="line" animated>
        <n-tab-pane name="short" tab="短评">
          <n-form label-placement="top">
            <n-form-item label="短评内容">
              <n-input
                v-model:value="shortReview"
                type="textarea"
                :maxlength="300"
                show-count
                placeholder="写下你的简短评价（300字以内）"
                :autosize="{ minRows: 4, maxRows: 8 }"
              />
            </n-form-item>
          </n-form>
        </n-tab-pane>
        <n-tab-pane name="long" tab="长评">
          <n-form label-placement="top">
            <n-form-item label="标题">
              <n-input v-model:value="longReviewTitle" placeholder="请输入标题" />
            </n-form-item>
            <n-form-item label="正文">
              <n-input
                v-model:value="longReviewContent"
                type="textarea"
                :maxlength="5000"
                show-count
                placeholder="写下你的长评（支持富文本的纯文本占位）"
                :autosize="{ minRows: 8, maxRows: 16 }"
              />
            </n-form-item>
          </n-form>
        </n-tab-pane>
      </n-tabs>

      <template #footer>
        <div class="flex justify-end gap-3">
          <n-button @click="showReviewModal = false">取消</n-button>
          <n-button type="primary" :loading="submitReviewLoading" @click="submitReview">
            发布
          </n-button>
        </div>
      </template>
    </n-modal>
  </div>
</template>

<style scoped>
:deep(.n-divider--vertical) {
  background-color: rgb(51 65 85);
}
</style>
