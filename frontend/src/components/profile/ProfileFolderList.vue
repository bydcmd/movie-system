<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  NButton,
  NCheckbox,
  NEmpty,
  NModal,
  NSpin,
  NTag,
  useDialog,
  useMessage
} from 'naive-ui'
import {
  useCreateFavoriteFolder,
  useDeleteFavoriteFolders,
  useUpdateFavoriteFolder
} from '@/api/endpoints/favorite-folder-management/favorite-folder-management'
import { useGetFolderMovies, useRemoveFavorite } from '@/api/endpoints/favorite-management/favorite-management'
import type { FavoriteFolderDTO, FavoriteFolderVO, MovieItemVO, PageInfoMovieItemVO } from '@/api/model'
import MoviePlaceholder from '@/components/movie/MoviePlaceholder.vue'
import { isDefaultFavoriteFolder, sortFavoriteFolders } from '@/utils/favorite-folder'
import ProfileFavoriteFolderFormModal from './ProfileFavoriteFolderFormModal.vue'
import {
  formatDateLabel,
  formatDateTimeLabel,
  resolveAssetUrl,
  splitCsvLike,
  truncateText
} from '@/utils/profile'

type FolderFormMode = 'create' | 'edit'
type IdentifiedFavoriteFolder = FavoriteFolderVO & { id: number }

type PageResult<T> = {
  list: T[]
  total: number
}

const props = withDefaults(
  defineProps<{
    folders: FavoriteFolderVO[]
    total: number
    loading?: boolean
  }>(),
  {
    loading: false
  }
)

const emit = defineEmits<{
  refresh: []
}>()

const router = useRouter()
const dialog = useDialog()
const message = useMessage()
const formMode = ref<FolderFormMode>('create')
const editingFolder = ref<FavoriteFolderVO | null>(null)
const activeFolderId = ref<number | null>(null)
const showFormModal = ref(false)
const showDetailModal = ref(false)
const savingFolder = ref(false)
const deletingFolderIds = ref<number[]>([])
const selectedFolderIds = ref<number[]>([])
const selectedMovieIds = ref<number[]>([])
const removingMovieIds = ref<number[]>([])
const bulkRemoving = ref(false)
const posterLoadErrors = reactive<Record<string, boolean>>({})

const folderMoviesQuery = useGetFolderMovies<PageInfoMovieItemVO>(
  computed(() => activeFolderId.value ?? 0),
  { page: 1, size: 100 },
  {
    query: {
      enabled: false,
      retry: false
    }
  }
)
const createFavoriteFolderMutation = useCreateFavoriteFolder()
const updateFavoriteFolderMutation = useUpdateFavoriteFolder()
const deleteFavoriteFoldersMutation = useDeleteFavoriteFolders()
const removeFavoriteMutation = useRemoveFavorite()

const activeFolder = computed(() => {
  return props.folders.find((folder) => folder.id === activeFolderId.value) ?? null
})
const sortedFolders = computed(() => sortFavoriteFolders(props.folders))
const manageableFolderIds = computed(() => {
  return props.folders
    .filter((folder): folder is IdentifiedFavoriteFolder => canDeleteFolder(folder))
    .map((folder) => folder.id)
})
const selectedFolderCount = computed(() => selectedFolderIds.value.length)
const allManageableSelected = computed(() => {
  return manageableFolderIds.value.length > 0 && selectedFolderCount.value === manageableFolderIds.value.length
})
const partiallySelectedFolders = computed(() => {
  return selectedFolderCount.value > 0 && !allManageableSelected.value
})
const deletingSelectedFolderCount = computed(() => {
  return selectedFolderIds.value.filter((folderId) => deletingFolderIds.value.includes(folderId)).length
})
const bulkDeletingFolders = computed(() => {
  return selectedFolderCount.value > 0 && deletingSelectedFolderCount.value === selectedFolderCount.value
})
const selectedFoldersForDeletion = computed(() => {
  const selectedFolderIdSet = new Set(selectedFolderIds.value)

  return sortedFolders.value.filter(
    (folder): folder is IdentifiedFavoriteFolder => canDeleteFolder(folder) && selectedFolderIdSet.has(folder.id)
  )
})

