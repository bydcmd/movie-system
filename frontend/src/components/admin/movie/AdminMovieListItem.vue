<script setup lang="ts">
import { computed } from 'vue'
import { NButton, NTag } from 'naive-ui'
import type { Movie } from '@/api/model'
import { isRecord } from '@/utils/admin'
import { getNameInitial, resolveAssetUrl, splitCsvLike, truncateText } from '@/utils/profile'

const props = defineProps<{
  movie: Movie
  formatCount: (value?: number | null) => string
  deleting?: boolean
}>()

const emit = defineEmits<{
  viewDetail: []
  edit: []
  delete: []
}>()

const coverUrl = computed(() => resolveAssetUrl(props.movie.cover) || undefined)
const title = computed(() => props.movie.name || '未命名电影')
const genres = computed(() => splitCsvLike(props.movie.genres).slice(0, 4))
const summary = computed(() => truncateText(props.movie.alias || props.movie.storyline || '暂无简介', 140))
const hasActionableId = computed(() => typeof props.movie.id === 'number')
const directorSummary = computed(() => formatPeopleSummary(props.movie.directors, '导演'))
const actorSummary = computed(() => formatPeopleSummary(props.movie.actors, '主演'))
const writerSummary = computed(() => formatPeopleSummary(props.movie.writers, '编剧'))

function getPersonName(item: unknown): string {
  if (typeof item === 'string') {
    return item.trim()
  }

  if (!isRecord(item)) {
    return ''
  }

  if (typeof item.name === 'string') {
    return item.name.trim()
  }

  return ''
}

function formatPeopleSummary(value: unknown, label: string): string {
  if (!Array.isArray(value) || value.length === 0) {
    return ''
  }

  const names = value
    .map((item) => getPersonName(item))
    .filter(Boolean)
    .slice(0, 3)

  if (names.length === 0) {
    return ''
  }

  return `${label} ${names.join(' / ')}`
}
</script>

<template>
  <article
    class="flex flex-wrap items-start justify-between gap-4 rounded-[24px] border border-slate-200 bg-slate-50/70 p-4"
  >
    <div class="flex min-w-0 flex-1 items-start gap-4">
      <div class="flex h-[104px] w-[76px] items-center justify-center overflow-hidden rounded-2xl bg-slate-900 text-white">
        <img
          v-if="coverUrl"
          :src="coverUrl"
          :alt="title"
          class="h-full w-full object-cover"
        />
        <span v-else class="font-display text-2xl font-bold">
          {{ getNameInitial(title) }}
        </span>
      </div>

      <div class="min-w-0 flex-1">
        <div class="flex flex-wrap items-center gap-2">
          <h3 class="text-base font-semibold text-slate-900">
            {{ title }}
          </h3>

          <n-tag v-if="movie.year" size="small">
            {{ movie.year }}
          </n-tag>

          <n-tag v-if="typeof movie.score === 'number'" size="small" type="warning">
            本站 {{ movie.score.toFixed(1) }}
          </n-tag>

          <n-tag v-if="typeof movie.doubanScore === 'number'" size="small" type="success">
            豆瓣 {{ movie.doubanScore.toFixed(1) }}
          </n-tag>
        </div>

        <p class="mt-2 text-sm leading-7 text-slate-700">
          {{ summary }}
        </p>

        <div v-if="genres.length > 0" class="mt-3 flex flex-wrap gap-2">
          <span
            v-for="genre in genres"
            :key="`${movie.id || title}-${genre}`"
            class="rounded-full bg-slate-200/70 px-3 py-1 text-xs font-semibold text-slate-700"
          >
            {{ genre }}
          </span>
        </div>

        <div class="mt-3 space-y-1 text-sm leading-6 text-slate-600">
          <p>
            地区 {{ movie.regions || '未知' }} · 评分人数 {{ formatCount(movie.votes) }} · 上映
            {{ movie.releaseDate || movie.year || '未知' }}
          </p>
          <p v-if="directorSummary || actorSummary || writerSummary">
            {{ [directorSummary, actorSummary, writerSummary].filter(Boolean).join(' · ') }}
          </p>
        </div>
      </div>
    </div>

    <div class="flex flex-wrap justify-end gap-3">
      <n-button
        secondary
        size="small"
        :disabled="!hasActionableId"
        @click="emit('viewDetail')"
      >
        查看详情
      </n-button>

      <n-button
        secondary
        size="small"
        :disabled="!hasActionableId"
        @click="emit('edit')"
      >
        编辑
      </n-button>

      <n-button
        secondary
        strong
        type="error"
        size="small"
        :disabled="!hasActionableId"
        :loading="deleting"
        @click="emit('delete')"
      >
        删除
      </n-button>
    </div>
  </article>
</template>
