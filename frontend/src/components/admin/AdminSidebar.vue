<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

type AdminNavItem = {
  name: string
  label: string
  description: string
}

const route = useRoute()
const router = useRouter()

const navItems: AdminNavItem[] = [
  {
    name: 'admin-dashboard',
    label: '仪表盘',
    description: '查看统计和趋势'
  },
  {
    name: 'admin-users',
    label: '用户',
    description: '账号状态与推荐缓存'
  },
  {
    name: 'admin-comments',
    label: '评论',
    description: '内容审核与删除'
  },
  {
    name: 'admin-movies',
    label: '电影',
    description: '检索与快速跳转'
  }
]

const currentRouteName = computed(() => String(route.name || ''))

function isActive(name: string): boolean {
  return currentRouteName.value === name
}

function navigateTo(name: string) {
  void router.push({ name })
}
</script>

<template>
  <aside class="sidebar-shell">
    <div class="sidebar-brand" @click="navigateTo('admin-dashboard')">
      <div class="sidebar-brand-mark">M</div>
      <div>
        <p class="sidebar-kicker">MOVIE SYSTEM</p>
        <h1 class="sidebar-title">Admin Console</h1>
      </div>
    </div>

    <nav class="sidebar-nav" aria-label="管理员导航">
      <button
        v-for="item in navItems"
        :key="item.name"
        type="button"
        :class="['sidebar-link', { 'sidebar-link--active': isActive(item.name) }]"
        @click="navigateTo(item.name)"
      >
        <span class="sidebar-link-label">{{ item.label }}</span>
        <span class="sidebar-link-description">{{ item.description }}</span>
      </button>
    </nav>
  </aside>
</template>

<style scoped>
.sidebar-shell {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  min-height: 100%;
  padding: 1.25rem;
  border: 1px solid rgba(51, 65, 85, 0.3);
  border-radius: 1.75rem;
  background:
    radial-gradient(circle at top, rgba(245, 158, 11, 0.18), transparent 24%),
    linear-gradient(180deg, rgba(2, 6, 23, 0.98), rgba(15, 23, 42, 0.96));
  color: #f8fafc;
}

.sidebar-brand {
  display: flex;
  gap: 0.9rem;
  align-items: center;
  cursor: pointer;
}

.sidebar-brand-mark {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 2.8rem;
  height: 2.8rem;
  border-radius: 1rem;
  background: linear-gradient(135deg, #f59e0b, #f97316);
  color: #ffffff;
  font-family: var(--font-display);
  font-size: 1.35rem;
  font-weight: 700;
}

.sidebar-kicker {
  margin: 0;
  color: rgba(252, 211, 77, 0.88);
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.2em;
}

.sidebar-title {
  margin: 0.2rem 0 0;
  font-family: var(--font-display);
  font-size: 1.35rem;
  font-weight: 700;
}

.sidebar-copy {
  padding: 1rem;
  border: 1px solid rgba(148, 163, 184, 0.14);
  border-radius: 1.25rem;
  background: rgba(255, 255, 255, 0.05);
}

.sidebar-description {
  margin: 0;
  color: rgba(226, 232, 240, 0.8);
  font-size: 0.92rem;
  line-height: 1.7;
}

.sidebar-nav {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.sidebar-link {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  padding: 0.95rem 1rem;
  border: 1px solid rgba(148, 163, 184, 0.14);
  border-radius: 1.2rem;
  background: rgba(255, 255, 255, 0.04);
  color: #e2e8f0;
  text-align: left;
  transition: transform 0.18s ease, border-color 0.18s ease, background 0.18s ease;
}

.sidebar-link:hover {
  transform: translateY(-1px);
  border-color: rgba(245, 158, 11, 0.26);
  background: rgba(255, 255, 255, 0.08);
}

.sidebar-link--active {
  border-color: rgba(245, 158, 11, 0.42);
  background: linear-gradient(135deg, rgba(245, 158, 11, 0.18), rgba(249, 115, 22, 0.08));
  box-shadow: inset 0 0 0 1px rgba(245, 158, 11, 0.18);
}

.sidebar-link-label {
  font-size: 0.96rem;
  font-weight: 700;
}

.sidebar-link-description {
  color: rgba(203, 213, 225, 0.78);
  font-size: 0.82rem;
}

.sidebar-footer {
  margin-top: auto;
  padding: 1rem;
  border-radius: 1.2rem;
  background: rgba(15, 23, 42, 0.58);
}

.sidebar-footer-label {
  margin: 0;
  color: rgba(148, 163, 184, 0.8);
  font-size: 0.76rem;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.sidebar-footer-value {
  margin: 0.4rem 0 0;
  color: rgba(226, 232, 240, 0.88);
  font-size: 0.9rem;
  line-height: 1.6;
}

@media (max-width: 1024px) {
  .sidebar-nav {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .sidebar-nav {
    grid-template-columns: 1fr;
  }
}
</style>
