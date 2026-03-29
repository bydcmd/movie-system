<script setup lang="ts">
import { computed } from 'vue'
import { RouterView, useRoute, useRouter } from 'vue-router'
import { NAvatar, NButton, NTag } from 'naive-ui'
import AdminSidebar from '@/components/admin/AdminSidebar.vue'
import { useAuthz } from '@/composables/useAuthz'
import { useAuthStore } from '@/stores/auth'
import { getNameInitial, resolveAssetUrl } from '@/utils/profile'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const { user } = useAuthz()

const pageTitle = computed(() => (
  typeof route.meta.adminTitle === 'string' ? route.meta.adminTitle : '管理后台'
))

const pageDescription = computed(() => (
  typeof route.meta.adminDescription === 'string'
    ? route.meta.adminDescription
    : '后台接口已按仪表盘、用户、评论、电影、类型、地区、影人和推荐缓存拆分，当前区域只承载管理员路由。'
))

const adminAvatar = computed(() => resolveAssetUrl(user.value?.avatar))
const adminInitial = computed(() => getNameInitial(user.value?.nickname || user.value?.id))
const adminDisplayName = computed(() => user.value?.nickname?.trim() || user.value?.id || '未知管理员')

function goHome() {
  void router.push('/')
}

async function handleLogout() {
  await authStore.logout()
  void router.push('/login')
}
</script>

<template>
  <div class="admin-layout">
    <div class="admin-shell">
      <AdminSidebar />

      <section class="admin-main">
        <header class="admin-topbar">
          <div class="admin-topbar-copy">
            <span class="admin-topbar-kicker">ADMIN AREA</span>
            <h1 class="admin-topbar-title">{{ pageTitle }}</h1>
            <p class="admin-topbar-description">{{ pageDescription }}</p>
          </div>

          <div class="admin-topbar-actions">
            <div class="admin-user-card">
              <n-avatar
                round
                :size="44"
                :src="adminAvatar || undefined"
              >
                {{ adminInitial }}
              </n-avatar>

              <div>
                <p class="admin-user-name">{{ adminDisplayName }}</p>
                <div class="admin-user-tags">
                  <n-tag size="small" type="warning">
                    管理员
                  </n-tag>
                </div>
              </div>
            </div>

            <div class="admin-shortcuts">
              <n-button quaternary @click="goHome">
                前台首页
              </n-button>
              <n-button secondary type="error" @click="handleLogout">
                退出登录
              </n-button>
            </div>
          </div>
        </header>

        <main class="admin-content">
          <RouterView />
        </main>
      </section>
    </div>
  </div>
</template>

<style scoped>
.admin-layout {
  min-height: 100vh;
  background:
    radial-gradient(circle at top left, rgba(245, 158, 11, 0.12), transparent 24%),
    radial-gradient(circle at bottom right, rgba(14, 165, 233, 0.1), transparent 22%),
    linear-gradient(180deg, #e2e8f0 0%, #f8fafc 28%, #f8fafc 100%);
  color: #0f172a;
}

.admin-shell {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: 1rem;
  width: min(1600px, calc(100% - 2rem));
  min-height: 100vh;
  margin: 0 auto;
  padding: 1rem 0 1.5rem;
}

.admin-main {
  display: flex;
  flex-direction: column;
  min-height: calc(100vh - 2rem);
  border: 1px solid rgba(148, 163, 184, 0.22);
  border-radius: 1.75rem;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 22px 60px rgba(15, 23, 42, 0.08);
  overflow: hidden;
}

.admin-topbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
  padding: 1.5rem;
  border-bottom: 1px solid rgba(148, 163, 184, 0.16);
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(248, 250, 252, 0.92));
}

.admin-topbar-copy {
  max-width: 46rem;
}

.admin-topbar-kicker {
  display: inline-flex;
  color: #f59e0b;
  font-size: 0.75rem;
  font-weight: 700;
  letter-spacing: 0.2em;
}

.admin-topbar-title {
  margin: 0.45rem 0 0;
  font-family: var(--font-display);
  font-size: clamp(1.8rem, 2.8vw, 2.4rem);
  line-height: 1.08;
  font-weight: 700;
}

.admin-topbar-description {
  margin: 0.7rem 0 0;
  color: #64748b;
  line-height: 1.75;
}

.admin-topbar-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 0.9rem;
}

.admin-user-card {
  display: flex;
  gap: 0.8rem;
  align-items: center;
  padding: 0.7rem 0.9rem;
  border: 1px solid rgba(148, 163, 184, 0.16);
  border-radius: 1.2rem;
  background: rgba(255, 255, 255, 0.84);
}

.admin-user-name {
  margin: 0;
  font-size: 0.95rem;
  font-weight: 700;
}

.admin-user-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 0.45rem;
  margin-top: 0.35rem;
}

.admin-shortcuts {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 0.75rem;
}

.admin-content {
  flex: 1;
  padding: 1.5rem;
  overflow-y: auto;
}

@media (max-width: 1180px) {
  .admin-shell {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .admin-shell {
    width: min(100% - 1rem, 100%);
    padding-top: 0.5rem;
  }

  .admin-topbar,
  .admin-topbar-actions {
    align-items: flex-start;
  }

  .admin-topbar {
    flex-direction: column;
    padding: 1rem;
  }

  .admin-content {
    padding: 1rem;
  }
}
</style>
