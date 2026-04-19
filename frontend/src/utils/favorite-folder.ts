import type { FavoriteFolderVO } from '@/api/model'

export type FavoriteFolderId = string | number
export type FavoriteFolderWithId = FavoriteFolderVO & { id: FavoriteFolderId }

export function isDefaultFavoriteFolder(folder?: FavoriteFolderVO | null): boolean {
  return folder?.isDefault === 1
}

export function normalizeFavoriteFolderId(value: unknown): FavoriteFolderId | null {
  if (typeof value === 'number') {
    return Number.isFinite(value) && value > 0 ? value : null
  }

  if (typeof value === 'string') {
    const trimmedValue = value.trim()
    return trimmedValue ? trimmedValue : null
  }

  return null
}

export function hasFavoriteFolderId(folder?: FavoriteFolderVO | null): folder is FavoriteFolderWithId {
  return normalizeFavoriteFolderId(folder?.id) !== null
}

export function getFavoriteFolderIdKey(value: unknown): string | null {
  const normalizedValue = normalizeFavoriteFolderId(value)
  return normalizedValue === null ? null : String(normalizedValue)
}

export function favoriteFolderIdEquals(left: unknown, right: unknown): boolean {
  const leftKey = getFavoriteFolderIdKey(left)
  const rightKey = getFavoriteFolderIdKey(right)

  return leftKey !== null && rightKey !== null && leftKey === rightKey
}

export function sortFavoriteFolders(folders: FavoriteFolderVO[]): FavoriteFolderVO[] {
  return [...folders].sort((left, right) => {
    return Number(isDefaultFavoriteFolder(right)) - Number(isDefaultFavoriteFolder(left))
  })
}
