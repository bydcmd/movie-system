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
  NPagination,
  NSelect,
  NSpace,
  NSpin,
  NTag
} from 'naive-ui'
import { useAdminPersons } from '@/composables/admin/useAdminPersons'
import { resolveAssetUrl } from '@/utils/profile'
import type { MovieId } from '@/utils/movie'

const {
  state,
  persons,
  total,
  loading,
  hasLoadError,
  lastUpdatedText,
  isSubmitting,
  canSubmitForm,
  sexOptions,
  formatCount,
  getSexLabel,
  applyKeyword,
  resetFilters,
  openCreateModal,
  openEditModal,
  closeCreateModal,
  closeEditModal,
  refreshPersons,
  createPerson,
  updatePerson,
  deletePerson,
  isDeletingPerson
} = useAdminPersons()

function getPersonInitial(person: { name?: string; id?: MovieId }): string {
  const name = person.name?.trim()
  if (!name) {
    return person.id ? String(person.id).slice(0, 1).toUpperCase() : '?'
  }
  return name.slice(0, 1).toUpperCase()
}

function getDisplayAvatar(avatar?: string): string | undefined {
  return resolveAssetUrl(avatar) || undefined
}

function summarizeBiography(biography?: string, maxLength = 80): string {
  if (!biography) return '暂无简介'
  if (biography.length <= maxLength) return biography
  return biography.slice(0, maxLength) + '...'
}
</script>

