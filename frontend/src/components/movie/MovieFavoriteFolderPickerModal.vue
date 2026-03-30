<script setup lang="ts">
import { computed, watch } from 'vue'
import { NButton, NCheckbox, NEmpty, NModal, NSpin, NTag } from 'naive-ui'
import type { FavoriteFolderVO } from '@/api/model'
import { isDefaultFavoriteFolder, sortFavoriteFolders } from '@/utils/favorite-folder'

const show = defineModel<boolean>('show', { required: true })
const selectedFolderIds = defineModel<number[]>('selectedFolderIds', { required: true })

const props = withDefaults(
  defineProps<{
    folders: FavoriteFolderVO[]
    initialFolderIds?: number[]
    loading?: boolean
    submitting?: boolean
  }>(),
  {
    initialFolderIds: () => [],
    loading: false,
    submitting: false
  }
)

const emit = defineEmits<{
  submit: [folderIds: number[]]
  createFolder: []
}>()

const modalStyle = computed(() => ({
  width: 'min(780px, calc(100vw - 1.5rem))'
}))

const selectableFolders = computed(() => {
  return sortFavoriteFolders(props.folders).filter(
    (folder): folder is FavoriteFolderVO & { id: number } =>
      typeof folder.id === 'number' && folder.id > 0
  )
})
const hasFolders = computed(() => selectableFolders.value.length > 0)
const hasPendingSubmission = computed(() => props.submitting)
const selectedCount = computed(() => selectedFolderIds.value.length)
const initialSelectedFolderIds = computed(() => {
  return Array.from(
    new Set(
      (props.initialFolderIds ?? []).filter(
        (folderId): folderId is number => typeof folderId === 'number' && folderId > 0
      )
    )
  )
})
const initialFolderIdSet = computed(() => new Set(initialSelectedFolderIds.value))
const initialSelectedCount = computed(() => initialSelectedFolderIds.value.length)
const hasChanges = computed(() => {
  if (selectedFolderIds.value.length !== initialSelectedFolderIds.value.length) {
    return true
  }

  const selectedFolderIdSet = new Set(selectedFolderIds.value)
  return initialSelectedFolderIds.value.some((folderId) => !selectedFolderIdSet.has(folderId))
})
const canSubmit = computed(() => hasChanges.value && !hasPendingSubmission.value)

function getVisibilityLabel(folder: FavoriteFolderVO) {
  if (isDefaultFavoriteFolder(folder)) {
    return '系统默认'
  }

  return folder.isPublic === 1 ? '公开' : '私密'
}

function getMovieCountLabel(folder: FavoriteFolderVO) {
  const count = typeof folder.movieCount === 'number' ? folder.movieCount : 0
  return `${count} 部电影`
}

function isSelected(folderId: number) {
  return selectedFolderIds.value.includes(folderId)
}

function isInitiallySelected(folderId: number) {
  return initialFolderIdSet.value.has(folderId)
}

function willAdd(folderId: number) {
  return isSelected(folderId) && !isInitiallySelected(folderId)
}

function willRemove(folderId: number) {
  return !isSelected(folderId) && isInitiallySelected(folderId)
}

function handleFolderSelection(folderId: number, checked: boolean) {
  if (hasPendingSubmission.value) {
    return
  }

  if (checked) {
    if (!selectedFolderIds.value.includes(folderId)) {
      selectedFolderIds.value = [...selectedFolderIds.value, folderId]
    }
    return
  }

  selectedFolderIds.value = selectedFolderIds.value.filter((id) => id !== folderId)
}

function handleSubmit() {
  if (!canSubmit.value) {
    return
  }

  emit('submit', [...selectedFolderIds.value])
}

watch(
  selectableFolders,
  (nextFolders) => {
    const validFolderIds = new Set(nextFolders.map((folder) => folder.id))
    selectedFolderIds.value = selectedFolderIds.value.filter((folderId) => validFolderIds.has(folderId))
  },
  { immediate: true }
)

watch(
  () => show.value,
  (visible) => {
    if (!visible) {
      selectedFolderIds.value = []
    }
  }
)
</script>

