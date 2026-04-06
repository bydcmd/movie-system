<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  NButton,
  NDivider,
  NEmpty,
  NIcon,
  NRate,
  NSpace,
  NSpin,
  NTabPane,
  NTag,
  NTabs,
  useDialog,
  useMessage
} from 'naive-ui'
import { ArrowBack, CheckmarkCircle, Heart } from '@vicons/ionicons5'
import type {
  Comment,
  CommentVO,
  FavoriteFolderDTO,
  FavoriteFolderVO,
  Movie,
  PageInfoCommentVO
} from '@/api/model'
import type { CommentFilter, ReviewSubmitPayload } from '@/utils/comment'
import CommentComposerModal from '@/components/comment/CommentComposerModal.vue'
import CommentList from '@/components/comment/CommentList.vue'
import NavBar from '@/components/layout/NavBar.vue'
import MovieFavoriteFolderPickerModal from '@/components/movie/MovieFavoriteFolderPickerModal.vue'
import MovieDetailSimilarMovies from '@/components/movie/MovieDetailSimilarMovies.vue'
import MoviePlaceholder from '@/components/movie/MoviePlaceholder.vue'
import ProfileFavoriteFolderFormModal from '@/components/profile/ProfileFavoriteFolderFormModal.vue'
import {
  useDeleteMyComments,
  useGetMovieComments,
  useGetMyMovieComment,
  useGetMyMovieLongReview,
  useLikeComment,
  useSubmitMovieComment,
  useUnlikeComment,
  useUpdateMyMovieCommentContent,
} from '@/api/endpoints/comment-management/comment-management'
import {
  useCreateFavoriteFolder,
  useGetMyFavoriteFolders
} from '@/api/endpoints/favorite-folder-management/favorite-folder-management'
import {
  useAddFavorite,
  useGetMovieFolderIds,
  useIsFavorited,
  useRemoveFavorite
} from '@/api/endpoints/favorite-management/favorite-management'
import {
  useGetMovieDetail,
  useGetSimilarMoviesByMovie
} from '@/api/endpoints/movie-management/movie-management'
import { useGetUserRating, useUpdateRating } from '@/api/endpoints/rating-management/rating-management'
import { useRecordViewHistory } from '@/api/endpoints/view-history-management/view-history-management'
import {
  useAddWatched,
  useIsWatched,
  useRemoveWatched
} from '@/api/endpoints/watched-management/watched-management'
import { useAuthStore } from '@/stores/auth'
import { getMovieId } from '@/utils/movie'
import { isDefaultFavoriteFolder, sortFavoriteFolders } from '@/utils/favorite-folder'

const route = useRoute()
const router = useRouter()
const dialog = useDialog()
const message = useMessage()
const authStore = useAuthStore()
const GUEST_COMMENT_LIMIT = 20
const SIMILAR_MOVIES_DISPLAY_LIMIT = 20
const SIMILAR_MOVIES_FETCH_LIMIT = SIMILAR_MOVIES_DISPLAY_LIMIT * 2
const movieId = computed(() => Number(route.params.id))
const currentUserId = computed(() => authStore.user?.id ?? null)
const isGuestMode = computed(() => !authStore.isAuthenticated)

const movie = ref<Movie | null>(null)
const loading = ref(false)
const favoriteLoading = ref(false)
const watchedLoading = ref(false)
const isFavorited = ref(false)
const favoriteFolders = ref<FavoriteFolderVO[]>([])
const isWatched = ref(false)
const comments = ref<CommentVO[]>([])
const commentsTotal = ref(0)
const commentsPage = ref(1)
const commentsPageSize = ref(10)
const commentsLoading = ref(false)
const similarMovies = ref<Movie[]>([])
const similarMoviesLoading = ref(false)
const commentFilter = ref<CommentFilter>(route.query.filter === 'long' ? 'long' : 'short')
const pendingLikeIds = ref<number[]>([])
const pendingDeleteIds = ref<number[]>([])
const showReviewModal = ref(false)
const reviewPrefillLoading = ref(false)
const submitReviewLoading = ref(false)
const userRating = ref<number | null>(null)
const ratingLoading = ref(false)
const activeTab = ref(route.query.tab === 'comments' ? 'comments' : 'overview')
const imageLoaded = ref(false)
const imageError = ref(false)
const myShortComment = ref<Comment | null>(null)
const myLongReview = ref<Comment | null>(null)
const reviewDraftResetToken = ref(0)
const showFavoriteFolderPicker = ref(false)
const selectedFavoriteFolderIds = ref<number[]>([])
const currentFavoriteFolderIds = ref<number[]>([])
const showCreateFavoriteFolderModal = ref(false)
const favoriteFolderSubmitting = ref(false)
const creatingFavoriteFolder = ref(false)
const optionalAuthRequest = {
  skipUnauthorizedRedirect: true
} as const

type FavoriteFolderChangeAction = 'add' | 'remove'
type FavoriteFolderSubmitStatus = 'success' | 'duplicate' | 'failed'

type FavoriteFolderSubmitResult = {
  folder: FavoriteFolderVO & { id: number }
  action: FavoriteFolderChangeAction
  status: FavoriteFolderSubmitStatus
  errorMessage?: string
}

