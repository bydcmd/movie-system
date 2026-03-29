<script setup lang="ts">
import { computed, shallowRef } from 'vue'
import { useRouter } from 'vue-router'
import { NButton } from 'naive-ui'
import NavBar from '@/components/layout/NavBar.vue'
import MovieCard from '@/components/movie/MovieCard.vue'
import TrendingPeriodTabs from '@/components/movie/TrendingPeriodTabs.vue'
import { getTrendingPeriodLabel, useTrendingMovies } from '@/composables/useTrendingMovies'
import { GetTrendingMoviesPeriod, type GetTrendingMoviesPeriod as TrendingPeriod } from '@/api/model'

const router = useRouter()
const previewLimit = 10
const selectedPeriod = shallowRef<TrendingPeriod>(GetTrendingMoviesPeriod.DAILY)

const { query: trendingMoviesQuery, movies: trendingMovies, isLoading } = useTrendingMovies({
  period: selectedPeriod,
  limit: previewLimit,
})

const trendingSummary = computed(() => {
  return `当前展示${getTrendingPeriodLabel(selectedPeriod.value)}前 ${previewLimit} 名`
})

const goToMovies = () => {
  void router.push('/movies')
}

const goToTrendingTop = () => {
  void router.push({
    name: 'trending',
    query: {
      period: selectedPeriod.value,
    },
  })
}
</script>

<template>
  <div class="min-h-screen bg-slate-50 flex flex-col">
    <NavBar />

    <!-- Hero Section -->
    <section class="relative bg-slate-900 text-white overflow-hidden">
      <div
        class="absolute inset-0 bg-[url('https://image.tmdb.org/t/p/original/8Gxv8gSFCU0XGDykEGv7zR1n2ua.jpg')] bg-cover bg-center opacity-30">
      </div>
      <div class="absolute inset-0 bg-gradient-to-t from-slate-900 via-slate-900/50 to-transparent"></div>

      <div class="container mx-auto px-4 py-32 relative z-10 flex flex-col items-center text-center">
        <h1 class="text-5xl md:text-7xl font-display font-bold tracking-tight mb-6">
          电影<span class="text-accent">新视界</span>
        </h1>
        <p class="text-xl md:text-2xl text-slate-300 max-w-2xl font-light mb-10">
          发现、评分、评论全球精彩电影，与影迷共建社区
        </p>
        <div class="flex gap-4">
          <n-button type="primary" size="large" class="px-8 py-6 text-lg rounded-full font-bold" @click="goToMovies">
            立即探索
          </n-button>
        </div>
      </div>
    </section>

    <!-- Main Content -->
    <main class="container mx-auto px-4 py-16 flex-1">
      <div class="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
        <div>
          <h2 class="text-3xl font-display font-bold text-slate-900">热门电影</h2>
          <p class="mt-2 text-sm text-slate-500">
            {{ trendingSummary }}，支持切换日榜、周榜、月榜和总榜。
          </p>
        </div>

        <n-button type="primary" secondary class="rounded-full" @click="goToTrendingTop">
          查看 Top 100
        </n-button>
      </div>

      <TrendingPeriodTabs v-model="selectedPeriod" class="mt-6" />

      <div class="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-8">
        <div v-if="isLoading" class="col-span-full py-10 text-center text-slate-500">
          正在加载热门电影...
        </div>
        <div v-else-if="trendingMoviesQuery.isError.value" class="col-span-full py-10 text-center text-slate-500">
          热门电影加载失败
        </div>
        <div v-else-if="trendingMovies.length === 0" class="col-span-full py-10 text-center text-slate-500">
          当前周期暂无热榜数据
        </div>
        <template v-else>
          <MovieCard v-for="movie in trendingMovies" :key="movie.movieId" :movie="movie" />
        </template>
      </div>
    </main>

    <footer class="bg-slate-900 text-slate-400 py-12 text-center">
      <p>&copy; 2026 MovieReviews. 保留所有权利</p>
    </footer>
  </div>
</template>
