import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import { logout as requestLogout, refreshToken as requestRefreshToken } from '@/api/endpoints/auth-management/auth-management'
import { getCurrentUserInfo } from '@/api/endpoints/user-management/user-management'
import type { UserVO } from '@/api/model'

export const AUTH_ROLE = {
  ADMIN: 0,
  USER: 1
} as const

export type AuthRole = (typeof AUTH_ROLE)[keyof typeof AUTH_ROLE]

export type AuthUser = Pick<
  UserVO,
  'id' | 'nickname' | 'avatar' | 'email' | 'url' | 'role' | 'receivedLikes' | 'commentCount' | 'watchedCount'
>

type AuthPayload = UserVO

const TOKEN_STORAGE_KEY = 'token'
const USER_STORAGE_KEY = 'auth:user'

function normalizeRole(role: unknown): AuthRole | undefined {
  return role === AUTH_ROLE.ADMIN || role === AUTH_ROLE.USER ? role : undefined
}

function readStoredUser(): AuthUser | null {
  const raw = localStorage.getItem(USER_STORAGE_KEY)
  if (!raw) {
    return null
  }

  try {
    const parsed = JSON.parse(raw)
    if (!parsed || typeof parsed !== 'object') {
      return null
    }
    return toAuthUser(parsed as AuthPayload)
  } catch {
    return null
  }
}

function toAuthUser(payload: AuthPayload | null | undefined): AuthUser | null {
  if (!payload) {
    return null
  }

  return {
    id: payload.id,
    nickname: payload.nickname,
    avatar: payload.avatar,
    email: payload.email,
    url: payload.url,
    role: normalizeRole(payload.role),
    receivedLikes: payload.receivedLikes,
    commentCount: payload.commentCount,
    watchedCount: payload.watchedCount
  }
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem(TOKEN_STORAGE_KEY))
  const user = ref<AuthUser | null>(readStoredUser())
  const initialized = ref(false)

  let initializePromise: Promise<void> | null = null
  let refreshPromise: Promise<string | null> | null = null

  const isAuthenticated = computed(() => Boolean(token.value))
  const role = computed<AuthRole | undefined>(() => normalizeRole(user.value?.role))
  const isAdmin = computed(() => role.value === AUTH_ROLE.ADMIN)

  function setToken(newToken: string | null) {
    token.value = newToken
    if (newToken) {
      localStorage.setItem(TOKEN_STORAGE_KEY, newToken)
    } else {
      localStorage.removeItem(TOKEN_STORAGE_KEY)
    }
  }

  function setUser(newUser: AuthUser | null) {
    user.value = newUser
    if (newUser) {
      localStorage.setItem(USER_STORAGE_KEY, JSON.stringify(newUser))
    } else {
      localStorage.removeItem(USER_STORAGE_KEY)
    }
  }

  function setAuth(payload: AuthPayload) {
    if (payload.accessToken) {
      setToken(payload.accessToken)
    }
    setUser(toAuthUser(payload))
    initialized.value = true
  }

  function clearAuth() {
    setToken(null)
    setUser(null)
  }

  function hasRole(...roles: AuthRole[]) {
    if (roles.length === 0) {
      return true
    }

    const currentRole = role.value
    return currentRole !== undefined && roles.includes(currentRole)
  }

  async function refreshAccessToken(): Promise<string | null> {
    if (refreshPromise) {
      return refreshPromise
    }

    refreshPromise = requestRefreshToken()
      .then((nextToken) => {
        if (!nextToken) {
          clearAuth()
          return null
        }
        setToken(nextToken)
        return nextToken
      })
      .catch(() => {
        clearAuth()
        return null
      })
      .finally(() => {
        refreshPromise = null
      })

    return refreshPromise
  }

  async function fetchCurrentUser(): Promise<AuthUser | null> {
    if (!token.value) {
      setUser(null)
      return null
    }

    try {
      const payload = await getCurrentUserInfo()
      const nextUser = toAuthUser(payload)
      if (!nextUser) {
        setUser(null)
        return null
      }
      setUser(nextUser)
      return nextUser
    } catch {
      setUser(null)
      return null
    }
  }

  async function initializeAuth() {
    if (initialized.value) {
      return
    }
    if (initializePromise) {
      return initializePromise
    }

    initializePromise = (async () => {
      if (!token.value) {
        const refreshedToken = await refreshAccessToken()
        if (!refreshedToken) {
          return
        }
      }

      const profile = await fetchCurrentUser()
      if (profile) {
        return
      }

      const refreshedToken = await refreshAccessToken()
      if (!refreshedToken) {
        return
      }
      await fetchCurrentUser()
    })().finally(() => {
      initialized.value = true
      initializePromise = null
    })

    return initializePromise
  }

  async function logout() {
    const currentToken = token.value
    clearAuth()

    try {
      await requestLogout(
        currentToken
          ? {
              headers: {
                Authorization: `Bearer ${currentToken}`
              }
            }
          : undefined
      )
    } catch {
      // 忽略服务端注销失败，保证前端本地状态已清理
    }
  }

  return {
    token,
    user,
    isAuthenticated,
    role,
    isAdmin,
    initialized,
    setToken,
    setUser,
    setAuth,
    clearAuth,
    hasRole,
    refreshAccessToken,
    fetchCurrentUser,
    initializeAuth,
    logout
  }
})
