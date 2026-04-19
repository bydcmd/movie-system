<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import {
  NAlert,
  NButton,
  NEmpty,
  NInput,
  NPagination,
  NSpin
} from 'naive-ui'
import AdminMovieFormModal from '@/components/admin/movie/AdminMovieFormModal.vue'
import AdminMovieListItem from '@/components/admin/movie/AdminMovieListItem.vue'
import { useAdminMovies } from '@/composables/admin/useAdminMovies'
import type { MovieId } from '@/utils/movie'

const router = useRouter()
const {
  state,
  movies,
  total,
  loading,
  hasLoadError,
  lastUpdatedText,
  formatCount,
  applyKeyword,
  resetFilters,
  refreshMovies,
  movieFormVisible,
  movieFormMode,
  movieFormInitialValue,
  submittingMovie,
  openCreateMovieForm,
  openEditMovieForm,
  submitMovieForm,
  requestDeleteMovie,
  isDeletingMovie
} = useAdminMovies()

const emptyDescription = computed(() => {
  return state.keyword ? '没有匹配的电影' : '暂时还没有电影数据'
})

function openMovieDetail(movieId?: MovieId) {
  if (!movieId) {
    return
  }

  void router.push(`/movie/${movieId}`)
}
</script>

<template>
  <section class="space-y-5">
    <div class="flex flex-wrap items-start justify-between gap-4">
      <div>
        <h2 class="font-display text-2xl font-bold text-slate-900">电影管理</h2>
        <p class="mt-2 max-w-3xl text-sm leading-7 text-slate-600">
          后台检索、录入、修改和删除电影信息，统一维护封面、简介、评分与演职员数据。
        </p>
      </div>

      <div class="flex flex-wrap items-center gap-3">
        <span class="rounded-full bg-slate-100 px-4 py-2 text-sm text-slate-600">
          最近同步 {{ lastUpdatedText }}
        </span>
        <n-button type="primary" @click="openCreateMovieForm">
          录入电影
        </n-button>
        <n-button secondary @click="refreshMovies">
          刷新列表
        </n-button>
      </div>
    </div>

    <n-alert
      v-if="hasLoadError"
      type="warning"
      title="电影数据加载失败"
      class="rounded-3xl"
    >
      可以重试刷新列表，或调整搜索条件后重新查询。
    </n-alert>

    <section class="rounded-[28px] border border-slate-200 bg-white/90 p-5 shadow-sm">
      <div class="flex flex-wrap items-center gap-3">
        <n-input
          v-model:value="state.keywordInput"
          clearable
          placeholder="搜索电影名称或关键词"
          class="min-w-[260px] flex-1"
          @keyup.enter="applyKeyword"
        />

        <div class="flex gap-3">
          <n-button type="primary" @click="applyKeyword">
            搜索
          </n-button>
          <n-button quaternary @click="resetFilters">
            清空
          </n-button>
        </div>
      </div>

      <n-spin :show="loading" class="mt-5 block">
        <div v-if="movies.length > 0" class="space-y-4">
          <AdminMovieListItem
            v-for="(entry, index) in movies"
            :key="entry.id || entry.name || `movie-${index}`"
            :movie="entry"
            :format-count="formatCount"
            :deleting="isDeletingMovie(entry.id)"
            @view-detail="openMovieDetail(entry.id)"
            @edit="openEditMovieForm(entry)"
            @delete="requestDeleteMovie(entry)"
          />
        </div>

        <n-empty
          v-else
          :description="emptyDescription"
          class="py-12"
        />
      </n-spin>

      <div class="mt-5 flex flex-wrap items-center justify-between gap-4 border-t border-slate-200 pt-5">
        <span class="text-sm text-slate-500">共 {{ formatCount(total) }} 部电影</span>

        <n-pagination
          v-model:page="state.page"
          :page-size="state.size"
          :item-count="total"
          simple
        />
      </div>
    </section>

    <AdminMovieFormModal
      v-model:show="movieFormVisible"
      :mode="movieFormMode"
      :initial-value="movieFormInitialValue"
      :saving="submittingMovie"
      @submit="submitMovieForm"
    />
  </section>
</template>
