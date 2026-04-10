<script setup lang="ts">
import { computed, shallowRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NButton, NForm, NFormItem, NInput, NTag, useMessage } from 'naive-ui'
import { ArrowBack } from '@vicons/ionicons5'
import TiptapEditor from '@/components/comment/TiptapEditor.vue'
import { createEmptyTiptapDocument, extractTiptapText } from '@/utils/comment'
import {
  useGetMyMovieLongReview,
  usePublishDraft,
  useSaveLongReviewDraft,
  useSubmitMovieLongReview,
  useUpdateMyMovieLongReview
} from '@/api/endpoints/comment-management/comment-management'
import type { Comment, UpdateLongReviewDTO } from '@/api/model'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const DRAFT_STATUS = 1 as const
const PUBLISHED_STATUS = 2 as const

type LongReviewStatus = typeof DRAFT_STATUS | typeof PUBLISHED_STATUS
type SaveAction = 'draft' | 'publish' | null
type EditableLongReview = Omit<Comment, 'id'> & { id?: string }

function parseRouteId(value: unknown): string | null {
  if (Array.isArray(value)) {
    return parseRouteId(value[0])
  }

  if (typeof value === 'string') {
    const trimmed = value.trim()
    return /^\d+$/.test(trimmed) ? trimmed : null
  }

  if (typeof value === 'bigint') {
    return value > 0n ? value.toString() : null
  }

  if (typeof value === 'number') {
    return Number.isFinite(value) && value > 0 ? Math.trunc(value).toString() : null
  }

  return null
}

function extractCreatedCommentId(value: unknown): string | null {
  if (!value || typeof value !== 'object') {
    return null
  }

  const record = value as {
    id?: unknown
    data?: {
      id?: unknown
    }
  }

  return parseRouteId(record.id) ?? parseRouteId(record.data?.id)
}

function normalizeLongReviewStatus(value?: number | null): LongReviewStatus {
  return value === DRAFT_STATUS ? DRAFT_STATUS : PUBLISHED_STATUS
}

const movieId = computed(() => Number(route.params.movieId))
const commentId = computed(() => {
  return parseRouteId(route.params.commentId) ?? parseRouteId(route.query.commentId)
})
const isEditMode = computed(() => commentId.value !== null)

const longTitle = shallowRef('')
const longContent = shallowRef(createEmptyTiptapDocument())
const longPlainText = shallowRef('')
const longReviewStatus = shallowRef<LongReviewStatus>(DRAFT_STATUS)
const activeSaveAction = shallowRef<SaveAction>(null)

const { mutateAsync: createComment, isPending: isCreating } = useSubmitMovieLongReview()
const { mutateAsync: updateComment, isPending: isUpdating } = useUpdateMyMovieLongReview()
const { mutateAsync: saveDraft, isPending: isSavingDraft } = useSaveLongReviewDraft()
const { mutateAsync: publishDraft, isPending: isPublishingDraft } = usePublishDraft()
const myLongReviewQuery = useGetMyMovieLongReview(
  movieId,
  {
    query: {
      enabled: computed(() => isEditMode.value && movieId.value > 0),
      retry: false
    }
  }
)
const { data: commentDetail, isLoading: isLoadingDetail } = myLongReviewQuery

const isSaving = computed(() => (
  isCreating.value ||
  isUpdating.value ||
  isSavingDraft.value ||
  isPublishingDraft.value
))
const isLoading = computed(() => isLoadingDetail.value)
const isDraftSaving = computed(() => isSaving.value && activeSaveAction.value === 'draft')
const isPublishSaving = computed(() => isSaving.value && activeSaveAction.value === 'publish')

