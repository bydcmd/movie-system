<script setup lang="ts">
import { computed, reactive, ref, useTemplateRef, watch } from 'vue'
import { NButton, NForm, NFormItem, NInput, NModal, useMessage } from 'naive-ui'
import { useUploadImage } from '@/api/endpoints/file-management/file-management'
import MoviePlaceholder from '@/components/movie/MoviePlaceholder.vue'
import type { AdminMovieFormMode, AdminMovieFormValue } from '@/utils/adminMovie'
import { createEmptyAdminMovieFormValue } from '@/utils/adminMovie'
import { resolveAssetUrl } from '@/utils/profile'

const show = defineModel<boolean>('show', { required: true })

const props = withDefaults(
  defineProps<{
    mode: AdminMovieFormMode
    initialValue?: AdminMovieFormValue | null
    saving?: boolean
  }>(),
  {
    initialValue: null,
    saving: false
  }
)

const emit = defineEmits<{
  submit: [payload: AdminMovieFormValue]
}>()

const message = useMessage()
const form = reactive<AdminMovieFormValue>(createEmptyAdminMovieFormValue())
const uploadImageMutation = useUploadImage()
const coverInputRef = useTemplateRef<HTMLInputElement>('coverInput')
const coverUploading = ref(false)
const coverLoadError = ref(false)
const ACCEPTED_IMAGE_EXTENSIONS = ['.jpg', '.jpeg', '.png', '.gif', '.webp']
const IMAGE_ACCEPT_ATTRIBUTE = ACCEPTED_IMAGE_EXTENSIONS.join(',')
const MAX_IMAGE_FILE_SIZE = 5 * 1024 * 1024

const dialogTitle = computed(() => (props.mode === 'edit' ? '编辑电影' : '录入电影'))
const submitLabel = computed(() => (props.mode === 'edit' ? '保存修改' : '新增电影'))
const modalStyle = computed(() => ({
  width: 'min(960px, calc(100vw - 1.5rem))'
}))
const coverPreview = computed(() => resolveAssetUrl(form.cover))
const showCoverImage = computed(() => Boolean(coverPreview.value) && !coverLoadError.value)
const coverActionText = computed(() => (coverUploading.value ? '上传中...' : '上传封面'))

function applyFormState(nextValue?: AdminMovieFormValue | null) {
  const source = nextValue ?? createEmptyAdminMovieFormValue()

  form.name = source.name
  form.alias = source.alias
  form.cover = source.cover
  form.score = source.score
  form.doubanScore = source.doubanScore
  form.votes = source.votes
  form.doubanVotes = source.doubanVotes
  form.genres = source.genres
  form.imdbId = source.imdbId
  form.languages = source.languages
  form.mins = source.mins
  form.regions = source.regions
  form.releaseDate = source.releaseDate
  form.storyline = source.storyline
  form.year = source.year
  form.reason = source.reason
  form.actorsInput = source.actorsInput
  form.directorsInput = source.directorsInput
  form.writersInput = source.writersInput
  coverLoadError.value = false
}

function normalizeUploadResult(value: unknown): string | null {
  if (typeof value === 'string') {
    return value
  }

  if (!value || typeof value !== 'object') {
    return null
  }

  const record = value as Record<string, unknown>
  const nestedData = record.data

  if (typeof nestedData === 'string') {
    return nestedData
  }

  if (nestedData && typeof nestedData === 'object') {
    const nestedRecord = nestedData as Record<string, unknown>

    if (typeof nestedRecord.url === 'string') {
      return nestedRecord.url
    }

    if (typeof nestedRecord.path === 'string') {
      return nestedRecord.path
    }
  }

  if (typeof record.url === 'string') {
    return record.url
  }

  if (typeof record.path === 'string') {
    return record.path
  }

  return null
}

function resolveRequestErrorMessage(error: unknown): string {
  if (!error || typeof error !== 'object') {
    return ''
  }

  const record = error as {
    message?: unknown
    response?: {
      data?: {
        message?: unknown
      } | string
    }
  }

  if (typeof record.response?.data === 'string') {
    return record.response.data
  }

  const responseMessage = record.response?.data?.message
  if (typeof responseMessage === 'string') {
    return responseMessage
  }

  return typeof record.message === 'string' ? record.message : ''
}

