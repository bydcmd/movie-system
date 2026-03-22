import { storeToRefs } from 'pinia'
import { useAuthStore, type AuthRole } from '@/stores/auth'

type AccessOptions = {
  requiresAuth?: boolean
  allowedRoles?: AuthRole[]
}

export function useAuthz() {
  const authStore = useAuthStore()
  const { isAdmin, isAuthenticated, role, user } = storeToRefs(authStore)

  function hasRole(...roles: AuthRole[]) {
    return authStore.hasRole(...roles)
  }

  function canAccess(options?: AccessOptions) {
    if (!options) {
      return true
    }

    if (options.requiresAuth && !isAuthenticated.value) {
      return false
    }

    if (options.allowedRoles?.length) {
      return hasRole(...options.allowedRoles)
    }

    return true
  }

  return {
    user,
    role,
    isAdmin,
    isAuthenticated,
    hasRole,
    canAccess
  }
}
