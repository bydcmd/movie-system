import { computed, reactive, shallowRef } from 'vue'
import { useDialog, useMessage } from 'naive-ui'
import {
  useGetRegionListAdmin,
  useAddRegionAdmin,
  useUpdateRegionAdmin,
  useDeleteRegionAdmin
} from '@/api/endpoints/admin-region-management/admin-region-management'
import type { Region } from '@/api/model'
import { formatDateTimeLabel } from '@/utils/profile'
import { extractAdminErrorMessage, refetchOrThrow } from '@/utils/admin'

type AdminRegionState = {
  isCreateModalOpen: boolean
  isEditModalOpen: boolean
  editingRegion: Region | null
  formData: {
    name: string
    nameEn: string
    description: string
  }
}

export function useAdminRegions() {
  const dialog = useDialog()
  const message = useMessage()
  const pendingDeletedRegionId = shallowRef<number | null>(null)

  const state = reactive<AdminRegionState>({
    isCreateModalOpen: false,
    isEditModalOpen: false,
    editingRegion: null,
    formData: {
      name: '',
      nameEn: '',
      description: ''
    }
  })

  const regionQuery = useGetRegionListAdmin<Region[]>({
    query: {
      retry: false
    }
  })

  const addRegionMutation = useAddRegionAdmin()
  const updateRegionMutation = useUpdateRegionAdmin()
  const deleteRegionMutation = useDeleteRegionAdmin()

  const regions = computed(() => {
    const data = regionQuery.data.value
    if (Array.isArray(data)) {
      return data
    }
    return []
  })

  const loading = computed(() => regionQuery.isLoading.value || regionQuery.isFetching.value)
  const hasLoadError = computed(() => regionQuery.isError.value)
  const lastUpdatedText = computed(() => {
    if (!regionQuery.dataUpdatedAt.value) {
      return '尚未同步'
    }
    return formatDateTimeLabel(new Date(regionQuery.dataUpdatedAt.value).toISOString())
  })

  const isSubmitting = computed(() =>
    addRegionMutation.isPending.value ||
    updateRegionMutation.isPending.value ||
    deleteRegionMutation.isPending.value
  )

  const canSubmitForm = computed(() => {
    const name = state.formData.name.trim()
    return name.length > 0 && name.length <= 50
  })

  function resetForm() {
    state.formData.name = ''
    state.formData.nameEn = ''
    state.formData.description = ''
    state.editingRegion = null
  }

  function openCreateModal() {
    resetForm()
    state.isCreateModalOpen = true
  }

  function openEditModal(region: Region) {
    state.editingRegion = region
    state.formData.name = region.name || ''
    state.formData.nameEn = region.nameEn || ''
    state.formData.description = region.description || ''
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

  async function refreshRegions() {
    try {
      await refetchOrThrow(regionQuery)
      message.success('地区列表已刷新')
    } catch (error) {
      console.error('Failed to refresh regions:', error)
      if (!extractAdminErrorMessage(error)) {
        message.error('刷新地区列表失败，请稍后再试')
      }
    }
  }

  async function createRegion() {
    if (!canSubmitForm.value) {
      message.warning('请输入有效的地区名称（1-50个字符）')
      return
    }

    try {
      await addRegionMutation.mutateAsync({
        data: {
          name: state.formData.name.trim(),
          nameEn: state.formData.nameEn.trim() || undefined,
          description: state.formData.description.trim() || undefined
        }
      })
      await refetchOrThrow(regionQuery)
      message.success('地区创建成功')
      closeCreateModal()
    } catch (error) {
      console.error('Failed to create region:', error)
      if (!extractAdminErrorMessage(error)) {
        message.error('创建地区失败，请稍后再试')
      }
    }
  }

  async function updateRegion() {
    if (!state.editingRegion?.id) {
      message.warning('地区 ID 无效')
      return
    }

    if (!canSubmitForm.value) {
      message.warning('请输入有效的地区名称（1-50个字符）')
      return
    }

    try {
      await updateRegionMutation.mutateAsync({
        id: state.editingRegion.id,
        data: {
          name: state.formData.name.trim(),
          nameEn: state.formData.nameEn.trim() || undefined,
          description: state.formData.description.trim() || undefined
        }
      })
      await refetchOrThrow(regionQuery)
      message.success('地区更新成功')
      closeEditModal()
    } catch (error) {
      console.error('Failed to update region:', error)
      if (!extractAdminErrorMessage(error)) {
        message.error('更新地区失败，请稍后再试')
      }
    }
  }

  function isDeletingRegion(regionId?: number | null): boolean {
    return Boolean(regionId && pendingDeletedRegionId.value === regionId)
  }

  async function deleteRegion(region: Region) {
    if (!region.id) {
      message.warning('地区 ID 无效，无法删除')
      return
    }

    pendingDeletedRegionId.value = region.id

    try {
      await deleteRegionMutation.mutateAsync({ id: region.id })
      await refetchOrThrow(regionQuery)
      message.success('地区已删除')
    } catch (error) {
      console.error('Failed to delete region:', error)
      if (!extractAdminErrorMessage(error)) {
        message.error('删除地区失败，请稍后再试')
      }
    } finally {
      pendingDeletedRegionId.value = null
    }
  }

  function requestDeleteRegion(region: Region) {
    if (!region.id) {
      message.warning('地区 ID 无效，无法删除')
      return
    }

    dialog.warning({
      title: '删除地区',
      content: `确定要删除地区 "${region.name || '未命名'}" 吗？该操作不可恢复。`,
      positiveText: '确认删除',
      negativeText: '取消',
      onPositiveClick: () => deleteRegion(region)
    })
  }

  return {
    state,
    regions,
    loading,
    hasLoadError,
    lastUpdatedText,
    isSubmitting,
    canSubmitForm,
    openCreateModal,
    openEditModal,
    closeCreateModal,
    closeEditModal,
    refreshRegions,
    createRegion,
    updateRegion,
    deleteRegion: requestDeleteRegion,
    isDeletingRegion
  }
}
