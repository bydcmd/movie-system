import { computed, reactive, shallowRef } from 'vue'
import { useDialog, useMessage } from 'naive-ui'
import {
  useFreezeUserAdmin,
  useDeleteUserAdmin,
  useGetUserListAdmin,
  useUnfreezeUserAdmin
} from '@/api/endpoints/admin-user-management/admin-user-management'
import type {
  GetUserListAdminParams,
  PageInfoUser,
  User
} from '@/api/model'
import { formatDateTimeLabel, getNameInitial } from '@/utils/profile'
import {
  DEFAULT_ADMIN_PAGE_SIZE,
  extractAdminErrorMessage,
  formatAdminCount,
  normalizePage,
  refetchOrThrow
} from '@/utils/admin'
import { useAuthStore } from '@/stores/auth'

type AdminUserListState = {
  keywordInput: string
  keyword: string
  status: UserStatusFilter
  page: number
  size: number
}

type UserStatusAction = 'freeze' | 'unfreeze'
type NormalizedUserStatus = 0 | 1 | 2 | undefined
type UserStatusFilter = 'all' | 0 | 1 | 2

const USER_STATUS_FILTER_OPTIONS = [
  { label: '全部状态', value: 'all' },
  { label: '正常', value: 0 },
  { label: '冻结', value: 1 },
  { label: '已注销', value: 2 }
]