const trimmedTitle = computed(() => longTitle.value.trim())
const trimmedPlainText = computed(() => longPlainText.value.trim())
const longTitleCount = computed(() => longTitle.value.length)
const longWordCount = computed(() => trimmedPlainText.value.length)
const hasDraftContent = computed(() => Boolean(trimmedTitle.value || trimmedPlainText.value))
const canSaveDraft = computed(() => hasDraftContent.value)
const draftActionLabel = computed(() => (
  longReviewStatus.value === DRAFT_STATUS ? '保存草稿' : '另存为草稿'
))

const pageTitle = computed(() => {
  if (!isEditMode.value) {
    return '写长评'
  }

  return longReviewStatus.value === DRAFT_STATUS ? '编辑长评草稿' : '编辑长评'
})
const reviewStatusLabel = computed(() => (
  longReviewStatus.value === DRAFT_STATUS ? '草稿' : '已发布'
))
const reviewStatusDescription = computed(() => (
  longReviewStatus.value === DRAFT_STATUS
    ? '当前内容仅自己可见，可继续修改后再发布。'
    : '当前内容对其他用户可见，可直接保存修改，也可另存为草稿。'
))
const submitLabel = computed(() => {
  if (!isEditMode.value) {
    return '发布长评'
  }

  return longReviewStatus.value === DRAFT_STATUS ? '发布草稿' : '保存修改'
})
const statusTagType = computed(() => (
  longReviewStatus.value === DRAFT_STATUS ? 'warning' : 'success'
))

async function refetchOrThrow<T>(query: { refetch: () => Promise<{ data?: T; error?: unknown }> }) {
  const result = await query.refetch()
  if (result.error) {
    throw result.error
  }
  return result.data
}

function getEditableCommentId(): string | null {
  const loadedCommentId = parseRouteId((commentDetail.value as EditableLongReview | null)?.id)
  return loadedCommentId ?? commentId.value
}

watch(
  [() => commentDetail.value, isEditMode],
  ([nextDetail, nextIsEditMode]) => {
    if (!nextIsEditMode || !nextDetail) {
      return
    }

    const detail = nextDetail as EditableLongReview
    longTitle.value = detail.title || ''
    longContent.value = detail.content || createEmptyTiptapDocument()
    longPlainText.value = extractTiptapText(longContent.value)
    longReviewStatus.value = normalizeLongReviewStatus(detail.status)

    const loadedCommentId = parseRouteId(detail.id)
    if (
      isEditMode.value &&
      loadedCommentId &&
      loadedCommentId !== commentId.value
    ) {
      void router.replace({
        name: 'long-review-editor-edit',
        params: {
          movieId: String(movieId.value),
          commentId: String(loadedCommentId)
        }
      })
    }
  },
  { immediate: true }
)

watch(
  isEditMode,
  (nextIsEditMode) => {
    if (nextIsEditMode) {
      return
    }

    longTitle.value = ''
    longContent.value = createEmptyTiptapDocument()
    longPlainText.value = ''
    longReviewStatus.value = DRAFT_STATUS
  },
  { immediate: true }
)

function handlePlainTextUpdate(value: string) {
  longPlainText.value = value
}

function handleBack() {
  if (isSaving.value) {
    return
  }
  router.back()
}

function validateLongReview(status: LongReviewStatus): boolean {
  if (status === DRAFT_STATUS) {
    if (!hasDraftContent.value) {
      message.warning('草稿至少需要标题或正文内容')
      return false
    }

    return true
  }

  if (!trimmedTitle.value) {
    message.warning('请输入长评标题')
    return false
  }

  if (!trimmedPlainText.value) {
    message.warning('长评正文不能为空')
    return false
  }

  return true
}

function buildLongReviewPayload(): UpdateLongReviewDTO {
  return {
    title: trimmedTitle.value,
    content: longContent.value
  }
}

function getSuccessMessage(
  nextStatus: LongReviewStatus,
  previousStatus: LongReviewStatus,
  wasEditMode: boolean
): string {
  if (nextStatus === DRAFT_STATUS) {
    return previousStatus === PUBLISHED_STATUS
      ? '草稿已保存，已发布内容未受影响'
      : '草稿已保存'
  }

  if (!wasEditMode) {
    return '长评已发布'
  }

  return previousStatus === DRAFT_STATUS ? '草稿已发布' : '长评已更新'
}