<template>
  <n-modal
    v-model:show="show"
    preset="card"
    title="管理收藏夹"
    class="movie-favorite-folder-picker-modal"
    :style="modalStyle"
  >
    <div class="picker-layout">
      <section class="picker-intro">
        <div>
          <h3 class="picker-title">勾选想保留这部电影的收藏夹</h3>
          <p class="picker-description">
            当前已在 {{ initialSelectedCount }} 个收藏夹中。勾选表示提交后保留在该收藏夹，取消勾选会移除电影，包括默认收藏夹。
          </p>
        </div>

        <n-button
          tertiary
          class="rounded-full px-5"
          :disabled="loading || hasPendingSubmission"
          @click="emit('createFolder')"
        >
          新建收藏夹
        </n-button>
      </section>

      <div v-if="loading" class="picker-loading">
        <n-spin size="large" />
      </div>

      <n-empty
        v-else-if="!hasFolders"
        description="暂无可用收藏夹，先新建一个再继续。"
        class="picker-empty"
      />

      <div v-else class="picker-list">
        <article
          v-for="folder in selectableFolders"
          :key="folder.id"
          class="picker-card"
          :class="{ 'picker-card-selected': isSelected(folder.id) }"
        >
          <div class="picker-card-copy">
            <div class="picker-card-header">
              <div class="picker-card-main">
                <n-checkbox
                  :checked="isSelected(folder.id)"
                  :disabled="hasPendingSubmission"
                  @update:checked="handleFolderSelection(folder.id, $event)"
                >
                  <span class="picker-card-title">{{ folder.name || '未命名收藏夹' }}</span>
                </n-checkbox>
                <p class="picker-card-meta">{{ getMovieCountLabel(folder) }}</p>
              </div>

              <div class="picker-card-tags">
                <n-tag v-if="isInitiallySelected(folder.id)" size="small" type="success">
                  当前已收藏
                </n-tag>
                <n-tag v-if="willAdd(folder.id)" size="small" type="info">
                  将添加
                </n-tag>
                <n-tag v-if="willRemove(folder.id)" size="small" type="warning">
                  将移除
                </n-tag>
                <n-tag size="small" :type="isDefaultFavoriteFolder(folder) ? 'warning' : 'default'">
                  {{ getVisibilityLabel(folder) }}
                </n-tag>
              </div>
            </div>

            <p class="picker-card-description">
              {{ folder.description || '还没有填写这个收藏夹的说明。' }}
            </p>
          </div>
        </article>
      </div>

      <section v-if="hasFolders" class="picker-actions">
        <p class="picker-selection-summary">
          当前 {{ initialSelectedCount }} 个，已选 {{ selectedCount }} 个收藏夹
        </p>

        <n-button
          type="primary"
          class="rounded-full px-6"
          :loading="submitting"
          :disabled="!canSubmit"
          @click="handleSubmit"
        >
          保存收藏夹变更
        </n-button>
      </section>
    </div>
  </n-modal>
</template>

<style scoped>
.picker-layout {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.picker-intro {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
  padding: 1rem 1.1rem;
  border-radius: 1.25rem;
  background: linear-gradient(135deg, rgba(248, 250, 252, 0.96), rgba(241, 245, 249, 0.92));
}

.picker-title {
  margin: 0;
  font-size: 1rem;
  font-weight: 700;
  color: #0f172a;
}

.picker-description {
  margin: 0.45rem 0 0;
  max-width: 34rem;
  font-size: 0.92rem;
  line-height: 1.7;
  color: #64748b;
}

.picker-loading,
.picker-empty {
  min-height: 16rem;
  display: flex;
  align-items: center;
  justify-content: center;
}

.picker-list {
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
}

.picker-card {
  display: flex;
  align-items: flex-start;
  padding: 1rem 1.1rem;
  border: 1px solid rgba(226, 232, 240, 0.95);
  border-radius: 1.25rem;
  background: #ffffff;
  transition: border-color 0.2s ease, box-shadow 0.2s ease, background-color 0.2s ease;
}

.picker-card-selected {
  border-color: rgba(245, 158, 11, 0.6);
  background: linear-gradient(135deg, rgba(255, 251, 235, 0.92), rgba(255, 255, 255, 1));
  box-shadow: 0 18px 42px rgba(245, 158, 11, 0.12);
}

.picker-card-copy {
  min-width: 0;
  flex: 1;
}

.picker-card-header {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 0.75rem;
}

.picker-card-main {
  min-width: 0;
}

.picker-card-title {
  font-size: 1rem;
  font-weight: 700;
  color: #0f172a;
}

.picker-card-meta {
  margin: 0.3rem 0 0;
  font-size: 0.86rem;
  color: #64748b;
}

.picker-card-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 0.45rem;
}

.picker-card-description {
  margin: 0.75rem 0 0;
  font-size: 0.92rem;
  line-height: 1.7;
  color: #475569;
}

.picker-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  padding: 0.9rem 1.1rem;
  border-radius: 1.25rem;
  background: #f8fafc;
}

.picker-selection-summary {
  margin: 0;
  font-size: 0.92rem;
  font-weight: 600;
  color: #475569;
}

@media (max-width: 640px) {
  .picker-actions {
    justify-content: flex-start;
  }
}
</style>
