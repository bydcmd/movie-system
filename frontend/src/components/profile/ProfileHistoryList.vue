<script setup lang="ts">
import { computed, ref, shallowRef, watch } from 'vue'
import { useRouter } from 'vue-router'
import { NButton, NCheckbox, NEmpty, NPagination, useDialog, useMessage } from 'naive-ui'
import {
  useClearHistory,
  useDeleteHistoryBatch
} from '@/api/endpoints/view-history-management/view-history-management'
import type { MovieItemVO } from '@/api/model'
import MoviePlaceholder from '@/components/movie/MoviePlaceholder.vue'
import { formatDateLabel, resolveAssetUrl, splitCsvLike, truncateText } from '@/utils/profile'
import { getMovieIdKey, normalizeMovieId, normalizeMovieIdList, type MovieId } from '@/utils/movie'

const props = withDefaults(defineProps<{
  items: MovieItemVO[]
  total: number
  loading?: boolean
  page?: number
  pageSize?: number
}>(), {
  loading: false,
  page: 1,
  pageSize: 10
})

const emit = defineEmits<{
  refresh: []
  'update:page': [page: number]
  'update:pageSize': [pageSize: number]
}>()

const router = useRouter()
const dialog = useDialog()
const message = useMessage()
const clearingAll = shallowRef(false)
const deletingSelected = shallowRef(false)
const selectedHistoryIds = ref<MovieId[]>([])

const failedPosterIds = ref<Set<string>>(new Set())

const clearHistoryMutation = useClearHistory()
const deleteHistoryBatchMutation = useDeleteHistoryBatch()

const selectableHistoryIds = computed(() => {
  return normalizeMovieIdList(props.items.map((item) => item.movieId))
})
const selectedCount = computed(() => selectedHistoryIds.value.length)
const allVisibleSelected = computed(() => {
  return selectableHistoryIds.value.length > 0 && selectedCount.value === selectableHistoryIds.value.length
})
const partiallySelected = computed(() => {
  return selectedCount.value > 0 && !allVisibleSelected.value
})
const isMutating = computed(() => clearingAll.value || deletingSelected.value)
const isPreviewTruncated = computed(() => {
  return props.total > props.items.length && props.items.length > 0
})

const showPagination = computed(() => props.total > props.pageSize)

function handlePageChange(newPage: number) {
  emit('update:page', newPage)
  emit('refresh')
}

function handlePageSizeChange(newPageSize: number) {
  emit('update:pageSize', newPageSize)
  emit('update:page', 1)
  emit('refresh')
}

watch(
  selectableHistoryIds,
  (nextIds) => {
    const nextIdSet = new Set(nextIds)
    selectedHistoryIds.value = selectedHistoryIds.value.filter((historyId) => nextIdSet.has(historyId))
  },
  { immediate: true }
)

function getPosterUrl(movie?: MovieItemVO | null): string | null {
  const url = resolveAssetUrl(movie?.cover)
  return url && url.trim() ? url : null
}

function handlePosterError(movieId?: MovieId) {
  const movieIdKey = getMovieIdKey(movieId)
  if (movieIdKey) {
    failedPosterIds.value.add(movieIdKey)
  }
}

function shouldShowPlaceholder(movie?: MovieItemVO | null): boolean {
  const movieIdKey = getMovieIdKey(movie?.movieId)
  return !getPosterUrl(movie) || (movieIdKey !== null && failedPosterIds.value.has(movieIdKey))
}

function getGenres(movie: MovieItemVO) {
  return splitCsvLike(movie.genres).slice(0, 3)
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

function openMovie(movieId?: MovieId) {
  const movieIdKey = getMovieIdKey(movieId)
  if (!movieIdKey) {
    return
  }

  void router.push(`/movie/${movieIdKey}`)
}

function handleToggleAllHistory(checked: boolean) {
  selectedHistoryIds.value = checked ? [...selectableHistoryIds.value] : []
}

function handleToggleHistory(historyId: MovieId, checked: boolean) {
  const historyIdKey = getMovieIdKey(historyId)
  const normalizedHistoryId = normalizeMovieId(historyId)
  if (!historyIdKey || !normalizedHistoryId) {
    return
  }

  if (checked) {
    if (!selectedHistoryIds.value.some((id) => getMovieIdKey(id) === historyIdKey)) {
      selectedHistoryIds.value = [...selectedHistoryIds.value, normalizedHistoryId]
    }
    return
  }

  selectedHistoryIds.value = selectedHistoryIds.value.filter((id) => getMovieIdKey(id) !== historyIdKey)
}

async function deleteHistory(historyIds: MovieId[]) {
  const uniqueIds = normalizeMovieIdList(historyIds)
  if (!uniqueIds.length) {
    return
  }

  deletingSelected.value = true

  try {
    await deleteHistoryBatchMutation.mutateAsync({
      data: {
        ids: uniqueIds as unknown as number[]
      }
    })
    const uniqueIdKeys = new Set(uniqueIds.map((id) => String(id)))
    selectedHistoryIds.value = selectedHistoryIds.value.filter((historyId) => !uniqueIdKeys.has(String(historyId)))
    message.success(uniqueIds.length > 1 ? `已删除 ${uniqueIds.length} 条浏览记录` : '浏览记录已删除')
    emit('refresh')
  } catch (error) {
    console.error('Failed to delete history in batch:', error)
    message.error(extractErrorMessage(error) || '删除浏览记录失败，请稍后再试')
  } finally {
    deletingSelected.value = false
  }
}

function confirmDeleteSelectedHistory() {
  if (!selectedCount.value) {
    return
  }

  const count = selectedCount.value
  const content = isPreviewTruncated.value
    ? `确认删除已选 ${count} 条浏览记录？当前页面只展示最近 ${props.items.length} / ${props.total} 条记录。`
    : `确认删除已选 ${count} 条浏览记录？此操作不可撤销。`

  dialog.warning({
    title: '删除所选记录',
    content,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      await deleteHistory(selectedHistoryIds.value)
    }
  })
}