const shortReviewInitial = computed(() => myShortComment.value?.content?.trim() ?? '')
const shortReviewActionLabel = computed(() => (myShortComment.value?.id ? '改短评' : '写短评'))
const longReviewActionLabel = computed(() => (myLongReview.value?.id ? '改长评' : '写长评'))
const reviewDraftStorageKeyBase = computed(() =>
  movieId.value > 0 ? `movie-review-draft:${movieId.value}` : ''
)
const imdbDisplayId = computed(() => {
  const rawValue = movie.value?.imdbId?.trim()
  if (!rawValue) {
    return ''
  }
  return rawValue.startsWith('tt') ? rawValue : `tt${rawValue}`
})
const commentTypeParam = computed<number>(() => {
  return commentFilter.value === 'long' ? 2 : 1
})
const commentQueryParams = computed(() => ({
  page: commentsPage.value,
  size: commentsPageSize.value,
  type: commentTypeParam.value
}))
const similarMoviesQueryParams = computed(() => ({
  limit: SIMILAR_MOVIES_FETCH_LIMIT
}))
const movieDetailQuery = useGetMovieDetail(movieId, {
  query: {
    enabled: false,
    retry: false
  },
  request: {
    timeout: 60000
  }
})
const movieCommentsQuery = useGetMovieComments(movieId, commentQueryParams, {
  query: {
    enabled: false,
    retry: false
  }
})
const similarMoviesQuery = useGetSimilarMoviesByMovie(movieId, similarMoviesQueryParams, {
  query: {
    enabled: false,
    retry: false
  }
})
const myShortCommentQuery = useGetMyMovieComment(movieId, {
  query: {
    enabled: false,
    retry: false
  },
  request: optionalAuthRequest
})
const myLongReviewQuery = useGetMyMovieLongReview(movieId, {
  query: {
    enabled: false,
    retry: false
  },
  request: optionalAuthRequest
})
const favoriteStatusQuery = useIsFavorited(movieId, {
  query: {
    enabled: false,
    retry: false
  },
  request: optionalAuthRequest
})
const movieFavoriteFolderIdsQuery = useGetMovieFolderIds(movieId, {
  query: {
    enabled: false,
    retry: false
  },
  request: optionalAuthRequest
})
const favoriteFoldersQuery = useGetMyFavoriteFolders({
  query: {
    enabled: false,
    retry: false
  }
})
const watchedStatusQuery = useIsWatched(movieId, {
  query: {
    enabled: false,
    retry: false
  },
  request: optionalAuthRequest
})
const userRatingQuery = useGetUserRating(movieId, {
  query: {
    enabled: false,
    retry: false
  },
  request: optionalAuthRequest
})
const deleteCommentMutation = useDeleteMyComments()
const likeCommentMutation = useLikeComment()
const unlikeCommentMutation = useUnlikeComment()
const submitMovieCommentMutation = useSubmitMovieComment()
const updateMyMovieCommentContentMutation = useUpdateMyMovieCommentContent()
const addFavoriteMutation = useAddFavorite()
const removeFavoriteMutation = useRemoveFavorite()
const createFavoriteFolderMutation = useCreateFavoriteFolder()
const addWatchedMutation = useAddWatched()
const removeWatchedMutation = useRemoveWatched()
const updateRatingMutation = useUpdateRating()
const recordViewHistoryMutation = useRecordViewHistory({
  request: optionalAuthRequest
})
const favoriteFoldersLoading = computed(() => {
  return favoriteFoldersQuery.isLoading.value || favoriteFoldersQuery.isFetching.value
})
const favoriteFolderMembershipLoading = computed(() => {
  return movieFavoriteFolderIdsQuery.isLoading.value || movieFavoriteFolderIdsQuery.isFetching.value
})
const favoriteFolderPickerLoading = computed(() => {
  return favoriteFoldersLoading.value || favoriteFolderMembershipLoading.value
})
const favoriteActionLabel = computed(() => {
  return isFavorited.value ? '取消收藏' : '收藏'
})
const favoriteFolderActionLabel = computed(() => {
  return isFavorited.value ? '管理收藏夹' : '添加到收藏夹'
})

async function refetchOrThrow<T>(query: { refetch: () => Promise<{ data?: T; error?: unknown }> }) {
  const result = await query.refetch()
  if (result.error) {
    throw result.error
  }
  return result.data
}

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

const normalizeMovieList = (value: unknown): Movie[] => {
  return Array.isArray(value) ? (value as Movie[]) : []
}

const normalizeSimilarMovieList = (value: unknown, targetMovieId: number): Movie[] => {
  const result: Movie[] = []
  const seenMovieIds = new Set<number>()

  for (const movie of normalizeMovieList(value)) {
    const id = getMovieId(movie)
    if (!id || id <= 0 || id === targetMovieId || seenMovieIds.has(id)) {
      continue
    }

    seenMovieIds.add(id)
    result.push(movie)

    if (result.length >= SIMILAR_MOVIES_DISPLAY_LIMIT) {
      break
    }
  }

  return result
}

const normalizeFavoriteFolderList = (value: unknown): FavoriteFolderVO[] => {
  if (!Array.isArray(value)) {
    return []
  }

  return sortFavoriteFolders(value as FavoriteFolderVO[])
}

const normalizeFolderIdList = (value: unknown): number[] => {
  if (!Array.isArray(value)) {
    return []
  }

  return Array.from(
    new Set(value.filter((folderId): folderId is number => typeof folderId === 'number' && folderId > 0))
  )
}

