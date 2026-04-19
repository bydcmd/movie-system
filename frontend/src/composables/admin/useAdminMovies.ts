import { computed, nextTick, reactive, shallowRef } from 'vue'
import { useDialog, useMessage } from 'naive-ui'
import {
  useAddMovieAdmin,
  useDeleteMovieAdmin,
  useGetMovieListAdmin,
  useUpdateMovieAdmin
} from '@/api/endpoints/admin-movie-management/admin-movie-management'
import type {
  GetMovieListAdminParams,
  Movie,
  PageInfoMovie
} from '@/api/model'
import { formatDateTimeLabel } from '@/utils/profile'
import {
  DEFAULT_ADMIN_PAGE_SIZE,
  extractAdminErrorMessage,
  formatAdminCount,
  normalizePage,
  refetchOrThrow
} from '@/utils/admin'
import type { AdminMovieFormMode, AdminMovieFormValue } from '@/utils/adminMovie'
import {
  buildAdminMoviePayload,
  createEmptyAdminMovieFormValue,
  toAdminMovieFormValue
} from '@/utils/adminMovie'
import type { MovieId } from '@/utils/movie'

type AdminMovieListState = {
  keywordInput: string
  keyword: string
  page: number
  size: number
}

export function useAdminMovies() {
  const dialog = useDialog()
  const message = useMessage()
  const pendingDeletedMovieId = shallowRef<MovieId | null>(null)
  const movieFormVisible = shallowRef(false)
  const movieFormMode = shallowRef<AdminMovieFormMode>('create')
  const movieFormInitialValue = shallowRef<AdminMovieFormValue>(createEmptyAdminMovieFormValue())
  const editingMovieId = shallowRef<MovieId | null>(null)
  const submittingMovie = shallowRef(false)

  const state = reactive<AdminMovieListState>({
    keywordInput: '',
    keyword: '',
    page: 1,
    size: DEFAULT_ADMIN_PAGE_SIZE
  })

  const params = computed<GetMovieListAdminParams>(() => ({
    keyword: state.keyword || undefined,
    page: state.page,
    size: state.size
  }))

  const movieQuery = useGetMovieListAdmin<PageInfoMovie>(params, {
    query: {
      retry: false
    }
  })
  const addMovieMutation = useAddMovieAdmin()
  const updateMovieMutation = useUpdateMovieAdmin()
  const deleteMovieMutation = useDeleteMovieAdmin()

  const page = computed(() => normalizePage<Movie>(movieQuery.data.value))
  const movies = computed(() => page.value.list)
  const total = computed(() => page.value.total)
  const loading = computed(() => movieQuery.isLoading.value || movieQuery.isFetching.value)
  const hasLoadError = computed(() => movieQuery.isError.value)
  const lastUpdatedText = computed(() => {
    if (!movieQuery.dataUpdatedAt.value) {
      return '尚未同步'
    }

    return formatDateTimeLabel(new Date(movieQuery.dataUpdatedAt.value).toISOString())
  })

  function applyKeyword() {
    state.keyword = state.keywordInput.trim()
    state.page = 1
  }

  function resetFilters() {
    state.keywordInput = ''
    state.keyword = ''
    state.page = 1
  }

  async function refreshMovies() {
    try {
      await refetchOrThrow(movieQuery)
      message.success('电影列表已刷新')
    } catch (error) {
      console.error('Failed to refresh movies:', error)
      const errorMessage = extractAdminErrorMessage(error)
      message.error(errorMessage || '刷新电影列表失败，请稍后再试')
    }
  }

  function openCreateMovieForm() {
    movieFormMode.value = 'create'
    movieFormInitialValue.value = createEmptyAdminMovieFormValue()
    editingMovieId.value = null
    movieFormVisible.value = true
  }

  function openEditMovieForm(movie: Movie) {
    if (!movie.id) {
      message.warning('电影 ID 无效，无法编辑')
      return
    }

    movieFormMode.value = 'edit'
    movieFormInitialValue.value = toAdminMovieFormValue(movie)
    editingMovieId.value = movie.id
    movieFormVisible.value = true
  }

  function isDeletingMovie(movieId?: MovieId | null): boolean {
    return Boolean(movieId && pendingDeletedMovieId.value === movieId)
  }

  async function syncMovieListAfterMutation(shouldResetToFirstPage = false) {
    if (shouldResetToFirstPage && state.page !== 1) {
      state.page = 1
      await nextTick()
    }

    await refetchOrThrow(movieQuery)
  }

  async function submitMovieForm(formValue: AdminMovieFormValue) {
    submittingMovie.value = true

    try {
      const payload = buildAdminMoviePayload(formValue)

      if (movieFormMode.value === 'edit') {
        const movieId = editingMovieId.value
        if (!movieId) {
          throw new Error('电影 ID 无效，无法保存修改')
        }

        await updateMovieMutation.mutateAsync({
          id: movieId as unknown as number,
          data: payload
        })
      } else {
        await addMovieMutation.mutateAsync({
          data: payload
        })
      }

      await syncMovieListAfterMutation(movieFormMode.value === 'create')
      movieFormVisible.value = false
      message.success(movieFormMode.value === 'edit' ? '电影信息已更新' : '电影已新增')
    } catch (error) {
      console.error('Failed to submit movie form:', error)
      const errorMessage = extractAdminErrorMessage(error)
      message.error(errorMessage || '保存电影信息失败，请稍后再试')
    } finally {
      submittingMovie.value = false
    }
  }

  async function deleteMovie(movie: Movie) {
    const movieId = movie.id
    if (!movieId) {
      message.warning('电影 ID 无效，无法删除')
      return
    }

    pendingDeletedMovieId.value = movieId

    try {
      const shouldFallbackToPreviousPage = movies.value.length === 1 && state.page > 1

      await deleteMovieMutation.mutateAsync({ id: movieId as unknown as number })

      if (shouldFallbackToPreviousPage) {
        state.page -= 1
        await nextTick()
      }

      await refetchOrThrow(movieQuery)
      message.success('电影已删除')
    } catch (error) {
      console.error('Failed to delete movie:', error)
      const errorMessage = extractAdminErrorMessage(error)
      message.error(errorMessage || '删除电影失败，请稍后再试')
    } finally {
      pendingDeletedMovieId.value = null
    }
  }

  function requestDeleteMovie(movie: Movie) {
    if (!movie.id) {
      message.warning('电影 ID 无效，无法删除')
      return
    }

    dialog.warning({
      title: '删除电影',
      content: `确定删除《${movie.name || '未命名电影'}》吗？删除后无法恢复。`,
      positiveText: '确认删除',
      negativeText: '取消',
      onPositiveClick: () => deleteMovie(movie)
    })
  }

  return {
    state,
    movies,
    total,
    loading,
    hasLoadError,
    lastUpdatedText,
    formatCount: formatAdminCount,
    applyKeyword,
    resetFilters,
    refreshMovies,
    movieFormVisible,
    movieFormMode,
    movieFormInitialValue,
    submittingMovie,
    openCreateMovieForm,
    openEditMovieForm,
    submitMovieForm,
    requestDeleteMovie,
    isDeletingMovie
  }
}
