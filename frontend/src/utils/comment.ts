import type { Comment, CommentVO } from '@/api/model'

type JsonRecord = Record<string, unknown>

export type ReviewComposerTab = 'short' | 'long'
export type CommentFilter = 'short' | 'long'

export type ReviewSubmitPayload =
  | {
      type: 'short'
      content: string
    }
  | {
      type: 'long'
      title: string
      content: string
      plainText: string
    }

const EMPTY_TIPTAP_DOCUMENT: JsonRecord = {
  type: 'doc',
  content: [{ type: 'paragraph' }]
}

const BLOCK_NODE_TYPES = new Set([
  'paragraph',
  'heading',
  'blockquote',
  'bulletList',
  'orderedList',
  'listItem',
  'codeBlock'
])

type CommentPreviewSource = Pick<Comment, 'type' | 'content' | 'title'> &
  Partial<Pick<CommentVO, 'contentSummary' | 'isJsonContent'>>

export function createEmptyTiptapDocument(): string {
  return JSON.stringify(EMPTY_TIPTAP_DOCUMENT)
}

export function isLongReview(type?: number | null): boolean {
  return type === 2
}

export function getCommentTypeLabel(type?: number | null): string {
  return isLongReview(type) ? '长评' : '短评'
}

export function parseTiptapJson(content?: string | null): JsonRecord | null {
  if (!content?.trim()) {
    return null
  }

  try {
    const parsed = JSON.parse(content) as unknown
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
      return null
    }

    const record = parsed as JsonRecord
    if (record.type !== 'doc' && record.type !== 'paragraph') {
      return null
    }

    return record
  } catch {
    return null
  }
}

export function hasRichTextContent(content?: string | null): boolean {
  return Boolean(parseTiptapJson(content))
}

function joinNodeText(type: unknown, parts: string[]): string {
  const delimiter = BLOCK_NODE_TYPES.has(String(type)) ? '\n\n' : ''
  return parts.filter(Boolean).join(delimiter)
}

function extractNodeText(node: unknown): string {
  if (!node) {
    return ''
  }

  if (typeof node === 'string') {
    return node
  }

  if (Array.isArray(node)) {
    return node
      .map((item) => extractNodeText(item))
      .filter(Boolean)
      .join('')
  }

  if (typeof node !== 'object') {
    return ''
  }

  const record = node as JsonRecord

  if (typeof record.text === 'string') {
    return record.text
  }

  if (record.type === 'hardBreak') {
    return '\n'
  }

  const children = Array.isArray(record.content) ? record.content : []
  const childText = children
    .map((item) => extractNodeText(item))
    .filter(Boolean)

  return joinNodeText(record.type, childText)
}

function normalizeCommentText(value: string): string {
  return value
    .replace(/\r\n/g, '\n')
    .replace(/\n{3,}/g, '\n\n')
    .replace(/[ \t]+\n/g, '\n')
    .replace(/\n[ \t]+/g, '\n')
    .trim()
}

export function extractTiptapText(content?: string | null): string {
  const parsed = parseTiptapJson(content)
  if (!parsed) {
    return content?.trim() ?? ''
  }

  return normalizeCommentText(extractNodeText(parsed))
}

export function truncateCommentText(value: string, maxLength = 160): string {
  if (value.length <= maxLength) {
    return value
  }

  return `${value.slice(0, maxLength).trimEnd()}...`
}

export function countReadableCharacters(value: string): number {
  return value.replace(/\s+/g, '').length
}

export function estimateReadingMinutes(
  value: string,
  charactersPerMinute = 320
): number {
  const totalCharacters = countReadableCharacters(value)
  if (!totalCharacters) {
    return 0
  }

  return Math.max(1, Math.ceil(totalCharacters / charactersPerMinute))
}

export function getCommentPreviewText(
  comment: CommentPreviewSource,
  maxLength = 160
): string {
  const summary =
    isLongReview(comment.type) && comment.contentSummary?.trim()
      ? comment.contentSummary.trim()
      : extractTiptapText(comment.content)

  if (!summary) {
    return '暂无内容'
  }

  return truncateCommentText(summary, maxLength)
}

export function createParagraphDocument(text: string): string {
  const normalized = text.trim()
  if (!normalized) {
    return createEmptyTiptapDocument()
  }

  return JSON.stringify({
    type: 'doc',
    content: normalized.split(/\n+/).map((line) => ({
      type: 'paragraph',
      content: [{ type: 'text', text: line }]
    }))
  })
}
