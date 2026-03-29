<script setup lang="ts">
import { computed, nextTick, shallowRef, useTemplateRef, watch } from 'vue'
import type { InputInst } from 'naive-ui'
import { NButton, NForm, NFormItem, NInput, NModal, useMessage } from 'naive-ui'
import type { ReviewSubmitPayload } from '@/utils/comment'

const show = defineModel<boolean>('show', { required: true })

const props = withDefaults(
  defineProps<{
    saving?: boolean
    shortInitial?: string
    draftStorageKeyBase?: string
    draftResetToken?: number
  }>(),
  {
    saving: false,
    shortInitial: '',
    draftStorageKeyBase: '',
    draftResetToken: 0
  }
)

const emit = defineEmits<{
  submit: [payload: ReviewSubmitPayload]
}>()

const message = useMessage()
const shortInputRef = useTemplateRef<InputInst>('shortInput')
const shortText = shallowRef('')

const isEditingShortReview = computed(() => props.shortInitial.trim().length > 0)
const dialogTitle = computed(() => (isEditingShortReview.value ? '编辑短评' : '发布短评'))
const submitLabel = computed(() => (isEditingShortReview.value ? '更新短评' : '发布短评'))
const shortCharacterCount = computed(() => `${shortText.value.length}/500`)
const shortDraftStorageKey = computed(() =>
  props.draftStorageKeyBase ? `${props.draftStorageKeyBase}:short` : ''
)
const modalStyle = computed(() => ({
  width: 'min(560px, calc(100vw - 1.5rem))'
}))
const hasUnsavedChanges = computed(() => {
  const currentValue = shortText.value.trim()
  return currentValue.length > 0 && currentValue !== props.shortInitial.trim()
})

function readStoredDraft<T>(storageKey: string): T | null {
  if (!storageKey || typeof window === 'undefined') {
    return null
  }

  try {
    const raw = window.localStorage.getItem(storageKey)
    return raw ? JSON.parse(raw) as T : null
  } catch (error) {
    console.warn('Failed to read stored review draft:', error)
    return null
  }
}

function writeStoredDraft(storageKey: string, payload: unknown) {
  if (!storageKey || typeof window === 'undefined') {
    return
  }

  try {
    window.localStorage.setItem(storageKey, JSON.stringify(payload))
  } catch (error) {
    console.warn('Failed to persist review draft:', error)
  }
}

function removeStoredDraft(storageKey: string) {
  if (!storageKey || typeof window === 'undefined') {
    return
  }

  window.localStorage.removeItem(storageKey)
}

function syncDraft() {
  const storedDraft = readStoredDraft<{ content?: string }>(shortDraftStorageKey.value)
  shortText.value = storedDraft?.content?.trim() ? storedDraft.content : props.shortInitial
}

function persistShortDraft(value: string) {
  if (!show.value || !shortDraftStorageKey.value) {
    return
  }

  const trimmed = value.trim()
  if (!trimmed || trimmed === props.shortInitial.trim()) {
    removeStoredDraft(shortDraftStorageKey.value)
    return
  }

  writeStoredDraft(shortDraftStorageKey.value, { content: value })
}

function focusShortInput() {
  nextTick(() => {
    if (!show.value) {
      return
    }

    shortInputRef.value?.focus()
  })
}

function handleComposerKeydown(event: KeyboardEvent) {
  if (event.isComposing) {
    return
  }

  if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') {
    event.preventDefault()
    submitCurrentReview()
  }
}

function closeModal() {
  if (props.saving) {
    return
  }

  if (hasUnsavedChanges.value) {
    message.info('未提交内容已保存为草稿，下次打开会自动恢复。')
  }

  show.value = false
}

function submitCurrentReview() {
  const content = shortText.value.trim()
  if (!content) {
    message.warning('请输入短评内容')
    return
  }

  emit('submit', {
    type: 'short',
    content
  })
}

watch(
  () => show.value,
  (visible) => {
    if (!visible) {
      return
    }

    syncDraft()
    focusShortInput()
  }
)

watch(shortText, (value) => {
  persistShortDraft(value)
})

watch(
  () => props.draftResetToken,
  () => {
    removeStoredDraft(shortDraftStorageKey.value)
  }
)
</script>

<template>
  <n-modal
    v-model:show="show"
    preset="card"
    :title="dialogTitle"
    class="composer-modal"
    :style="modalStyle"
  >
    <div class="composer-panel" @keydown="handleComposerKeydown">
      <div class="composer-header">
        <span class="composer-meta">{{ shortCharacterCount }}</span>
      </div>

      <section class="composer-form-card">
        <n-form label-placement="top" class="composer-form">
          <n-form-item label="短评内容">
            <n-input
              ref="shortInput"
              v-model:value="shortText"
              type="textarea"
              :maxlength="500"
              placeholder="写下你的看法"
              :autosize="{ minRows: 8, maxRows: 12 }"
            />
          </n-form-item>
        </n-form>
      </section>
    </div>

    <template #footer>
      <div class="composer-footer">
        <div class="composer-footer-actions">
          <n-button :disabled="props.saving" @click="closeModal">取消</n-button>
          <n-button type="primary" :loading="props.saving" @click="submitCurrentReview">
            {{ submitLabel }}
          </n-button>
        </div>
      </div>
    </template>
  </n-modal>
</template>

<style scoped>
.composer-modal :deep(.n-card) {
  overflow: hidden;
  border-radius: 10px;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.08);
}

.composer-modal :deep(.n-card-header),
.composer-modal :deep(.n-card__content),
.composer-modal :deep(.n-card__footer) {
  display: flex;
  justify-content: center;
  padding-left: clamp(1rem, 3vw, 1.5rem);
  padding-right: clamp(1rem, 3vw, 1.5rem);
}

.composer-modal :deep(.n-card-header__main),
.composer-modal :deep(.n-card__content > *),
.composer-modal :deep(.n-card__footer > *) {
  width: 100%;
  max-width: 31rem;
}

.composer-modal :deep(.n-card__content) {
  padding-top: 0.75rem;
}

.composer-panel {
  width: 100%;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 0.9rem;
}

.composer-header {
  display: flex;
  justify-content: flex-end;
  padding-bottom: 0.6rem;
  border-bottom: 1px solid rgb(226 232 240);
}

.composer-meta {
  color: rgb(100 116 139);
  font-size: 0.82rem;
  line-height: 1.5;
}

.composer-form-card {
  padding-top: 0.25rem;
}

.composer-form :deep(.n-form-item) {
  margin-bottom: 0.9rem;
}

.composer-form :deep(.n-form-item-label) {
  font-weight: 600;
}

.composer-form :deep(.n-input__input-el),
.composer-form :deep(.n-input__textarea-el) {
  line-height: 1.65;
}

.composer-footer {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 0.75rem;
  border-top: 1px solid rgb(226 232 240);
  padding-top: 0.9rem;
}

.composer-footer-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.75rem;
}

@media (max-width: 700px) {
  .composer-form-card {
    padding: 0.9rem;
  }

  .composer-footer-actions {
    width: 100%;
  }

  .composer-footer-actions :deep(.n-button) {
    flex: 1;
  }
}
</style>