const folderMoviesPage = computed<PageResult<MovieItemVO>>(() => {
  return normalizePage(folderMoviesQuery.data.value)
})

const folderMovies = computed(() => folderMoviesPage.value.list)
const folderMoviesTotal = computed(() => folderMoviesPage.value.total)
const folderMoviesLoading = computed(() => {
  return folderMoviesQuery.isLoading.value || folderMoviesQuery.isFetching.value
})
const selectedMovieCount = computed(() => selectedMovieIds.value.length)
const allVisibleMovieIds = computed(() => {
  return folderMovies.value
    .map((movie) => movie.movieId)
    .filter((movieId): movieId is number => typeof movieId === 'number')
})
const allVisibleSelected = computed(() => {
  return allVisibleMovieIds.value.length > 0 && selectedMovieCount.value === allVisibleMovieIds.value.length
})
const partiallySelected = computed(() => {
  return selectedMovieCount.value > 0 && !allVisibleSelected.value
})
const isFolderMoviesTruncated = computed(() => {
  return folderMoviesTotal.value > folderMovies.value.length && folderMovies.value.length > 0
})
const canManageActiveFolder = computed(() => canManageFolder(activeFolder.value))
const canDeleteActiveFolder = computed(() => canDeleteFolder(activeFolder.value))
const canEditActiveFolder = computed(() => canEditFolder(activeFolder.value))

function canManageFolder(folder?: FavoriteFolderVO | null): folder is IdentifiedFavoriteFolder {
  return Boolean(folder && typeof folder.id === 'number' && folder.id > 0)
}

function canDeleteFolder(folder?: FavoriteFolderVO | null): folder is IdentifiedFavoriteFolder {
  return Boolean(folder && !isDefaultFavoriteFolder(folder) && typeof folder.id === 'number' && folder.id > 0)
}

function canEditFolder(folder?: FavoriteFolderVO | null): folder is IdentifiedFavoriteFolder {
  return Boolean(folder && typeof folder.id === 'number' && folder.id > 0)
}

function canViewFolderDetail(folder?: FavoriteFolderVO | null): folder is IdentifiedFavoriteFolder {
  return Boolean(folder && typeof folder.id === 'number' && folder.id > 0)
}

