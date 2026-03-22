<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { NButton } from 'naive-ui'
import { getMovieId, type MovieIdLike } from '@/utils/movie'
import MoviePlaceholder from './MoviePlaceholder.vue'

type MovieCardMovie = MovieIdLike & {
  name?: string
  cover?: string
  doubanScore?: number
  year?: number
}

const props = defineProps<{
  movie: MovieCardMovie
}>()

const router = useRouter()

// 计算显示分数：优先使用 doubanScore，没有则显示 '-'
const displayScore = computed(() => {
  return props.movie.doubanScore ? props.movie.doubanScore.toFixed(1) : '-'
})

// 处理图片 URL
const imageUrl = computed(() => {
  const cover = props.movie.cover
  if (!cover) {
    return null
  }
  // 如果已经是完整 URL，直接返回
  if (cover.startsWith('http://') || cover.startsWith('https://')) {
    return cover
  }
  // 如果是相对路径，拼接 API baseURL
  if (cover.startsWith('/')) {
    return `${import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'}${cover}`
  }
  return cover
})

// 图片加载状态
const imageLoaded = ref(false)
const imageError = ref(false)

// 图片加载成功
const handleImageLoad = () => {
  imageLoaded.value = true
}

// 图片加载失败
const handleImageError = () => {
  imageError.value = true
}

// 跳转到详情页
const goToDetail = () => {
  const id = getMovieId(props.movie)
  if (id && id > 0) {
    router.push(`/movie/${id}`)
  }
}
</script>

<template>
  <div class="movie-card group relative cursor-pointer transition-transform duration-300 hover:-translate-y-2" @click="goToDetail">
    <div class="aspect-[2/3] overflow-hidden rounded-lg shadow-md group-hover:shadow-xl transition-shadow bg-slate-200 relative">
      <!-- 占位图组件 -->
      <MoviePlaceholder 
        v-if="!imageUrl || imageError" 
        :title="movie.name" 
        class="absolute inset-0"
      />
      
      <!-- 实际图片 -->
      <img 
        v-if="imageUrl && !imageError"
        :src="imageUrl" 
        :alt="movie.name" 
        class="h-full w-full object-cover transition-transform duration-500 group-hover:scale-110"
        :class="{ 'opacity-0': !imageLoaded, 'opacity-100': imageLoaded }"
        @load="handleImageLoad"
        @error="handleImageError"
        loading="lazy"
      />
      
      <!-- 悬停遮罩 -->
      <div class="absolute inset-0 bg-gradient-to-t from-black/80 via-transparent to-transparent opacity-0 group-hover:opacity-100 transition-opacity duration-300 flex flex-col justify-end p-4" @click.stop>
        <n-button type="primary" size="small" class="w-full" @click="goToDetail">查看详情</n-button>
      </div>
    </div>
    
    <div class="mt-3">
      <h3 class="font-display font-bold text-lg leading-tight truncate text-slate-900 group-hover:text-accent transition-colors">
        {{ movie.name }}
      </h3>
      <div class="flex items-center justify-between mt-1 text-sm text-slate-500">
        <span>{{ movie.year }}</span>
        <div class="flex items-center gap-1">
          <span class="text-accent">★</span>
          <span>{{ displayScore }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* Custom card styles if needed */
</style>
