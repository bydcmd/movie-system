<script setup lang="ts">
const props = defineProps<{
  title?: string
  size?: 'default' | 'small'
}>()

// 获取标题首字母或默认文字
const displayText = props.title ? props.title.charAt(0).toUpperCase() : '电影'

const isSmall = props.size === 'small'
</script>

<template>
  <div class="w-full h-full bg-slate-700 flex flex-col items-center justify-center relative overflow-hidden">
    <!-- 背景装饰 -->
    <div class="absolute inset-0 bg-gradient-to-br from-slate-600 via-slate-700 to-slate-800"></div>

    <!-- 电影胶片装饰 -->
    <div class="absolute top-2 left-2 right-2 flex justify-between opacity-20" :class="{ 'top-4 left-4 right-4': !isSmall }">
      <div v-for="i in (isSmall ? 4 : 8)" :key="i" class="bg-white rounded-sm" :class="isSmall ? 'w-1 h-2' : 'w-2 h-3'"></div>
    </div>
    <div class="absolute bottom-2 left-2 right-2 flex justify-between opacity-20" :class="{ 'bottom-4 left-4 right-4': !isSmall }">
      <div v-for="i in (isSmall ? 4 : 8)" :key="i" class="bg-white rounded-sm" :class="isSmall ? 'w-1 h-2' : 'w-2 h-3'"></div>
    </div>

    <!-- 中央图标 -->
    <div class="relative z-10 flex flex-col items-center">
      <div class="rounded-full bg-slate-600/50 flex items-center justify-center border-2 border-slate-500/50" :class="isSmall ? 'w-6 h-6' : 'w-16 h-16 mb-3'">
        <svg class="text-slate-400" :class="isSmall ? 'w-3 h-3' : 'w-8 h-8'" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" :stroke-width="isSmall ? 2.5 : 1.5" d="M7 4v16M17 4v16M3 8h4m10 0h4M3 12h18M3 16h4m10 0h4M4 20h16a1 1 0 001-1V5a1 1 0 00-1-1H4a1 1 0 00-1 1v14a1 1 0 001 1z"/>
        </svg>
      </div>
      <span v-if="title && !isSmall" class="text-slate-400 text-xs px-4 text-center line-clamp-2 max-w-full">
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
