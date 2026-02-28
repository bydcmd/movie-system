import axios from 'axios'
import { ref, computed } from 'vue'
import { defineStore } from 'pinia'

type AuthUser = {
  id?: string
  nickname?: string
  avatar?: string
  email?: string
  url?: string
  role?: number
}

type AuthPayload = AuthUser & {
  accessToken?: string
}

const TOKEN_STORAGE_KEY = 'token'
const USER_STORAGE_KEY = 'auth:user'
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:9090'

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
    return parsed as AuthUser
  } catch {
    return null
  }
}

function extractPayload(value: unknown): AuthPayload | null {
  if (!value || typeof value !== 'object') {
    return null
  }

  const record = value as Record<string, unknown>
  if (record.data && typeof record.data === 'object') {
    return record.data as AuthPayload
  }
  return record as AuthPayload
}

function toAuthUser(payload: AuthPayload): AuthUser {
  return {
    id: payload.id,
    nickname: payload.nickname,
    avatar: payload.avatar,
    email: payload.email,
    url: payload.url,
    role: payload.role
  }
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem(TOKEN_STORAGE_KEY))
  const user = ref<AuthUser | null>(readStoredUser())
  const initialized = ref(false)

  let initializePromise: Promise<void> | null = null
  let refreshPromise: Promise<string | null> | null = null

  const isAuthenticated = computed(() => Boolean(token.value))

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
  }

  function clearAuth() {
    setToken(null)
    setUser(null)
  }

  async function refreshAccessToken(): Promise<string | null> {
    if (refreshPromise) {
      return refreshPromise
    }

    refreshPromise = axios
      .post('/auth/token/refresh', undefined, {
        baseURL: API_BASE_URL,
        withCredentials: true,
        timeout: 10000
      })
      .then((response) => {
        const payload = extractPayload(response.data)
        const nextToken = payload?.accessToken
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
      const response = await axios.get('/users/me', {
        baseURL: API_BASE_URL,
        timeout: 10000,
        headers: {
          Authorization: `Bearer ${token.value}`
        }
      })
      const payload = extractPayload(response.data)
      if (!payload) {
        setUser(null)
        return null
      }
      const nextUser = toAuthUser(payload)
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
      await axios.post('/auth/logout', undefined, {
        baseURL: API_BASE_URL,
        withCredentials: true,
        timeout: 10000,
        headers: currentToken
          ? {
              Authorization: `Bearer ${currentToken}`
            }
          : undefined
      })
    } catch {
      // 忽略服务端注销失败，保证前端本地状态已清理
    }
  }

  return {
    token,
    user,
    isAuthenticated,
    initialized,
    setToken,
    setUser,
    setAuth,
    clearAuth,
    refreshAccessToken,
    fetchCurrentUser,
    initializeAuth,
    logout
  }
})
