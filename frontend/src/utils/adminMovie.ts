import type { AdminMovieDTO, Movie } from '@/api/model'
import { isRecord } from '@/utils/admin'

export type AdminMovieFormMode = 'create' | 'edit'

export type AdminMovieFormValue = {
  name: string
  alias: string
  cover: string
  score: string
  doubanScore: string
  votes: string
  doubanVotes: string
  genres: string
  imdbId: string
  languages: string
  mins: string
  regions: string
  releaseDate: string
  storyline: string
  year: string
  reason: string
  actorsInput: string
  directorsInput: string
  writersInput: string
}

type MoviePeopleKind = 'actor' | 'crew'

const ACTOR_KEYS = ['id', 'name', 'role']
const CREW_KEYS = ['id', 'name']

export function createEmptyAdminMovieFormValue(): AdminMovieFormValue {
  return {
    name: '',
    alias: '',
    cover: '',
    score: '',
    doubanScore: '',
    votes: '',
    doubanVotes: '',
    genres: '',
    imdbId: '',
    languages: '',
    mins: '',
    regions: '',
    releaseDate: '',
    storyline: '',
    year: '',
    reason: '',
    actorsInput: '',
    directorsInput: '',
    writersInput: ''
  }
}

function toInputString(value?: string | number | null): string {
  return value === undefined || value === null ? '' : String(value)
}

function toOptionalString(value: string): string | undefined {
  const trimmed = value.trim()
  return trimmed || undefined
}

function stringifySimpleValue(value: unknown): string {
  if (typeof value === 'string') {
    return value.trim()
  }

  if (typeof value === 'number' && Number.isFinite(value)) {
    return String(value)
  }

  return ''
}

function hasOnlyKnownKeys(record: Record<string, unknown>, kind: MoviePeopleKind): boolean {
  const allowedKeys = kind === 'actor' ? ACTOR_KEYS : CREW_KEYS
  return Object.keys(record).every((key) => allowedKeys.includes(key))
}

function formatPeopleLine(record: Record<string, unknown>, kind: MoviePeopleKind): string | null {
  const name = stringifySimpleValue(record.name)
  if (!name) {
    return null
  }

  const id = stringifySimpleValue(record.id)
  if (kind === 'actor') {
    const role = stringifySimpleValue(record.role)
    return [name, role, id].filter(Boolean).join(' | ')
  }

  return [name, id].filter(Boolean).join(' | ')
}

function stringifyMoviePeople(value: unknown, kind: MoviePeopleKind): string {
  if (!Array.isArray(value) || value.length === 0) {
    return ''
  }

  if (value.every((item) => typeof item === 'string')) {
    return value
      .map((item) => item.trim())
      .filter(Boolean)
      .join('\n')
  }

  if (value.every((item) => isRecord(item) && hasOnlyKnownKeys(item, kind))) {
    return value
      .map((item) => formatPeopleLine(item as Record<string, unknown>, kind))
      .filter((item): item is string => Boolean(item))
      .join('\n')
  }

  return JSON.stringify(value, null, 2)
}

export function toAdminMovieFormValue(movie?: Movie | null): AdminMovieFormValue {
  if (!movie) {
    return createEmptyAdminMovieFormValue()
  }

  return {
    name: movie.name ?? '',
    alias: movie.alias ?? '',
    cover: movie.cover ?? '',
    score: toInputString(movie.score),
    doubanScore: toInputString(movie.doubanScore),
    votes: toInputString(movie.votes),
    doubanVotes: toInputString(movie.doubanVotes),
    genres: movie.genres ?? '',
    imdbId: movie.imdbId ?? '',
    languages: movie.languages ?? '',
    mins: movie.mins ?? '',
    regions: movie.regions ?? '',
    releaseDate: movie.releaseDate ?? '',
    storyline: movie.storyline ?? '',
    year: toInputString(movie.year),
    reason: movie.reason ?? '',
    actorsInput: stringifyMoviePeople(movie.actors, 'actor'),
    directorsInput: stringifyMoviePeople(movie.directors, 'crew'),
    writersInput: stringifyMoviePeople(movie.writers, 'crew')
  }
}

