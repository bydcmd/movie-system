<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { NButton, NCheckbox, NEmpty, NRate, useDialog, useMessage } from 'naive-ui'
import {
  useClearMyRatings,
  useDeleteRatingsBatch
} from '@/api/endpoints/rating-management/rating-management'
import type { MyRatingVO } from '@/api/model'
import MoviePlaceholder from '@/components/movie/MoviePlaceholder.vue'
import { formatDateLabel, resolveAssetUrl, truncateText } from '@/utils/profile'

const props = withDefaults(defineProps<{
  items: MyRatingVO[]
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
const selectedRatingIds = ref<number[]>([])
const deletingSelected = ref(false)
const clearingAll = ref(false)

// Track which rating posters failed to load
const failedPosterIds = ref<Set<number>>(new Set())

const deleteRatingsBatchMutation = useDeleteRatingsBatch()
const clearMyRatingsMutation = useClearMyRatings()

const selectableRatingIds = computed(() => {
  return props.items
    .map((item) => getRatingId(item))
    .filter((ratingId): ratingId is number => typeof ratingId === 'number')
})
const selectedCount = computed(() => selectedRatingIds.value.length)
const allVisibleSelected = computed(() => {
  return selectableRatingIds.value.length > 0 && selectedCount.value === selectableRatingIds.value.length
})
const partiallySelected = computed(() => {
  return selectedCount.value > 0 && !allVisibleSelected.value
})
const isMutating = computed(() => deletingSelected.value || clearingAll.value)
const isPreviewTruncated = computed(() => {
  return props.total > props.items.length && props.items.length > 0
})

watch(
  selectableRatingIds,
  (nextIds) => {
    const nextIdSet = new Set(nextIds)
    selectedRatingIds.value = selectedRatingIds.value.filter((ratingId) => nextIdSet.has(ratingId))
  },
  { immediate: true }
)

function getRatingId(item?: MyRatingVO | null) {
  const candidate = item?.id ?? item?.movieId
  return typeof candidate === 'number' && candidate > 0 ? candidate : null
}

function getPosterUrl(item?: MyRatingVO | null): string | null {
  const url = resolveAssetUrl(item?.posterUrl)
  // Ensure we don't return empty strings or invalid URLs
  return url && url.trim() ? url : null
}

function handlePosterError(movieId: number) {
  if (movieId) {
    failedPosterIds.value.add(movieId)
  }
}

function shouldShowPlaceholder(item?: MyRatingVO | null): boolean {
  return !getPosterUrl(item) || failedPosterIds.value.has(item?.movieId ?? 0)
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

function openMovie(movieId?: number) {
  if (!movieId) {
    return
  }

  void router.push(`/movie/${movieId}`)
}

function handleToggleAllRatings(checked: boolean) {
  selectedRatingIds.value = checked ? [...selectableRatingIds.value] : []
}

function handleToggleRating(ratingId: number, checked: boolean) {
  if (checked) {
    if (!selectedRatingIds.value.includes(ratingId)) {
      selectedRatingIds.value = [...selectedRatingIds.value, ratingId]
    }
    return
  }

  selectedRatingIds.value = selectedRatingIds.value.filter((id) => id !== ratingId)
}

async function deleteRatings(ratingIds: number[]) {
  const uniqueIds = Array.from(new Set(ratingIds))
  if (!uniqueIds.length) {
    return
  }

  deletingSelected.value = true

  try {
    await deleteRatingsBatchMutation.mutateAsync({
      data: {
        ids: uniqueIds
      }
    })
    selectedRatingIds.value = selectedRatingIds.value.filter((ratingId) => !uniqueIds.includes(ratingId))
    message.success(uniqueIds.length > 1 ? `已删除 ${uniqueIds.length} 条评分记录` : '评分记录已删除')
    emit('refresh')
  } catch (error) {
    console.error('Failed to delete ratings in batch:', error)
    message.error(extractErrorMessage(error) || '删除评分记录失败，请稍后再试')
  } finally {
    deletingSelected.value = false
  }
}

function confirmDeleteSelectedRatings() {
  if (!selectedCount.value) {
    return
  }

  const count = selectedCount.value
  const content = isPreviewTruncated.value
    ? `确认删除已选 ${count} 条评分记录？当前页面只展示最近 ${props.items.length} / ${props.total} 条记录。`
    : `确认删除已选 ${count} 条评分记录？此操作不可撤销。`

  dialog.warning({
    title: '删除所选评分',
    content,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      await deleteRatings(selectedRatingIds.value)
    }
  })
}

function confirmClearRatings() {
  if (!props.total) {
    return
  }

  dialog.warning({
    title: '清空全部评分',
    content: `确认清空全部 ${props.total} 条评分记录？此操作不可撤销。`,
    positiveText: '清空全部',
    negativeText: '取消',
    onPositiveClick: async () => {
      clearingAll.value = true

      try {
        await clearMyRatingsMutation.mutateAsync()
        selectedRatingIds.value = []
        message.success('已清空全部评分记录')
        emit('refresh')
      } catch (error) {
        console.error('Failed to clear ratings:', error)
        message.error(extractErrorMessage(error) || '清空评分记录失败，请稍后再试')
      } finally {
        clearingAll.value = false
      }
    }
  })
}
</script>

<template>
  <div class="profile-rating-list">
    <div class="profile-rating-header">
      <div>
        <h3 class="profile-rating-title">评分记录</h3>
        <p class="profile-rating-description">回看自己的打分轨迹，能更清楚地看到口味偏向。</p>
      </div>
      <div class="profile-rating-total">
        共 {{ total }} 条
      </div>
    </div>

    <section
      v-if="!loading && total > 0"
      class="profile-rating-toolbar"
    >
      <div class="profile-rating-toolbar-main">
        <n-checkbox
          :checked="allVisibleSelected"
          :indeterminate="partiallySelected"
          :disabled="selectableRatingIds.length === 0 || isMutating"
          @update:checked="handleToggleAllRatings"
        >
          全选当前列表
        </n-checkbox>

        <span class="profile-rating-selection-count">
          已选 {{ selectedCount }} 条
        </span>

        <span class="profile-rating-toolbar-hint">
          <template v-if="isPreviewTruncated">
            当前展示最近 {{ items.length }} / {{ total }} 条，删除所选仅作用于当前可见记录。
          </template>
          <template v-else>
            删除所选仅影响勾选记录，清空会删除全部评分。
          </template>
        </span>
      </div>

      <div class="profile-rating-toolbar-actions">
        <n-button
          type="error"
          secondary
          class="profile-rating-action"
          :disabled="selectedCount === 0 || isMutating"
          :loading="deletingSelected"
          @click="confirmDeleteSelectedRatings"
        >
          删除所选
        </n-button>
        <n-button
          type="error"
          quaternary
          class="profile-rating-action"
          :disabled="total === 0 || isMutating"
          :loading="clearingAll"
          @click="confirmClearRatings"
        >
          清空全部
        </n-button>
      </div>
    </section>

    <div v-if="loading" class="profile-rating-loading">
      <div
        v-for="index in 4"
        :key="index"
        class="profile-rating-skeleton"
      />
    </div>

    <n-empty
      v-else-if="items.length === 0"
      description="还没有评分记录，先去给喜欢的电影打分吧。"
      class="profile-rating-empty"
    />

    <div v-else class="profile-rating-rows">
      <article
        v-for="item in items"
        :key="item.id || item.movieId"
        class="profile-rating-row"
      >
        <div class="profile-rating-select">
          <n-checkbox
            v-if="getRatingId(item)"
            :checked="selectedRatingIds.includes(getRatingId(item) as number)"
            :disabled="isMutating"
            @update:checked="handleToggleRating(getRatingId(item) as number, $event)"
          />
        </div>

        <button
          type="button"
          class="profile-rating-poster"
          @click="openMovie(item.movieId)"
        >
          <MoviePlaceholder
            v-if="shouldShowPlaceholder(item)"
            :title="item.movieName"
            size="small"
            class="profile-rating-poster-image"
          />
          <img
            v-else
            :src="getPosterUrl(item) || undefined"
            :alt="item.movieName"
            class="profile-rating-poster-image"
            loading="lazy"
            @error="handlePosterError(item.movieId ?? 0)"
          />
        </button>

        <div class="profile-rating-content">
          <div class="profile-rating-top">
            <div>
              <button
                type="button"
                class="profile-rating-movie"
                @click="openMovie(item.movieId)"
              >
                {{ item.movieName || '未命名电影' }}
              </button>
              <div class="profile-rating-meta">
                评分于 {{ formatDateLabel(item.ratingTime) }}
              </div>
            </div>
            <n-rate :value="item.rating || 0" readonly />
          </div>

          <p class="profile-rating-body">
            {{ truncateText(item.description, 120) }}
          </p>

          <n-button text type="primary" class="profile-rating-link" @click="openMovie(item.movieId)">
            查看影片详情
          </n-button>
        </div>
      </article>
    </div>
  </div>
</template>

<style scoped>
.profile-rating-list {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.profile-rating-header {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}

.profile-rating-title {
  margin: 0;
  font-family: var(--font-display);
  font-size: 1.15rem;
  font-weight: 700;
  color: #0f172a;
}

.profile-rating-description {
  margin: 0.35rem 0 0;
  font-size: 0.92rem;
  line-height: 1.6;
  color: #64748b;
}

.profile-rating-total {
  font-size: 0.92rem;
  font-weight: 600;
  color: #475569;
}

.profile-rating-toolbar {
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

.profile-rating-toolbar-main {
  display: flex;
  flex: 1 1 24rem;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.75rem 1rem;
}

.profile-rating-toolbar-hint {
  font-size: 0.88rem;
  line-height: 1.6;
  color: #64748b;
}

.profile-rating-selection-count {
  border-radius: 999px;
  background: rgba(245, 158, 11, 0.14);
  padding: 0.28rem 0.72rem;
  font-size: 0.82rem;
  font-weight: 600;
  color: #b45309;
}

.profile-rating-toolbar-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
}

.profile-rating-action {
  border-radius: 999px;
}

.profile-rating-loading {
  display: grid;
  gap: 0.75rem;
}

.profile-rating-skeleton {
  height: 6rem;
  border-radius: 1rem;
  background: rgba(226, 232, 240, 0.72);
}

.profile-rating-empty {
  border-radius: 1rem;
  border: 1px dashed rgba(148, 163, 184, 0.36);
  padding: 3.5rem 0;
}

.profile-rating-rows {
  border-top: 1px solid rgba(148, 163, 184, 0.2);
}

.profile-rating-row {
  display: flex;
  align-items: flex-start;
  gap: 1rem;
  border-bottom: 1px solid rgba(148, 163, 184, 0.2);
  padding: 1rem 0;
}

.profile-rating-select {
  display: flex;
  padding-top: 0.4rem;
}

.profile-rating-poster {
  height: 7.75rem;
  width: 5.25rem;
  flex-shrink: 0;
  overflow: hidden;
  border-radius: 1rem;
  background: #e2e8f0;
  text-align: left;
}

.profile-rating-poster-image {
  height: 100%;
  width: 100%;
  object-fit: cover;
}

.profile-rating-poster-fallback {
  display: flex;
  height: 100%;
  width: 100%;
  align-items: center;
  justify-content: center;
  background: #0f172a;
  color: #ffffff;
  font-size: 0.78rem;
  font-weight: 700;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.profile-rating-content {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 0.75rem;
}

.profile-rating-top {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
}

.profile-rating-movie {
  color: #0f172a;
  font-size: 1.05rem;
  font-weight: 700;
  text-align: left;
  transition: color 0.2s ease;
}

.profile-rating-movie:hover {
  color: #b45309;
}

.profile-rating-meta {
  margin-top: 0.35rem;
  font-size: 0.9rem;
  color: #64748b;
}

.profile-rating-body {
  margin: 0;
  font-size: 0.94rem;
  line-height: 1.75;
  color: #475569;
}

.profile-rating-link {
  align-self: flex-start;
}
</style>