function confirmClearAll() {
  if (!props.total) {
    return
  }

  dialog.warning({
    title: '清空全部浏览记录',
    content: `确认清空全部 ${props.total} 条浏览记录？此操作不可撤销。`,
    positiveText: '清空全部',
    negativeText: '取消',
    onPositiveClick: async () => {
      clearingAll.value = true

      try {
        await clearHistoryMutation.mutateAsync()
        message.success('浏览历史已清空')
        emit('refresh')
      } catch (error) {
        console.error('Failed to clear history:', error)
        message.error(extractErrorMessage(error) || '清空浏览记录失败，请稍后再试')
      } finally {
        clearingAll.value = false
      }
    }
  })
}
</script>

<template>
  <div class="profile-history-list">
    <div class="profile-history-header">
      <div>
        <h3 class="profile-history-title">浏览历史</h3>
        <p class="profile-history-description">查看你最近浏览过的电影，可清除全部记录。</p>
      </div>
      <div class="profile-history-total">
        共 {{ total }} 条
      </div>
    </div>

    <section
      v-if="!loading && total > 0"
      class="profile-history-toolbar"
    >
      <div class="profile-history-toolbar-main">
        <n-checkbox
          :checked="allVisibleSelected"
          :indeterminate="partiallySelected"
          :disabled="selectableHistoryIds.length === 0 || isMutating"
          @update:checked="handleToggleAllHistory"
        >
          全选当前列表
        </n-checkbox>

        <span class="profile-history-selection-count">
          已选 {{ selectedCount }} 条
        </span>
      </div>

      <div class="profile-history-toolbar-actions">
        <n-button
          type="error"
          secondary
          class="profile-history-action"
          :disabled="selectedCount === 0 || isMutating"
          :loading="deletingSelected"
          @click="confirmDeleteSelectedHistory"
        >
          删除所选
        </n-button>
        <n-button
          type="error"
          quaternary
          class="profile-history-action"
          :disabled="total === 0 || isMutating"
          :loading="clearingAll"
          @click="confirmClearAll"
        >
          清空全部
        </n-button>
      </div>
    </section>

    <div v-if="loading" class="profile-history-loading">
      <div
        v-for="index in 4"
        :key="index"
        class="profile-history-skeleton"
      />
    </div>

    <n-empty
      v-else-if="items.length === 0"
      description="还没有浏览记录，去看看电影吧。"
      class="profile-history-empty"
    />

    <div v-else class="profile-history-rows">
      <article
        v-for="item in items"
        :key="item.movieId"
        class="profile-history-row"
      >
        <div class="profile-history-select">
          <n-checkbox
            v-if="item.movieId"
            :checked="selectedHistoryIds.includes(item.movieId)"
            :disabled="isMutating"
            @update:checked="handleToggleHistory(item.movieId, $event)"
          />
        </div>

        <button
          type="button"
          class="profile-history-poster"
          @click="openMovie(item.movieId)"
        >
          <MoviePlaceholder
            v-if="shouldShowPlaceholder(item)"
            :title="item.movieName"
            size="small"
            class="profile-history-poster-image"
          />
          <img
            v-else
            :src="getPosterUrl(item) || undefined"
            :alt="item.movieName"
            class="profile-history-poster-image"
            loading="lazy"
            @error="handlePosterError(item.movieId ?? 0)"
          />
        </button>

        <div class="profile-history-content">
          <div class="profile-history-top">
            <div class="profile-history-heading">
              <button
                type="button"
                class="profile-history-movie"
                @click="openMovie(item.movieId)"
              >
                {{ item.movieName || '未命名电影' }}
              </button>

              <div class="profile-history-meta">
                <span v-if="item.year" class="profile-history-chip">
                  {{ item.year }}
                </span>
                <span
                  v-for="genre in getGenres(item)"
                  :key="genre"
                  class="profile-history-chip"
                >
                  {{ genre }}
                </span>
              </div>
            </div>

            <div class="profile-history-score">
              {{ typeof item.score === 'number' ? item.score.toFixed(1) : '--' }}
            </div>
          </div>

          <p class="profile-history-story">
            {{ truncateText(item.storyline, 92) }}
          </p>

          <div class="profile-history-foot">
            <span class="profile-history-time">
              浏览于 {{ formatDateLabel(item.favoriteTime) }}
            </span>
            <n-button
              text
              type="primary"
              size="small"
              class="profile-history-link"
              @click="openMovie(item.movieId)"
            >
              查看详情
            </n-button>
          </div>
        </div>
      </article>
    </div>

    <div v-if="showPagination && !loading" class="profile-history-pagination">
      <n-pagination
        :page="page"
        :page-size="pageSize"
        :item-count="total"
        :page-sizes="[10, 20, 50]"
        show-size-picker
        @update:page="handlePageChange"
        @update:page-size="handlePageSizeChange"
      />
    </div>
  </div>
