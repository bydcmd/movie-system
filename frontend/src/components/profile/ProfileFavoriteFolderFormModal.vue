<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import { NButton, NForm, NFormItem, NInput, NModal, NSwitch, useMessage } from 'naive-ui'
import type { FavoriteFolderDTO, FavoriteFolderVO } from '@/api/model'
import { isDefaultFavoriteFolder } from '@/utils/favorite-folder'

type FolderFormMode = 'create' | 'edit'

type FolderFormState = {
  name: string
  description: string
  isPublic: boolean
}

const show = defineModel<boolean>('show', { required: true })

const props = withDefaults(
  defineProps<{
    mode: FolderFormMode
    initialFolder?: FavoriteFolderVO | null
    saving?: boolean
    deleting?: boolean
    allowDelete?: boolean
    allowBulkRemoveMovies?: boolean
  }>(),
  {
    initialFolder: null,
    saving: false,
    deleting: false,
    allowDelete: false,
    allowBulkRemoveMovies: false
  }
)

const emit = defineEmits<{
  submit: [payload: FavoriteFolderDTO]
  deleteFolder: []
  bulkRemoveMovies: []
}>()

const message = useMessage()
const form = reactive<FolderFormState>({
  name: '',
  description: '',
  isPublic: false
})

const isEditingDefaultFolder = computed(() => {
  return props.mode === 'edit' && isDefaultFavoriteFolder(props.initialFolder)
})
const canDeleteFolder = computed(() => props.mode === 'edit' && props.allowDelete)
const canBulkRemoveMovies = computed(() => props.mode === 'edit' && props.allowBulkRemoveMovies)
const showFolderActions = computed(() => canDeleteFolder.value || canBulkRemoveMovies.value)
const busy = computed(() => props.saving || props.deleting)
const dialogTitle = computed(() => {
  if (props.mode !== 'edit') {
    return '新建收藏夹'
  }

  return isEditingDefaultFolder.value ? '编辑默认收藏夹' : '编辑收藏夹'
})
const submitLabel = computed(() => (props.mode === 'edit' ? '保存修改' : '创建收藏夹'))
const modalStyle = computed(() => ({
  width: 'min(720px, calc(100vw - 1.5rem))'
}))
const introText = computed(() => {
  if (props.mode !== 'edit') {
    return '设置片单名称、说明和可见性，方便后续整理不同主题的电影。'
  }

  if (isEditingDefaultFolder.value) {
    if (canDeleteFolder.value && canBulkRemoveMovies.value) {
      return '默认收藏夹名称由系统维护，这里可以更新说明和公开状态；如需整理内容，也支持删除收藏夹或批量移除其中的电影。'
    }

    if (canBulkRemoveMovies.value) {
      return '默认收藏夹名称由系统维护，这里可以更新说明和公开状态；如需整理内容，也可以继续批量移除其中的电影。'
    }

    return '默认收藏夹名称由系统维护，这里可以更新说明和公开状态。'
  }

  return '修改收藏夹名称、说明和公开状态，让片单信息保持清晰。'
})
const nameInputPlaceholder = computed(() => {
  return props.mode === 'create' ? '例如：午夜科幻、年度十佳、想再看一遍' : ''
})
const descriptionPlaceholder = computed(() => {
  return isEditingDefaultFolder.value ? '补充这个默认收藏夹的用途说明' : '添加说明'
})
const actionHintText = computed(() => {
  if (isEditingDefaultFolder.value) {
    if (canDeleteFolder.value) {
      return '默认收藏夹现在也支持删除；点击“批量移除电影”后，会进入内容管理面板继续勾选处理。'
    }

    return '默认收藏夹名称仍由系统维护；点击“批量移除电影”后，会进入内容管理面板继续勾选处理。'
  }

  return '需要继续整理片单时，可以直接删除当前收藏夹，或进入内容管理面板批量移除电影。'
})

function applyFormState(folder?: FavoriteFolderVO | null) {
  form.name = folder?.name ?? ''
  form.description = folder?.description ?? ''
  form.isPublic = folder?.isPublic === 1
}