async function syncMyLongReview(): Promise<EditableLongReview | null> {
  try {
    return await refetchOrThrow(myLongReviewQuery) as EditableLongReview | null
  } catch (error) {
    console.error('Failed to refetch my long review:', error)
    return null
  }
}

async function resolveEditableCommentId(
  result: unknown,
  options: { preferRefetch?: boolean } = {}
): Promise<string | null> {
  if (!options.preferRefetch) {
    const currentCommentId = getEditableCommentId()
    if (currentCommentId) {
      return currentCommentId
    }

    const commentIdFromResult = extractCreatedCommentId(result)
    if (commentIdFromResult) {
      return commentIdFromResult
    }
  }

  const review = await syncMyLongReview()
  return parseRouteId(review?.id)
}

async function replaceEditorRouteIfNeeded(nextCommentId: string | null) {
  if (!nextCommentId || commentId.value === nextCommentId) {
    return
  }

  await router.replace({
    name: 'long-review-editor-edit',
    params: {
      movieId: String(movieId.value),
      commentId: String(nextCommentId)
    }
  })
}

async function persistLongReview(status: LongReviewStatus) {
  if (!validateLongReview(status)) {
    return
  }

  if (!movieId.value) {
    message.error('电影信息无效')
    return
  }

  const wasEditMode = isEditMode.value
  const previousStatus = longReviewStatus.value
  const nextAction: SaveAction = status === DRAFT_STATUS ? 'draft' : 'publish'
  activeSaveAction.value = nextAction

  try {
    const payload = buildLongReviewPayload()
    let savedResult: unknown = null
    let targetCommentId = getEditableCommentId()

    if (status === DRAFT_STATUS) {
      savedResult = await saveDraft({
        movieId: movieId.value,
        data: payload
      })
      targetCommentId = await resolveEditableCommentId(savedResult, { preferRefetch: true })

      longReviewStatus.value = DRAFT_STATUS
      message.success(getSuccessMessage(DRAFT_STATUS, previousStatus, wasEditMode))
      await replaceEditorRouteIfNeeded(targetCommentId)
      return
    }

    if (previousStatus === DRAFT_STATUS) {
      savedResult = await saveDraft({
        movieId: movieId.value,
        data: payload
      })
      targetCommentId = await resolveEditableCommentId(savedResult, { preferRefetch: true })

      if (!targetCommentId) {
        message.error('未找到可发布的草稿，请刷新页面后重试')
        return
      }

      savedResult = await publishDraft({
        commentId: targetCommentId
      })
      targetCommentId = await resolveEditableCommentId(savedResult, { preferRefetch: true })
    } else if (wasEditMode && commentId.value) {
      savedResult = await updateComment({
        movieId: movieId.value,
        data: payload
      })
    } else {
      savedResult = await createComment({
        movieId: movieId.value,
        data: payload
      })
      targetCommentId = await resolveEditableCommentId(savedResult, { preferRefetch: true })
    }

    longReviewStatus.value = PUBLISHED_STATUS
    message.success(getSuccessMessage(PUBLISHED_STATUS, previousStatus, wasEditMode))

    const publishedCommentId = targetCommentId ?? await resolveEditableCommentId(savedResult, {
      preferRefetch: true
    })

    if (publishedCommentId) {
      router.push({
        name: 'movie-review-detail',
        params: { id: movieId.value, commentId: publishedCommentId }
      })
    } else {
      router.push({
        name: 'movie-detail',
        params: { id: movieId.value }
      })
    }
  } catch (error) {
    console.error('Failed to save long review:', error)
    message.error('保存失败，请稍后再试')
  } finally {
    activeSaveAction.value = null
  }
}