export function useAdminUsers() {
  const authStore = useAuthStore()
  const dialog = useDialog()
  const message = useMessage()
  const pendingDeletedUserId = shallowRef<string | null>(null)
  const pendingRefreshUserId = shallowRef<string | null>(null)
  const pendingStatusAction = shallowRef<{
    userId: string
    action: UserStatusAction
  } | null>(null)

  const state = reactive<AdminUserListState>({
    keywordInput: '',
    keyword: '',
    status: 'all',
    page: 1,
    size: DEFAULT_ADMIN_PAGE_SIZE
  })

  const params = computed<GetUserListAdminParams>(() => ({
    keyword: state.keyword || undefined,
    status: state.status === 'all' ? undefined : state.status,
    page: state.page,
    size: state.size
  }))

  const userQuery = useGetUserListAdmin<PageInfoUser>(params, {
    query: {
      retry: false
    }
  })
  const freezeUserMutation = useFreezeUserAdmin()
  const unfreezeUserMutation = useUnfreezeUserAdmin()
  const deleteUserMutation = useDeleteUserAdmin()

  const page = computed(() => normalizePage<User>(userQuery.data.value))
  const users = computed(() => page.value.list)
  const total = computed(() => page.value.total)
  const loading = computed(() => userQuery.isLoading.value || userQuery.isFetching.value)
  const hasLoadError = computed(() => userQuery.isError.value)
  const lastUpdatedText = computed(() => {
    if (!userQuery.dataUpdatedAt.value) {
      return '尚未同步'
    }

    return formatDateTimeLabel(new Date(userQuery.dataUpdatedAt.value).toISOString())
  })

  function getRoleLabel(role?: number | null): string {
    return role === 0 ? '管理员' : '普通用户'
  }

  function normalizeUserStatus(status?: number | string | null): NormalizedUserStatus {
    if (status === null || status === undefined || status === '') {
      return undefined
    }

    const numericStatus = typeof status === 'string' ? Number(status) : status
    if (numericStatus === 0 || numericStatus === 1 || numericStatus === 2) {
      return numericStatus
    }

    return undefined
  }

  function getUserStatusLabel(status?: number | string | null): string {
    const normalizedStatus = normalizeUserStatus(status)
    if (normalizedStatus === 1) {
      return '冻结'
    }
    if (normalizedStatus === 2) {
      return '已注销'
    }
    return '正常'
  }

  function getUserStatusType(status?: number | string | null): 'success' | 'warning' | 'error' {
    const normalizedStatus = normalizeUserStatus(status)
    if (normalizedStatus === 1) {
      return 'warning'
    }
    if (normalizedStatus === 2) {
      return 'error'
    }
    return 'success'
  }

  function getUserInitial(user: User): string {
    return getNameInitial(user.nickname || user.id)
  }

  function isUserFrozen(user: User): boolean {
    return normalizeUserStatus(user.status) === 1
  }

  function isUserCancelled(user: User): boolean {
    return normalizeUserStatus(user.status) === 2
  }

  function isCurrentAdminUser(user: User): boolean {
    const userId = user.id?.trim()
    return Boolean(userId && authStore.user?.id === userId)
  }

  function getUserStatusActionLabel(user: User): string {
    return isUserFrozen(user) ? '解除冻结' : '冻结用户'
  }

  function getUserStatusActionType(user: User): 'success' | 'warning' {
    return isUserFrozen(user) ? 'success' : 'warning'
  }

  function canToggleUserStatus(user: User): boolean {
    const userId = user.id?.trim()
    if (!userId) {
      return false
    }

    if (isUserCancelled(user)) {
      return false
    }

    return !isCurrentAdminUser(user)
  }

  function canDeleteUser(user: User): boolean {
    const userId = user.id?.trim()
    if (!userId) {
      return false
    }

    if (isUserCancelled(user)) {
      return false
    }

    return !isCurrentAdminUser(user)
  }

  function applyFilters() {
    state.keyword = state.keywordInput.trim()
    state.page = 1
  }

  function applyStatusFilter() {
    state.page = 1
  }

  function resetFilters() {
    state.keywordInput = ''
    state.keyword = ''
    state.status = 'all'
    state.page = 1
  }

  async function refreshUsers() {
    try {
      await refetchOrThrow(userQuery)
      message.success('用户列表已刷新')
    } catch (error) {
      console.error('Failed to refresh users:', error)
      if (!extractAdminErrorMessage(error)) {
        message.error('刷新用户列表失败，请稍后再试')
      }
    }
  }

  function isDeletingUser(userId?: string | null): boolean {
    return Boolean(userId && pendingDeletedUserId.value === userId)
  }

  function isFreezingUser(userId?: string | null): boolean {
    return Boolean(
      userId
      && pendingStatusAction.value?.userId === userId
      && pendingStatusAction.value.action === 'freeze'
    )
  }

  function isUnfreezingUser(userId?: string | null): boolean {
    return Boolean(
      userId
      && pendingStatusAction.value?.userId === userId
      && pendingStatusAction.value.action === 'unfreeze'
    )
  }

  async function freezeUser(user: User) {
    const userId = user.id?.trim()
    if (!userId) {
      message.warning('用户 ID 无效，无法执行冻结')
      return
    }

    if (isCurrentAdminUser(user)) {
      message.warning('不能冻结当前登录的管理员账号')
      return
    }

    if (isUserCancelled(user)) {
      message.warning('已注销用户无法冻结')
      return
    }

    if (isUserFrozen(user)) {
      message.warning('该用户已被冻结')
      return
    }

    pendingStatusAction.value = {
      userId,
      action: 'freeze'
    }

    try {
      await freezeUserMutation.mutateAsync({ id: userId })
      await refetchOrThrow(userQuery)
      message.success(`已冻结 ${user.nickname || userId}`)
    } catch (error) {
      console.error('Failed to freeze user:', error)
      if (!extractAdminErrorMessage(error)) {
        message.error('冻结用户失败，请稍后再试')
      }
    } finally {
      pendingStatusAction.value = null
    }
  }

  async function unfreezeUser(user: User) {
    const userId = user.id?.trim()
    if (!userId) {
      message.warning('用户 ID 无效，无法执行解冻')
      return
    }

    if (isCurrentAdminUser(user)) {
      message.warning('不能操作当前登录的管理员账号')
      return
    }

    if (isUserCancelled(user)) {
      message.warning('已注销用户无法解冻')
      return
    }

    if (!isUserFrozen(user)) {
      message.warning('该用户当前不是冻结状态')
      return
    }

    pendingStatusAction.value = {
      userId,
      action: 'unfreeze'
    }

    try {
      await unfreezeUserMutation.mutateAsync({ id: userId })
      await refetchOrThrow(userQuery)
      message.success(`已解除 ${user.nickname || userId} 的冻结状态`)
    } catch (error) {
      console.error('Failed to unfreeze user:', error)
      if (!extractAdminErrorMessage(error)) {
        message.error('解冻用户失败，请稍后再试')
      }
    } finally {
      pendingStatusAction.value = null
    }
  }

  function requestToggleUserStatus(user: User) {
    const userId = user.id?.trim()
    if (!userId) {
      message.warning('用户 ID 无效，无法变更账号状态')
      return
    }

    if (isCurrentAdminUser(user)) {
      message.warning('不能操作当前登录的管理员账号')
      return
    }

    if (isUserCancelled(user)) {
      message.warning('已注销用户不支持冻结或解冻')
      return
    }

    const title = isUserFrozen(user) ? '解除冻结' : '冻结用户'
    const content = isUserFrozen(user)
      ? `确定解除用户 ${user.nickname || userId} 的冻结状态吗？恢复后该账号可重新登录。`
      : `确定冻结用户 ${user.nickname || userId} 吗？冻结后该账号将无法继续登录。`

    dialog.warning({
      title,
      content,
      positiveText: '确认',
      negativeText: '取消',
      onPositiveClick: () => (isUserFrozen(user) ? unfreezeUser(user) : freezeUser(user))
    })
  }

  async function deleteUser(user: User) {
    const userId = user.id?.trim()
    if (!userId) {
      message.warning('用户 ID 无效，无法执行注销')
      return
    }

    if (authStore.user?.id === userId) {
      message.warning('不能注销当前登录的管理员账号')
      return
    }

    pendingDeletedUserId.value = userId

    try {
      const shouldFallbackToPreviousPage = users.value.length === 1 && state.page > 1

      await deleteUserMutation.mutateAsync({ id: userId })

      if (shouldFallbackToPreviousPage) {
        state.page -= 1
      }

      await refetchOrThrow(userQuery)
      message.success('用户已注销')
    } catch (error) {
      console.error('Failed to delete user:', error)
      if (!extractAdminErrorMessage(error)) {
        message.error('注销用户失败，请稍后再试')
      }
    } finally {
      pendingDeletedUserId.value = null
    }
  }

  function requestDeleteUser(user: User) {
    const userId = user.id?.trim()
    if (!userId) {
      message.warning('用户 ID 无效，无法执行注销')
      return
    }

    dialog.warning({
      title: '注销用户',
      content: `确定要注销用户 ${user.nickname || userId} 吗？该操作会把账号状态改为已注销。`,
      positiveText: '确认注销',
      negativeText: '取消',
      onPositiveClick: () => deleteUser(user)
    })
  }

  return {
    state,
    users,
    total,
    loading,
    hasLoadError,
    lastUpdatedText,
    userStatusFilterOptions: USER_STATUS_FILTER_OPTIONS,
    formatCount: formatAdminCount,
    getRoleLabel,
    getUserStatusLabel,
    getUserStatusType,
    getUserInitial,
    getUserStatusActionLabel,
    getUserStatusActionType,
    canToggleUserStatus,
    canDeleteUser,
    applyFilters,
    applyStatusFilter,
    resetFilters,
    refreshUsers,
    requestToggleUserStatus,
    requestDeleteUser,
    isDeletingUser,
    isFreezingUser,
    isUnfreezingUser
  }
}
