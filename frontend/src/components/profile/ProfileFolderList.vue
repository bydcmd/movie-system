<script setup lang="ts">
import { NEmpty, NTag } from 'naive-ui'
import type { FavoriteFolderVO } from '@/api/model'
import { formatDateLabel, formatDateTimeLabel } from '@/utils/profile'

defineProps<{
  folders: FavoriteFolderVO[]
  total: number
  loading?: boolean
}>()
</script>

<template>
  <div class="space-y-4">
    <div class="flex items-center justify-between">
      <div>
        <h3 class="text-lg font-display font-semibold text-slate-950">收藏夹编排</h3>
        <p class="text-sm text-slate-500">用不同片单整理你的观影语境和私藏主题。</p>
      </div>
      <div class="rounded-full bg-amber-50 px-3 py-1 text-sm font-medium text-amber-700">
        共 {{ total }} 个
      </div>
    </div>

    <div v-if="loading" class="grid gap-4 md:grid-cols-2">
      <div
        v-for="index in 4"
        :key="index"
        class="h-40 animate-pulse rounded-[26px] bg-slate-200/70"
      />
    </div>

    <n-empty
      v-else-if="folders.length === 0"
      description="还没有收藏夹，先从收藏几部电影开始。"
      class="rounded-[28px] border border-dashed border-slate-200 bg-white/70 py-14"
    />

    <div v-else class="grid gap-4 md:grid-cols-2">
      <article
        v-for="folder in folders"
        :key="folder.id || folder.name"
        class="rounded-[28px] border border-slate-200 bg-white p-5 shadow-[0_18px_45px_rgba(148,163,184,0.15)]"
      >
        <div class="mb-4 flex items-start justify-between gap-4">
          <div class="space-y-2">
            <h4 class="text-lg font-semibold text-slate-950">{{ folder.name || '未命名收藏夹' }}</h4>
            <p class="text-sm leading-6 text-slate-600">
              {{ folder.description || '还没有填写这个收藏夹的说明。' }}
            </p>
          </div>
          <n-tag :type="folder.isPublic === 1 ? 'warning' : 'default'" round>
            {{ folder.isPublic === 1 ? '公开' : '私密' }}
          </n-tag>
        </div>

        <div class="flex flex-wrap gap-3 text-sm text-slate-500">
          <span class="rounded-full bg-slate-100 px-3 py-1">
            {{ folder.movieCount || 0 }} 部电影
          </span>
          <span class="rounded-full bg-slate-100 px-3 py-1">
            创建于 {{ formatDateLabel(folder.createTime) }}
          </span>
          <span class="rounded-full bg-slate-100 px-3 py-1">
            更新于 {{ formatDateTimeLabel(folder.updateTime) }}
          </span>
        </div>
      </article>
    </div>
  </div>
</template>
