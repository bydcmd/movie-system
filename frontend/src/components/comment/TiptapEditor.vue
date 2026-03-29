<script setup lang="ts">
import { computed, onBeforeUnmount, shallowRef, useTemplateRef, watch } from 'vue'
import { NButton, useMessage } from 'naive-ui'
import Placeholder from '@tiptap/extension-placeholder'
import StarterKit from '@tiptap/starter-kit'
import Underline from '@tiptap/extension-underline'
import { EditorContent, useEditor } from '@tiptap/vue-3'
import { useUploadImage } from '@/api/endpoints/file-management/file-management'
import { RichImage } from '@/components/comment/extensions/RichImage'
import { createEmptyTiptapDocument, createParagraphDocument, parseTiptapJson } from '@/utils/comment'

const model = defineModel<string>({ required: true })

const props = withDefaults(
  defineProps<{
    editable?: boolean
    placeholder?: string
    minHeight?: number
    autofocus?: boolean
  }>(),
  {
    editable: true,
    placeholder: '写点什么...',
    minHeight: 240,
    autofocus: false
  }
)

const emit = defineEmits<{
  'update:plainText': [value: string]
}>()

type ToolbarAction = {
  key: string
  label: string
  title: string
  active: boolean
  disabled: boolean
  loading: boolean
  run: () => void
}

const editorStateTick = shallowRef(0)
const editorFocused = shallowRef(false)
const imageUploadCount = shallowRef(0)
const message = useMessage()
const uploadImageMutation = useUploadImage()
const imageInputRef = useTemplateRef<HTMLInputElement>('imageInput')
const shortcutHints = [
  'Ctrl / Cmd + B 加粗',
  '拖拽 / 粘贴图片上传',
  'Ctrl / Cmd + Z 撤销'
]
const ACCEPTED_IMAGE_TYPES = new Set(['image/jpeg', 'image/png', 'image/gif', 'image/webp'])
const ACCEPTED_IMAGE_EXTENSIONS = ['.jpg', '.jpeg', '.png', '.gif', '.webp']
const IMAGE_ACCEPT_ATTRIBUTE = ACCEPTED_IMAGE_EXTENSIONS.join(',')
const MAX_IMAGE_FILE_SIZE = 5 * 1024 * 1024

const isUploadingImage = computed(() => imageUploadCount.value > 0)

function resolveContent(value?: string): Record<string, unknown> {
  const parsed = parseTiptapJson(value)
  if (parsed) {
    return parsed
  }

  if (value?.trim()) {
    return JSON.parse(createParagraphDocument(value))
  }

  return JSON.parse(createEmptyTiptapDocument())
}

