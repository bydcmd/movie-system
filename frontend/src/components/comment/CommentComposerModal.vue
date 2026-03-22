<script setup lang="ts">
import { computed, nextTick, shallowRef, useTemplateRef, watch } from 'vue'
import type { InputInst } from 'naive-ui'
import { NButton, NForm, NFormItem, NInput, NModal, useMessage } from 'naive-ui'
import TiptapEditor from '@/components/comment/TiptapEditor.vue'
import {
  createEmptyTiptapDocument,
  extractTiptapText,
  type ReviewComposerTab,
  type ReviewSubmitPayload
} from '@/utils/comment'

const show = defineModel<boolean>('show', { required: true })
const activeTab = defineModel<ReviewComposerTab>('activeTab', { default: 'short' })

const props = withDefaults(
  defineProps<{
    saving?: boolean
    shortInitial?: string
    longTitleInitial?: string
    longContentInitial?: string
    draftStorageKeyBase?: string
    draftResetToken?: number
  }>(),
  {
    saving: false,
    shortInitial: '',
    longTitleInitial: '',
    longContentInitial: '',
    draftStorageKeyBase: '',
    draftResetToken: 0
  }
)

const emit = defineEmits<{
  submit: [payload: ReviewSubmitPayload]
}>()

const message = useMessage()
const shortInputRef = useTemplateRef<InputInst>('shortInput')
const longEditorRef = useTemplateRef<{ focusEditor: () => void }>('longEditor')
const shortText = shallowRef('')
const longTitle = shallowRef('')
const longContent = shallowRef(createEmptyTiptapDocument())
const longPlainText = shallowRef('')

type ComposerModeOption = {
  value: ReviewComposerTab
  label: string
}

const modeOptions: ComposerModeOption[] = [
  {
    value: 'short',
    label: '短评'
  },
  {
    value: 'long',
    label: '长评'
  }
]