function triggerCoverPicker() {
  if (props.saving || coverUploading.value) {
    return
  }

  coverInputRef.value?.click()
}

function clearCover() {
  if (props.saving || coverUploading.value) {
    return
  }

  form.cover = ''
  coverLoadError.value = false
}

async function handleCoverChange(event: Event) {
  const target = event.target as HTMLInputElement | null
  const file = target?.files?.[0]

  if (!file) {
    return
  }

  if (file.size > MAX_IMAGE_FILE_SIZE) {
    message.error('封面图片不能超过 5MB')
    if (target) {
      target.value = ''
    }
    return
  }

  coverUploading.value = true

  try {
    const result = await uploadImageMutation.mutateAsync({
      data: {
        file
      }
    })
    const coverUrl = normalizeUploadResult(result)

    if (!coverUrl) {
      throw new Error('cover upload did not return a valid url')
    }

    form.cover = coverUrl
    coverLoadError.value = false
    message.success('电影封面上传成功')
  } catch (error) {
    console.error('Failed to upload movie cover:', error)
    message.error(resolveRequestErrorMessage(error) || '电影封面上传失败，请稍后再试')
  } finally {
    coverUploading.value = false
    if (target) {
      target.value = ''
    }
  }
}

function handleCoverLoad() {
  coverLoadError.value = false
}

function handleCoverError() {
  coverLoadError.value = true
}

function handleSubmit() {
  if (!form.name.trim()) {
    message.warning('请先填写电影名称')
    return
  }

  emit('submit', {
    ...form
  })
}

function closeModal() {
  if (props.saving) {
    return
  }

  show.value = false
}

function handleFormKeydown(event: KeyboardEvent) {
  if (event.isComposing) {
    return
  }

  if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') {
    event.preventDefault()
    handleSubmit()
  }
}

