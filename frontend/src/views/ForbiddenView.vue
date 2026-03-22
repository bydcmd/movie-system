<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NButton } from 'naive-ui'

const route = useRoute()
const router = useRouter()

const redirectTarget = computed(() => (typeof route.query.redirect === 'string' ? route.query.redirect : '/'))
</script>

<template>
  <section class="min-h-[calc(100vh-4rem)] bg-slate-50 px-4 py-16">
    <div class="mx-auto flex max-w-3xl flex-col gap-6 rounded-3xl border border-rose-100 bg-white p-8 shadow-sm">
      <div class="space-y-3">
        <span class="inline-flex rounded-full bg-rose-100 px-3 py-1 text-xs font-semibold tracking-[0.24em] text-rose-700">
          403 FORBIDDEN
        </span>
        <h1 class="font-display text-3xl font-bold text-slate-900">当前账号没有访问权限</h1>
        <p class="text-sm leading-6 text-slate-600">
          你已经完成登录，但当前角色不允许访问这个页面。请返回上一页，或切换到拥有权限的账号后再试。
        </p>
      </div>

      <div class="rounded-2xl bg-slate-100 p-5 text-sm text-slate-700">
        请求页面：<code>{{ redirectTarget }}</code>
      </div>

      <div class="flex flex-wrap gap-3">
        <n-button type="primary" @click="router.push('/')">
          返回首页
        </n-button>
        <n-button quaternary @click="router.push('/profile')">
          进入个人中心
        </n-button>
      </div>
    </div>
  </section>
</template>