const isSelectableFavoriteFolder = (
  folder?: FavoriteFolderVO | null
): folder is FavoriteFolderVO & { id: number } => {
  return Boolean(folder && typeof folder.id === 'number' && folder.id > 0)
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

const openReviewModal = async () => {
  if (!ensureAuthenticated()) return

  reviewPrefillLoading.value = true
  try {
    await fetchMyShortComment()
  } finally {
    reviewPrefillLoading.value = false
  }

  showReviewModal.value = true
}

const goToLongReviewEditor = () => {
  if (!ensureAuthenticated() || !movieId.value) return

  if (myLongReview.value?.id) {
    void router.push({
      name: 'long-review-editor-edit',
      params: {
        movieId: String(movieId.value),
        commentId: String(myLongReview.value.id)
      }
    })
    return
  }

  void router.push({
    name: 'long-review-editor',
    params: { movieId: String(movieId.value) }
  })
}

const extractErrorMessage = (error: unknown): string => {
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

const isDuplicateShortReviewError = (messageText: string): boolean => {
  return messageText.includes('已经发表过短评')
}

const isDuplicateFavoriteError = (messageText: string): boolean => {
  return messageText.includes('已在默认收藏夹中') || messageText.includes('已在当前收藏夹中')
}

const handleFavoriteActionError = (error: unknown, fallbackMessage: string) => {
  const messageText = extractErrorMessage(error)
  if (messageText) {
    if (isDuplicateFavoriteError(messageText)) {
      isFavorited.value = true
      message.info(messageText)
      return
    }

    message.error(messageText)
    return
  }

  message.error(fallbackMessage)
}

const markPending = (
  target: typeof pendingLikeIds | typeof pendingDeleteIds,
  id: number,
  pending: boolean
) => {
  if (pending) {
    if (!target.value.includes(id)) {
      target.value = [...target.value, id]
    }
    return
  }

  target.value = target.value.filter((item) => item !== id)
}

const fetchUserRating = async () => {
  if (!authStore.isAuthenticated || !movieId.value) {
    userRating.value = null
    return
  }
  try {
    const rating = await refetchOrThrow(userRatingQuery)
    userRating.value = resolveRating(rating)
  } catch (error) {
    console.error('Failed to fetch user rating:', error)
  }
}

const fetchMyShortComment = async () => {
  if (!authStore.isAuthenticated || !movieId.value) {
    myShortComment.value = null
    return null
  }

  try {
    const comment = await refetchOrThrow(myShortCommentQuery) as Comment | null
    myShortComment.value = comment ?? null
    return myShortComment.value
  } catch (error) {
    console.error('Failed to fetch my short comment:', error)
    myShortComment.value = null
    return null
  }
}

const fetchMyLongReview = async () => {
  if (!authStore.isAuthenticated || !movieId.value) {
    myLongReview.value = null
    return null
  }

  try {
    const review = await refetchOrThrow(myLongReviewQuery) as Comment | null
    myLongReview.value = review ?? null
    return myLongReview.value
  } catch (error) {
    console.error('Failed to fetch my long review:', error)
    myLongReview.value = null
    return null
  }
}

const submitUserRating = async (value: number) => {
  if (!ensureAuthenticated() || !movieId.value) return
  ratingLoading.value = true
  try {
    await updateRatingMutation.mutateAsync({ movieId: movieId.value, params: { rating: value } })
    userRating.value = value
    message.success('评分已提交')
  } catch (error) {
    console.error('Failed to submit rating:', error)
    message.error('评分失败')
  } finally {
    ratingLoading.value = false
  }
}

const imageUrl = computed(() => {
  const cover = movie.value?.cover
  if (!cover) return null
  if (cover.startsWith('http://') || cover.startsWith('https://')) {
    return cover
  }
  if (cover.startsWith('/')) {
    return `${import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'}${cover}`
  }
  return cover
})

const maxGuestCommentPage = computed(() => {
  return Math.max(1, Math.ceil(GUEST_COMMENT_LIMIT / commentsPageSize.value))
})

const visibleCommentsTotal = computed(() => {
  if (authStore.isAuthenticated) {
    return commentsTotal.value
  }

  return Math.min(commentsTotal.value, GUEST_COMMENT_LIMIT)
})

const showGuestCommentLimitNotice = computed(() => {
  return !authStore.isAuthenticated && commentsTotal.value > GUEST_COMMENT_LIMIT
})
const commentTabLabel = computed(() => {
  if (authStore.isAuthenticated) {
    return `评论 (${commentsTotal.value})`
  }

  if (commentsTotal.value > GUEST_COMMENT_LIMIT) {
    return `评论 (${visibleCommentsTotal.value}/${commentsTotal.value})`
  }

  return `评论 (${visibleCommentsTotal.value})`
})
const guestCommentActionHint = computed(() => {
  if (!isGuestMode.value) {
    return ''
  }

  return '游客可浏览短评和长评，并进入长评详情页继续阅读；点赞、写短评、写长评和查看全部评论需登录。'
})

const displayDoubanScore = computed(() => {
  return movie.value?.doubanScore ? movie.value.doubanScore.toFixed(1) : '-'
})

const displaySiteScore = computed(() => {
  return movie.value?.score ? (movie.value.score / 2).toFixed(1) : '-'
})

const genresList = computed(() => {
  return movie.value?.genres?.split(',').map(g => g.trim()).filter(Boolean) || []
})

const regionsList = computed(() => {
  return movie.value?.regions?.split(',').map(r => r.trim()).filter(Boolean) || []
})

const languagesList = computed(() => {
  return movie.value?.languages?.split(',').map(l => l.trim()).filter(Boolean) || []
})

const getName = (person: any): string => {
  return (person?.name || person?.NAME || '').trim()
}

const getRole = (person: any): string => {
  return (person?.role || person?.ROLE || '').trim()
}

const getPersonId = (person: any): number | null => {
  const raw = person?.id ?? person?.ID ?? person?.personId ?? null
  if (raw == null) return null
  const num = Number(raw)
  return Number.isFinite(num) && num > 0 ? num : null
}

const navigateToPerson = (person: any) => {
  const id = getPersonId(person)
  if (id) {
    void router.push({ name: 'person-detail', params: { id } })
  }
}

const actorsList = computed(() => {
  return (movie.value?.actors || [])
    .map((actor: any) => ({
      name: getName(actor),
      role: getRole(actor)
    }))
    .filter(a => a.name)
})

const directorsList = computed(() => {
  return (movie.value?.directors || [])
    .map((director: any) => getName(director))
    .filter(Boolean)
})

const writersList = computed(() => {
  return (movie.value?.writers || [])
    .map((writer: any) => getName(writer))
    .filter(Boolean)
})

const recordMovieViewHistory = async (targetMovieId: number) => {
  if (!authStore.isAuthenticated || !targetMovieId) {
    return
  }

  try {
    await recordViewHistoryMutation.mutateAsync({
      data: {
        movieId: targetMovieId
      }
    })
  } catch (error) {
    console.error('Failed to record view history:', error)
  }
}

const fetchMovieDetail = async () => {
  if (!movieId.value) return

  const targetMovieId = movieId.value
  loading.value = true
  try {
    const data = await refetchOrThrow(movieDetailQuery) as Movie | null
    if (targetMovieId !== movieId.value) {
      return
    }

    movie.value = data ?? null

    if (movie.value) {
      await recordMovieViewHistory(targetMovieId)
    }
  } catch (error) {
    console.error('Failed to fetch movie detail:', error)
    if (targetMovieId === movieId.value) {
      movie.value = null
    }
  } finally {
    if (targetMovieId === movieId.value) {
      loading.value = false
    }
  }
}

const fetchFavoriteStatus = async () => {
  if (!authStore.isAuthenticated || !movieId.value) {
    isFavorited.value = false
    return
  }
  try {
    const status = await refetchOrThrow(favoriteStatusQuery)
    isFavorited.value = resolveStatus(status)
  } catch (error) {
    console.error('Failed to fetch favorite status:', error)
  }
}

const fetchMovieFavoriteFolderIds = async () => {
  if (!authStore.isAuthenticated || !movieId.value) {
    currentFavoriteFolderIds.value = []
    return []
  }

  try {
    const data = await refetchOrThrow(movieFavoriteFolderIdsQuery)
    const folderIds = normalizeFolderIdList(data)
    currentFavoriteFolderIds.value = folderIds
    return folderIds
  } catch (error) {
    console.error('Failed to fetch movie favorite folder ids:', error)
    currentFavoriteFolderIds.value = []
    message.error(extractErrorMessage(error) || '电影收藏夹状态加载失败，请稍后再试')
    return []
  }
}

const loadFavoriteFolders = async () => {
  if (!authStore.isAuthenticated) {
    favoriteFolders.value = []
    return
  }

  try {
    const data = await refetchOrThrow(favoriteFoldersQuery)
    favoriteFolders.value = normalizeFavoriteFolderList(data)
  } catch (error) {
    console.error('Failed to load favorite folders:', error)
    favoriteFolders.value = []
    message.error(extractErrorMessage(error) || '收藏夹加载失败，请稍后再试')
  }
}

const fetchSimilarMovies = async () => {
  if (!movieId.value) {
    similarMovies.value = []
    return
  }

  const targetMovieId = movieId.value
  similarMoviesLoading.value = true
  try {
    const data = await refetchOrThrow(similarMoviesQuery)
    if (targetMovieId !== movieId.value) {
      return
    }

    similarMovies.value = normalizeSimilarMovieList(data, targetMovieId)
  } catch (error) {
    console.error('Failed to fetch similar movies:', error)
    if (targetMovieId === movieId.value) {
      similarMovies.value = []
    }
  } finally {
    if (targetMovieId === movieId.value) {
      similarMoviesLoading.value = false
    }
  }
}

const fetchWatchedStatus = async () => {
  if (!authStore.isAuthenticated || !movieId.value) {
    isWatched.value = false
    return
  }
  try {
    const status = await refetchOrThrow(watchedStatusQuery)
    isWatched.value = resolveStatus(status)
  } catch (error) {
    console.error('Failed to fetch watched status:', error)
  }
}

const toggleFavorite = async () => {
  if (!ensureAuthenticated() || !movieId.value) return

  const shouldRemoveFavorite = isFavorited.value
  favoriteLoading.value = true

  try {
    if (shouldRemoveFavorite) {
      await removeFavoriteMutation.mutateAsync({ movieId: movieId.value })
      isFavorited.value = false
      currentFavoriteFolderIds.value = []
      selectedFavoriteFolderIds.value = []
      message.success('已取消收藏')
      return
    }

    await addFavoriteMutation.mutateAsync({ movieId: movieId.value })
    isFavorited.value = true
    const defaultFolder = favoriteFolders.value.find(
      (folder) => isDefaultFavoriteFolder(folder)
    )
    if (isSelectableFavoriteFolder(defaultFolder) && !currentFavoriteFolderIds.value.includes(defaultFolder.id)) {
      currentFavoriteFolderIds.value = [...currentFavoriteFolderIds.value, defaultFolder.id]
    }
    message.success('已添加到默认收藏夹')
  } catch (error) {
    if (shouldRemoveFavorite) {
      console.error('Failed to remove movie from favorites:', error)
      message.error(extractErrorMessage(error) || '取消收藏失败')
    } else {
      console.error('Failed to add movie to default favorite folder:', error)
      handleFavoriteActionError(error, '添加到默认收藏夹失败')
    }
  } finally {
    favoriteLoading.value = false
  }
}

const openFavoriteFolderPicker = async () => {
  if (!ensureAuthenticated() || !movieId.value) {
    return
  }

  selectedFavoriteFolderIds.value = []
  currentFavoriteFolderIds.value = []
  showFavoriteFolderPicker.value = true
  const [, folderIds] = await Promise.all([loadFavoriteFolders(), fetchMovieFavoriteFolderIds()])

  if (!showFavoriteFolderPicker.value) {
    return
  }

  selectedFavoriteFolderIds.value = [...folderIds]
}

const describeFolderSelection = (folders: Array<FavoriteFolderVO & { id: number }>) => {
  if (folders.length === 1) {
    const folder = folders[0]!
    return isDefaultFavoriteFolder(folder) ? '默认收藏夹' : `“${folder.name || '未命名收藏夹'}”`
  }

  return `${folders.length} 个收藏夹`
}

const handleFavoriteFolderSubmit = async (folderIds: number[]) => {
  if (!ensureAuthenticated() || !movieId.value) {
    return
  }

  const nextFolderIds = normalizeFolderIdList(folderIds)
  const favoriteFolderMap = new Map(
    favoriteFolders.value
      .filter(isSelectableFavoriteFolder)
      .map((folder) => [folder.id, folder] as const)
  )
  const currentFolderIdSet = new Set(currentFavoriteFolderIds.value)
  const nextFolderIdSet = new Set(nextFolderIds)
  const targetAddFolders = nextFolderIds
    .filter((folderId) => !currentFolderIdSet.has(folderId))
    .map((folderId) => favoriteFolderMap.get(folderId))
    .filter((folder): folder is FavoriteFolderVO & { id: number } => Boolean(folder))
  const targetRemoveFolders = currentFavoriteFolderIds.value
    .filter((folderId) => !nextFolderIdSet.has(folderId))
    .map((folderId) => favoriteFolderMap.get(folderId))
    .filter((folder): folder is FavoriteFolderVO & { id: number } => Boolean(folder))

  if (!targetAddFolders.length && !targetRemoveFolders.length) {
    showFavoriteFolderPicker.value = false
    message.info('收藏夹未发生变化')
    return
  }

  favoriteFolderSubmitting.value = true
  const results: FavoriteFolderSubmitResult[] = []

  try {
    for (const folder of targetAddFolders) {
      try {
        await addFavoriteMutation.mutateAsync({
          movieId: movieId.value,
          params: { folderId: folder.id }
        })
        results.push({
          folder,
          action: 'add',
          status: 'success'
        })
      } catch (error) {
        const messageText = extractErrorMessage(error)

        if (messageText && isDuplicateFavoriteError(messageText)) {
          results.push({
            folder,
            action: 'add',
            status: 'duplicate',
            errorMessage: messageText
          })
          continue
        }

        console.error('Failed to add movie to favorite folder:', error)
        results.push({
          folder,
          action: 'add',
          status: 'failed',
          errorMessage: messageText || '添加到收藏夹失败'
        })
      }
    }

    for (const folder of targetRemoveFolders) {
      try {
        await removeFavoriteMutation.mutateAsync({
          movieId: movieId.value,
          params: { folderId: folder.id }
        })
        results.push({
          folder,
          action: 'remove',
          status: 'success'
        })
      } catch (error) {
        const messageText = extractErrorMessage(error)
        console.error('Failed to remove movie from favorite folder:', error)
        results.push({
          folder,
          action: 'remove',
          status: 'failed',
          errorMessage: messageText || '取消收藏失败'
        })
      }
    }

    const succeededFolders = results
      .filter((result) => result.action === 'add' && result.status === 'success')
      .map((result) => result.folder)
    const removedFolders = results
      .filter((result) => result.action === 'remove' && result.status === 'success')
      .map((result) => result.folder)
    const duplicateFolders = results
      .filter((result) => result.action === 'add' && result.status === 'duplicate')
      .map((result) => result.folder)
    const failedResults = results.filter((result) => result.status === 'failed')
    const [, latestFolderIds] = await Promise.all([loadFavoriteFolders(), fetchMovieFavoriteFolderIds()])

    isFavorited.value = latestFolderIds.length > 0
    selectedFavoriteFolderIds.value = failedResults.length > 0 ? [...nextFolderIds] : [...latestFolderIds]

    if (failedResults.length === 0) {
      showFavoriteFolderPicker.value = false
    }

    if (
      succeededFolders.length > 0 &&
      removedFolders.length === 0 &&
      duplicateFolders.length === 0 &&
      failedResults.length === 0
    ) {
      message.success(`已添加到${describeFolderSelection(succeededFolders)}`)
      return
    }

    if (
      succeededFolders.length === 0 &&
      removedFolders.length === 0 &&
      duplicateFolders.length > 0 &&
      failedResults.length === 0
    ) {
      message.info(`所选的${describeFolderSelection(duplicateFolders)}中已包含这部电影`)
      return
    }

    if (
      succeededFolders.length === 0 &&
      removedFolders.length > 0 &&
      duplicateFolders.length === 0 &&
      failedResults.length === 0
    ) {
      message.success(`已从${describeFolderSelection(removedFolders)}移除`)
      return
    }

    if (failedResults.length === 0) {
      const changeSummary: string[] = []

      if (succeededFolders.length > 0) {
        changeSummary.push(`添加到${describeFolderSelection(succeededFolders)}`)
      }

      if (removedFolders.length > 0) {
        changeSummary.push(`从${describeFolderSelection(removedFolders)}移除`)
      }

      if (duplicateFolders.length > 0) {
        changeSummary.push(`${duplicateFolders.length} 个收藏夹已存在`)
      }

      message.success(
        changeSummary.length > 0 ? `已更新收藏夹：${changeSummary.join('，')}` : '收藏夹已更新'
      )
      return
    }

    if (
      failedResults.length > 0 &&
      (succeededFolders.length > 0 || removedFolders.length > 0 || duplicateFolders.length > 0)
    ) {
      message.warning(
        `已处理 ${succeededFolders.length + removedFolders.length + duplicateFolders.length} 个收藏夹，仍有 ${failedResults.length} 个收藏夹处理失败`
      )
      return
    }

    message.error(failedResults[0]?.errorMessage || '收藏夹更新失败')
  } catch (error) {
    console.error('Failed to update movie favorite folders:', error)
    handleFavoriteActionError(error, '收藏夹更新失败')
  } finally {
    favoriteFolderSubmitting.value = false
  }
}

const openCreateFavoriteFolder = () => {
  showCreateFavoriteFolderModal.value = true
}

const handleCreateFavoriteFolder = async (payload: FavoriteFolderDTO) => {
  const name = payload.name.trim()
  if (!name) {
    message.warning('请先填写收藏夹名称')
    return
  }

  creatingFavoriteFolder.value = true

  try {
    await createFavoriteFolderMutation.mutateAsync({
      data: {
        name,
        description: payload.description?.trim() || undefined,
        isPublic: payload.isPublic ?? 0
      }
    })
    showCreateFavoriteFolderModal.value = false
    message.success('收藏夹已创建')
    await loadFavoriteFolders()
  } catch (error) {
    console.error('Failed to create favorite folder:', error)
    message.error(extractErrorMessage(error) || '收藏夹创建失败，请稍后再试')
  } finally {
    creatingFavoriteFolder.value = false
  }
}

const toggleWatched = async () => {
  if (!ensureAuthenticated() || !movieId.value) return
  watchedLoading.value = true
  try {
    if (isWatched.value) {
      await removeWatchedMutation.mutateAsync({ movieId: movieId.value })
      isWatched.value = false
      message.success('已取消看过')
    } else {
      await addWatchedMutation.mutateAsync({ movieId: movieId.value })
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

const fetchComments = async () => {
  if (!movieId.value) return

  if (!authStore.isAuthenticated && commentsPage.value > maxGuestCommentPage.value) {
    commentsPage.value = maxGuestCommentPage.value
  }

  commentsLoading.value = true
  try {
    const pageInfo = await refetchOrThrow(movieCommentsQuery) as PageInfoCommentVO | null
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

const handleCommentsPageChange = (page: number) => {
  commentsPage.value = page
  void fetchComments()
}

const handleCommentFilterChange = (nextFilter: CommentFilter) => {
  commentFilter.value = nextFilter
  commentsPage.value = 1
  void fetchComments()
}

const handleReviewSubmit = async (payload: ReviewSubmitPayload) => {
  if (!ensureAuthenticated() || !movieId.value) {
    return
  }

  submitReviewLoading.value = true

  try {
    let updated = Boolean(myShortComment.value?.id)

    try {
      if (updated) {
        await updateMyMovieCommentContentMutation.mutateAsync({
          movieId: movieId.value,
          data: {
            content: payload.content
          }
        })
      } else {
        await submitMovieCommentMutation.mutateAsync({
          movieId: movieId.value,
          data: {
            content: payload.content
          }
        })
      }
    } catch (error) {
      const messageText = extractErrorMessage(error)
      if (!updated && isDuplicateShortReviewError(messageText)) {
        await updateMyMovieCommentContentMutation.mutateAsync({
          movieId: movieId.value,
          data: {
            content: payload.content
          }
        })
        updated = true
      } else {
        throw error
      }
    }

    message.success(updated ? '短评已更新' : '短评已发布')
    commentFilter.value = 'short'

    commentsPage.value = 1
    activeTab.value = 'comments'
    reviewDraftResetToken.value += 1
    showReviewModal.value = false
    await Promise.all([fetchComments(), fetchMyShortComment(), fetchMyLongReview()])
  } catch (error) {
    console.error('Failed to submit review:', error)
    if (!extractErrorMessage(error)) {
      message.error('评论保存失败，请稍后再试')
    }
  } finally {
    submitReviewLoading.value = false
  }
}

const handleToggleCommentLike = async (commentId: number) => {
  if (!ensureAuthenticated()) {
    return
  }

  const target = comments.value.find((item) => item.id === commentId)
  if (!target) {
    return
  }

  const previousLiked = Boolean(target.isLiked)
  const previousVotes = target.votes || 0
  const nextLiked = !previousLiked
  const nextVotes = Math.max(0, previousVotes + (nextLiked ? 1 : -1))

  comments.value = comments.value.map((item) =>
    item.id === commentId
      ? {
          ...item,
          isLiked: nextLiked,
          votes: nextVotes
        }
      : item
  )
  markPending(pendingLikeIds, commentId, true)

  try {
    if (nextLiked) {
      await likeCommentMutation.mutateAsync({ commentId })
    } else {
      await unlikeCommentMutation.mutateAsync({ commentId })
    }
  } catch (error) {
    console.error('Failed to toggle comment like:', error)
    comments.value = comments.value.map((item) =>
      item.id === commentId
        ? {
            ...item,
            isLiked: previousLiked,
            votes: previousVotes
          }
        : item
    )
    if (!extractErrorMessage(error)) {
      message.error('点赞操作失败，请稍后再试')
    }
  } finally {
    markPending(pendingLikeIds, commentId, false)
  }
}

const confirmDeleteComment = async (commentId?: number) => {
  if (!commentId || !ensureAuthenticated()) {
    return
  }

  markPending(pendingDeleteIds, commentId, true)

  try {
    await deleteCommentMutation.mutateAsync({
      data: {
        ids: [commentId]
      }
    })
    const shouldFallbackToPreviousPage = comments.value.length === 1 && commentsPage.value > 1
    if (shouldFallbackToPreviousPage) {
      commentsPage.value -= 1
    }

    await Promise.all([fetchComments(), fetchMyShortComment(), fetchMyLongReview()])
    message.success('评论已删除')
  } catch (error) {
    console.error('Failed to delete comment:', error)
    if (!extractErrorMessage(error)) {
      message.error('删除评论失败，请稍后再试')
    }
  } finally {
    markPending(pendingDeleteIds, commentId, false)
  }
}

const requestDeleteComment = (comment: CommentVO) => {
  if (!ensureAuthenticated()) {
    return
  }

  dialog.warning({
    title: '删除评论',
    content: `确定删除这条${comment.type === 2 ? '长评' : '短评'}吗？删除后无法恢复。`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: () => confirmDeleteComment(comment.id)
  })
}

const goBack = () => {
  router.back()
}

const handleImageLoad = () => {
  imageLoaded.value = true
}

const handleImageError = () => {
  imageError.value = true
}

onMounted(() => {
  fetchMovieDetail()
  fetchSimilarMovies()
  fetchComments()
  fetchMyShortComment()
  fetchMyLongReview()
  fetchFavoriteStatus()
  fetchWatchedStatus()
  fetchUserRating()
})

watch(
  () => authStore.isAuthenticated,
  (authenticated) => {
    if (!authenticated) {
      showFavoriteFolderPicker.value = false
      showCreateFavoriteFolderModal.value = false
      favoriteFolders.value = []
      selectedFavoriteFolderIds.value = []
      currentFavoriteFolderIds.value = []
      favoriteFolderSubmitting.value = false
    }

    fetchComments()
    fetchMyShortComment()
    fetchMyLongReview()
    fetchFavoriteStatus()
    fetchWatchedStatus()
    fetchUserRating()
  }
)

watch(
  () => movieId.value,
  () => {
    commentsPage.value = 1
    imageLoaded.value = false
    imageError.value = false
    showFavoriteFolderPicker.value = false
    showCreateFavoriteFolderModal.value = false
    selectedFavoriteFolderIds.value = []
    currentFavoriteFolderIds.value = []
    favoriteFolderSubmitting.value = false
    fetchMovieDetail()
    fetchSimilarMovies()
    fetchComments()
    fetchMyShortComment()
    fetchMyLongReview()
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
                <span v-if="imdbDisplayId" class="text-slate-500">IMDb: {{ imdbDisplayId }}</span>
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
                  <div v-if="isGuestMode" class="mt-2 text-xs text-amber-200">
                    登录后可评分
                  </div>
                </div>
              </div>

              <!-- Actions -->
              <div class="space-y-3">
                <n-space wrap>
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
                    {{ favoriteActionLabel }}
                  </n-button>
                  <n-button
                    secondary
                    size="large"
                    :type="isFavorited ? 'success' : 'default'"
                    @click="openFavoriteFolderPicker"
                  >
                    {{ favoriteFolderActionLabel }}
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
                  <div v-if="movie.directors && movie.directors.length > 0" class="mb-4">
                    <h3 class="text-sm font-medium text-slate-500 mb-2">导演</h3>
                    <div class="flex flex-wrap gap-2">
                      <n-tag
                        v-for="(director, idx) in movie.directors"
                        :key="getName(director) || idx"
                        size="medium"
                        :class="{ 'cursor-pointer': getPersonId(director) }"
                        @click="navigateToPerson(director)"
                      >
                        {{ getName(director) }}
                      </n-tag>
                    </div>
                  </div>

                  <!-- Writers -->
                  <div v-if="movie.writers && movie.writers.length > 0" class="mb-4">
                    <h3 class="text-sm font-medium text-slate-500 mb-2">编剧</h3>
                    <div class="flex flex-wrap gap-2">
                      <n-tag
                        v-for="(writer, idx) in movie.writers"
                        :key="getName(writer) || idx"
                        size="medium"
                        :class="{ 'cursor-pointer': getPersonId(writer) }"
                        @click="navigateToPerson(writer)"
                      >
                        {{ getName(writer) }}
                      </n-tag>
                    </div>
                  </div>

                  <!-- Actors -->
                  <div v-if="movie.actors && movie.actors.length > 0">
                    <h3 class="text-sm font-medium text-slate-500 mb-2">主演</h3>
                    <div class="flex flex-wrap gap-2">
                      <n-tag
                        v-for="(actor, idx) in movie.actors.slice(0, 10)"
                        :key="getName(actor) || idx"
                        size="medium"
                        :class="{ 'cursor-pointer': getPersonId(actor) }"
                        @click="navigateToPerson(actor)"
                      >
                        {{ getName(actor) }}
                        <span v-if="getRole(actor)" class="text-slate-400 ml-1">饰 {{ getRole(actor) }}</span>
                      </n-tag>
                    </div>
                  </div>
                </section>
                <MovieDetailSimilarMovies
                  :movies="similarMovies"
                  :loading="similarMoviesLoading"
                />
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
                    
                    <div v-if="imdbDisplayId">
                      <div class="text-sm text-slate-500">IMDb</div>
                      <div class="text-slate-900">{{ imdbDisplayId }}</div>
                    </div>
                  </div>
                </section>
              </div>
            </div>
          </n-tab-pane>

          <!-- Comments Tab -->
          <n-tab-pane name="comments" :tab="commentTabLabel">
            <div class="bg-white rounded-xl p-6 shadow-sm border border-slate-200">
              <div class="flex flex-col gap-4 mb-6 sm:flex-row sm:items-center sm:justify-between">
                <div class="space-y-2">
                  <h2 class="text-xl font-bold text-slate-900">观众评论</h2>
                  <p v-if="guestCommentActionHint" class="text-sm leading-6 text-slate-500">
                    {{ guestCommentActionHint }}
                  </p>
                </div>
                <n-space>
                  <n-button
                    type="primary"
                    :disabled="reviewPrefillLoading"
                    @click="openReviewModal"
                  >
                    {{ shortReviewActionLabel }}
                  </n-button>
                  <n-button
                    type="info"
                    @click="goToLongReviewEditor"
                  >
                    {{ longReviewActionLabel }}
                  </n-button>
                </n-space>
              </div>

              <div
                v-if="showGuestCommentLimitNotice"
                class="mb-4 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm leading-6 text-amber-800"
              >
                当前为游客模式，最多查看前 20 条评论。登录后可浏览全部评论并参与互动。
              </div>

              <CommentList
                v-model:page="commentsPage"
                v-model:filter="commentFilter"
                :items="comments"
                :total="visibleCommentsTotal"
                :page-size="commentsPageSize"
                :loading="commentsLoading"
                :current-user-id="currentUserId"
                :is-authenticated="authStore.isAuthenticated"
                :pending-like-ids="pendingLikeIds"
                :pending-delete-ids="pendingDeleteIds"
                @update:page="handleCommentsPageChange"
                @update:filter="handleCommentFilterChange"
                @toggle-like="handleToggleCommentLike"
                @delete="requestDeleteComment"
              />
            </div>
          </n-tab-pane>
        </n-tabs>
      </main>
    </template>

    <footer class="bg-slate-900 text-slate-400 py-12 text-center mt-auto">
      <p>&copy; 2026 MovieReviews. 保留所有权利</p>
    </footer>

    <CommentComposerModal
      v-model:show="showReviewModal"
      :short-initial="shortReviewInitial"
      :draft-storage-key-base="reviewDraftStorageKeyBase"
      :draft-reset-token="reviewDraftResetToken"
      :saving="submitReviewLoading"
      @submit="handleReviewSubmit"
    />

    <MovieFavoriteFolderPickerModal
      v-model:show="showFavoriteFolderPicker"
      v-model:selected-folder-ids="selectedFavoriteFolderIds"
      :initial-folder-ids="currentFavoriteFolderIds"
      :folders="favoriteFolders"
      :loading="favoriteFolderPickerLoading"
      :submitting="favoriteFolderSubmitting"
      @submit="handleFavoriteFolderSubmit"
      @create-folder="openCreateFavoriteFolder"
    />

    <ProfileFavoriteFolderFormModal
      v-model:show="showCreateFavoriteFolderModal"
      mode="create"
      :saving="creatingFavoriteFolder"
      @submit="handleCreateFavoriteFolder"
    />
  </div>
</template>

<style scoped>
:deep(.n-divider--vertical) {
  background-color: rgb(51 65 85);
}
</style>
