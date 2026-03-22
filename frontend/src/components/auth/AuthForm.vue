<script setup lang="ts">
import { NButton, NCard } from 'naive-ui'

defineProps<{
  title: string
  buttonText: string
  type: 'login' | 'register'
  loading?: boolean
}>()

const emit = defineEmits(['submit'])
</script>

<template>
  <div class="flex items-center justify-center min-h-screen bg-slate-50 relative overflow-hidden">
    <!-- Background Decor -->
    <div class="absolute inset-0 z-0">
      <div class="absolute top-0 left-0 w-full h-full bg-[radial-gradient(circle_at_center,_var(--tw-gradient-stops))] from-slate-200 via-slate-50 to-slate-50 opacity-50"></div>
      <div class="absolute -top-[20%] -right-[10%] w-[600px] h-[600px] rounded-full bg-accent/10 blur-3xl"></div>
      <div class="absolute -bottom-[20%] -left-[10%] w-[600px] h-[600px] rounded-full bg-blue-500/10 blur-3xl"></div>
    </div>

    <n-card class="w-full max-w-md shadow-2xl relative z-10 border-none bg-white/80 backdrop-blur-xl" size="huge" :bordered="false">
      <div class="text-center mb-8">
        <div class="w-12 h-12 bg-accent rounded-xl mx-auto mb-4 flex items-center justify-center shadow-lg shadow-accent/30">
          <span class="text-white font-bold font-display text-2xl">M</span>
        </div>
        <h2 class="text-3xl font-display font-bold text-slate-900">{{ title }}</h2>
      </div>

      <form class="space-y-6" @submit.prevent="emit('submit')">
        <slot></slot>

        <div class="mt-6">
          <n-button
            attr-type="submit"
            type="primary"
            block
            size="large"
            :loading="loading"
            class="shadow-lg shadow-accent/30 transition-shadow hover:shadow-accent/50"
          >
            {{ buttonText }}
          </n-button>
        </div>

        <div class="mt-6 text-center text-slate-500 text-sm">
          <slot name="footer"></slot>
        </div>
      </form>
    </n-card>
  </div>
</template>