const isEditingShortReview = computed(() => props.shortInitial.trim().length > 0)
const isEditingLongReview = computed(() => props.longTitleInitial.trim().length > 0)
const dialogTitle = computed(() => {
  if (activeTab.value === 'short') {
    return isEditingShortReview.value ? '编辑短评' : '发布短评'
  }

  return isEditingLongReview.value ? '编辑长评' : '发布长评'
})
const submitLabel = computed(() => {
  if (activeTab.value === 'short') {
    return isEditingShortReview.value ? '更新短评' : '发布短评'
  }

  return isEditingLongReview.value ? '更新长评' : '发布长评'
})
const shortCharacterCount = computed(() => shortText.value.length)
const longTitleCount = computed(() => longTitle.value.length)
const longWordCount = computed(() => longPlainText.value.trim().length)
const initialLongPlainText = computed(() => extractTiptapText(props.longContentInitial))
const shortDraftStorageKey = computed(() =>
  props.draftStorageKeyBase ? `${props.draftStorageKeyBase}:short` : ''
)
const longDraftStorageKey = computed(() =>
  props.draftStorageKeyBase ? `${props.draftStorageKeyBase}:long` : ''
)
const isShortMode = computed(() => activeTab.value === 'short')
const modalStyle = computed(() => ({
  width: isShortMode.value
    ? 'min(560px, calc(100vw - 1.5rem))'
    : 'min(700px, calc(100vw - 1.5rem))'
}))
const activeMeta = computed(() => {
  if (isShortMode.value) {
    return `${shortCharacterCount.value}/500`
  }

  return `标题 ${longTitleCount.value}/100 · 正文 ${longWordCount.value} 字`
})
const hasUnsavedChanges = computed(() => {
  const shortChanged = shortText.value.trim() !== props.shortInitial.trim() && shortText.value.trim().length > 0
  const longChanged =
    longTitle.value.trim() !== props.longTitleInitial.trim()
    || longPlainText.value.trim() !== initialLongPlainText.value.trim()

  return shortChanged || longChanged
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

function clearStoredDrafts() {
  removeStoredDraft(shortDraftStorageKey.value)
  removeStoredDraft(longDraftStorageKey.value)
}

function syncDrafts() {
  const storedShortDraft = readStoredDraft<{ content?: string }>(shortDraftStorageKey.value)
  const storedLongDraft = readStoredDraft<{ title?: string; content?: string }>(longDraftStorageKey.value)
  const hasStoredShortDraft = Boolean(storedShortDraft?.content?.trim())
  const longStoredContent = storedLongDraft?.content || ''
  const hasStoredLongDraft = Boolean(
    storedLongDraft?.title?.trim() || extractTiptapText(longStoredContent).trim()
  )

  shortText.value = hasStoredShortDraft ? storedShortDraft?.content || '' : props.shortInitial
  longTitle.value = hasStoredLongDraft ? storedLongDraft?.title || '' : props.longTitleInitial
  longContent.value = hasStoredLongDraft
    ? longStoredContent || createEmptyTiptapDocument()
    : props.longContentInitial || createEmptyTiptapDocument()
  longPlainText.value = extractTiptapText(longContent.value)
}

watch(
  () => show.value,
  (visible) => {
    if (visible) {
      syncDrafts()
      focusActiveField()
    }
  }
)

function handlePlainTextUpdate(value: string) {
  longPlainText.value = value
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

function persistLongDraft() {
  if (!show.value || !longDraftStorageKey.value) {
    return
  }

  const title = longTitle.value.trim()
  const plainText = extractTiptapText(longContent.value).trim()
  if (!title && !plainText) {
    removeStoredDraft(longDraftStorageKey.value)
    return
  }

  if (
    title === props.longTitleInitial.trim()
    && plainText === initialLongPlainText.value.trim()
  ) {
    removeStoredDraft(longDraftStorageKey.value)
    return
  }

  writeStoredDraft(longDraftStorageKey.value, {
    title: longTitle.value,
    content: longContent.value
  })
}

watch(shortText, (value) => {
  persistShortDraft(value)
})

watch([longTitle, longContent], () => {
  persistLongDraft()
})

watch(
  () => activeTab.value,
  () => {
    if (!show.value) {
      return
    }

    focusActiveField()
  }
)

function focusActiveField() {
  nextTick(() => {
    if (!show.value) {
      return
    }

    if (activeTab.value === 'short') {
      shortInputRef.value?.focus()
      return
    }

    longEditorRef.value?.focusEditor()
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
  if (activeTab.value === 'short') {
    const content = shortText.value.trim()
    if (!content) {
      message.warning('请输入短评内容')
      return
    }

    emit('submit', {
      type: 'short',
      content
    })
    return
  }

  const title = longTitle.value.trim()
  const plainText = longPlainText.value.trim()

  if (!title) {
    message.warning('请输入长评标题')
    return
  }

  if (!plainText) {
    message.warning('长评正文不能为空')
    return
  }

  emit('submit', {
    type: 'long',
    title,
    content: longContent.value,
    plainText
  })
}

watch(
  () => props.draftResetToken,
  () => {
    clearStoredDrafts()
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
    :class="{ 'is-short': isShortMode, 'is-long': !isShortMode }"
  >
    <div class="composer-panel" :class="{ 'is-short': isShortMode, 'is-long': !isShortMode }" @keydown="handleComposerKeydown">
      <div class="composer-header">
        <div class="composer-mode-switch" role="tablist" aria-label="评论发布模式">
          <button
            v-for="option in modeOptions"
            :key="option.value"
            :id="`composer-tab-${option.value}`"
            type="button"
            class="composer-mode-button"
            :class="{ 'is-active': activeTab === option.value }"
            :aria-selected="activeTab === option.value"
            :aria-controls="`composer-panel-${option.value}`"
            :disabled="props.saving"
            role="tab"
            @click="activeTab = option.value"
          >
            {{ option.label }}
          </button>
        </div>
        <span class="composer-meta">{{ activeMeta }}</span>
      </div>

      <section
        v-if="activeTab === 'short'"
        id="composer-panel-short"
        class="composer-form-card"
        role="tabpanel"
        aria-labelledby="composer-tab-short"
      >
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

      <section
        v-else
        id="composer-panel-long"
        class="composer-form-card"
        role="tabpanel"
        aria-labelledby="composer-tab-long"
      >
        <n-form label-placement="top" class="composer-form">
          <n-form-item label="标题">
            <n-input
              v-model:value="longTitle"
              :maxlength="100"
              placeholder="输入标题"
            />
          </n-form-item>

          <n-form-item label="正文">
            <TiptapEditor
              ref="longEditor"
              v-model="longContent"
              placeholder="写下正文"
              :min-height="340"
              autofocus
              @update:plain-text="handlePlainTextUpdate"
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
}

.composer-modal.is-short :deep(.n-card-header__main),
.composer-modal.is-short :deep(.n-card__content > *),
.composer-modal.is-short :deep(.n-card__footer > *) {
  max-width: 31rem;
}

.composer-modal.is-long :deep(.n-card-header__main),
.composer-modal.is-long :deep(.n-card__content > *),
.composer-modal.is-long :deep(.n-card__footer > *) {
  max-width: 36rem;
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

.composer-panel.is-short {
  max-width: 31rem;
}

.composer-panel.is-long {
  max-width: 36rem;
}

.composer-header {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  padding-bottom: 0.6rem;
  border-bottom: 1px solid rgb(226 232 240);
}

.composer-mode-switch {
  display: inline-grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.35rem;
}

.composer-mode-button {
  min-height: 2.5rem;
  padding: 0.55rem 1rem;
  cursor: pointer;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: rgb(71 85 105);
  font-size: 0.88rem;
  font-weight: 600;
  line-height: 1;
  transition:
    background-color 180ms ease,
    color 180ms ease;
}

.composer-mode-button:hover:not(:disabled) {
  color: rgb(15 23 42);
}

.composer-mode-button:focus-visible {
  outline: none;
  box-shadow: 0 0 0 2px rgba(56, 189, 248, 0.18);
}

.composer-mode-button.is-active {
  background: rgb(241 245 249);
  color: rgb(15 23 42);
}

.composer-mode-button:disabled {
  cursor: not-allowed;
  opacity: 0.7;
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

@media (max-width: 960px) {
  .composer-header {
    align-items: flex-start;
  }
}

@media (max-width: 700px) {
  .composer-panel.is-short,
  .composer-panel.is-long {
    max-width: none;
  }

  .composer-form-card {
    padding: 0.9rem;
  }

  .composer-footer-actions {
    width: 100%;
  }

  .composer-footer-actions :deep(.n-button),
  .composer-mode-switch {
    width: 100%;
  }

  .composer-footer-actions :deep(.n-button) {
    flex: 1;
  }
}
</style>