function handleSaveDraft() {
  void persistLongReview(DRAFT_STATUS)
}

async function handleSubmit() {
  await persistLongReview(PUBLISHED_STATUS)
}

function handleKeydown(event: KeyboardEvent) {
  if (event.isComposing) {
    return
  }
  if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') {
    event.preventDefault()
    handleSubmit()
  }
}
</script>

<template>
  <div class="long-review-editor-page" @keydown="handleKeydown">
    <header class="editor-header">
      <div class="editor-header-inner">
        <div class="editor-header-start">
          <n-button quaternary class="back-button" @click="handleBack">
            <template #icon>
              <ArrowBack />
            </template>
            返回
          </n-button>

          <div class="editor-heading">
            <h1 class="editor-title">{{ pageTitle }}</h1>
            <div class="editor-status-row">
              <n-tag :bordered="false" :type="statusTagType" round size="small">
                {{ reviewStatusLabel }}
              </n-tag>
              <span class="editor-status-text">{{ reviewStatusDescription }}</span>
            </div>
          </div>
        </div>

        <div class="header-actions">
          <n-button :disabled="isSaving" @click="handleBack">
            取消
          </n-button>
          <n-button
            secondary
            type="warning"
            :disabled="isSaving || !canSaveDraft"
            :loading="isDraftSaving"
            @click="handleSaveDraft"
          >
            {{ draftActionLabel }}
          </n-button>
          <n-button type="primary" :disabled="isSaving" :loading="isPublishSaving" @click="handleSubmit">
            {{ submitLabel }}
          </n-button>
        </div>
      </div>
    </header>

    <main v-if="isLoading" class="editor-loading">
      <div class="editor-loading-inner">
        <div class="loading-text">加载中...</div>
      </div>
    </main>

    <main v-else class="editor-main">
      <section class="editor-workspace">
        <n-form label-placement="top" class="editor-form">
          <n-form-item label="标题">
            <n-input
              v-model:value="longTitle"
              :maxlength="100"
              placeholder="输入长评标题"
              size="large"
              class="title-input"
            />
            <div class="input-meta-row">
              <span class="input-meta">{{ longTitleCount }}/100</span>
            </div>
          </n-form-item>

          <n-form-item label="正文">
            <TiptapEditor
              v-model="longContent"
              placeholder="写下你的观影感受..."
              :min-height="620"
              autofocus
              @update:plain-text="handlePlainTextUpdate"
            />
            <div class="input-meta-row">
              <span class="input-meta">正文 {{ longWordCount }} 字</span>
            </div>
          </n-form-item>
        </n-form>
      </section>
    </main>

    <footer class="editor-footer">
      <div class="editor-footer-inner">
        <div class="footer-hints">
          <span class="hint">Ctrl + Enter 快速发布</span>
          <span class="hint">保存草稿后仅自己可见</span>
          <span class="hint">支持拖拽/粘贴图片</span>
        </div>
      </div>
    </footer>
  </div>
</template>

<style scoped>
.long-review-editor-page {
  --page-max-width: 1180px;
  min-height: 100vh;
  background:
    radial-gradient(circle at top, rgba(59, 130, 246, 0.08), transparent 28rem),
    linear-gradient(180deg, rgb(248 250 252) 0%, rgb(255 255 255) 24%, rgb(248 250 252) 100%);
}

.editor-header {
  position: sticky;
  top: 0;
  z-index: 20;
  border-bottom: 1px solid rgba(226, 232, 240, 0.9);
  background: rgba(248, 250, 252, 0.9);
  backdrop-filter: blur(16px);
}

.editor-header-inner {
  max-width: var(--page-max-width);
  margin: 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1.5rem;
  padding: 1rem clamp(1rem, 3vw, 2rem);
}

.editor-header-start {
  display: flex;
  align-items: center;
  gap: 1rem;
  min-width: 0;
}

