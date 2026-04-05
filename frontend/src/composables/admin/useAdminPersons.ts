import { computed, reactive, shallowRef } from 'vue'
import { useDialog, useMessage } from 'naive-ui'
import {
  useGetPersonListAdmin,
  useAddPersonAdmin,
  useUpdatePersonAdmin,
  useDeletePersonAdmin
} from '@/api/endpoints/admin-person-management/admin-person-management'
import type { AdminPersonDTO, GetPersonListAdminParams, Person } from '@/api/model'
import { formatDateTimeLabel } from '@/utils/profile'
import {
  DEFAULT_ADMIN_PAGE_SIZE,
  extractAdminErrorMessage,
  formatAdminCount,
  refetchOrThrow
} from '@/utils/admin'

type AdminPersonState = {
  keywordInput: string
  keyword: string
  page: number
  size: number
  isCreateModalOpen: boolean
  isEditModalOpen: boolean
  editingPerson: Person | null
  formData: PersonFormData
}

type PersonFormData = {
  name: string
  nameEn: string
  nameZh: string
  sex: string
  birth: string
  birthplace: string
  profession: string
  biography: string
  avatar: string
}

const PERSON_SEX_OPTIONS = [
  { label: '未知', value: '' },
  { label: '男', value: '男' },
  { label: '女', value: '女' }
]

function createEmptyFormData(): PersonFormData {
  return {
    name: '',
    nameEn: '',
    nameZh: '',
    sex: '',
    birth: '',
    birthplace: '',
    profession: '',
    biography: '',
    avatar: ''
  }
}

function personToFormData(person: Person | null): PersonFormData {
  if (!person) {
    return createEmptyFormData()
  }
  return {
    name: person.name ?? '',
    nameEn: person.nameEn ?? '',
    nameZh: person.nameZh ?? '',
    sex: person.sex ?? '',
    birth: person.birth ?? '',
    birthplace: person.birthplace ?? '',
    profession: person.profession ?? '',
    biography: person.biography ?? '',
    avatar: person.avatar ?? ''
  }
}

function formDataToAdminPersonDTO(formData: PersonFormData): AdminPersonDTO {
  return {
    name: formData.name.trim(),
    nameEn: formData.nameEn.trim() || undefined,
    nameZh: formData.nameZh.trim() || undefined,
    sex: formData.sex.trim() || undefined,
    birth: formData.birth.trim() || undefined,
    birthplace: formData.birthplace.trim() || undefined,
    profession: formData.profession.trim() || undefined,
    biography: formData.biography.trim() || undefined,
    avatar: formData.avatar.trim() || undefined
  }
}

