<script setup lang="ts">
import { computed } from 'vue'
import { NButton, NEmpty, NModal, NSpin, NTag } from 'naive-ui'
import type { FavoriteFolderVO } from '@/api/model'
import { isDefaultFavoriteFolder, sortFavoriteFolders } from '@/utils/favorite-folder'

const show = defineModel<boolean>('show', { required: true })

const props = withDefaults(
  defineProps<{
    folders: FavoriteFolderVO[]
    loading?: boolean
    submittingFolderId?: number | null
  }>(),
  {
    loading: false,
    submittingFolderId: null
  }
)

const emit = defineEmits<{
  selectFolder: [folder: FavoriteFolderVO]
  createFolder: []
}>()

const modalStyle = computed(() => ({
  width: 'min(780px, calc(100vw - 1.5rem))'
}))

const sortedFolders = computed(() => sortFavoriteFolders(props.folders))
const hasFolders = computed(() => sortedFolders.value.length > 0)
const hasPendingSubmission = computed(() => props.submittingFolderId !== null && props.submittingFolderId !== undefined)

function isSubmitting(folderId?: number) {
  return folderId !== undefined && folderId !== null && props.submittingFolderId === folderId
}

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
</script>

<template>
  <n-modal
    v-model:show="show"
    preset="card"
    title="添加到收藏夹"
    class="movie-favorite-folder-picker-modal"
    :style="modalStyle"
  >
    <div class="picker-layout">
      <section class="picker-intro">
        <div>
          <h3 class="picker-title">选择一个收藏夹来保存这部电影</h3>
          <p class="picker-description">
            直接添加会进入默认收藏夹；如果你想按主题整理片单，可以在这里选择或新建自定义收藏夹。
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
          v-for="folder in sortedFolders"
          :key="folder.id ?? folder.name"
          class="picker-card"
        >
          <div class="picker-card-copy">
            <div class="picker-card-header">
              <div>
                <h4 class="picker-card-title">{{ folder.name || '未命名收藏夹' }}</h4>
                <p class="picker-card-meta">{{ getMovieCountLabel(folder) }}</p>
              </div>

              <div class="picker-card-tags">
                <n-tag size="small" :type="isDefaultFavoriteFolder(folder) ? 'warning' : 'default'">
                  {{ getVisibilityLabel(folder) }}
                </n-tag>
              </div>
            </div>

            <p class="picker-card-description">
              {{ folder.description || '还没有填写这个收藏夹的说明。' }}
            </p>
          </div>

          <n-button
            type="primary"
            class="rounded-full px-5"
            :loading="isSubmitting(folder.id)"
            :disabled="hasPendingSubmission && !isSubmitting(folder.id)"
            @click="emit('selectFolder', folder)"
          >
            {{ isDefaultFavoriteFolder(folder) ? '添加到默认收藏夹' : '添加到此夹' }}
          </n-button>
        </article>
      </div>
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
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding: 1rem 1.1rem;
  border: 1px solid rgba(226, 232, 240, 0.95);
  border-radius: 1.25rem;
  background: #ffffff;
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

.picker-card-title {
  margin: 0;
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

@media (max-width: 640px) {
  .picker-card {
    align-items: flex-start;
  }
}
</style>
