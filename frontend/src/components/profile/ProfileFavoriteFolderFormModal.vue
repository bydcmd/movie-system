<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import { NButton, NForm, NFormItem, NInput, NModal, NSelect, NSwitch, useMessage } from 'naive-ui'
import type { FavoriteFolderDTO, FavoriteFolderVO } from '@/api/model'
import {
  hasFavoriteFolderId,
  isDefaultFavoriteFolder,
  type FavoriteFolderId
} from '@/utils/favorite-folder'

type FolderFormMode = 'create' | 'edit' | 'move'

type FolderFormState = {
  name: string
  description: string
  isPublic: boolean
}

type MoveFormState = {
  targetFolderId: FavoriteFolderId | null
}

const show = defineModel<boolean>('show', { required: true })

const props = withDefaults(
  defineProps<{
    mode: FolderFormMode
    initialFolder?: FavoriteFolderVO | null
    moveTargetFolders?: FavoriteFolderVO[]
    selectedMovieCount?: number
    selectedMovieLabel?: string
    saving?: boolean
    deleting?: boolean
    allowDelete?: boolean
    allowBulkRemoveMovies?: boolean
  }>(),
  {
    initialFolder: null,
    moveTargetFolders: () => [],
    selectedMovieCount: 0,
    selectedMovieLabel: '',
    saving: false,
    deleting: false,
    allowDelete: false,
    allowBulkRemoveMovies: false
  }
)

const emit = defineEmits<{
  submit: [payload: FavoriteFolderDTO]
  moveFavorites: [payload: { toFolderId: FavoriteFolderId }]
  deleteFolder: []
  bulkRemoveMovies: []
}>()

const message = useMessage()
const form = reactive<FolderFormState>({
  name: '',
  description: '',
  isPublic: false
})
const moveForm = reactive<MoveFormState>({
  targetFolderId: null
})

const isEditingDefaultFolder = computed(() => {
  return props.mode === 'edit' && isDefaultFavoriteFolder(props.initialFolder)
})
const isMovingFavorites = computed(() => props.mode === 'move')
const canDeleteFolder = computed(() => props.mode === 'edit' && props.allowDelete)
const canBulkRemoveMovies = computed(() => props.mode === 'edit' && props.allowBulkRemoveMovies)
const showFolderActions = computed(() => canDeleteFolder.value || canBulkRemoveMovies.value)
const busy = computed(() => props.saving || props.deleting)
const moveMovieCount = computed(() => {
  return props.selectedMovieCount > 0 ? props.selectedMovieCount : 0
})
const moveSourceFolderName = computed(() => {
  return props.initialFolder?.name?.trim() || '当前收藏夹'
})
const moveSubjectLabel = computed(() => {
  const explicitLabel = props.selectedMovieLabel.trim()
  if (explicitLabel) {
    return explicitLabel
  }

  if (moveMovieCount.value > 1) {
    return `${moveMovieCount.value} 部电影`
  }

  return '这部电影'
})
const moveTargetFolderOptions = computed(() => {
  return props.moveTargetFolders
    .filter(hasFavoriteFolderId)
    .map((folder) => {
      const count = typeof folder.movieCount === 'number' ? folder.movieCount : 0
      const visibilityLabel = isDefaultFavoriteFolder(folder)
        ? '系统默认'
        : folder.isPublic === 1
          ? '公开'
          : '私密'

      return {
        label: `${folder.name || '未命名收藏夹'} · ${count} 部电影 · ${visibilityLabel}`,
        value: folder.id
      }
    })
})
const hasMoveTargetFolders = computed(() => moveTargetFolderOptions.value.length > 0)
const dialogTitle = computed(() => {
  if (isMovingFavorites.value) {
    return moveMovieCount.value > 1 ? '批量移动收藏' : '移动收藏'
  }

  if (props.mode !== 'edit') {
    return '新建收藏夹'
  }

  return isEditingDefaultFolder.value ? '编辑默认收藏夹' : '编辑收藏夹'
})
const submitLabel = computed(() => {
  if (isMovingFavorites.value) {
    return moveMovieCount.value > 1 ? `移动 ${moveMovieCount.value} 部电影` : '移动电影'
  }

  return props.mode === 'edit' ? '保存修改' : '创建收藏夹'
})
const modalStyle = computed(() => ({
  width: 'min(720px, calc(100vw - 1.5rem))'
}))
const introText = computed(() => {
  if (isMovingFavorites.value) {
    return ''
  }

  if (props.mode !== 'edit') {
    return '设置片单名称、说明和可见性，方便后续整理不同主题的电影。'
  }

  return ''
})
const nameInputPlaceholder = computed(() => {
  return props.mode === 'create' ? '例如：午夜科幻、年度十佳、想再看一遍' : ''
})
const descriptionPlaceholder = computed(() => {
  return isEditingDefaultFolder.value ? '补充这个默认收藏夹的用途说明' : '添加说明'
})

