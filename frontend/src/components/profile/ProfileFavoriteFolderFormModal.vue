<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import { NButton, NForm, NFormItem, NInput, NModal, NSwitch, useMessage } from 'naive-ui'
import type { FavoriteFolderDTO, FavoriteFolderVO } from '@/api/model'

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
  }>(),
  {
    initialFolder: null,
    saving: false
  }
)

const emit = defineEmits<{
  submit: [payload: FavoriteFolderDTO]
}>()

const message = useMessage()
const form = reactive<FolderFormState>({
  name: '',
  description: '',
  isPublic: false
})

const dialogTitle = computed(() => (props.mode === 'edit' ? '编辑收藏夹' : '新建收藏夹'))
const submitLabel = computed(() => (props.mode === 'edit' ? '保存修改' : '创建收藏夹'))
const modalStyle = computed(() => ({
  width: 'min(720px, calc(100vw - 1.5rem))'
}))

function applyFormState(folder?: FavoriteFolderVO | null) {
  form.name = folder?.name ?? ''
  form.description = folder?.description ?? ''
  form.isPublic = folder?.isPublic === 1
}

function closeModal() {
  if (props.saving) {
    return
  }

  show.value = false
}

function handleSubmit() {
  const name = form.name.trim()
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
        自定义片单名称、说明和可见性。默认收藏夹由系统维护，不支持在这里修改。
      </section>

      <n-form label-placement="top" class="space-y-4">
        <n-form-item label="收藏夹名称" required>
          <n-input
            v-model:value="form.name"
            placeholder="例如：午夜科幻、年度十佳、想再看一遍"
            maxlength="100"
            show-count
          />
        </n-form-item>

        <n-form-item label="收藏夹说明">
          <n-input
            v-model:value="form.description"
            type="textarea"
            placeholder="给这份片单留一段说明，帮助以后快速回想起它的主题。"
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
                公开后，其他用户可以查看这个收藏夹的详情页与片单内容。
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
      </n-form>

      <div class="flex flex-wrap justify-end gap-3">
        <n-button tertiary class="rounded-full px-6" :disabled="saving" @click="closeModal">
          取消
        </n-button>
        <n-button
          type="primary"
          class="rounded-full px-6"
          :loading="saving"
          @click="handleSubmit"
        >
          {{ submitLabel }}
        </n-button>
      </div>
    </div>
  </n-modal>
</template>