function closeModal() {
  if (busy.value) {
    return
  }

  show.value = false
}

function handleSubmit() {
  const name = (isEditingDefaultFolder.value ? props.initialFolder?.name ?? form.name : form.name).trim()
  const description = form.description.trim()

  if (!name) {
    message.warning('请先填写收藏夹名称')
    return
  }

  emit('submit', {
    name,
    description: description || undefined,
    isPublic: form.isPublic ? 1 : 0
  })
}

watch(
  () => [show.value, props.mode, props.initialFolder] as const,
  ([visible]) => {
    if (!visible) {
      return
    }

    applyFormState(props.initialFolder)
  },
  {
    immediate: true,
    deep: true
  }
)
</script>

<template>
  <n-modal
    v-model:show="show"
    preset="card"
    :title="dialogTitle"
    class="favorite-folder-form-modal"
    :style="modalStyle"
  >
    <div class="space-y-6">
      <section class="rounded-[24px] bg-slate-50 px-4 py-4 text-sm leading-6 text-slate-600">
        {{ introText }}
      </section>

      <n-form label-placement="top" class="space-y-4">
        <n-form-item label="收藏夹名称" :required="!isEditingDefaultFolder">
          <n-input
            v-model:value="form.name"
            :readonly="isEditingDefaultFolder"
            :placeholder="nameInputPlaceholder"
            maxlength="100"
            show-count
          />
          <p v-if="isEditingDefaultFolder" class="mt-2 text-xs leading-5 text-slate-500">
            默认收藏夹名称由系统维护，暂不支持在这里修改名称。
          </p>
        </n-form-item>

        <n-form-item label="收藏夹说明">
          <n-input
            v-model:value="form.description"
            type="textarea"
            :placeholder="descriptionPlaceholder"
            maxlength="500"
            :autosize="{ minRows: 4, maxRows: 8 }"
            show-count
          />
        </n-form-item>

        <section class="rounded-[24px] border border-slate-200 bg-white px-4 py-4">
          <div class="flex flex-wrap items-center justify-between gap-4">
            <div>
              <h3 class="text-sm font-semibold text-slate-900">公开设置</h3>
              <p class="mt-1 text-sm leading-6 text-slate-500">
                公开后，其他用户可以查看这个{{ isEditingDefaultFolder ? '默认' : '' }}收藏夹的详情页与片单内容。
              </p>
            </div>

            <div class="flex items-center gap-3">
              <span class="text-sm font-medium text-slate-600">
                {{ form.isPublic ? '公开' : '私密' }}
              </span>
              <n-switch v-model:value="form.isPublic" />
            </div>
          </div>
        </section>

        <section
          v-if="showFolderActions"
          class="rounded-[24px] border border-slate-200 bg-white px-4 py-4"
        >
          <div class="flex flex-wrap items-center justify-between gap-4">
            <div>
              <h3 class="text-sm font-semibold text-slate-900">内容整理</h3>
              <p class="mt-1 text-sm leading-6 text-slate-500">
                {{ actionHintText }}
              </p>
            </div>

            <div class="flex flex-wrap items-center gap-3">
              <n-button
                v-if="canBulkRemoveMovies"
                tertiary
                class="rounded-full px-6"
                :disabled="busy"
                @click="emit('bulkRemoveMovies')"
              >
                批量移除电影
              </n-button>
              <n-button
                v-if="canDeleteFolder"
                type="error"
                secondary
                class="rounded-full px-6"
                :loading="deleting"
                :disabled="saving"
                @click="emit('deleteFolder')"
              >
                删除收藏夹
              </n-button>
            </div>
          </div>
        </section>
      </n-form>

      <div class="flex flex-wrap justify-end gap-3">
        <n-button tertiary class="rounded-full px-6" :disabled="busy" @click="closeModal">
          取消
        </n-button>
        <n-button
          type="primary"
          class="rounded-full px-6"
          :loading="saving"
          :disabled="deleting"
          @click="handleSubmit"
        >
          {{ submitLabel }}
        </n-button>
      </div>
    </div>
  </n-modal>
</template>