</template>

<style scoped>
.profile-history-list {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.profile-history-header {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}

.profile-history-title {
  margin: 0;
  font-family: var(--font-display);
  font-size: 1.15rem;
  font-weight: 700;
  color: #0f172a;
}

.profile-history-description {
  margin: 0.35rem 0 0;
  font-size: 0.92rem;
  line-height: 1.6;
  color: #64748b;
}

.profile-history-total {
  font-size: 0.92rem;
  font-weight: 600;
  color: #475569;
}

.profile-history-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 0.9rem;
  border: 1px solid rgba(148, 163, 184, 0.24);
  border-radius: 1rem;
  background: rgba(248, 250, 252, 0.86);
  padding: 0.95rem 1rem;
}

.profile-history-toolbar-main {
  display: flex;
  flex: 1 1 24rem;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.75rem 1rem;
}

.profile-history-toolbar-hint {
  font-size: 0.88rem;
  line-height: 1.6;
  color: #64748b;
}

.profile-history-selection-count {
  border-radius: 999px;
  background: rgba(245, 158, 11, 0.14);
  padding: 0.28rem 0.72rem;
  font-size: 0.82rem;
  font-weight: 600;
  color: #b45309;
}

.profile-history-toolbar-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
}

.profile-history-action {
  border-radius: 999px;
}

.profile-history-loading {
  display: grid;
  gap: 0.75rem;
}

.profile-history-skeleton {
  height: 6rem;
  border-radius: 1rem;
  background: rgba(226, 232, 240, 0.72);
}

.profile-history-empty {
  border-radius: 1rem;
  border: 1px dashed rgba(148, 163, 184, 0.36);
  padding: 3.5rem 0;
}

.profile-history-rows {
  border-top: 1px solid rgba(148, 163, 184, 0.2);
}

.profile-history-row {
  display: flex;
  align-items: flex-start;
  gap: 1rem;
  border-bottom: 1px solid rgba(148, 163, 184, 0.2);
  padding: 1rem 0;
}

.profile-history-select {
  display: flex;
  padding-top: 0.4rem;
}

.profile-history-poster {
  position: relative;
  height: 8.5rem;
  width: 5.75rem;
  flex-shrink: 0;
  overflow: hidden;
  border-radius: 1rem;
  background: #e2e8f0;
  text-align: left;
}

.profile-history-poster-image {
  height: 100%;
  width: 100%;
  object-fit: cover;
}

.profile-history-content {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 0.75rem;
}

.profile-history-top {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 0.75rem;
}

.profile-history-heading {
  min-width: 0;
}

.profile-history-movie {
  color: #0f172a;
  font-size: 1.05rem;
  font-weight: 700;
  text-align: left;
  transition: color 0.2s ease;
}

.profile-history-movie:hover {
  color: #b45309;
}

.profile-history-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 0.45rem;
  margin-top: 0.45rem;
}

.profile-history-chip {
  font-size: 0.8rem;
  color: #475569;
}

.profile-history-score {
  font-family: var(--font-display);
  font-size: 1.1rem;
  font-weight: 700;
  color: #b45309;
}

.profile-history-story {
  margin: 0;
  font-size: 0.94rem;
  line-height: 1.75;
  color: #475569;
}

.profile-history-foot {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
}

.profile-history-time {
  font-size: 0.82rem;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #94a3b8;
}

.profile-history-link {
  font-size: 0.85rem;
}

.profile-history-pagination {
  display: flex;
  justify-content: center;
  padding-top: 1.5rem;
  border-top: 1px solid rgba(148, 163, 184, 0.2);
}

@media (max-width: 639px) {
  .profile-history-row {
    align-items: flex-start;
  }

  .profile-history-poster {
    height: 7.75rem;
    width: 5.25rem;
  }
}
</style>
