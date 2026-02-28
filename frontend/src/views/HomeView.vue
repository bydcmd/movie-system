<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { NButton } from 'naive-ui'
import NavBar from '@/components/layout/NavBar.vue'
import MovieCard from '@/components/movie/MovieCard.vue'
import { getTrendingMovies } from '@/api/endpoints/analytics/analytics'
import type { TrendingMovieDTO } from '@/api/model'

const router = useRouter()
const trendingMovies = ref<TrendingMovieDTO[]>([])
const isLoading = ref(false)

const fetchTrendingMovies = async () => {
  isLoading.value = true
  try {
    const response = await getTrendingMovies({ period: 'DAILY', limit: 10 })
    trendingMovies.value = Array.isArray(response) ? (response as TrendingMovieDTO[]) : []
  } catch (error) {
    console.error('Failed to fetch trending movies:', error)
    trendingMovies.value = []
  } finally {
    isLoading.value = false
  }
}

const goToMovies = () => {
  router.push('/movies')
}

onMounted(() => {
  void fetchTrendingMovies()
})
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
          <n-button type="primary" size="large" class="px-8 py-6 text-lg rounded-full font-bold">
            立即探索
          </n-button>
        </div>
      </div>
    </section>

    <!-- Main Content -->
    <main class="container mx-auto px-4 py-16 flex-1">
      <div class="flex items-center justify-between mb-8">
        <h2 class="text-3xl font-display font-bold text-slate-900">今日热门</h2>
        <n-button text class="text-accent hover:underline" @click="goToMovies">查看全部</n-button>
      </div>

      <div class="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-8">
        <div v-if="isLoading" class="col-span-full py-10 text-center text-slate-500">
          正在加载热门电影...
        </div>
        <div v-else-if="trendingMovies.length === 0" class="col-span-full py-10 text-center text-slate-500">
          暂无热门电影
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