function normalizePage(value: unknown): PageResult<MovieItemVO> {
  if (!value || typeof value !== 'object') {
    return { list: [], total: 0 }
  }

  const record = value as PageInfoMovieItemVO
  const list = Array.isArray(record.list) ? record.list : []
  const total = typeof record.total === 'number' ? record.total : list.length

  return { list, total }
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

async function refetchOrThrow<T>(query: { refetch: () => Promise<{ data?: T; error?: unknown }> }) {
  const result = await query.refetch()
  if (result.error) {
    throw result.error
  }
  return result.data
}

function getPoster(movie: MovieItemVO) {
  return resolveAssetUrl(movie.cover)
}

function getPosterStateKey(movie: MovieItemVO) {
  const posterUrl = getPoster(movie) || 'empty'
  const identity =
    movie.movieId && movie.movieId > 0
      ? String(movie.movieId)
      : `${movie.movieName || 'unknown'}-${movie.year ?? 'unknown'}`

  return `${identity}::${posterUrl}`
}

function hasPoster(movie: MovieItemVO) {
  return Boolean(getPoster(movie)) && !posterLoadErrors[getPosterStateKey(movie)]
}

function handlePosterError(movie: MovieItemVO) {
  posterLoadErrors[getPosterStateKey(movie)] = true
}

function getGenres(movie: MovieItemVO) {
  return splitCsvLike(movie.genres).slice(0, 3)
}

function openMovie(movieId?: number) {
  if (!movieId) {
    return
  }

  void router.push(`/movie/${movieId}`)
}

function openCreateFolder() {
  formMode.value = 'create'
  editingFolder.value = null
  showFormModal.value = true
}

function openEditFolder(folder: FavoriteFolderVO) {
  if (!canEditFolder(folder)) {
    message.info('当前收藏夹暂不支持编辑。')
    return
  }

  formMode.value = 'edit'
  editingFolder.value = folder
  showFormModal.value = true
}

async function handleFolderSubmit(payload: FavoriteFolderDTO) {
  const name = payload.name.trim()
  if (!name) {
    message.warning('请先填写收藏夹名称')
    return
  }

  const nextPayload: FavoriteFolderDTO = {
    name,
    description: payload.description?.trim() || undefined,
    isPublic: payload.isPublic ?? 0
  }

  savingFolder.value = true

  try {
    if (formMode.value === 'create') {
      await createFavoriteFolderMutation.mutateAsync({ data: nextPayload })
      message.success('收藏夹已创建')
    } else {
      const folderId = editingFolder.value?.id
      if (!folderId || folderId <= 0) {
        message.error('当前收藏夹不可编辑')
        return
      }

      await updateFavoriteFolderMutation.mutateAsync({
        folderId,
        data: nextPayload
      })
      message.success('收藏夹信息已更新')
    }

    showFormModal.value = false
    emit('refresh')
  } catch (error) {
    console.error('Failed to save favorite folder:', error)
    message.error(extractErrorMessage(error) || '收藏夹保存失败，请稍后再试')
  } finally {
    savingFolder.value = false
  }
}

async function loadActiveFolderMovies() {
  if (!activeFolderId.value || activeFolderId.value <= 0) {
    return
  }

  try {
    await refetchOrThrow(folderMoviesQuery)
  } catch (error) {
    console.error('Failed to load folder movies:', error)
    message.error(extractErrorMessage(error) || '收藏夹内容加载失败，请稍后再试')
  }
}

function openFolderDetail(folder: FavoriteFolderVO) {
  if (!canViewFolderDetail(folder)) {
    return
  }

  activeFolderId.value = folder.id ?? null
  selectedMovieIds.value = []
  showDetailModal.value = true
  void loadActiveFolderMovies()
}

function closeDetailModal() {
  showDetailModal.value = false
  activeFolderId.value = null
  selectedMovieIds.value = []
  removingMovieIds.value = []
}

function isDeletingFolder(folderId?: number | null) {
  return Boolean(folderId && deletingFolderIds.value.includes(folderId))
}

function isFolderSelected(folderId?: number | null) {
  return Boolean(folderId && selectedFolderIds.value.includes(folderId))
}

function handleToggleAllFolders(checked: boolean) {
  selectedFolderIds.value = checked ? [...manageableFolderIds.value] : []
}

function handleToggleFolder(folderId: number, checked: boolean) {
  if (checked) {
    if (!selectedFolderIds.value.includes(folderId)) {
      selectedFolderIds.value = [...selectedFolderIds.value, folderId]
    }
    return
  }

  selectedFolderIds.value = selectedFolderIds.value.filter((id) => id !== folderId)
}

async function handleDeleteFolders(folderIds: number[]) {
  const uniqueFolderIds = Array.from(
    new Set(folderIds.filter((folderId) => Number.isInteger(folderId) && folderId > 0))
  )
  if (!uniqueFolderIds.length) {
    return
  }

  deletingFolderIds.value = Array.from(new Set([...deletingFolderIds.value, ...uniqueFolderIds]))

  try {
    await deleteFavoriteFoldersMutation.mutateAsync({
      data: {
        ids: uniqueFolderIds
      }
    })
    message.success(uniqueFolderIds.length > 1 ? `已删除 ${uniqueFolderIds.length} 个收藏夹` : '收藏夹已删除')

    if (activeFolderId.value && uniqueFolderIds.includes(activeFolderId.value)) {
      closeDetailModal()
    }

    if (editingFolder.value?.id && uniqueFolderIds.includes(editingFolder.value.id)) {
      showFormModal.value = false
      editingFolder.value = null
    }

    selectedFolderIds.value = selectedFolderIds.value.filter((folderId) => !uniqueFolderIds.includes(folderId))
    emit('refresh')
  } catch (error) {
    console.error('Failed to delete favorite folder:', error)
    message.error(extractErrorMessage(error) || '删除收藏夹失败，请稍后再试')
  } finally {
    deletingFolderIds.value = deletingFolderIds.value.filter((folderId) => !uniqueFolderIds.includes(folderId))
  }
}

function confirmDeleteFolder(folder: FavoriteFolderVO) {
  if (!canDeleteFolder(folder)) {
    message.info(isDefaultFavoriteFolder(folder) ? '默认收藏夹不可删除' : '当前收藏夹暂不支持删除')
    return
  }
  const folderName = folder.name || '未命名收藏夹'

  dialog.warning({
    title: '删除收藏夹',
    content: `删除“${folderName}”后，夹内收藏记录也会一起移除。此操作不可撤销。`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      await handleDeleteFolders([folder.id])
    }
  })
}

