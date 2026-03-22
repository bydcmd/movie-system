<script setup lang="ts">
import type { Component } from 'vue'
import { NIcon } from 'naive-ui'

type ProfileStatItem = {
  label: string
  value: number
  hint: string
  icon: Component
}

defineProps<{
  items: ProfileStatItem[]
}>()
</script>

<template>
  <section class="grid gap-4 sm:grid-cols-2 xl:grid-cols-5">
    <article
      v-for="item in items"
      :key="item.label"
      class="rounded-[28px] border border-white/60 bg-[rgba(255,255,255,0.76)] p-5 shadow-[0_20px_50px_rgba(148,163,184,0.12)] backdrop-blur-xl"
    >
      <div class="mb-5 flex h-11 w-11 items-center justify-center rounded-2xl bg-slate-900 text-white">
        <n-icon :component="item.icon" size="22" />
      </div>
      <div class="space-y-2">
        <div class="text-sm font-medium text-slate-500">{{ item.label }}</div>
        <div class="text-3xl font-display font-semibold tracking-tight text-slate-950">
          {{ item.value }}
        </div>
        <p class="text-sm leading-6 text-slate-600">{{ item.hint }}</p>
      </div>
    </article>
  </section>
</template>
