<script setup lang="ts">
import { TRENDING_PERIOD_OPTIONS } from '@/composables/useTrendingMovies'
import type { GetTrendingMoviesPeriod as TrendingPeriod } from '@/api/model'

const props = defineProps<{
  modelValue: TrendingPeriod
}>()

const emit = defineEmits<{
  'update:modelValue': [value: TrendingPeriod]
}>()

const selectPeriod = (period: TrendingPeriod) => {
  if (period !== props.modelValue) {
    emit('update:modelValue', period)
  }
}
</script>

<template>
  <div class="flex flex-wrap gap-3">
    <button
      v-for="option in TRENDING_PERIOD_OPTIONS"
      :key="option.value"
      type="button"
      class="rounded-full border px-4 py-2 text-sm font-semibold transition-all duration-200"
      :class="option.value === modelValue
        ? 'border-slate-900 bg-slate-900 text-white shadow-sm'
        : 'border-slate-200 bg-white text-slate-600 hover:border-slate-300 hover:text-slate-900'"
      @click="selectPeriod(option.value)"
    >
      {{ option.label }}
    </button>
  </div>
</template>