function confirmDeleteSelectedFolders() {
  if (!selectedFolderIds.value.length) {
    message.info('请先选择要删除的收藏夹')
    return
  }

  const previewNames = selectedFoldersForDeletion.value
    .slice(0, 3)
    .map((folder) => `“${folder.name || '未命名收藏夹'}”`)
    .join('、')
  const contentPrefix =
    selectedFolderCount.value > 3
      ? `${previewNames} 等 ${selectedFolderCount.value} 个收藏夹`
      : previewNames || `${selectedFolderCount.value} 个收藏夹`

  dialog.warning({
    title: '批量删除收藏夹',
    content: `删除${contentPrefix}后，夹内收藏记录也会一起移除。此操作不可撤销。`,
    positiveText: '删除所选',
    negativeText: '取消',
    onPositiveClick: async () => {
      await handleDeleteFolders(selectedFolderIds.value)
    }
  })
}

function handleToggleAllMovies(checked: boolean) {
  selectedMovieIds.value = checked ? [...allVisibleMovieIds.value] : []
}

function handleToggleMovie(movieId: number, checked: boolean) {
  if (checked) {
    if (!selectedMovieIds.value.includes(movieId)) {
      selectedMovieIds.value = [...selectedMovieIds.value, movieId]
    }
    return
  }

  selectedMovieIds.value = selectedMovieIds.value.filter((id) => id !== movieId)
}

function isMovieRemoving(movieId?: number | null) {
  return Boolean(movieId && removingMovieIds.value.includes(movieId))
}

async function removeMoviesFromFolder(movieIds: number[]) {
  if (!movieIds.length || !activeFolderId.value || activeFolderId.value <= 0) {
    return
  }

  const folderId = activeFolderId.value
  const uniqueMovieIds = Array.from(new Set(movieIds))

  removingMovieIds.value = Array.from(new Set([...removingMovieIds.value, ...uniqueMovieIds]))
  bulkRemoving.value = uniqueMovieIds.length > 1

  try {
    const results = await Promise.allSettled(
      uniqueMovieIds.map((movieId) =>
        removeFavoriteMutation.mutateAsync({
          movieId,
          params: { folderId }
        })
      )
    )

    const failures = results.filter(
      (result): result is PromiseRejectedResult => result.status === 'rejected'
    )
    const firstFailure = failures[0]

    if (failures.length === uniqueMovieIds.length && firstFailure) {
      throw firstFailure.reason
    }

    if (failures.length > 0) {
      message.warning(`已移除 ${uniqueMovieIds.length - failures.length} 部电影，${failures.length} 部处理失败。`)
    } else {
      message.success(
        uniqueMovieIds.length > 1
          ? `已从当前收藏夹移除 ${uniqueMovieIds.length} 部电影`
          : '电影已从当前收藏夹移除'
      )
    }

    selectedMovieIds.value = selectedMovieIds.value.filter((movieId) => !uniqueMovieIds.includes(movieId))
    await loadActiveFolderMovies()
    emit('refresh')
  } catch (error) {
    console.error('Failed to remove movies from favorite folder:', error)
    message.error(extractErrorMessage(error) || '从收藏夹移除电影失败，请稍后再试')
  } finally {
    removingMovieIds.value = removingMovieIds.value.filter((movieId) => !uniqueMovieIds.includes(movieId))
    bulkRemoving.value = false
  }
}