function normalizeJsonString(value?: string): string {
  return JSON.stringify(resolveContent(value))
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

function isAcceptedImageFile(file: File): boolean {
  const normalizedName = file.name.toLowerCase()
  return (
    ACCEPTED_IMAGE_TYPES.has(file.type)
    || ACCEPTED_IMAGE_EXTENSIONS.some((extension) => normalizedName.endsWith(extension))
  )
}

function extractImageFiles(dataTransfer?: DataTransfer | null): File[] {
  const directFiles = Array.from(dataTransfer?.files ?? []).filter(isAcceptedImageFile)
  if (directFiles.length > 0) {
    return directFiles
  }

  return Array.from(dataTransfer?.items ?? [])
    .filter((item) => item.kind === 'file')
    .map((item) => item.getAsFile())
    .filter((file): file is File => file instanceof File)
    .filter((file) => isAcceptedImageFile(file))
}

async function uploadAndInsertImage(file: File) {
  if (!props.editable) {
    return
  }

  if (!isAcceptedImageFile(file)) {
    message.warning('仅支持 JPG、PNG、GIF、WEBP 图片')
    return
  }

  if (file.size > MAX_IMAGE_FILE_SIZE) {
    message.warning('图片大小不能超过 5MB')
    return
  }

  imageUploadCount.value += 1

  try {
    const result = await uploadImageMutation.mutateAsync({
      data: {
        file
      }
    })
    const imageUrl = normalizeUploadResult(result)

    if (!imageUrl) {
      throw new Error('image upload did not return a valid url')
    }

    const instance = editor.value
    if (!instance) {
      return
    }

    instance.chain().focus().insertContent({
      type: 'image',
      attrs: {
        src: imageUrl,
        alt: file.name || '长评插图',
        title: file.name || '长评插图'
      }
    }).run()
  } catch (error) {
    console.error('Failed to upload review image:', error)
    message.error(resolveRequestErrorMessage(error) || '图片上传失败，请稍后再试')
  } finally {
    imageUploadCount.value = Math.max(0, imageUploadCount.value - 1)
  }
}

async function uploadAndInsertImages(fileList: FileList | File[]) {
  const files = Array.from(fileList).filter(Boolean)
  for (const file of files) {
    // Upload sequentially so image order matches the user's selection order.
    // This also keeps the toolbar state predictable while a batch is in flight.
    // eslint-disable-next-line no-await-in-loop
    await uploadAndInsertImage(file)
  }
}

function triggerImagePicker() {
  if (!props.editable || isUploadingImage.value) {
    return
  }

  imageInputRef.value?.click()
}

function handleImageChange(event: Event) {
  const target = event.target as HTMLInputElement | null
  const files = target?.files
  if (!files?.length) {
    return
  }

  void uploadAndInsertImages(files).finally(() => {
    if (target) {
      target.value = ''
    }
  })
}

const editor = useEditor({
  autofocus: props.autofocus,
  editable: props.editable,
  content: resolveContent(model.value),
  extensions: [
    StarterKit.configure({
      heading: {
        levels: [2, 3]
      },
      link: {
        autolink: true,
        linkOnPaste: true,
        openOnClick: !props.editable
      },
      ...(props.editable
        ? {
            code: false,
            codeBlock: false
          }
        : {})
    }),
    Underline,
    RichImage,
    Placeholder.configure({
      placeholder: props.placeholder,
      showOnlyWhenEditable: true
    })
  ],
  editorProps: {
    handlePaste: (_view, event) => {
      if (!props.editable) {
        return false
      }

      const files = extractImageFiles(event.clipboardData)
      if (!files.length) {
        return false
      }

      event.preventDefault()
      void uploadAndInsertImages(files)
      return true
    },
    handleDrop: (_view, event) => {
      if (!props.editable) {
        return false
      }

      const files = extractImageFiles(event.dataTransfer)
      if (!files.length) {
        return false
      }

      event.preventDefault()
      void uploadAndInsertImages(files)
      return true
    }
  },
  onCreate: () => {
    emit('update:plainText', editor.value?.getText().trim() ?? '')
    editorStateTick.value += 1
  },
  onUpdate: ({ editor }) => {
    model.value = JSON.stringify(editor.getJSON())
    emit('update:plainText', editor.getText().trim())
    editorStateTick.value += 1
  },
  onSelectionUpdate: () => {
    editorStateTick.value += 1
  },
  onFocus: () => {
    editorFocused.value = true
    editorStateTick.value += 1
  },
  onBlur: () => {
    editorFocused.value = false
    editorStateTick.value += 1
  }
})

const editorStyle = computed(() => ({
  '--comment-editor-min-height': `${props.minHeight}px`
}))

watch(
  model,
  (nextValue) => {
    const instance = editor.value
    if (!instance) {
      return
    }

    if (normalizeJsonString(nextValue) === JSON.stringify(instance.getJSON())) {
      emit('update:plainText', instance.getText().trim())
      return
    }

    instance.commands.setContent(resolveContent(nextValue), { emitUpdate: false })
    emit('update:plainText', instance.getText().trim())
    editorStateTick.value += 1
  },
  { immediate: true }
)

watch(
  () => props.editable,
  (editable) => {
    editor.value?.setEditable(editable)
    editorStateTick.value += 1
  }
)

onBeforeUnmount(() => {
  editor.value?.destroy()
})

function runCommand(command: (instance: NonNullable<typeof editor.value>) => boolean) {
  const instance = editor.value
  if (!instance || !props.editable) {
    return
  }

  command(instance)
}

function isActive(name: string, attrs?: Record<string, unknown>): boolean {
  return editor.value?.isActive(name, attrs) ?? false
}

function canRun(command: (instance: NonNullable<typeof editor.value>) => boolean): boolean {
  const instance = editor.value
  if (!instance || !props.editable) {
    return false
  }

  return command(instance)
}

function createToolbarAction(
  key: string,
  label: string,
  title: string,
  active: boolean,
  disabled: boolean,
  run: () => void,
  loading = false
): ToolbarAction {
  return {
    key,
    label,
    title,
    active,
    disabled,
    loading,
    run
  }
}

const toolbarGroups = computed<ToolbarAction[][]>(() => {
  editorStateTick.value

  return [
    [
      createToolbarAction(
        'paragraph',
        '正文',
        '切换为正文',
        isActive('paragraph'),
        !canRun((instance) => instance.can().chain().focus().setParagraph().run()),
        () => runCommand((instance) => instance.chain().focus().setParagraph().run())
      ),
      createToolbarAction(
        'heading-2',
        'H2',
        '二级标题',
        isActive('heading', { level: 2 }),
        !canRun((instance) => instance.can().chain().focus().toggleHeading({ level: 2 }).run()),
        () => runCommand((instance) => instance.chain().focus().toggleHeading({ level: 2 }).run())
      ),
      createToolbarAction(
        'heading-3',
        'H3',
        '三级标题',
        isActive('heading', { level: 3 }),
        !canRun((instance) => instance.can().chain().focus().toggleHeading({ level: 3 }).run()),
        () => runCommand((instance) => instance.chain().focus().toggleHeading({ level: 3 }).run())
      )
    ],
    [
      createToolbarAction(
        'bold',
        '加粗',
        '加粗',
        isActive('bold'),
        !canRun((instance) => instance.can().chain().focus().toggleBold().run()),
        () => runCommand((instance) => instance.chain().focus().toggleBold().run())
      ),
      createToolbarAction(
        'italic',
        '斜体',
        '斜体',
        isActive('italic'),
        !canRun((instance) => instance.can().chain().focus().toggleItalic().run()),
        () => runCommand((instance) => instance.chain().focus().toggleItalic().run())
      ),
      createToolbarAction(
        'underline',
        '下划线',
        '下划线',
        isActive('underline'),
        !canRun((instance) => instance.can().chain().focus().toggleUnderline().run()),
        () => runCommand((instance) => instance.chain().focus().toggleUnderline().run())
      ),
      createToolbarAction(
        'strike',
        '删除线',
        '删除线',
        isActive('strike'),
        !canRun((instance) => instance.can().chain().focus().toggleStrike().run()),
        () => runCommand((instance) => instance.chain().focus().toggleStrike().run())
      )
    ],
    [
      createToolbarAction(
        'bullet-list',
        '列表',
        '无序列表',
        isActive('bulletList'),
        !canRun((instance) => instance.can().chain().focus().toggleBulletList().run()),
        () => runCommand((instance) => instance.chain().focus().toggleBulletList().run())
      ),
      createToolbarAction(
        'ordered-list',
        '编号',
        '有序列表',
        isActive('orderedList'),
        !canRun((instance) => instance.can().chain().focus().toggleOrderedList().run()),
        () => runCommand((instance) => instance.chain().focus().toggleOrderedList().run())
      ),
      createToolbarAction(
        'blockquote',
        '引用',
        '引用块',
        isActive('blockquote'),
        !canRun((instance) => instance.can().chain().focus().toggleBlockquote().run()),
        () => runCommand((instance) => instance.chain().focus().toggleBlockquote().run())
      ),
      createToolbarAction(
        'divider',
        '分隔线',
        '插入分隔线',
        false,
        !canRun((instance) => instance.can().chain().focus().setHorizontalRule().run()),
        () => runCommand((instance) => instance.chain().focus().setHorizontalRule().run())
      )
    ],
    [
      createToolbarAction(
        'image',
        isUploadingImage.value ? '上传中' : '图片',
        '上传并插入图片',
        false,
        !props.editable || isUploadingImage.value,
        () => triggerImagePicker(),
        isUploadingImage.value
      ),
      createToolbarAction(
        'undo',
        '撤销',
        '撤销',
        false,
        !canRun((instance) => instance.can().chain().focus().undo().run()),
        () => runCommand((instance) => instance.chain().focus().undo().run())
      ),
      createToolbarAction(
        'redo',
        '重做',
        '重做',
        false,
        !canRun((instance) => instance.can().chain().focus().redo().run()),
        () => runCommand((instance) => instance.chain().focus().redo().run())
      ),
      createToolbarAction(
        'clear-format',
        '清空格式',
        '清空当前格式',
        false,
        !canRun((instance) => instance.can().chain().focus().unsetAllMarks().clearNodes().run()),
        () => runCommand((instance) => instance.chain().focus().unsetAllMarks().clearNodes().run())
      )
    ]
  ]
})

const editorStats = computed(() => {
  editorStateTick.value

  const instance = editor.value
  const plainText = instance?.getText().trim() ?? ''
  const blockCount = plainText ? (instance?.state.doc.childCount ?? 0) : 0

  if (!instance) {
    return {
      plainTextLength: 0,
      blockCount: 0,
      currentBlockLabel: '正文'
    }
  }

  if (instance.isActive('blockquote')) {
    return {
      plainTextLength: plainText.length,
      blockCount,
      currentBlockLabel: '引用块'
    }
  }

  if (instance.isActive('orderedList')) {
    return {
      plainTextLength: plainText.length,
      blockCount,
      currentBlockLabel: '编号列表'
    }
  }

  if (instance.isActive('bulletList')) {
    return {
      plainTextLength: plainText.length,
      blockCount,
      currentBlockLabel: '项目列表'
    }
  }

  if (instance.isActive('heading', { level: 2 })) {
    return {
      plainTextLength: plainText.length,
      blockCount,
      currentBlockLabel: '二级标题'
    }
  }

  if (instance.isActive('heading', { level: 3 })) {
    return {
      plainTextLength: plainText.length,
      blockCount,
      currentBlockLabel: '三级标题'
    }
  }

  return {
    plainTextLength: plainText.length,
    blockCount,
    currentBlockLabel: '正文'
  }
})

function focusEditor() {
  if (!props.editable) {
    return
  }

  editor.value?.chain().focus().run()
}

defineExpose({
  focusEditor
})
</script>

<template>
  <div
    class="editor-root"
    :class="{ 'is-readonly': !props.editable, 'is-focused': editorFocused }"
    :style="editorStyle"
  >
    <div v-if="props.editable" class="editor-toolbar">
      <div v-for="group in toolbarGroups" :key="group[0]?.key" class="editor-toolbar-group">
        <n-button
          v-for="action in group"
          :key="action.key"
          quaternary
          round
          size="small"
          :type="action.active ? 'primary' : 'default'"
          :disabled="action.disabled"
          :loading="action.loading"
          :title="action.title"
          @click="action.run"
        >
          {{ action.label }}
        </n-button>
      </div>
    </div>

    <input
      v-if="props.editable"
      ref="imageInput"
      type="file"
      :accept="IMAGE_ACCEPT_ATTRIBUTE"
      multiple
      class="editor-file-input"
      @change="handleImageChange"
    />

    <div class="editor-shell">
      <EditorContent v-if="editor" :editor="editor" class="editor-content" />
      <div v-else class="editor-loading">编辑器正在初始化...</div>
    </div>

    <div v-if="props.editable" class="editor-footer">
      <div class="editor-stats">
        <span class="editor-stat">{{ editorStats.plainTextLength }} 字</span>
        <span class="editor-stat">{{ editorStats.blockCount }} 个段落</span>
        <span class="editor-stat editor-stat--accent">{{ editorStats.currentBlockLabel }}</span>
      </div>
      <div class="editor-shortcuts">
        <span v-for="hint in shortcutHints" :key="hint" class="editor-shortcut">{{ hint }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.editor-root {
  border-radius: 8px;
  border: 1px solid rgb(226 232 240);
  background: rgb(255 255 255);
  overflow: hidden;
}

.editor-root.is-focused {
  border-color: rgb(59 130 246);
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
}

.editor-root.is-readonly {
  border-color: transparent;
  background: transparent;
}

.editor-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 0.875rem;
  padding: 0.75rem 1rem 0.5rem;
  background: rgb(249 250 251);
  border-bottom: 1px solid rgb(241 245 249);
}

.editor-toolbar-group {
  display: flex;
  flex-wrap: wrap;
  gap: 0.375rem;
  padding: 0.25rem;
  background: rgb(255 255 255);
  border-radius: 6px;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.05);
}

