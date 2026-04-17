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

    <main class="container mx-auto px-4 py-8 flex-1">
      <!-- Page Header -->
      <div class="bg-white rounded-xl shadow-sm border border-slate-200 p-6 mb-6">
        <div class="flex flex-col lg:flex-row lg:items-end lg:justify-between gap-4">
          <div>
            <p class="text-sm font-semibold uppercase tracking-widest text-amber-500">Trending 100</p>
            <h1 class="mt-2 text-2xl font-display font-bold text-slate-900">
              {{ pageTitle }}
            </h1>
            <p class="mt-1 text-sm text-slate-500">
              查看当前热榜前 100 名电影，支持日榜、周榜、月榜和总榜切换
            </p>
          </div>

          <div class="flex flex-wrap gap-3">
            <n-button quaternary size="small" @click="goHome">
              返回首页
            </n-button>
            <n-button type="primary" size="small" @click="goToMovies">
              浏览电影库
            </n-button>
          </div>
        </div>
      </div>

      <!-- Period Tabs -->
      <div class="bg-white rounded-xl shadow-sm border border-slate-200 p-4 mb-6">
        <TrendingPeriodTabs :model-value="selectedPeriod" @update:model-value="handlePeriodChange" />
      </div>

      <!-- Content -->
      <div class="bg-white rounded-xl shadow-sm border border-slate-200 p-6">
        <div v-if="isLoading" class="flex items-center justify-center py-24">
          <n-spin size="large" />
        </div>

        <div
          v-else-if="trendingMoviesQuery.isError.value"
          class="rounded-xl border border-dashed border-slate-300 py-16 text-center text-slate-500"
        >
          热榜加载失败，请稍后重试
        </div>

        <n-empty v-else-if="trendingMovies.length === 0" description="当前周期暂无热榜数据" />

        <div v-else class="grid grid-cols-2 md:grid-cols-3 xl:grid-cols-5 gap-6">
          <div
            v-for="(movie, index) in trendingMovies"
            :key="movie.movieId ?? `${selectedPeriod}-${index}`"
            class="relative"
          >
            <div class="absolute left-2 top-2 z-10 rounded-full bg-accent px-2 py-0.5 text-xs font-semibold text-white shadow-md">
              #{{ movie.rank ?? index + 1 }}
            </div>
            <MovieCard :movie="movie" />
          </div>
        </div>
      </div>
    </main>

    <footer class="bg-slate-900 text-slate-400 py-12 text-center mt-auto">
      <p>&copy; 2026 MovieReviews. 保留所有权利</p>
    </footer>
  </div>
</template>