<template>
  <section class="space-y-5">
    <div class="flex flex-wrap items-start justify-between gap-4">
      <div>
        <h2 class="font-display text-2xl font-bold text-slate-900">影人管理</h2>
      </div>

      <div class="flex flex-wrap items-center gap-3">
        <span class="rounded-full bg-slate-100 px-4 py-2 text-sm text-slate-600">
          最近同步 {{ lastUpdatedText }}
        </span>
        <n-button secondary @click="refreshPersons">
          刷新列表
        </n-button>
        <n-button type="primary" @click="openCreateModal">
          新增影人
        </n-button>
      </div>
    </div>

    <n-alert
      v-if="hasLoadError"
      type="warning"
      title="影人数据加载失败"
      class="rounded-3xl"
    >
      可以重试刷新列表，或调整搜索条件后重新查询。
    </n-alert>

    <section class="rounded-[28px] border border-slate-200 bg-white/90 p-5 shadow-sm">
      <div class="flex flex-wrap items-center gap-3">
        <n-input
          v-model:value="state.keywordInput"
          clearable
          placeholder="搜索影人名称"
          class="min-w-[220px] flex-1"
          @keyup.enter="applyKeyword"
        />

        <div class="flex gap-3">
          <n-button type="primary" @click="applyKeyword">
            搜索
          </n-button>
          <n-button quaternary @click="resetFilters">
            清空
          </n-button>
        </div>
      </div>

      <n-spin :show="loading" class="mt-5 block">
        <div v-if="persons.length > 0" class="space-y-4">
          <article
            v-for="person in persons"
            :key="person.id"
            class="flex flex-wrap items-start justify-between gap-4 rounded-[24px] border border-slate-200 bg-slate-50/70 p-4"
          >
            <div class="flex min-w-0 flex-1 items-start gap-4">
              <div
                class="flex h-14 w-14 flex-shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-amber-400 to-orange-500 text-lg font-bold text-white"
              >
                {{ getPersonInitial(person) }}
              </div>

              <div class="min-w-0 flex-1">
                <div class="flex flex-wrap items-center gap-2">
                  <h3 class="text-base font-semibold text-slate-900">
                    {{ person.name || '未命名影人' }}
                  </h3>

                  <n-tag v-if="person.sex" size="small" type="info">
                    {{ getSexLabel(person.sex) }}
                  </n-tag>

                  <n-tag v-if="person.profession" size="small">
                    {{ person.profession }}
                  </n-tag>
                </div>

                <p v-if="person.nameEn" class="mt-1 text-sm text-slate-500">
                  {{ person.nameEn }}
                </p>

                <p class="mt-2 text-sm leading-6 text-slate-600">
                  {{ summarizeBiography(person.biography) }}
                </p>

                <p class="mt-1 text-xs text-slate-400">
                  ID: {{ person.id ?? '未知' }}
                  <template v-if="person.birth"> · 出生: {{ person.birth }}</template>
                  <template v-if="person.birthplace"> · {{ person.birthplace }}</template>
                </p>
              </div>
            </div>

            <div class="flex flex-wrap justify-end gap-3">
              <n-button
                secondary
                size="small"
                @click="openEditModal(person)"
              >
                编辑
              </n-button>
              <n-button
                secondary
                strong
                type="error"
                size="small"
                :loading="isDeletingPerson(person.id)"
                @click="deletePerson(person)"
              >
                删除
              </n-button>
            </div>
          </article>
        </div>

        <n-empty
          v-else
          description="没有匹配的影人"
          class="py-12"
        />
      </n-spin>

      <div class="mt-5 flex flex-wrap items-center justify-between gap-4 border-t border-slate-200 pt-5">
        <span class="text-sm text-slate-500">共 {{ formatCount(total) }} 个影人</span>

        <n-pagination
          v-model:page="state.page"
          :page-size="state.size"
          :item-count="total"
          simple
        />
      </div>
    </section>

    <!-- Create Modal -->
    <n-modal
      v-model:show="state.isCreateModalOpen"
      title="新增影人"
      preset="card"
      class="max-w-xl"
      :mask-closable="false"
      @after-leave="closeCreateModal"
    >
      <n-form label-placement="left" label-width="80">
        <n-form-item label="中文名" required>
          <n-input
            v-model:value="state.formData.name"
            placeholder="请输入影人中文名"
            maxlength="100"
            show-count
          />
        </n-form-item>

        <n-form-item label="英文名">
          <n-input
            v-model:value="state.formData.nameEn"
            placeholder="请输入影人英文名"
            maxlength="100"
          />
        </n-form-item>

        <n-form-item label="中文别名">
          <n-input
            v-model:value="state.formData.nameZh"
            placeholder="请输入影人中文别名"
            maxlength="100"
          />
        </n-form-item>

        <n-form-item label="性别">
          <n-select
            v-model:value="state.formData.sex"
            :options="sexOptions"
            placeholder="请选择性别"
          />
        </n-form-item>

        <n-form-item label="出生日期">
          <n-input
            v-model:value="state.formData.birth"
            placeholder="例如：1990-01-01"
            maxlength="50"
          />
        </n-form-item>

        <n-form-item label="出生地">
          <n-input
            v-model:value="state.formData.birthplace"
            placeholder="请输入出生地"
            maxlength="200"
          />
        </n-form-item>

        <n-form-item label="职业">
          <n-input
            v-model:value="state.formData.profession"
            placeholder="例如：演员、导演、编剧"
            maxlength="200"
          />
        </n-form-item>

        <n-form-item label="头像 URL">
          <n-input
            v-model:value="state.formData.avatar"
            placeholder="请输入头像图片 URL"
            maxlength="500"
          />
        </n-form-item>

        <n-form-item label="简介">
          <n-input
            v-model:value="state.formData.biography"
            type="textarea"
            placeholder="请输入影人简介"
            :rows="4"
            maxlength="20000"
            show-count
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
            @click="createPerson"
          >
            确认创建
          </n-button>
        </n-space>
      </template>
    </n-modal>

    <!-- Edit Modal -->
    <n-modal
      v-model:show="state.isEditModalOpen"
      title="编辑影人"
      preset="card"
      class="max-w-xl"
      :mask-closable="false"
      @after-leave="closeEditModal"
    >
      <n-form label-placement="left" label-width="80">
        <n-form-item label="影人 ID">
          <n-input :value="String(state.editingPerson?.id ?? '')" disabled />
        </n-form-item>

        <n-form-item label="中文名" required>
          <n-input
            v-model:value="state.formData.name"
            placeholder="请输入影人中文名"
            maxlength="100"
            show-count
          />
        </n-form-item>

        <n-form-item label="英文名">
          <n-input
            v-model:value="state.formData.nameEn"
            placeholder="请输入影人英文名"
            maxlength="100"
          />
        </n-form-item>

        <n-form-item label="中文别名">
          <n-input
            v-model:value="state.formData.nameZh"
            placeholder="请输入影人中文别名"
            maxlength="100"
          />
        </n-form-item>

        <n-form-item label="性别">
          <n-select
            v-model:value="state.formData.sex"
            :options="sexOptions"
            placeholder="请选择性别"
          />
        </n-form-item>

        <n-form-item label="出生日期">
          <n-input
            v-model:value="state.formData.birth"
            placeholder="例如：1990-01-01"
            maxlength="50"
          />
        </n-form-item>

        <n-form-item label="出生地">
          <n-input
            v-model:value="state.formData.birthplace"
            placeholder="请输入出生地"
            maxlength="200"
          />
        </n-form-item>

        <n-form-item label="职业">
          <n-input
            v-model:value="state.formData.profession"
            placeholder="例如：演员、导演、编剧"
            maxlength="200"
          />
        </n-form-item>

        <n-form-item label="头像 URL">
          <n-input
            v-model:value="state.formData.avatar"
            placeholder="请输入头像图片 URL"
            maxlength="500"
          />
        </n-form-item>

        <n-form-item label="简介">
          <n-input
            v-model:value="state.formData.biography"
            type="textarea"
            placeholder="请输入影人简介"
            :rows="4"
            maxlength="20000"
            show-count
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
            @click="updatePerson"
          >
            确认更新
          </n-button>
        </n-space>
      </template>
    </n-modal>
  </section>
</template>
