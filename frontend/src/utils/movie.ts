export type MovieId = string | number

export type MovieIdLike = {
  id?: MovieId
  movieId?: MovieId
}

export function normalizeMovieId(value: unknown): MovieId | null {
  if (typeof value === 'number') {
    return Number.isFinite(value) && value > 0 ? value : null
  }

  if (typeof value === 'string') {
    const trimmedValue = value.trim()
    return trimmedValue ? trimmedValue : null
  }

  return null
}

export function getMovieId(movie: MovieIdLike): MovieId | undefined {
  return normalizeMovieId(movie.id) ?? normalizeMovieId(movie.movieId) ?? undefined
}

export function getMovieIdKey(value: unknown): string | null {
  const normalizedValue = normalizeMovieId(value)
  return normalizedValue === null ? null : String(normalizedValue)
}

export function movieIdEquals(left: unknown, right: unknown): boolean {
  const leftKey = getMovieIdKey(left)
  const rightKey = getMovieIdKey(right)

  return leftKey !== null && rightKey !== null && leftKey === rightKey
}

export function normalizeMovieIdList(values: unknown[]): MovieId[] {
  const result: MovieId[] = []
  const seenKeys = new Set<string>()

  for (const value of values) {
    const normalizedValue = normalizeMovieId(value)
    if (normalizedValue === null) {
      continue
    }

    const key = String(normalizedValue)
    if (seenKeys.has(key)) {
      continue
    }

    seenKeys.add(key)
    result.push(normalizedValue)
  }

  return result
}