.editor-shell {
  min-height: var(--comment-editor-min-height);
}

.editor-loading {
  display: flex;
  min-height: var(--comment-editor-min-height);
  align-items: center;
  justify-content: center;
  color: rgb(100 116 139);
  font-size: 0.95rem;
}

.editor-content {
  min-height: var(--comment-editor-min-height);
}

.editor-file-input {
  display: none;
}

.editor-root :deep(.ProseMirror) {
  min-height: var(--comment-editor-min-height);
  padding: 1.125rem 1.25rem 1.25rem;
  color: rgb(15 23 42);
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Noto Sans SC', 'Roboto',
               'Microsoft YaHei', 'PingFang SC', sans-serif;
  font-size: 0.975rem;
  line-height: 1.75;
  text-rendering: optimizeLegibility;
  outline: none;
  white-space: pre-wrap;
  word-break: break-word;
}

.editor-root.is-readonly :deep(.ProseMirror) {
  min-height: auto;
  padding: 0;
}

.editor-root :deep(.ProseMirror p.is-editor-empty:first-child::before) {
  content: attr(data-placeholder);
  color: rgb(148 163 184);
  pointer-events: none;
  float: left;
  height: 0;
}

.editor-root :deep(.ProseMirror h2) {
  margin: 0.5rem 0 1rem;
  font-size: 1.35rem;
  font-weight: 700;
  line-height: 1.35;
  letter-spacing: -0.01em;
}

