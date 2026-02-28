<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { NButton, NAvatar, NDropdown, NInput } from 'naive-ui'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()

const searchValue = ref('')
const avatarFallback = computed(() => {
  const seed = authStore.user?.nickname ?? authStore.user?.id ?? 'U'
  return String(seed).trim().charAt(0).toUpperCase() || 'U'
})

const handleSearch = () => {
  console.log('Search:', searchValue.value)
  // Implement search logic or navigation
}

const handleLogout = async () => {
  await authStore.logout()
  router.push('/login')
}

const userOptions = [
  { label: '个人中心', key: 'profile' },
  { label: '退出登录', key: 'logout', props: { onClick: handleLogout } }
]
</script>

<template>
  <header class="sticky top-0 z-50 bg-white/80 backdrop-blur-md border-b border-slate-200">
    <div class="container mx-auto px-4 h-16 flex items-center justify-between">
      <!-- Logo -->
      <div class="flex items-center gap-2 cursor-pointer" @click="router.push('/')">
        <div class="w-8 h-8 bg-accent rounded-lg flex items-center justify-center">
          <span class="text-white font-bold font-display text-xl">M</span>
        </div>
        <span class="font-display font-bold text-xl tracking-tight text-slate-900">MovieReviews</span>
      </div>

      <!-- Navigation Links -->
      <div class="hidden md:flex items-center gap-6 ml-8">
        <n-button text class="text-slate-600 hover:text-slate-900" @click="router.push('/movies')">
          电影索引
        </n-button>
      </div>

      <!-- Search -->
      <div class="hidden md:flex flex-1 max-w-md mx-8">
        <n-input 
          v-model:value="searchValue" 
          placeholder="搜索电影、演员、导演..." 
          class="rounded-full bg-slate-100 border-none"
          @keyup.enter="handleSearch"
        >
          <template #prefix>
            <span class="text-slate-400">🔍</span>
          </template>
        </n-input>
      </div>

      <!-- Auth / User -->
      <div class="flex items-center gap-4">
        <template v-if="authStore.isAuthenticated">
          <n-dropdown :options="userOptions">
            <div class="flex items-center gap-2 cursor-pointer">
              <n-avatar 
                round 
                size="medium" 
                :src="authStore.user?.avatar || undefined" 
              >
                {{ avatarFallback }}
              </n-avatar>
              <span class="text-sm text-slate-700 hidden sm:block">{{ authStore.user?.nickname || authStore.user?.id }}</span>
            </div>
          </n-dropdown>
        </template>
        <template v-else>
          <n-button text class="text-slate-600 hover:text-slate-900" @click="router.push('/login')">
            登录
          </n-button>
          <n-button type="primary" class="rounded-full px-6" @click="router.push('/register')">
            注册
          </n-button>
        </template>
      </div>
    </div>
  </header>
</template>