export function useAdminPersons() {
  const dialog = useDialog()
  const message = useMessage()
  const pendingDeletedPersonId = shallowRef<number | null>(null)

  const state = reactive<AdminPersonState>({
    keywordInput: '',
    keyword: '',
    page: 1,
    size: DEFAULT_ADMIN_PAGE_SIZE,
    isCreateModalOpen: false,
    isEditModalOpen: false,
    editingPerson: null,
    formData: createEmptyFormData()
  })

  const queryParams = computed<GetPersonListAdminParams>(() => ({
    keyword: state.keyword || undefined,
    page: state.page,
    size: state.size
  }))

  const personQuery = useGetPersonListAdmin<{ list: Person[]; total: number }>(queryParams, {
    query: {
      retry: false
    }
  })

  const addPersonMutation = useAddPersonAdmin()
  const updatePersonMutation = useUpdatePersonAdmin()
  const deletePersonMutation = useDeletePersonAdmin()

  const persons = computed(() => {
    const data = personQuery.data.value
    if (data && typeof data === 'object' && 'list' in data) {
      return Array.isArray(data.list) ? data.list : []
    }
    if (Array.isArray(data)) {
      return data
    }
    return []
  })

  const total = computed(() => {
    const data = personQuery.data.value
    if (data && typeof data === 'object' && 'total' in data) {
      return typeof data.total === 'number' ? data.total : 0
    }
    return 0
  })

  const loading = computed(() => personQuery.isLoading.value || personQuery.isFetching.value)
  const hasLoadError = computed(() => personQuery.isError.value)

  const lastUpdatedText = computed(() => {
    if (!personQuery.dataUpdatedAt.value) {
      return '尚未同步'
    }
    return formatDateTimeLabel(new Date(personQuery.dataUpdatedAt.value).toISOString())
  })

  const isSubmitting = computed(
    () =>
      addPersonMutation.isPending.value ||
      updatePersonMutation.isPending.value ||
      deletePersonMutation.isPending.value
  )

  const canSubmitForm = computed(() => {
    const name = state.formData.name.trim()
    return name.length > 0 && name.length <= 100
  })

  function resetForm() {
    state.formData = createEmptyFormData()
    state.editingPerson = null
  }

  function applyKeyword() {
    state.keyword = state.keywordInput.trim()
    state.page = 1
  }

  function resetFilters() {
    state.keywordInput = ''
    state.keyword = ''
    state.page = 1
  }



  function openCreateModal() {
    resetForm()
    state.isCreateModalOpen = true
  }

  function openEditModal(person: Person) {
    state.editingPerson = person
    state.formData = personToFormData(person)
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

  async function refreshPersons() {
    try {
      await refetchOrThrow(personQuery)
      message.success('影人列表已刷新')
    } catch (error) {
      console.error('Failed to refresh persons:', error)
      if (!extractAdminErrorMessage(error)) {
        message.error('刷新影人列表失败，请稍后再试')
      }
    }
  }

  async function createPerson() {
    if (!canSubmitForm.value) {
      message.warning('请输入有效的影人名称（1-100个字符）')
      return
    }

    try {
      await addPersonMutation.mutateAsync({
        data: formDataToAdminPersonDTO(state.formData)
      })
      await refetchOrThrow(personQuery)
      message.success('影人创建成功')
      closeCreateModal()
    } catch (error) {
      console.error('Failed to create person:', error)
      if (!extractAdminErrorMessage(error)) {
        message.error('创建影人失败，请稍后再试')
      }
    }
  }

  async function updatePerson() {
    if (!state.editingPerson?.id) {
      message.warning('影人 ID 无效')
      return
    }

    if (!canSubmitForm.value) {
      message.warning('请输入有效的影人名称（1-100个字符）')
      return
    }

    try {
      await updatePersonMutation.mutateAsync({
        id: state.editingPerson.id,
        data: formDataToAdminPersonDTO(state.formData)
      })
      await refetchOrThrow(personQuery)
      message.success('影人更新成功')
      closeEditModal()
    } catch (error) {
      console.error('Failed to update person:', error)
      if (!extractAdminErrorMessage(error)) {
        message.error('更新影人失败，请稍后再试')
      }
    }
  }

  function isDeletingPerson(personId?: number | null): boolean {
    return Boolean(personId && pendingDeletedPersonId.value === personId)
  }

  async function deletePerson(person: Person) {
    if (!person.id) {
      message.warning('影人 ID 无效，无法删除')
      return
    }

    pendingDeletedPersonId.value = person.id

    try {
      await deletePersonMutation.mutateAsync({ id: person.id })
      await refetchOrThrow(personQuery)
      message.success('影人已删除')
    } catch (error) {
      console.error('Failed to delete person:', error)
      if (!extractAdminErrorMessage(error)) {
        message.error('删除影人失败，请稍后再试')
      }
    } finally {
      pendingDeletedPersonId.value = null
    }
  }

  function requestDeletePerson(person: Person) {
    if (!person.id) {
      message.warning('影人 ID 无效，无法删除')
      return
    }

    dialog.warning({
      title: '删除影人',
      content: `确定要删除影人 "${person.name || '未命名'}" 吗？该操作不可恢复。`,
      positiveText: '确认删除',
      negativeText: '取消',
      onPositiveClick: () => deletePerson(person)
    })
  }

  function getSexLabel(sex?: string): string {
    if (!sex) return '未知'
    return sex
  }

  return {
    state,
    persons,
    total,
    loading,
    hasLoadError,
    lastUpdatedText,
    isSubmitting,
    canSubmitForm,
    sexOptions: PERSON_SEX_OPTIONS,
    formatCount: formatAdminCount,
    getSexLabel,
    applyKeyword,
    resetFilters,
    openCreateModal,
    openEditModal,
    closeCreateModal,
    closeEditModal,
    refreshPersons,
    createPerson,
    updatePerson,
    deletePerson: requestDeletePerson,
    isDeletingPerson
  }
}