.editor-root :deep(.ProseMirror h3) {
  margin: 0.4rem 0 0.85rem;
  font-size: 1.1rem;
  font-weight: 700;
  line-height: 1.45;
  letter-spacing: -0.005em;
}

.editor-root :deep(.ProseMirror p) {
  margin: 0 0 1rem;
}

.editor-root :deep(.ProseMirror img) {
  display: block;
  max-width: 100%;
  height: auto;
  margin: 1rem auto;
  border-radius: 0.85rem;
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.08);
}

.editor-root :deep(.ProseMirror a) {
  color: rgb(3 105 161);
  text-decoration: underline;
  text-decoration-thickness: 1.5px;
  text-underline-offset: 0.15em;
}

.editor-root :deep(.ProseMirror code) {
  border-radius: 4px;
  background: rgb(226 232 240 / 0.9);
  padding: 0.16rem 0.38rem;
  color: rgb(15 23 42);
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 0.92em;
}

.editor-root :deep(.ProseMirror pre) {
  margin: 0.9rem 0 1rem;
  overflow-x: auto;
  border-radius: 8px;
  background: rgb(15 23 42);
  padding: 0.95rem 1rem;
}

.editor-root :deep(.ProseMirror pre code) {
  background: transparent;
  padding: 0;
  color: rgb(226 232 240);
}

