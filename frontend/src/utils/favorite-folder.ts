import type { FavoriteFolderVO } from '@/api/model'

export function isDefaultFavoriteFolder(folder?: FavoriteFolderVO | null): boolean {
  return folder?.isDefault === 1
}

export function sortFavoriteFolders(folders: FavoriteFolderVO[]): FavoriteFolderVO[] {
  return [...folders].sort((left, right) => {
    return Number(isDefaultFavoriteFolder(right)) - Number(isDefaultFavoriteFolder(left))
  })
}
