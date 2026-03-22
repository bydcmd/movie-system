<script setup lang="ts">
import { useRouter } from 'vue-router'
import { NButton } from 'naive-ui'
import { useAuthz } from '@/composables/useAuthz'

const router = useRouter()
const { user } = useAuthz()
</script>

<template>
  <section class="min-h-[calc(100vh-4rem)] bg-slate-50 px-4 py-16">
    <div class="mx-auto flex max-w-4xl flex-col gap-6 rounded-3xl border border-slate-200 bg-white p-8 shadow-sm">
      <div class="space-y-3">
        <span class="inline-flex rounded-full bg-amber-100 px-3 py-1 text-xs font-semibold tracking-[0.24em] text-amber-700">
          ADMIN ONLY
        </span>
        <h1 class="font-display text-3xl font-bold text-slate-900">管理员空间</h1>
        <p class="max-w-2xl text-sm leading-6 text-slate-600">
          当前登录身份已通过前端角色守卫校验。后续新增管理页时，只需要继续给路由配置
          <code>allowedRoles: [0]</code> 即可复用这套权限链路。
        </p>
      </div>

      <div class="rounded-2xl bg-slate-900 p-5 text-sm text-slate-100">
        <p>当前用户：{{ user?.nickname || user?.id || '未知管理员' }}</p>
        <p class="mt-2 text-slate-300">这里先保留为管理员入口占位页，后续可挂接电影、评论、用户管理模块。</p>
      </div>

      <div class="flex flex-wrap gap-3">
        <n-button type="primary" @click="router.push('/movies')">
          返回电影页
        </n-button>
        <n-button quaternary @click="router.push('/profile')">
          前往个人中心
        </n-button>
      </div>
    </div>
  </section>
</template>