const actionHintText = computed(() => {
  if (isEditingDefaultFolder.value) {
    return canDeleteFolder.value ? '支持删除收藏夹或批量移除电影。' : '支持批量移除电影。'
  }

  return '可以删除收藏夹或批量移除电影。'
})

function applyFormState(folder?: FavoriteFolderVO | null) {
  form.name = folder?.name ?? ''
  form.description = folder?.description ?? ''
  form.isPublic = folder?.isPublic === 1
}

function resetMoveForm() {
  moveForm.targetFolderId = null
}

function closeModal() {
  if (busy.value) {
    return
  }

  show.value = false
}

function handleSubmit() {
  if (isMovingFavorites.value) {
    if (!moveForm.targetFolderId) {
      message.warning('请先选择目标收藏夹')
      return
    }

    emit('moveFavorites', {
      toFolderId: moveForm.targetFolderId
    })
    return
  }

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
      resetMoveForm()
      return
    }

    resetMoveForm()
    applyFormState(props.initialFolder)
  },
  {
    immediate: true,
    deep: true
  }
)

watch(
  moveTargetFolderOptions,
  (nextOptions) => {
    const validTargetFolderIds = new Set<FavoriteFolderId>(
      nextOptions.map((option) => option.value as FavoriteFolderId)
    )
    if (!moveForm.targetFolderId || validTargetFolderIds.has(moveForm.targetFolderId)) {
      return
    }

    moveForm.targetFolderId = null
  },
  { immediate: true }
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
    <div class="space-y-4">
      <section v-if="introText" class="rounded-[24px] bg-slate-50 px-4 py-3 text-sm text-slate-600">
        {{ introText }}
      </section>

      <n-form label-placement="top" class="space-y-3">
        <template v-if="isMovingFavorites">
          <section class="rounded-[24px] border border-slate-200 bg-white px-4 py-3">
            <div class="flex flex-wrap items-start justify-between gap-4">
              <div>
                <h3 class="text-sm font-semibold text-slate-900">移动范围</h3>
              </div>

              <div class="rounded-full bg-slate-100 px-3 py-1 text-sm font-medium text-slate-600">
                {{ moveMovieCount }} 项待移动
              </div>
            </div>
          </section>

          <n-form-item label="目标收藏夹" required>
            <n-select
              v-model:value="moveForm.targetFolderId"
              :options="moveTargetFolderOptions"
              :disabled="busy || !hasMoveTargetFolders"
              placeholder="选择目标收藏夹"
              filterable
              clearable
            />
          </n-form-item>
        </template>

        <template v-else>
          <div class="space-y-3">
            <div class="flex items-start gap-4">
              <n-form-item label="收藏夹名称" :required="!isEditingDefaultFolder" class="flex-1 mb-0">
                <n-input
                  v-model:value="form.name"
                  :readonly="isEditingDefaultFolder"
                  :placeholder="nameInputPlaceholder"
                  maxlength="100"
                  show-count
                />
              </n-form-item>

              <div class="flex items-center gap-3 pt-[22px]">
                <n-button
                  v-if="canBulkRemoveMovies"
                  size="small"
                  tertiary
                  :disabled="busy"
                  @click="emit('bulkRemoveMovies')"
                >
                  内容管理
                </n-button>
                <n-button
                  v-if="canDeleteFolder"
                  size="small"
                  type="error"
                  secondary
                  :loading="deleting"
                  :disabled="saving"
                  @click="emit('deleteFolder')"
                >
                  删除
                </n-button>
                <div class="flex items-center gap-2">
                  <span class="text-sm text-slate-600">公开</span>
                  <n-switch v-model:value="form.isPublic" />
                </div>
              </div>
            </div>

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
          </div>
        </template>
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