watch(
  () => [show.value, props.mode, props.initialValue] as const,
  ([visible]) => {
    if (!visible) {
      return
    }

    applyFormState(props.initialValue)
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
    class="movie-form-modal"
    :style="modalStyle"
  >
    <div class="space-y-6" @keydown="handleFormKeydown">
      <section class="rounded-[24px] bg-slate-50 px-4 py-4 text-sm leading-6 text-slate-600">
        先维护基础信息，再按需要补充演员、导演和编剧。人物字段支持两种写法：简写模式按行填写，或直接粘贴 JSON
        数组。
      </section>

      <n-form label-placement="top" class="movie-form">
        <section class="space-y-4">
          <div class="flex items-center justify-between">
            <h3 class="text-sm font-semibold text-slate-900">基础信息</h3>
            <span class="text-xs text-slate-500">支持 `Ctrl/Cmd + Enter` 提交</span>
          </div>

          <div class="grid gap-4 md:grid-cols-2">
            <n-form-item label="电影名称" required>
              <n-input
                v-model:value="form.name"
                placeholder="例如：肖申克的救赎"
                maxlength="200"
                show-count
              />
            </n-form-item>

            <n-form-item label="电影别名">
              <n-input
                v-model:value="form.alias"
                placeholder="例如：The Shawshank Redemption / 刺激1995"
                maxlength="500"
              />
            </n-form-item>

            <n-form-item label="电影封面" class="md:col-span-2">
              <div class="cover-field">
                <button
                  type="button"
                  class="cover-preview-button"
                  :disabled="props.saving || coverUploading"
                  :aria-label="coverActionText"
                  :aria-busy="coverUploading"
                  @click="triggerCoverPicker"
                >
                  <img
                    v-if="showCoverImage"
                    :src="coverPreview || undefined"
                    :alt="form.name || '电影封面预览'"
                    class="cover-preview-image"
                    @load="handleCoverLoad"
                    @error="handleCoverError"
                  />
                  <MoviePlaceholder
                    v-else
                    :title="form.name || '电影封面'"
                    class="cover-preview-placeholder"
                  />
                  <span class="cover-preview-overlay">{{ coverActionText }}</span>
                </button>

                <div class="cover-field-body">
                  <input
                    ref="coverInput"
                    type="file"
                    :accept="IMAGE_ACCEPT_ATTRIBUTE"
                    class="cover-file-input"
                    @change="handleCoverChange"
                  />

                  <n-input
                    v-model:value="form.cover"
                    placeholder="支持相对路径或完整 URL"
                    maxlength="500"
                  />

                  <div class="cover-field-actions">
                    <n-button
                      secondary
                      :disabled="props.saving || coverUploading"
                      @click="triggerCoverPicker"
                    >
                      {{ coverActionText }}
                    </n-button>
                    <n-button
                      quaternary
                      :disabled="props.saving || coverUploading || !form.cover.trim()"
                      @click="clearCover"
                    >
                      清空封面
                    </n-button>
                  </div>

                  <p class="cover-field-hint">
                    支持 JPG、PNG、GIF、WEBP，单张不超过 5MB。上传后会自动填入封面地址。
                  </p>
                </div>
              </div>
            </n-form-item>

            <n-form-item label="电影类型">
              <n-input
                v-model:value="form.genres"
                placeholder="例如：剧情,犯罪"
                maxlength="300"
              />
            </n-form-item>

            <n-form-item label="上映年份">
              <n-input
                v-model:value="form.year"
                placeholder="例如：1994"
                inputmode="numeric"
              />
            </n-form-item>

            <n-form-item label="上映日期">
              <n-input
                v-model:value="form.releaseDate"
                placeholder="例如：1994-09-10(多伦多电影节)"
                maxlength="100"
              />
            </n-form-item>

            <n-form-item label="地区">
              <n-input
                v-model:value="form.regions"
                placeholder="例如：美国"
                maxlength="200"
              />
            </n-form-item>

            <n-form-item label="语言">
              <n-input
                v-model:value="form.languages"
                placeholder="例如：英语"
                maxlength="100"
              />
            </n-form-item>

            <n-form-item label="片长">
              <n-input
                v-model:value="form.mins"
                placeholder="例如：142分钟"
                maxlength="50"
              />
            </n-form-item>

            <n-form-item label="IMDB ID">
              <n-input
                v-model:value="form.imdbId"
                placeholder="例如：tt0111161"
                maxlength="50"
              />
            </n-form-item>
          </div>
        </section>

        <section class="mt-2 space-y-4">
          <h3 class="text-sm font-semibold text-slate-900">评分与统计</h3>

          <div class="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
            <n-form-item label="本站评分">
              <n-input
                v-model:value="form.score"
                placeholder="0 - 10"
                inputmode="decimal"
              />
            </n-form-item>

            <n-form-item label="豆瓣评分">
              <n-input
                v-model:value="form.doubanScore"
                placeholder="0 - 10"
                inputmode="decimal"
              />
            </n-form-item>

            <n-form-item label="本站评分人数">
              <n-input
                v-model:value="form.votes"
                placeholder="例如：2956885"
                inputmode="numeric"
              />
            </n-form-item>

            <n-form-item label="豆瓣评分人数">
              <n-input
                v-model:value="form.doubanVotes"
                placeholder="例如：2956885"
                inputmode="numeric"
              />
            </n-form-item>
          </div>
        </section>

        <section class="mt-2 space-y-4">
          <h3 class="text-sm font-semibold text-slate-900">简介与附加信息</h3>

          <div class="grid gap-4 md:grid-cols-2">
            <n-form-item label="剧情简介" class="md:col-span-2">
              <n-input
                v-model:value="form.storyline"
                type="textarea"
                placeholder="补充电影简介"
                maxlength="20000"
                show-count
                :autosize="{ minRows: 5, maxRows: 9 }"
              />
            </n-form-item>

            <n-form-item label="上榜理由" class="md:col-span-2">
              <n-input
                v-model:value="form.reason"
                type="textarea"
                placeholder="冷门佳作榜等场景可以填写推荐理由"
                maxlength="500"
                show-count
                :autosize="{ minRows: 2, maxRows: 4 }"
              />
            </n-form-item>
          </div>
        </section>

        <section class="mt-2 space-y-4">
          <h3 class="text-sm font-semibold text-slate-900">人物信息</h3>

          <div class="grid gap-4 md:grid-cols-2">
            <div class="space-y-2">
              <n-form-item label="演员列表">
                <n-input
                  v-model:value="form.actorsInput"
                  type="textarea"
                  placeholder="每行一个：演员名 | 饰演角色 | 人物ID&#10;也支持 JSON 数组"
                  :autosize="{ minRows: 7, maxRows: 12 }"
                />
              </n-form-item>
              <p class="text-xs leading-6 text-slate-500">
                简写示例：`蒂姆·罗宾斯 | 安迪·杜佛兰 | 1047973`
              </p>
            </div>

            <div class="space-y-2">
              <n-form-item label="导演列表">
                <n-input
                  v-model:value="form.directorsInput"
                  type="textarea"
                  placeholder="每行一个：导演名 | 人物ID&#10;也支持 JSON 数组"
                  :autosize="{ minRows: 7, maxRows: 12 }"
                />
              </n-form-item>
              <p class="text-xs leading-6 text-slate-500">
                简写示例：`弗兰克·德拉邦特 | 1047977`
              </p>
            </div>

            <div class="space-y-2 md:col-span-2">
              <n-form-item label="编剧列表">
                <n-input
                  v-model:value="form.writersInput"
                  type="textarea"
                  placeholder="每行一个：编剧名 | 人物ID&#10;也支持 JSON 数组"
                  :autosize="{ minRows: 5, maxRows: 10 }"
                />
              </n-form-item>
              <p class="text-xs leading-6 text-slate-500">
                如果原始数据包含额外字段，直接粘贴 JSON 数组可以完整保留。
              </p>
            </div>
          </div>
        </section>
      </n-form>
    </div>

    <template #footer>
      <div class="flex justify-end gap-3">
        <n-button :disabled="props.saving" @click="closeModal">
          取消
        </n-button>
        <n-button type="primary" :loading="props.saving" @click="handleSubmit">
          {{ submitLabel }}
        </n-button>
      </div>
    </template>
  </n-modal>
</template>

<style scoped>
.movie-form-modal :deep(.n-card) {
  overflow: hidden;
  border-radius: 18px;
}

.movie-form-modal :deep(.n-card__content) {
  max-height: min(72vh, 48rem);
  overflow-y: auto;
  padding-right: 0.5rem;
}

.movie-form :deep(.n-form-item) {
  margin-bottom: 0.15rem;
}

.movie-form :deep(.n-form-item-label) {
  font-weight: 600;
}

.cover-field {
  display: grid;
  gap: 1rem;
  width: 100%;
}

.cover-preview-button {
  position: relative;
  display: flex;
  width: min(100%, 11rem);
  aspect-ratio: 2 / 3;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border: 1px dashed rgb(148 163 184);
  border-radius: 1rem;
  background:
    linear-gradient(180deg, rgb(248 250 252) 0%, rgb(226 232 240) 100%);
  color: rgb(71 85 105);
  transition:
    border-color 0.2s ease,
    transform 0.2s ease,
    box-shadow 0.2s ease;
}

.cover-preview-button:hover:not(:disabled),
.cover-preview-button:focus-visible {
  border-color: rgb(59 130 246);
  transform: translateY(-1px);
  box-shadow: 0 16px 32px rgba(15, 23, 42, 0.12);
}

.cover-preview-button:focus-visible {
  outline: 2px solid rgba(59, 130, 246, 0.32);
  outline-offset: 4px;
}

.cover-preview-button:disabled {
  cursor: progress;
}

.cover-preview-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cover-preview-overlay {
  position: absolute;
  inset: auto 0 0 0;
  padding: 0.85rem 0.75rem 0.75rem;
  background: linear-gradient(180deg, rgba(15, 23, 42, 0) 0%, rgba(15, 23, 42, 0.76) 100%);
  color: rgb(255 255 255);
  font-size: 0.8rem;
  font-weight: 600;
  letter-spacing: 0.02em;
}

.cover-preview-placeholder {
  width: 100%;
  height: 100%;
}

.cover-file-input {
  display: none;
}

.cover-field-body {
  display: grid;
  gap: 0.75rem;
  min-width: 0;
}

.cover-field-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
}

.cover-field-hint {
  margin: 0;
  font-size: 0.8rem;
  line-height: 1.6;
  color: rgb(100 116 139);
}

@media (min-width: 768px) {
  .cover-field {
    grid-template-columns: 11rem minmax(0, 1fr);
    align-items: start;
  }
}
</style>
