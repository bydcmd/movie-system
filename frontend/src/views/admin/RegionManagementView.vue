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
  NTag,
  NTooltip
} from 'naive-ui'
import { useAdminRegions } from '@/composables/admin/useAdminRegions'

const {
  state,
  regions,
  loading,
  hasLoadError,
  lastUpdatedText,
  isSubmitting,
  canSubmitForm,
  openCreateModal,
  openEditModal,
  closeCreateModal,
  closeEditModal,
  refreshRegions,
  createRegion,
  updateRegion,
  deleteRegion,
  isDeletingRegion
} = useAdminRegions()
</script>

<template>
  <section class="space-y-5">
    <div class="flex flex-wrap items-start justify-between gap-4">
      <div>
        <h2 class="font-display text-2xl font-bold text-slate-900">地区管理</h2>
      </div>

      <div class="flex flex-wrap items-center gap-3">
        <span class="rounded-full bg-slate-100 px-4 py-2 text-sm text-slate-600">
          最近同步 {{ lastUpdatedText }}
        </span>
        <n-button secondary @click="refreshRegions">
          刷新列表
        </n-button>
        <n-button type="primary" @click="openCreateModal">
          新增地区
        </n-button>
      </div>
    </div>

    <n-alert
      v-if="hasLoadError"
      type="warning"
      title="地区数据加载失败"
      class="rounded-3xl"
    >
      可以重试刷新列表。
    </n-alert>

    <section class="rounded-[28px] border border-slate-200 bg-white/90 p-5 shadow-sm">
      <n-spin :show="loading" class="block">
        <div v-if="regions.length > 0" class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
          <n-card
            v-for="region in regions"
            :key="region.id"
            class="region-card"
            :bordered="false"
          >
            <div class="flex flex-col gap-3">
              <div class="flex items-center justify-between">
                <div class="flex items-center gap-2">
                  <n-tag type="info" size="small">ID: {{ region.id }}</n-tag>
                </div>
                <n-space>
                  <n-button
                    secondary
                    size="small"
                    @click="openEditModal(region)"
                  >
                    编辑
                  </n-button>
                  <n-button
                    secondary
                    strong
                    type="error"
                    size="small"
                    :loading="isDeletingRegion(region.id)"
                    @click="deleteRegion(region)"
                  >
                    删除
                  </n-button>
                </n-space>
              </div>

              <div class="flex flex-col gap-1">
                <span class="font-medium text-slate-900">{{ region.name }}</span>
                <span v-if="region.nameEn" class="text-sm text-slate-500">{{ region.nameEn }}</span>
              </div>

              <n-tooltip v-if="region.description" trigger="hover">
                <template #trigger>
                  <p class="line-clamp-2 text-sm text-slate-600">
                    {{ region.description }}
                  </p>
                </template>
                {{ region.description }}
              </n-tooltip>
            </div>
          </n-card>
        </div>

        <n-empty
          v-else
          description="暂无地区数据"
          class="py-12"
        />
      </n-spin>

      <div class="mt-5 flex flex-wrap items-center justify-between gap-4 border-t border-slate-200 pt-5">
        <span class="text-sm text-slate-500">共 {{ regions.length }} 个地区</span>
      </div>
    </section>

    <!-- Create Modal -->
    <n-modal
      v-model:show="state.isCreateModalOpen"
      title="新增地区"
      preset="card"
      class="max-w-md"
      :mask-closable="false"
      @after-leave="closeCreateModal"
    >
      <n-form>
        <n-form-item label="地区名称" required>
          <n-input
            v-model:value="state.formData.name"
            placeholder="请输入地区名称"
            maxlength="50"
            show-count
            @keyup.enter="createRegion"
          />
        </n-form-item>
        <n-form-item label="英文名称">
          <n-input
            v-model:value="state.formData.nameEn"
            placeholder="请输入英文名称（可选）"
            maxlength="50"
            show-count
          />
        </n-form-item>
        <n-form-item label="地区描述">
          <n-input
            v-model:value="state.formData.description"
            type="textarea"
            placeholder="请输入地区描述（可选）"
            maxlength="200"
            show-count
            :rows="3"
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
            @click="createRegion"
          >
            确认创建
          </n-button>
        </n-space>
      </template>
    </n-modal>

    <!-- Edit Modal -->
    <n-modal
      v-model:show="state.isEditModalOpen"
      title="编辑地区"
      preset="card"
      class="max-w-md"
      :mask-closable="false"
      @after-leave="closeEditModal"
    >
      <n-form>
        <n-form-item label="地区 ID">
          <n-input :value="String(state.editingRegion?.id ?? '')" disabled />
        </n-form-item>
        <n-form-item label="地区名称" required>
          <n-input
            v-model:value="state.formData.name"
            placeholder="请输入地区名称"
            maxlength="50"
            show-count
            @keyup.enter="updateRegion"
          />
        </n-form-item>
        <n-form-item label="英文名称">
          <n-input
            v-model:value="state.formData.nameEn"
            placeholder="请输入英文名称（可选）"
            maxlength="50"
            show-count
          />
        </n-form-item>
        <n-form-item label="地区描述">
          <n-input
            v-model:value="state.formData.description"
            type="textarea"
            placeholder="请输入地区描述（可选）"
            maxlength="200"
            show-count
            :rows="3"
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
            @click="updateRegion"
          >
            确认更新
          </n-button>
        </n-space>
      </template>
    </n-modal>
  </section>
</template>

<style scoped>
.region-card {
  border-radius: 1rem;
  background: rgba(248, 250, 252, 0.8);
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}

.region-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.08);
}

:deep(.n-card__content) {
  padding: 1rem;
}

.line-clamp-2 {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
