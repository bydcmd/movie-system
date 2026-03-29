<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NButton, NEmpty, NSpin } from 'naive-ui'
import NavBar from '@/components/layout/NavBar.vue'
import MovieCard from '@/components/movie/MovieCard.vue'
import TrendingPeriodTabs from '@/components/movie/TrendingPeriodTabs.vue'
import {
  getTrendingPeriodLabel,
  isTrendingPeriod,
  useTrendingMovies,
} from '@/composables/useTrendingMovies'
import {
  GetTrendingMoviesPeriod,
  type GetTrendingMoviesPeriod as TrendingPeriod,
} from '@/api/model'

const route = useRoute()
const router = useRouter()
const topLimit = 100

const selectedPeriod = computed<TrendingPeriod>(() => {
  const period = Array.isArray(route.query.period) ? route.query.period[0] : route.query.period
  return isTrendingPeriod(period) ? period : GetTrendingMoviesPeriod.DAILY
})

const { query: trendingMoviesQuery, movies: trendingMovies, isLoading } = useTrendingMovies({
  period: selectedPeriod,
  limit: topLimit,
})

const pageTitle = computed(() => `${getTrendingPeriodLabel(selectedPeriod.value)} Top ${topLimit}`)

const handlePeriodChange = (period: TrendingPeriod) => {
  void router.replace({
    name: 'trending',
    query: {
      ...route.query,
      period,
    },
  })
}

const goHome = () => {
  void router.push({ name: 'home' })
}

const goToMovies = () => {
  void router.push({ name: 'movies' })
}
</script>

<template>
  <div class="min-h-screen bg-slate-50 flex flex-col">
    <NavBar />

    <main class="container mx-auto px-4 py-12 flex-1">
      <section class="relative overflow-hidden rounded-[2rem] bg-slate-900 px-6 py-10 text-white shadow-xl md:px-10">
        <div class="absolute inset-0 bg-[radial-gradient(circle_at_top_right,_rgba(245,158,11,0.3),_transparent_30%),radial-gradient(circle_at_bottom_left,_rgba(59,130,246,0.18),_transparent_32%)]"></div>

        <div class="relative z-10 flex flex-col gap-6 lg:flex-row lg:items-end lg:justify-between">
          <div>
            <p class="text-sm font-semibold uppercase tracking-[0.35em] text-amber-300/80">Trending 100</p>
            <h1 class="mt-4 text-4xl font-display font-bold tracking-tight md:text-5xl">
              {{ pageTitle }}
            </h1>
            <p class="mt-4 max-w-2xl text-slate-300">
              完整查看当前热榜前 100 名电影，支持日榜、周榜、月榜和总榜切换。
            </p>
          </div>

          <div class="flex flex-wrap gap-3">
            <n-button quaternary class="rounded-full" @click="goHome">返回首页</n-button>
            <n-button type="primary" class="rounded-full" @click="goToMovies">浏览电影库</n-button>
          </div>
        </div>
      </section>

      <section class="mt-8">
        <TrendingPeriodTabs :model-value="selectedPeriod" @update:model-value="handlePeriodChange" />
      </section>

      <section class="mt-8">
        <div v-if="isLoading" class="flex items-center justify-center py-24">
          <n-spin size="large" />
        </div>

        <div
          v-else-if="trendingMoviesQuery.isError.value"
          class="rounded-3xl border border-dashed border-slate-300 bg-white py-16 text-center text-slate-500"
        >
          热榜加载失败，请稍后重试。
        </div>

        <n-empty v-else-if="trendingMovies.length === 0" description="当前周期暂无热榜数据" class="rounded-3xl bg-white py-16" />

        <div v-else class="grid grid-cols-2 gap-8 md:grid-cols-3 xl:grid-cols-5">
          <div
            v-for="(movie, index) in trendingMovies"
            :key="movie.movieId ?? `${selectedPeriod}-${index}`"
            class="relative"
          >
            <div class="absolute left-3 top-3 z-10 rounded-full bg-slate-900/90 px-3 py-1 text-xs font-semibold text-white shadow-lg backdrop-blur">
              #{{ movie.rank ?? index + 1 }}
            </div>
            <MovieCard :movie="movie" />
          </div>
        </div>
      </section>
    </main>

    <footer class="bg-slate-900 py-12 text-center text-slate-400">
      <p>&copy; 2026 MovieReviews. 保留所有权利</p>
    </footer>
  </div>
</template>
