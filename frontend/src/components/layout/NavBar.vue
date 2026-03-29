<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NAvatar, NButton, NDropdown, NInput, type DropdownOption } from 'naive-ui'
import { useAuthz } from '@/composables/useAuthz'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const { isAdmin } = useAuthz()

const searchValue = ref('')
const routeKeyword = computed(() => (
  typeof route.query.keyword === 'string' ? route.query.keyword : ''
))

watch(routeKeyword, (keyword) => {
  searchValue.value = keyword
}, { immediate: true })

const avatarFallback = computed(() => {
  const seed = authStore.user?.nickname ?? authStore.user?.id ?? 'U'
  return String(seed).trim().charAt(0).toUpperCase() || 'U'
})

const handleSearch = async () => {
  const keyword = searchValue.value.trim()
  const nextQuery: Record<string, string> = {}

  if (route.name === 'movies') {
    Object.entries(route.query).forEach(([key, value]) => {
      if (key === 'page' || key === 'keyword') {
        return
      }

      if (typeof value === 'string' && value) {
        nextQuery[key] = value
        return
      }

      if (Array.isArray(value) && value.length > 0) {
        nextQuery[key] = value.join(',')
      }
    })
  }

  if (keyword) {
    nextQuery.keyword = keyword
  }

  await router.push({
    name: 'movies',
    query: Object.keys(nextQuery).length > 0 ? nextQuery : undefined
  })
}

const handleGuestEntry = (path: '/login' | '/register') => {
  const redirect = route.fullPath
  router.push({
    path,
    query: redirect && redirect !== '/' ? { redirect } : undefined
  })
}

const handleLogout = async () => {
  await authStore.logout()
  router.push('/login')
}

const userOptions = computed<DropdownOption[]>(() => {
  const options: DropdownOption[] = []

  if (isAdmin.value) {
    options.push({ label: '管理后台', key: 'admin' })
  }

  options.push({ label: '个人中心', key: 'profile' })
  options.push({ label: '退出登录', key: 'logout' })

  return options
})

const handleUserSelect = async (key: string | number) => {
  if (key === 'admin') {
    await router.push('/admin')
    return
  }

  if (key === 'profile') {
    await router.push('/profile')
    return
  }

  if (key === 'logout') {
    await handleLogout()
  }
}
</script>

<template>
  <header class="sticky top-0 z-50 bg-white/80 backdrop-blur-md border-b border-slate-200">
    <div class="container mx-auto flex h-16 items-center justify-between px-4">
      <div class="flex cursor-pointer items-center gap-2" @click="router.push('/')">
        <div class="flex h-8 w-8 items-center justify-center rounded-lg bg-accent">
          <span class="font-display text-xl font-bold text-white">M</span>
        </div>
        <span class="font-display text-xl font-bold tracking-tight text-slate-900">MovieReviews</span>
      </div>

      <div class="ml-8 hidden items-center gap-6 md:flex">
        <n-button text class="text-slate-600 hover:text-slate-900" @click="router.push('/movies')">
          电影索引
        </n-button>
      </div>

      <div class="mx-8 hidden max-w-md flex-1 md:flex">
        <n-input
          v-model:value="searchValue"
          placeholder="搜索电影、演员、导演..."
          class="rounded-full border-none bg-slate-100"
          :input-props="{ id: 'global-search', name: 'search', autocomplete: 'off' }"
          @keyup.enter="handleSearch"
        >
          <template #prefix>
            <span class="text-slate-400">🔍</span>
          </template>
        </n-input>
      </div>

      <div class="flex items-center gap-4">
        <template v-if="authStore.isAuthenticated">
          <n-dropdown :options="userOptions" @select="handleUserSelect">
            <div class="flex cursor-pointer items-center gap-2">
              <n-avatar
                round
                size="medium"
                :src="authStore.user?.avatar || undefined"
              >
                {{ avatarFallback }}
              </n-avatar>
              <span class="hidden text-sm text-slate-700 sm:block">
                {{ authStore.user?.nickname || authStore.user?.id }}
              </span>
              <span
                v-if="isAdmin"
                class="hidden rounded-full bg-amber-100 px-2 py-1 text-[11px] font-semibold tracking-wide text-amber-700 sm:inline-flex"
              >
                管理员
              </span>
            </div>
          </n-dropdown>
        </template>
        <template v-else>
          <n-button text class="text-slate-600 hover:text-slate-900" @click="handleGuestEntry('/login')">
            登录
          </n-button>
          <n-button type="primary" class="rounded-full px-6" @click="handleGuestEntry('/register')">
            注册
          </n-button>
        </template>
      </div>
    </div>
  </header>
</template>
