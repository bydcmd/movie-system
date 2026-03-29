<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import { useRouter } from 'vue-router'
import { NSpin } from 'naive-ui'
import type { Movie } from '@/api/model'
import MoviePlaceholder from '@/components/movie/MoviePlaceholder.vue'
import { resolveAssetUrl, splitCsvLike } from '@/utils/profile'
import { getMovieId } from '@/utils/movie'

const props = defineProps<{
  movies: Movie[]
  loading?: boolean
}>()

const router = useRouter()

const hasMovies = computed(() => props.movies.length > 0)
const posterLoadErrors = reactive<Record<string, boolean>>({})

type SimilarMovieViewItem = {
  key: string
  title: string
  meta: string
  movie: Movie
  posterUrl?: string
  posterStateKey: string
  scoreLabel: string
}

const buildMeta = (movie: Movie): string => {
  const parts: string[] = []

  if (movie.year) {
    parts.push(String(movie.year))
  }

  const genres = splitCsvLike(movie.genres)
  if (genres.length > 0) {
    parts.push(genres.slice(0, 2).join(' / '))
  }

  return parts.join(' · ')
}

const buildScoreLabel = (movie: Movie): string => {
  if (typeof movie.doubanScore === 'number') {
    return `豆瓣 ${movie.doubanScore.toFixed(1)}`
  }

  if (typeof movie.score === 'number') {
    return `本站 ${(movie.score / 2).toFixed(1)}`
  }

  return '暂无评分'
}

const movieItems = computed<SimilarMovieViewItem[]>(() =>
  props.movies.map((movie) => {
    const movieId = getMovieId(movie)
    const title = movie.name || '未命名电影'
    const posterUrl = resolveAssetUrl(movie.cover) || undefined
    const identity = movieId && movieId > 0 ? String(movieId) : `${title}-${movie.year ?? 'unknown'}`

    return {
      key: identity,
      title,
      meta: buildMeta(movie),
      movie,
      posterUrl,
      posterStateKey: `${identity}::${posterUrl ?? 'empty'}`,
      scoreLabel: buildScoreLabel(movie)
    }
  })
)

watch(
  movieItems,
  (items) => {
    const activeKeys = new Set(items.map((item) => item.posterStateKey))

    for (const key of Object.keys(posterLoadErrors)) {
      if (!activeKeys.has(key)) {
        delete posterLoadErrors[key]
      }
    }
  },
  { immediate: true }
)

const hasPoster = (item: SimilarMovieViewItem): boolean => {
  return Boolean(item.posterUrl) && !posterLoadErrors[item.posterStateKey]
}

const handlePosterError = (posterStateKey: string) => {
  posterLoadErrors[posterStateKey] = true
}

const openMovieDetail = (movie: Movie) => {
  const id = getMovieId(movie)
  if (!id || id <= 0) {
    return
  }

  void router.push(`/movie/${id}`)
}
</script>

<template>
  <section class="bg-white rounded-xl p-6 shadow-sm border border-slate-200">
    <h2 class="text-xl font-bold text-slate-900 mb-4">相似电影</h2>

    <div v-if="loading" class="flex items-center justify-center py-10">
      <n-spin size="small" />
    </div>

    <div v-else-if="hasMovies" class="divide-y divide-slate-100">
      <button
        v-for="item in movieItems"
        :key="item.key"
        type="button"
        class="flex w-full items-start gap-4 py-4 text-left first:pt-0 last:pb-0"
        @click="openMovieDetail(item.movie)"
      >
        <div class="h-24 w-16 flex-shrink-0 overflow-hidden rounded-lg bg-slate-100 relative">
          <MoviePlaceholder
            v-if="!hasPoster(item)"
            :title="item.title"
            class="absolute inset-0"
          />
          <img
            v-else
            :src="item.posterUrl"
            :alt="item.title"
            class="h-full w-full object-cover transition-transform duration-300 hover:scale-105"
            loading="lazy"
            @error="handlePosterError(item.posterStateKey)"
          />
        </div>

        <div class="min-w-0 flex-1">
          <div class="flex items-start justify-between gap-3">
            <div class="min-w-0">
              <div class="truncate text-base font-semibold text-slate-900 transition-colors hover:text-primary">
                {{ item.title }}
              </div>
              <div v-if="item.meta" class="mt-1 text-sm text-slate-500">
                {{ item.meta }}
              </div>
            </div>

            <div class="flex-shrink-0 text-sm font-medium text-amber-500">
              {{ item.scoreLabel }}
            </div>
          </div>
        </div>
      </button>
    </div>

    <div v-else class="py-2 text-sm text-slate-500">
      暂无相似推荐
    </div>
  </section>
</template>