.editor-root :deep(.ProseMirror ul),
.editor-root :deep(.ProseMirror ol) {
  margin: 0.75rem 0 0.95rem;
  padding-left: 1.35rem;
}

.editor-root :deep(.ProseMirror li) {
  margin-bottom: 0.4rem;
  line-height: 1.65;
}

.editor-root :deep(.ProseMirror blockquote) {
  margin: 0.85rem 0;
  border-left: 4px solid rgb(251 191 36);
  background: rgb(255 251 235 / 0.7);
  padding: 0.75rem 1rem;
  color: rgb(71 85 105);
}

.editor-root :deep(.ProseMirror blockquote p:last-child) {
  margin-bottom: 0;
}

.editor-root :deep(.ProseMirror hr) {
  margin: 1.2rem 0;
  border: none;
  border-top: 1px solid rgb(203 213 225);
}

/* 覆盖 Tailwind preflight 对 em/i 的 font-style: normal 重置 */
.editor-root :deep(.ProseMirror) em,
.editor-root :deep(.ProseMirror) i {
  font-style: italic !important;
}

.editor-root :deep(.ProseMirror ul) {
  list-style-type: disc;
}

.editor-root :deep(.ProseMirror ol) {
  list-style-type: decimal;
}

.editor-footer {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 0.75rem 1rem;
  border-top: 1px solid rgb(226 232 240);
  background: rgb(249 250 251);
  padding: 0.75rem 1rem 0.85rem;
}

.editor-stats,
.editor-shortcuts {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}

.editor-stat,
.editor-shortcut {
  display: inline-flex;
  align-items: center;
  padding: 0.25rem 0.5rem;
  color: rgb(71 85 105);
  font-size: 0.75rem;
  line-height: 1.4;
  background: rgb(255 255 255);
  border-radius: 4px;
  border: 1px solid rgb(226 232 240);
}

.editor-stat--accent {
  color: rgb(14 116 144);
  font-weight: 600;
  background: rgb(240 249 255);
  border-color: rgb(186 230 253);
}
</style>