function handleRemoveSelectedMovies() {
  if (!selectedMovieIds.value.length) {
    return
  }

  void removeMoviesFromFolder(selectedMovieIds.value)
}

function handleRemoveMovie(movieId?: number) {
  if (!movieId) {
    return
  }

  void removeMoviesFromFolder([movieId])
}

function openEditingFolderMovieManager() {
  if (!canManageFolder(editingFolder.value)) {
    message.info('当前收藏夹暂不支持管理内容')
    return
  }

  showFormModal.value = false
  openFolderDetail(editingFolder.value)
}

function handleDeleteEditingFolder() {
  if (!canDeleteFolder(editingFolder.value)) {
    message.info(isDefaultFavoriteFolder(editingFolder.value) ? '默认收藏夹不可删除' : '当前收藏夹暂不支持删除')
    return
  }

  confirmDeleteFolder(editingFolder.value)
}

watch(
  () => props.folders,
  (nextFolders) => {
    if (!activeFolderId.value) {
      return
    }

    const matchedFolder = nextFolders.find((folder) => folder.id === activeFolderId.value)
    if (!matchedFolder) {
      closeDetailModal()
    }
  },
  { deep: true }
)

watch(
  manageableFolderIds,
  (nextFolderIds) => {
    const validFolderIds = new Set(nextFolderIds)
    selectedFolderIds.value = selectedFolderIds.value.filter((folderId) => validFolderIds.has(folderId))
  },
  { immediate: true }
)

watch(
  folderMovies,
  (nextMovies) => {
    const nextMovieIds = new Set(
      nextMovies
        .map((movie) => movie.movieId)
        .filter((movieId): movieId is number => typeof movieId === 'number')
    )

    selectedMovieIds.value = selectedMovieIds.value.filter((movieId) => nextMovieIds.has(movieId))
  },
  { deep: true }
)

watch(
  folderMovies,
  (nextMovies) => {
    const activePosterKeys = new Set(nextMovies.map((movie) => getPosterStateKey(movie)))

    for (const key of Object.keys(posterLoadErrors)) {
      if (!activePosterKeys.has(key)) {
        delete posterLoadErrors[key]
      }
    }
  },
  { deep: true, immediate: true }
)
</script>