.back-button {
  flex-shrink: 0;
}

.editor-heading {
  min-width: 0;
}

.editor-title {
  font-size: 1.45rem;
  font-weight: 700;
  line-height: 1.2;
  letter-spacing: -0.02em;
  color: rgb(15 23 42);
  margin: 0;
}

.editor-status-row {
  margin-top: 0.45rem;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.65rem;
}

.editor-status-text {
  font-size: 0.84rem;
  line-height: 1.5;
  color: rgb(100 116 139);
}

.header-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
  flex-shrink: 0;
  align-items: center;
}

.editor-loading {
  min-height: calc(100vh - 152px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 3rem clamp(1rem, 4vw, 2rem);
}

.editor-loading-inner {
  width: 100%;
  max-width: var(--page-max-width);
  display: flex;
  align-items: center;
  justify-content: center;
}

.loading-text {
  color: rgb(100 116 139);
  font-size: 1rem;
}

.editor-main {
  padding: clamp(1.25rem, 3vw, 2.5rem) clamp(1rem, 4vw, 2rem) 2rem;
}

.editor-workspace {
  max-width: var(--page-max-width);
  margin: 0 auto;
}

.editor-form :deep(.n-form-item) {
  margin-bottom: 1.75rem;
}

.editor-form :deep(.n-form-item-label) {
  font-weight: 600;
  font-size: 1rem;
  color: rgb(51 65 85);
  margin-bottom: 0.6rem;
}

.title-input :deep(.n-input__input-el) {
  font-size: 1.2rem;
  font-weight: 500;
}

.title-input :deep(.n-input-wrapper) {
  padding-top: 0.25rem;
  padding-bottom: 0.25rem;
  border-radius: 14px;
}

.input-meta-row {
  display: flex;
  justify-content: flex-end;
  gap: 0.75rem;
  margin-top: 0.45rem;
}

.input-meta {
  font-size: 0.84rem;
  color: rgb(100 116 139);
  line-height: 1.5;
}

.editor-footer {
  border-top: 1px solid rgb(226 232 240);
  background: rgba(255, 255, 255, 0.7);
}

.editor-footer-inner {
  max-width: var(--page-max-width);
  margin: 0 auto;
  padding: 1rem clamp(1rem, 3vw, 2rem) 1.25rem;
}

.footer-hints {
  display: flex;
  flex-wrap: wrap;
  gap: 1rem;
  justify-content: center;
}

.hint {
  font-size: 0.8rem;
  color: rgb(100 116 139);
  padding: 0.35rem 0.85rem;
  background: rgba(255, 255, 255, 0.88);
  border-radius: 999px;
  border: 1px solid rgb(226 232 240);
}

@media (max-width: 960px) {
  .editor-header-inner {
    flex-direction: column;
    align-items: stretch;
  }

  .editor-header-start {
    align-items: flex-start;
  }

  .header-actions {
    justify-content: flex-end;
  }
}

@media (max-width: 640px) {
  .editor-header-inner {
    padding-top: 0.85rem;
    padding-bottom: 0.85rem;
    gap: 1rem;
  }

  .editor-header-start {
    gap: 0.75rem;
  }

  .editor-title {
    font-size: 1.1rem;
  }

  .editor-main {
    padding: 1rem;
  }

  .input-meta-row {
    flex-direction: column;
    gap: 0.2rem;
    align-items: flex-end;
  }

  .header-actions {
    width: 100%;
    justify-content: flex-end;
  }

  .header-actions :deep(.n-button) {
    flex: 1 1 calc(50% - 0.375rem);
  }

  .editor-footer-inner {
    padding: 0.85rem 1rem 1rem;
  }

  .footer-hints {
    gap: 0.5rem;
    justify-content: flex-start;
  }

  .hint {
    font-size: 0.75rem;
    padding: 0.2rem 0.5rem;
  }
}
</style>
