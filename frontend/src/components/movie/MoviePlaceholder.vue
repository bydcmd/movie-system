<script setup lang="ts">
const props = defineProps<{
  title?: string
}>()

// 获取标题首字母或默认文字
const displayText = props.title ? props.title.charAt(0).toUpperCase() : '电影'
</script>

<template>
  <div class="w-full h-full bg-slate-700 flex flex-col items-center justify-center relative overflow-hidden">
    <!-- 背景装饰 -->
    <div class="absolute inset-0 bg-gradient-to-br from-slate-600 via-slate-700 to-slate-800"></div>
    
    <!-- 电影胶片装饰 -->
    <div class="absolute top-4 left-4 right-4 flex justify-between opacity-20">
      <div v-for="i in 8" :key="i" class="w-2 h-3 bg-white rounded-sm"></div>
    </div>
    <div class="absolute bottom-4 left-4 right-4 flex justify-between opacity-20">
      <div v-for="i in 8" :key="i" class="w-2 h-3 bg-white rounded-sm"></div>
    </div>
    
    <!-- 中央图标 -->
    <div class="relative z-10 flex flex-col items-center">
      <div class="w-16 h-16 rounded-full bg-slate-600/50 flex items-center justify-center mb-3 border-2 border-slate-500/50">
        <svg class="w-8 h-8 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M7 4v16M17 4v16M3 8h4m10 0h4M3 12h18M3 16h4m10 0h4M4 20h16a1 1 0 001-1V5a1 1 0 00-1-1H4a1 1 0 00-1 1v14a1 1 0 001 1z"/>
        </svg>
      </div>
      <span v-if="title" class="text-slate-400 text-xs px-4 text-center line-clamp-2 max-w-full">
        {{ title }}
      </span>
    </div>
  </div>
</template>

<style scoped>
.line-clamp-2 {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