<template>
  <div class="space-y-4">
    <div class="flex flex-wrap items-center justify-between gap-3">
      <div>
        <h3 class="text-lg font-display font-semibold text-slate-950">收藏夹编排</h3>
        <p class="text-sm text-slate-500">整理你的主题片单，并在这里维护自定义收藏夹。</p>
      </div>

      <div class="flex flex-wrap items-center gap-2">
        <n-button type="primary" secondary class="rounded-full px-5" @click="openCreateFolder">
          新建收藏夹
        </n-button>
        <div class="rounded-full bg-amber-50 px-3 py-1 text-sm font-medium text-amber-700">
          共 {{ total }} 个
        </div>
      </div>
    </div>

    <div v-if="loading" class="grid gap-4 md:grid-cols-2">
      <div
        v-for="index in 4"
        :key="index"
        class="h-40 animate-pulse rounded-[26px] bg-slate-200/70"
      />
    </div>

    <n-empty
      v-else-if="folders.length === 0"
      description="还没有收藏夹，先从收藏几部电影开始。"
      class="rounded-[28px] border border-dashed border-slate-200 bg-white/70 py-14"
    />

    <template v-else>
      <section
        v-if="manageableFolderIds.length > 0"
        class="flex flex-wrap items-center justify-between gap-3 rounded-[24px] border border-slate-200 bg-slate-50 px-4 py-4"
      >
        <div class="flex flex-wrap items-center gap-3">
          <n-checkbox
            :checked="allManageableSelected"
            :indeterminate="partiallySelectedFolders"
            :disabled="deletingFolderIds.length > 0"
            @update:checked="handleToggleAllFolders"
          >
            全选可删除收藏夹
          </n-checkbox>

          <span class="text-sm text-slate-500">
            已选 {{ selectedFolderCount }} 个，系统默认收藏夹不会参与批量删除。
          </span>
        </div>

        <div class="flex flex-wrap items-center gap-2">
          <n-button
            type="error"
            class="rounded-full"
            :disabled="selectedFolderCount === 0 || deletingFolderIds.length > 0"
            :loading="bulkDeletingFolders"
            @click="confirmDeleteSelectedFolders"
          >
            删除所选
          </n-button>
        </div>
      </section>

      <div class="grid gap-4 md:grid-cols-2">
        <article
          v-for="folder in sortedFolders"
          :key="folder.id || folder.name"
          class="rounded-[28px] border border-slate-200 bg-white p-5 shadow-[0_18px_45px_rgba(148,163,184,0.15)]"
        >
          <div class="mb-4 flex items-start justify-between gap-4">
            <div class="flex min-w-0 flex-1 items-start gap-3">
              <div v-if="canDeleteFolder(folder)" class="pt-1">
                <n-checkbox
                  :checked="isFolderSelected(folder.id)"
                  :disabled="isDeletingFolder(folder.id)"
                  @update:checked="handleToggleFolder(folder.id, $event)"
                />
              </div>

              <div class="min-w-0 space-y-2">
                <div class="flex flex-wrap items-center gap-2">
                  <h4 class="text-lg font-semibold text-slate-950">{{ folder.name || '未命名收藏夹' }}</h4>
                  <n-tag v-if="isDefaultFavoriteFolder(folder)" size="small" round type="info">
                    系统默认
                  </n-tag>
                </div>
                <p class="text-sm leading-6 text-slate-600">
                  {{ folder.description || '还没有填写这个收藏夹的说明。' }}
                </p>
              </div>
            </div>

            <n-tag :type="folder.isPublic === 1 ? 'warning' : 'default'" round>
              {{ folder.isPublic === 1 ? '公开' : '私密' }}
            </n-tag>
          </div>

          <div class="flex flex-wrap gap-3 text-sm text-slate-500">
            <span class="rounded-full bg-slate-100 px-3 py-1">
              {{ folder.movieCount || 0 }} 部电影
            </span>
            <span class="rounded-full bg-slate-100 px-3 py-1">
              创建于 {{ formatDateLabel(folder.createTime) }}
            </span>
            <span class="rounded-full bg-slate-100 px-3 py-1">
              更新于 {{ formatDateTimeLabel(folder.updateTime) }}
            </span>
          </div>

          <div class="mt-5 flex flex-wrap gap-2">
            <n-button
              size="small"
              secondary
              class="rounded-full"
              @click="openFolderDetail(folder)"
            >
              {{ canManageFolder(folder) ? '管理内容' : '查看片单' }}
            </n-button>
            <n-button
              v-if="canEditFolder(folder)"
              size="small"
              tertiary
              class="rounded-full"
              @click="openEditFolder(folder)"
            >
              编辑信息
            </n-button>
            <template v-if="canDeleteFolder(folder)">
              <n-button
                size="small"
                type="error"
                quaternary
                class="rounded-full"
                :loading="isDeletingFolder(folder.id)"
                @click="confirmDeleteFolder(folder)"
              >
                删除
              </n-button>
            </template>
            <span
              v-if="isDefaultFavoriteFolder(folder)"
              class="rounded-full bg-slate-100 px-3 py-1 text-xs font-medium text-slate-500"
            >
              名称由系统维护，不支持删除
            </span>
          </div>
        </article>
      </div>
    </template>
  </div>

  <ProfileFavoriteFolderFormModal
    v-model:show="showFormModal"
    :mode="formMode"
    :initial-folder="editingFolder"
    :saving="savingFolder"
    :deleting="isDeletingFolder(editingFolder?.id)"
    :allow-delete="canDeleteFolder(editingFolder)"
    :allow-bulk-remove-movies="canManageFolder(editingFolder)"
    @submit="handleFolderSubmit"
    @delete-folder="handleDeleteEditingFolder"
    @bulk-remove-movies="openEditingFolderMovieManager"
  />

  <n-modal
    v-model:show="showDetailModal"
    preset="card"
    title="收藏夹管理"
    :style="{ width: 'min(1120px, calc(100vw - 1.5rem))' }"
    @after-leave="closeDetailModal"
  >
    <div class="space-y-6">
      <div class="flex flex-wrap items-start justify-between gap-4">
        <div class="space-y-3">
          <div class="flex flex-wrap items-center gap-2">
            <h3 class="text-xl font-display font-semibold text-slate-950">
              {{ activeFolder?.name || '未命名收藏夹' }}
            </h3>
            <n-tag v-if="activeFolder" :type="activeFolder.isPublic === 1 ? 'warning' : 'default'" round>
              {{ activeFolder.isPublic === 1 ? '公开' : '私密' }}
            </n-tag>
          </div>

          <p class="max-w-2xl text-sm leading-6 text-slate-600">
            {{ activeFolder?.description || '这个收藏夹还没有填写说明。' }}
          </p>

          <div class="flex flex-wrap gap-3 text-sm text-slate-500">
            <span class="rounded-full bg-slate-100 px-3 py-1">
              当前展示 {{ folderMovies.length }} / {{ folderMoviesTotal }} 部电影
            </span>
            <span class="rounded-full bg-slate-100 px-3 py-1">
              已选 {{ selectedMovieCount }} 部
            </span>
            <span class="rounded-full bg-slate-100 px-3 py-1">
              更新于 {{ formatDateTimeLabel(activeFolder?.updateTime) }}
            </span>
          </div>
        </div>

        <div v-if="activeFolder" class="flex flex-wrap gap-2">
          <n-button tertiary class="rounded-full" @click="loadActiveFolderMovies">
            刷新内容
          </n-button>
          <n-button
            v-if="canEditActiveFolder"
            secondary
            class="rounded-full"
            @click="openEditFolder(activeFolder)"
          >
            编辑信息
          </n-button>
          <template v-if="canDeleteActiveFolder">
            <n-button
              type="error"
              quaternary
              class="rounded-full"
              :loading="isDeletingFolder(activeFolder.id)"
              @click="confirmDeleteFolder(activeFolder)"
            >
              删除收藏夹
            </n-button>
          </template>
        </div>
      </div>

      <section
        class="flex flex-wrap items-center justify-between gap-3 rounded-[24px] border border-slate-200 bg-slate-50 px-4 py-4"
      >
        <template v-if="canManageActiveFolder">
          <div class="flex flex-wrap items-center gap-3">
            <n-checkbox
              :checked="allVisibleSelected"
              :indeterminate="partiallySelected"
              :disabled="folderMoviesLoading || allVisibleMovieIds.length === 0 || bulkRemoving"
              @update:checked="handleToggleAllMovies"
            >
              全选当前列表
            </n-checkbox>

            <span class="text-sm text-slate-500">
              这里只会从当前收藏夹移除，不影响其它片单中的同名电影。
            </span>
          </div>

          <div class="flex flex-wrap gap-2">
            <n-button
              type="error"
              class="rounded-full"
              :disabled="selectedMovieCount === 0 || folderMoviesLoading"
              :loading="bulkRemoving"
              @click="handleRemoveSelectedMovies"
            >
              移除所选电影
            </n-button>
          </div>
        </template>

      </section>

      <section v-if="isFolderMoviesTruncated" class="rounded-[20px] bg-amber-50 px-4 py-3 text-sm text-amber-800">
        当前接口单次最多加载前 100 部电影用于管理；如果收藏夹更大，请先清理一部分后再继续整理。
      </section>

      <n-spin :show="folderMoviesLoading">
        <div v-if="!folderMoviesLoading && folderMovies.length === 0">
          <n-empty
            description="这个收藏夹里还没有电影。"
            class="rounded-[28px] border border-dashed border-slate-200 bg-white/70 py-14"
          />
        </div>

        <div v-else class="space-y-3">
          <article
            v-for="movie in folderMovies"
            :key="movie.movieId || movie.movieName"
            class="flex flex-col gap-4 rounded-[24px] border border-slate-200 bg-white p-4 shadow-[0_14px_36px_rgba(148,163,184,0.14)] md:flex-row"
          >
            <div class="flex flex-1 gap-4">
              <div class="pt-1">
                <n-checkbox
                  v-if="movie.movieId && canManageActiveFolder"
                  :checked="selectedMovieIds.includes(movie.movieId)"
                  :disabled="isMovieRemoving(movie.movieId)"
                  @update:checked="handleToggleMovie(movie.movieId, $event)"
                />
              </div>

              <button
                type="button"
                class="h-32 w-24 shrink-0 overflow-hidden rounded-[18px] bg-slate-200 text-left"
                @click="openMovie(movie.movieId)"
              >
                <MoviePlaceholder
                  v-if="!hasPoster(movie)"
                  :title="movie.movieName"
                  class="h-full w-full"
                />
                <img
                  v-else
                  :src="getPoster(movie) || undefined"
                  :alt="movie.movieName"
                  class="h-full w-full object-cover"
                  loading="lazy"
                  @error="handlePosterError(movie)"
                />
              </button>

              <div class="min-w-0 flex-1 space-y-3">
                <div class="flex flex-wrap items-start justify-between gap-3">
                  <div class="min-w-0">
                    <button
                      type="button"
                      class="text-left text-lg font-semibold text-slate-950 transition hover:text-amber-700"
                      @click="openMovie(movie.movieId)"
                    >
                      {{ movie.movieName || '未命名电影' }}
                    </button>

                    <div class="mt-2 flex flex-wrap gap-2 text-xs text-slate-500">
                      <span v-if="movie.year" class="rounded-full bg-slate-100 px-2.5 py-1">
                        {{ movie.year }}
                      </span>
                      <span
                        v-for="genre in getGenres(movie)"
                        :key="genre"
                        class="rounded-full bg-slate-100 px-2.5 py-1"
                      >
                        {{ genre }}
                      </span>
                    </div>
                  </div>

                  <span class="rounded-full bg-amber-50 px-3 py-1 text-sm font-semibold text-amber-700">
                    {{ typeof movie.score === 'number' ? movie.score.toFixed(1) : '--' }}
                  </span>
                </div>

                <p class="text-sm leading-6 text-slate-600">
                  {{ truncateText(movie.storyline, 120) }}
                </p>

                <div class="flex flex-wrap gap-3 text-sm text-slate-500">
                  <span class="rounded-full bg-slate-100 px-3 py-1">
                    收藏于 {{ formatDateLabel(movie.favoriteTime) }}
                  </span>
                </div>
              </div>
            </div>

            <div class="flex items-start justify-end">
              <n-button
                v-if="canManageActiveFolder"
                type="error"
                quaternary
                class="rounded-full"
                :loading="isMovieRemoving(movie.movieId)"
                :disabled="!movie.movieId"
                @click="handleRemoveMovie(movie.movieId)"
              >
                移出片单
              </n-button>
            </div>
          </article>
        </div>
      </n-spin>
    </div>
  </n-modal>
</template>
