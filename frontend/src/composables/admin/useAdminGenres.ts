import { computed, reactive, shallowRef } from 'vue'
import { useDialog, useMessage } from 'naive-ui'
import {
  useGetGenreListAdmin,
  useAddGenreAdmin,
  useUpdateGenreAdmin,
  useDeleteGenreAdmin
} from '@/api/endpoints/admin-genre-management/admin-genre-management'
import type { Genre } from '@/api/model'
import { formatDateTimeLabel } from '@/utils/profile'
import { extractAdminErrorMessage, refetchOrThrow } from '@/utils/admin'

type AdminGenreState = {
  isCreateModalOpen: boolean
  isEditModalOpen: boolean
  editingGenre: Genre | null
  formData: {
    name: string
  }
}

export function useAdminGenres() {
  const dialog = useDialog()
  const message = useMessage()
  const pendingDeletedGenreId = shallowRef<number | null>(null)

  const state = reactive<AdminGenreState>({
    isCreateModalOpen: false,
    isEditModalOpen: false,
    editingGenre: null,
    formData: {
      name: ''
    }
  })

  const genreQuery = useGetGenreListAdmin<Genre[]>({
    query: {
      retry: false
    }
  })

  const addGenreMutation = useAddGenreAdmin()
  const updateGenreMutation = useUpdateGenreAdmin()
  const deleteGenreMutation = useDeleteGenreAdmin()

  const genres = computed(() => {
    const data = genreQuery.data.value
    if (Array.isArray(data)) {
      return data
    }
    return []
  })

  const loading = computed(() => genreQuery.isLoading.value || genreQuery.isFetching.value)
  const hasLoadError = computed(() => genreQuery.isError.value)
  const lastUpdatedText = computed(() => {
    if (!genreQuery.dataUpdatedAt.value) {
      return '尚未同步'
    }
    return formatDateTimeLabel(new Date(genreQuery.dataUpdatedAt.value).toISOString())
  })

  const isSubmitting = computed(() =>
    addGenreMutation.isPending.value ||
    updateGenreMutation.isPending.value ||
    deleteGenreMutation.isPending.value
  )

  const canSubmitForm = computed(() => {
    const name = state.formData.name.trim()
    return name.length > 0 && name.length <= 50
  })

  function resetForm() {
    state.formData.name = ''
    state.editingGenre = null
  }

  function openCreateModal() {
    resetForm()
    state.isCreateModalOpen = true
  }

  function openEditModal(genre: Genre) {
    state.editingGenre = genre
    state.formData.name = genre.name || ''
    state.isEditModalOpen = true
  }

  function closeCreateModal() {
    state.isCreateModalOpen = false
    resetForm()
  }

  function closeEditModal() {
    state.isEditModalOpen = false
    resetForm()
  }

  async function refreshGenres() {
    try {
      await refetchOrThrow(genreQuery)
      message.success('类型列表已刷新')
    } catch (error) {
      console.error('Failed to refresh genres:', error)
      if (!extractAdminErrorMessage(error)) {
        message.error('刷新类型列表失败，请稍后再试')
      }
    }
  }

  async function createGenre() {
    if (!canSubmitForm.value) {
      message.warning('请输入有效的类型名称（1-50个字符）')
      return
    }

    try {
      await addGenreMutation.mutateAsync({
        data: {
          name: state.formData.name.trim()
        }
      })
      await refetchOrThrow(genreQuery)
      message.success('类型创建成功')
      closeCreateModal()
    } catch (error) {
      console.error('Failed to create genre:', error)
      if (!extractAdminErrorMessage(error)) {
        message.error('创建类型失败，请稍后再试')
      }
    }
  }

  async function updateGenre() {
    if (!state.editingGenre?.id) {
      message.warning('类型 ID 无效')
      return
    }

    if (!canSubmitForm.value) {
      message.warning('请输入有效的类型名称（1-50个字符）')
      return
    }

    try {
      await updateGenreMutation.mutateAsync({
        id: state.editingGenre.id,
        data: {
          name: state.formData.name.trim()
        }
      })
      await refetchOrThrow(genreQuery)
      message.success('类型更新成功')
      closeEditModal()
    } catch (error) {
      console.error('Failed to update genre:', error)
      if (!extractAdminErrorMessage(error)) {
        message.error('更新类型失败，请稍后再试')
      }
    }
  }

  function isDeletingGenre(genreId?: number | null): boolean {
    return Boolean(genreId && pendingDeletedGenreId.value === genreId)
  }

  async function deleteGenre(genre: Genre) {
    if (!genre.id) {
      message.warning('类型 ID 无效，无法删除')
      return
    }

    pendingDeletedGenreId.value = genre.id

    try {
      await deleteGenreMutation.mutateAsync({ id: genre.id })
      await refetchOrThrow(genreQuery)
      message.success('类型已删除')
    } catch (error) {
      console.error('Failed to delete genre:', error)
      if (!extractAdminErrorMessage(error)) {
        message.error('删除类型失败，请稍后再试')
      }
    } finally {
      pendingDeletedGenreId.value = null
    }
  }

  function requestDeleteGenre(genre: Genre) {
    if (!genre.id) {
      message.warning('类型 ID 无效，无法删除')
      return
    }

    dialog.warning({
      title: '删除类型',
      content: `确定要删除类型 "${genre.name || '未命名'}" 吗？该操作不可恢复。`,
      positiveText: '确认删除',
      negativeText: '取消',
      onPositiveClick: () => deleteGenre(genre)
    })
  }

  return {
    state,
    genres,
    loading,
    hasLoadError,
    lastUpdatedText,
    isSubmitting,
    canSubmitForm,
    openCreateModal,
    openEditModal,
    closeCreateModal,
    closeEditModal,
    refreshGenres,
    createGenre,
    updateGenre,
    deleteGenre: requestDeleteGenre,
    isDeletingGenre
  }
}