function parseNumberField(
  label: string,
  value: string,
  options?: {
    integer?: boolean
    min?: number
    max?: number
  }
): number | undefined {
  const trimmed = value.trim()
  if (!trimmed) {
    return undefined
  }

  const parsedValue = Number(trimmed)
  if (!Number.isFinite(parsedValue)) {
    throw new Error(`${label} 必须是有效数字`)
  }

  if (options?.integer && !Number.isInteger(parsedValue)) {
    throw new Error(`${label} 必须是整数`)
  }

  if (options?.min !== undefined && parsedValue < options.min) {
    throw new Error(`${label} 不能小于 ${options.min}`)
  }

  if (options?.max !== undefined && parsedValue > options.max) {
    throw new Error(`${label} 不能大于 ${options.max}`)
  }

  return options?.integer ? Math.trunc(parsedValue) : parsedValue
}

function parseJsonPeopleField(label: string, value: string): Record<string, unknown>[] {
  let parsedValue: unknown

  try {
    parsedValue = JSON.parse(value)
  } catch (error) {
    console.error(`Failed to parse ${label}:`, error)
    throw new Error(`${label} JSON 格式不正确`)
  }

  if (!Array.isArray(parsedValue)) {
    throw new Error(`${label} 必须是数组`)
  }

  return parsedValue.map((item, index) => {
    if (typeof item === 'string') {
      const name = item.trim()
      if (!name) {
        throw new Error(`${label} 第 ${index + 1} 项不能为空`)
      }
      return {
        name
      }
    }

    if (!isRecord(item)) {
      throw new Error(`${label} 第 ${index + 1} 项必须是对象`)
    }

    return item
  })
}

function parseLinePeopleField(
  label: string,
  value: string,
  kind: MoviePeopleKind
): Record<string, unknown>[] {
  return value
    .split('\n')
    .map((item) => item.trim())
    .filter(Boolean)
    .map((item, index) => {
      const [namePart = '', secondPart = '', thirdPart = ''] = item.split('|').map((part) => part.trim())
      if (!namePart) {
        throw new Error(`${label} 第 ${index + 1} 行缺少名称`)
      }

      if (kind === 'actor') {
        const actor: Record<string, unknown> = {
          name: namePart
        }

        if (secondPart) {
          actor.role = secondPart
        }

        if (thirdPart) {
          actor.id = thirdPart
        }

        return actor
      }

      const crew: Record<string, unknown> = {
        name: namePart
      }

      if (secondPart) {
        crew.id = secondPart
      }

      return crew
    })
}

function parsePeopleField(
  label: string,
  value: string,
  kind: MoviePeopleKind
): Record<string, unknown>[] | undefined {
  const trimmed = value.trim()
  if (!trimmed) {
    return undefined
  }

  if (trimmed.startsWith('[')) {
    return parseJsonPeopleField(label, trimmed)
  }

  return parseLinePeopleField(label, trimmed, kind)
}

export function buildAdminMoviePayload(formValue: AdminMovieFormValue): AdminMovieDTO {
  const name = formValue.name.trim()
  if (!name) {
    throw new Error('电影名称不能为空')
  }

  return {
    name,
    alias: toOptionalString(formValue.alias),
    actors: parsePeopleField('演员列表', formValue.actorsInput, 'actor'),
    cover: toOptionalString(formValue.cover),
    directors: parsePeopleField('导演列表', formValue.directorsInput, 'crew'),
    score: parseNumberField('本站评分', formValue.score, {
      min: 0,
      max: 10
    }),
    doubanScore: parseNumberField('豆瓣评分', formValue.doubanScore, {
      min: 0,
      max: 10
    }),
    votes: parseNumberField('本站评分人数', formValue.votes, {
      integer: true,
      min: 0
    }),
    doubanVotes: parseNumberField('豆瓣评分人数', formValue.doubanVotes, {
      integer: true,
      min: 0
    }),
    genres: toOptionalString(formValue.genres),
    imdbId: toOptionalString(formValue.imdbId),
    languages: toOptionalString(formValue.languages),
    mins: toOptionalString(formValue.mins),
    regions: toOptionalString(formValue.regions),
    releaseDate: toOptionalString(formValue.releaseDate),
    storyline: toOptionalString(formValue.storyline),
    year: parseNumberField('上映年份', formValue.year, {
      integer: true,
      min: 1888,
      max: 2100
    }),
    writers: parsePeopleField('编剧列表', formValue.writersInput, 'crew'),
    reason: toOptionalString(formValue.reason)
  }
}
