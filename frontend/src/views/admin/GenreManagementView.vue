<script setup lang="ts">
import {
  NAlert,
  NButton,
  NCard,
  NEmpty,
  NForm,
  NFormItem,
  NInput,
  NModal,
  NSpace,
  NSpin,
  NTag
} from 'naive-ui'
import { useAdminGenres } from '@/composables/admin/useAdminGenres'

const {
  state,
  genres,
  loading,
  hasLoadError,
  lastUpdatedText,
  isSubmitting,
  canSubmitForm,
  openCreateModal,
  openEditModal,
  closeCreateModal,
  closeEditModal,
  refreshGenres,
  createGenre,
  updateGenre,
  deleteGenre,
  isDeletingGenre
} = useAdminGenres()
</script>

<template>
  <section class="space-y-5">
    <div class="flex flex-wrap items-start justify-between gap-4">
      <div>
        <h2 class="font-display text-2xl font-bold text-slate-900">类型管理</h2>
      </div>

      <div class="flex flex-wrap items-center gap-3">
        <span class="rounded-full bg-slate-100 px-4 py-2 text-sm text-slate-600">
          最近同步 {{ lastUpdatedText }}
        </span>
        <n-button secondary @click="refreshGenres">
          刷新列表
        </n-button>
        <n-button type="primary" @click="openCreateModal">
          新增类型
        </n-button>
      </div>
    </div>

    <n-alert
      v-if="hasLoadError"
      type="warning"
      title="类型数据加载失败"
      class="rounded-3xl"
    >
      可以重试刷新列表。
    </n-alert>

    <section class="rounded-[28px] border border-slate-200 bg-white/90 p-5 shadow-sm">
      <n-spin :show="loading" class="block">
        <div v-if="genres.length > 0" class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
          <n-card
            v-for="genre in genres"
            :key="genre.id"
            class="genre-card"
            :bordered="false"
          >
            <div class="flex items-center justify-between">
              <div class="flex items-center gap-3">
                <n-tag type="info" size="small">ID: {{ genre.id }}</n-tag>
                <span class="font-medium text-slate-900">{{ genre.name }}</span>
              </div>
              <n-space>
                <n-button
                  secondary
                  size="small"
                  @click="openEditModal(genre)"
                >
                  编辑
                </n-button>
                <n-button
                  secondary
                  strong
                  type="error"
                  size="small"
                  :loading="isDeletingGenre(genre.id)"
                  @click="deleteGenre(genre)"
                >
                  删除
                </n-button>
              </n-space>
            </div>
          </n-card>
        </div>

        <n-empty
          v-else
          description="暂无类型数据"
          class="py-12"
        />
      </n-spin>

      <div class="mt-5 flex flex-wrap items-center justify-between gap-4 border-t border-slate-200 pt-5">
        <span class="text-sm text-slate-500">共 {{ genres.length }} 个类型</span>
      </div>
    </section>

    <!-- Create Modal -->
    <n-modal
      v-model:show="state.isCreateModalOpen"
      title="新增类型"
      preset="card"
      class="max-w-md"
      :mask-closable="false"
      @after-leave="closeCreateModal"
    >
      <n-form>
        <n-form-item label="类型名称" required>
          <n-input
            v-model:value="state.formData.name"
            placeholder="请输入类型名称"
            maxlength="50"
            show-count
            @keyup.enter="createGenre"
          />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="closeCreateModal">取消</n-button>
          <n-button
            type="primary"
            :disabled="!canSubmitForm"
            :loading="isSubmitting"
            @click="createGenre"
          >
            确认创建
          </n-button>
        </n-space>
      </template>
    </n-modal>

    <!-- Edit Modal -->
    <n-modal
      v-model:show="state.isEditModalOpen"
      title="编辑类型"
      preset="card"
      class="max-w-md"
      :mask-closable="false"
      @after-leave="closeEditModal"
    >
      <n-form>
        <n-form-item label="类型 ID">
          <n-input :value="String(state.editingGenre?.id ?? '')" disabled />
        </n-form-item>
        <n-form-item label="类型名称" required>
          <n-input
            v-model:value="state.formData.name"
            placeholder="请输入类型名称"
            maxlength="50"
            show-count
            @keyup.enter="updateGenre"
          />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="closeEditModal">取消</n-button>
          <n-button
            type="primary"
            :disabled="!canSubmitForm"
            :loading="isSubmitting"
            @click="updateGenre"
          >
            确认更新
          </n-button>
        </n-space>
      </template>
    </n-modal>
  </section>
</template>

<style scoped>
.genre-card {
  border-radius: 1rem;
  background: rgba(248, 250, 252, 0.8);
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}

.genre-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.08);
}

:deep(.n-card__content) {
  padding: 1rem;
}
</style>
