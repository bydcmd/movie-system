<script setup lang="ts">
import { computed } from 'vue'
import { NAvatar, NButton, NIcon } from 'naive-ui'
import { CreateOutline, LinkOutline, MailOutline, RefreshOutline } from '@vicons/ionicons5'
import type { UserProfileVO } from '@/api/model'
import { getNameInitial, resolveAssetUrl } from '@/utils/profile'

const props = defineProps<{
  profile: UserProfileVO | null
  refreshing?: boolean
  lastUpdatedText?: string
}>()

const emit = defineEmits<{
  editProfile: []
  refresh: []
}>()

const avatarUrl = computed(() => resolveAssetUrl(props.profile?.avatar))
const avatarFallback = computed(() => getNameInitial(props.profile?.nickname ?? props.profile?.id))
const profileLink = computed(() => props.profile?.url?.trim() || '')
const emailLink = computed(() => props.profile?.email?.trim() || '')
</script>

<template>
  <section class="rounded-[32px] border border-white/60 bg-[rgba(255,252,246,0.82)] p-6 shadow-[0_30px_90px_rgba(15,23,42,0.08)] backdrop-blur-xl sm:p-8">
    <div class="flex flex-col gap-6 lg:flex-row lg:items-start lg:justify-between">
      <div class="flex flex-col gap-5 sm:flex-row sm:items-start">
        <n-avatar
          round
          :size="96"
          :src="avatarUrl || undefined"
          class="border-4 border-white shadow-lg shadow-amber-100/70"
        >
          {{ avatarFallback }}
        </n-avatar>

        <div class="space-y-4">
          <div class="space-y-2">
            <div class="inline-flex items-center rounded-full bg-slate-900 px-3 py-1 text-xs font-semibold uppercase tracking-[0.22em] text-white">
              Cine File
            </div>
            <div class="space-y-2">
              <h1 class="text-3xl font-display font-semibold tracking-tight text-slate-950 sm:text-4xl">
                {{ profile?.nickname || '未设置昵称' }}
              </h1>
              <p class="max-w-2xl text-sm leading-6 text-slate-600 sm:text-base">
                这里集中管理你的观影资料、收藏片单与社区表达，让个人品味和历史记录都有清晰的归档。
              </p>
            </div>
          </div>

          <div class="flex flex-wrap gap-3 text-sm text-slate-600">
            <div class="rounded-full border border-amber-200 bg-amber-50 px-3 py-1.5">
              用户 ID: {{ profile?.id || '未同步' }}
            </div>
            <div class="rounded-full border border-slate-200 bg-white px-3 py-1.5">
              {{ lastUpdatedText || '尚未同步资料' }}
            </div>
          </div>

          <div class="grid gap-3 text-sm text-slate-700 sm:grid-cols-2">
            <a
              v-if="emailLink"
              :href="`mailto:${emailLink}`"
              class="flex items-center gap-2 rounded-2xl border border-slate-200 bg-white px-4 py-3 transition-colors hover:border-amber-300 hover:text-slate-950"
            >
              <n-icon :component="MailOutline" />
              <span class="truncate">{{ emailLink }}</span>
            </a>
            <div
              v-else
              class="flex items-center gap-2 rounded-2xl border border-dashed border-slate-200 bg-white/70 px-4 py-3 text-slate-400"
            >
              <n-icon :component="MailOutline" />
              <span>未填写邮箱</span>
            </div>

            <a
              v-if="profileLink"
              :href="profileLink"
              target="_blank"
              rel="noreferrer"
              class="flex items-center gap-2 rounded-2xl border border-slate-200 bg-white px-4 py-3 transition-colors hover:border-amber-300 hover:text-slate-950"
            >
              <n-icon :component="LinkOutline" />
              <span class="truncate">访问个人主页</span>
            </a>
            <div
              v-else
              class="flex items-center gap-2 rounded-2xl border border-dashed border-slate-200 bg-white/70 px-4 py-3 text-slate-400"
            >
              <n-icon :component="LinkOutline" />
              <span>未填写主页链接</span>
            </div>
          </div>
        </div>
      </div>

      <div class="flex flex-wrap gap-3">
        <n-button tertiary strong class="rounded-full px-5" @click="emit('editProfile')">
          <template #icon>
            <n-icon :component="CreateOutline" />
          </template>
          编辑资料
        </n-button>
        <n-button
          type="primary"
          class="rounded-full px-5"
          :loading="refreshing"
          @click="emit('refresh')"
        >
          <template #icon>
            <n-icon :component="RefreshOutline" />
          </template>
          刷新数据
        </n-button>
      </div>
    </div>
  </section>
</template>
