import { computed, ref } from 'vue'
import { useMessage } from 'naive-ui'
import {
  useGetMyComments
} from '@/api/endpoints/comment-management/comment-management'
import { useGetMyFavoriteFolders } from '@/api/endpoints/favorite-folder-management/favorite-folder-management'
import { useGetMyFavorites } from '@/api/endpoints/favorite-management/favorite-management'
import { useGetMyRatings } from '@/api/endpoints/rating-management/rating-management'
import { useGetMyProfile, useUpdateMyProfile } from '@/api/endpoints/user-management/user-management'
import { useGetMyWatchedList } from '@/api/endpoints/watched-management/watched-management'
import type {
  Comment,
  FavoriteFolderVO,
  MovieItemVO,
  MyRatingVO,
  UpdateUserProfileDTO,
  UserProfileVO
} from '@/api/model'
import { useAuthStore } from '@/stores/auth'

const PREVIEW_SIZE = 4

type PageResult<T> = {
  list: T[]
  total: number
}

function toFallbackProfile(value: ReturnType<typeof useAuthStore>['user']): UserProfileVO | null {
  if (!value) {
    return null
  }

  return {
    id: value.id,
    nickname: value.nickname,
    avatar: value.avatar,
    url: value.url,
    email: value.email
  }
}

function normalizeProfile(value: unknown): UserProfileVO | null {
  if (!value || typeof value !== 'object') {
    return null
  }

  const record = value as Record<string, unknown>
  return {
    id: typeof record.id === 'string' ? record.id : undefined,
    nickname: typeof record.nickname === 'string' ? record.nickname : undefined,
    avatar: typeof record.avatar === 'string' ? record.avatar : undefined,
    url: typeof record.url === 'string' ? record.url : undefined,
    email: typeof record.email === 'string' ? record.email : undefined
  }
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

export function useProfileDashboard() {
  const authStore = useAuthStore()
  const message = useMessage()
  const previewParams = computed(() => ({ page: 1, size: PREVIEW_SIZE }))
  const profileQuery = useGetMyProfile({
    query: {
      enabled: false,
      retry: false
    }
  })
  const favoritesQuery = useGetMyFavorites(previewParams, {
    query: {
      enabled: false,
      retry: false
    }
  })
  const favoriteFoldersQuery = useGetMyFavoriteFolders({
    query: {
      enabled: false,
      retry: false
    }
  })
  const watchedQuery = useGetMyWatchedList(previewParams, {
    query: {
      enabled: false,
      retry: false
    }
  })
  const ratingsQuery = useGetMyRatings(previewParams, {
    query: {
      enabled: false,
      retry: false
    }
  })
  const commentsQuery = useGetMyComments(previewParams, {
    query: {
      enabled: false,
      retry: false
    }
  })
  const updateProfileMutation = useUpdateMyProfile()

  const profile = ref<UserProfileVO | null>(toFallbackProfile(authStore.user))
  const favoriteFolders = ref<FavoriteFolderVO[]>([])
  const favoriteMovies = ref<MovieItemVO[]>([])
  const watchedMovies = ref<MovieItemVO[]>([])
  const ratings = ref<MyRatingVO[]>([])
  const comments = ref<Comment[]>([])

  const totals = ref({
    favorites: 0,
    watched: 0,
    ratings: 0,
    comments: 0
  })

  const loading = ref(false)
  const refreshing = ref(false)
  const savingProfile = ref(false)
  const loadError = ref('')
  const lastUpdatedAt = ref<string>('')

  async function loadDashboard(options?: { silent?: boolean }) {
    const silent = Boolean(options?.silent)

    if (silent) {
      refreshing.value = true
    } else {
      loading.value = true
    }

    loadError.value = ''

    const [
      profileResult,
      favoriteFoldersResult,
      favoriteResult,
      watchedResult,
      ratingResult,
      commentResult
    ] = await Promise.allSettled([
      refetchOrThrow(profileQuery),
      refetchOrThrow(favoriteFoldersQuery),
      refetchOrThrow(favoritesQuery),
      refetchOrThrow(watchedQuery),
      refetchOrThrow(ratingsQuery),
      refetchOrThrow(commentsQuery)
    ])

    if (profileResult.status === 'fulfilled') {
      profile.value = normalizeProfile(profileResult.value) ?? profile.value
    }

    if (favoriteFoldersResult.status === 'fulfilled') {
      favoriteFolders.value = Array.isArray(favoriteFoldersResult.value)
        ? (favoriteFoldersResult.value as FavoriteFolderVO[])
        : []
    }

    if (favoriteResult.status === 'fulfilled') {
      const page = normalizePage<MovieItemVO>(favoriteResult.value)
      favoriteMovies.value = page.list
      totals.value.favorites = page.total
    }

    if (watchedResult.status === 'fulfilled') {
      const page = normalizePage<MovieItemVO>(watchedResult.value)
      watchedMovies.value = page.list
      totals.value.watched = page.total
    }

    if (ratingResult.status === 'fulfilled') {
      const page = normalizePage<MyRatingVO>(ratingResult.value)
      ratings.value = page.list
      totals.value.ratings = page.total
    }

    if (commentResult.status === 'fulfilled') {
      const page = normalizePage<Comment>(commentResult.value)
      comments.value = page.list
      totals.value.comments = page.total
    }

    const failures = [
      profileResult,
      favoriteFoldersResult,
      favoriteResult,
      watchedResult,
      ratingResult,
      commentResult
    ].filter((result) => result.status === 'rejected')

    if (failures.length > 0) {
      loadError.value = '部分资料加载失败，页面展示的是当前已成功同步的内容。'
      if (!silent) {
        message.warning(loadError.value)
      }
    }

    lastUpdatedAt.value = new Date().toISOString()
    loading.value = false
    refreshing.value = false
  }

  async function saveProfile(payload: UpdateUserProfileDTO) {
    savingProfile.value = true

    try {
      const result = await updateProfileMutation.mutateAsync({ data: payload })
      const nextProfile = normalizeProfile(result) ?? {
        ...profile.value,
        ...payload
      }

      profile.value = nextProfile
      authStore.setUser({
        ...(authStore.user ?? {}),
        id: nextProfile.id ?? authStore.user?.id,
        nickname: nextProfile.nickname,
        avatar: nextProfile.avatar,
        email: nextProfile.email,
        url: nextProfile.url,
        role: authStore.user?.role
      })

      lastUpdatedAt.value = new Date().toISOString()
      message.success('个人资料已更新')
    } catch (error) {
      console.error('Failed to update profile:', error)
      message.error('资料更新失败，请稍后再试')
      throw error
    } finally {
      savingProfile.value = false
    }
  }

  const hasProfile = computed(() => Boolean(profile.value?.id || profile.value?.nickname || authStore.user?.id))

  return {
    profile,
    favoriteFolders,
    favoriteMovies,
    watchedMovies,
    ratings,
    comments,
    totals,
    loading,
    refreshing,
    savingProfile,
    loadError,
    lastUpdatedAt,
    hasProfile,
    loadDashboard,
    saveProfile
  }
}
