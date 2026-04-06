<script setup lang="ts">
import { computed, ref, shallowRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NButton, NEmpty, NIcon, NSpin, NTag } from 'naive-ui'
import { ArrowBack } from '@vicons/ionicons5'
import type { Movie, Person } from '@/api/model'
import {
  useGetPersonDetail,
  useGetPersonMovies
} from '@/api/endpoints/person-management/person-management'
import NavBar from '@/components/layout/NavBar.vue'
import MoviePlaceholder from '@/components/movie/MoviePlaceholder.vue'
import { resolveAssetUrl, splitCsvLike } from '@/utils/profile'
import { getMovieId } from '@/utils/movie'

const route = useRoute()
const router = useRouter()

const personId = computed(() => Number(route.params.id))
const loading = shallowRef(true)
const person = ref<Person | null>(null)
const movies = ref<Movie[]>([])
const moviesLoading = shallowRef(false)
const avatarError = shallowRef(false)
const posterErrors = ref<Record<string, boolean>>({})

const personDetailQuery = useGetPersonDetail(personId, {
  query: { enabled: false, retry: false }
})

const personMoviesQuery = useGetPersonMovies(personId, {
  query: { enabled: false, retry: false }
})

const avatarUrl = computed(() => resolveAssetUrl(person.value?.avatar))

const professionTags = computed(() =>
  (person.value?.profession ?? '')
    .split(/[,，/]/)
    .map((s) => s.trim())
    .filter(Boolean)
)

const displayName = computed(() => {
  const p = person.value
  if (!p) return ''
  return p.name || p.nameZh || p.nameEn || '未知影人'
})

const secondaryName = computed(() => {
  const p = person.value
  if (!p) return ''
  const primary = displayName.value
  if (p.nameEn && p.nameEn !== primary) return p.nameEn
  if (p.nameZh && p.nameZh !== primary) return p.nameZh
  return ''
})

type FilmographyItem = {
  key: string
  title: string
  meta: string
  posterUrl: string | undefined
  posterKey: string
  scoreLabel: string
  movieId: number
}

const filmography = computed<FilmographyItem[]>(() =>
  movies.value
    .map((m) => {
      const id = getMovieId(m)
      if (!id || id <= 0) return null
      const title = m.name || '未命名电影'
      const posterUrl = resolveAssetUrl(m.cover) || undefined
      const posterKey = `${id}::${posterUrl ?? 'none'}`
      const parts: string[] = []
      if (m.year) parts.push(String(m.year))
      const genres = splitCsvLike(m.genres)
      if (genres.length > 0) parts.push(genres.slice(0, 2).join(' / '))
      let scoreLabel = '暂无评分'
      if (typeof m.doubanScore === 'number') scoreLabel = `豆瓣 ${m.doubanScore.toFixed(1)}`
      else if (typeof m.score === 'number') scoreLabel = `本站 ${(m.score / 2).toFixed(1)}`
      return { key: String(id), title, meta: parts.join(' · '), posterUrl, posterKey, scoreLabel, movieId: id }
    })
    .filter((item): item is FilmographyItem => item !== null)
)

const hasPoster = (item: FilmographyItem): boolean =>
  Boolean(item.posterUrl) && !posterErrors.value[item.posterKey]

const handlePosterError = (key: string) => {
  posterErrors.value = { ...posterErrors.value, [key]: true }
}

const fetchPersonDetail = async () => {
  if (!personId.value) return
  loading.value = true
  try {
    const result = await personDetailQuery.refetch()
    if (result.error) throw result.error
    person.value = (result.data as Person) ?? null
  } catch (error) {
    console.error('Failed to fetch person detail:', error)
    person.value = null
  } finally {
    loading.value = false
  }
}

const fetchPersonMovies = async () => {
  if (!personId.value) return
  moviesLoading.value = true
  try {
    const result = await personMoviesQuery.refetch()
    if (result.error) throw result.error
    movies.value = Array.isArray(result.data) ? (result.data as Movie[]) : []
  } catch (error) {
    console.error('Failed to fetch person movies:', error)
    movies.value = []
  } finally {
    moviesLoading.value = false
  }
}

const goBack = () => {
  router.back()
}

const openMovie = (movieId: number) => {
  void router.push({ name: 'movie-detail', params: { id: movieId } })
}

watch(
  personId,
  () => {
    person.value = null
    movies.value = []
    avatarError.value = false
    posterErrors.value = {}
    fetchPersonDetail()
    fetchPersonMovies()
  },
  { immediate: true }
)
</script>

<template>
  <div class="min-h-screen bg-slate-50 flex flex-col">
    <NavBar />

    <!-- Loading -->
    <div v-if="loading" class="flex-1 flex justify-center items-center">
      <n-spin size="large" />
    </div>

    <!-- Not Found -->
    <div v-else-if="!person" class="flex-1 flex justify-center items-center">
      <n-empty description="影人不存在或已被删除">
        <template #extra>
          <n-button @click="goBack">返回</n-button>
        </template>
      </n-empty>
    </div>

    <!-- Person Detail -->
    <template v-else>
      <!-- Hero -->
      <section class="relative bg-slate-900 text-white overflow-hidden">
        <div class="absolute inset-0 bg-gradient-to-r from-slate-900 via-slate-900/95 to-slate-900/80" />

        <div class="container mx-auto px-4 py-8 relative z-10">
          <n-button text class="text-slate-400 hover:text-white mb-6" @click="goBack">
            <template #icon>
              <n-icon><ArrowBack /></n-icon>
            </template>
            返回
          </n-button>

          <div class="flex flex-col md:flex-row gap-8">
            <!-- Avatar -->
            <div class="w-full md:w-56 flex-shrink-0">
              <div class="aspect-[3/4] rounded-xl overflow-hidden shadow-2xl bg-slate-800 flex items-center justify-center">
                <img
                  v-if="avatarUrl && !avatarError"
                  :src="avatarUrl"
                  :alt="displayName"
                  class="w-full h-full object-cover"
                  @error="avatarError = true"
                />
                <div v-else class="text-5xl font-bold text-slate-600 select-none">
                  {{ displayName.charAt(0) }}
                </div>
              </div>
            </div>

            <!-- Info -->
            <div class="flex-1 py-4">
              <h1 class="text-4xl md:text-5xl font-display font-bold mb-2">
                {{ displayName }}
              </h1>
              <p v-if="secondaryName" class="text-slate-400 text-lg mb-4">{{ secondaryName }}</p>

              <div class="flex flex-wrap gap-2 mb-6">
                <n-tag v-for="tag in professionTags" :key="tag" type="primary" size="small">
                  {{ tag }}
                </n-tag>
                <n-tag v-if="person.sex" size="small">{{ person.sex }}</n-tag>
              </div>

              <div class="space-y-3 text-sm text-slate-300">
                <div v-if="person.birth">
                  <span class="text-slate-500">出生日期：</span>{{ person.birth }}
                </div>
                <div v-if="person.birthplace">
                  <span class="text-slate-500">出生地：</span>{{ person.birthplace }}
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- Main Content -->
      <main class="container mx-auto px-4 py-8 flex-1">
        <div class="grid grid-cols-1 lg:grid-cols-3 gap-8">
          <!-- Biography -->
          <div class="lg:col-span-2 space-y-8">
            <section v-if="person.biography" class="bg-white rounded-xl p-6 shadow-sm border border-slate-200">
              <h2 class="text-xl font-bold text-slate-900 mb-4">个人简介</h2>
              <p class="text-slate-600 leading-relaxed whitespace-pre-line">{{ person.biography }}</p>
            </section>

            <!-- Filmography -->
            <section class="bg-white rounded-xl p-6 shadow-sm border border-slate-200">
              <h2 class="text-xl font-bold text-slate-900 mb-4">
                参演作品
                <span v-if="!moviesLoading && filmography.length > 0" class="text-base font-normal text-slate-400 ml-2">
                  ({{ filmography.length }})
                </span>
              </h2>

              <div v-if="moviesLoading" class="flex items-center justify-center py-10">
                <n-spin size="small" />
              </div>

              <div v-else-if="filmography.length > 0" class="divide-y divide-slate-100">
                <button
                  v-for="item in filmography"
                  :key="item.key"
                  type="button"
                  class="flex w-full items-start gap-4 py-4 text-left first:pt-0 last:pb-0"
                  @click="openMovie(item.movieId)"
                >
                  <div class="h-24 w-16 flex-shrink-0 overflow-hidden rounded-lg bg-slate-100 relative">
                    <MoviePlaceholder
                      v-if="!hasPoster(item)"
                      :title="item.title"
                      size="small"
                      class="absolute inset-0"
                    />
                    <img
                      v-else
                      :src="item.posterUrl"
                      :alt="item.title"
                      class="h-full w-full object-cover transition-transform duration-300 hover:scale-105"
                      loading="lazy"
                      @error="handlePosterError(item.posterKey)"
                    />
                  </div>

                  <div class="min-w-0 flex-1">
                    <div class="flex items-start justify-between gap-3">
                      <div class="min-w-0">
                        <div class="truncate text-base font-semibold text-slate-900 transition-colors hover:text-primary">
                          {{ item.title }}
                        </div>
                        <div v-if="item.meta" class="mt-1 text-sm text-slate-500">
                          {{ item.meta }}
                        </div>
                      </div>
                      <div class="flex-shrink-0 text-sm font-medium text-amber-500">
                        {{ item.scoreLabel }}
                      </div>
                    </div>
                  </div>
                </button>
              </div>

              <div v-else class="py-2 text-sm text-slate-500">
                暂无作品信息
              </div>
            </section>
          </div>

          <!-- Sidebar -->
          <div class="space-y-6">
            <section class="bg-white rounded-xl p-6 shadow-sm border border-slate-200">
              <h2 class="text-lg font-bold text-slate-900 mb-4">基本信息</h2>
              <div class="space-y-4">
                <div v-if="person.nameEn">
                  <div class="text-sm text-slate-500">英文名</div>
                  <div class="text-slate-900">{{ person.nameEn }}</div>
                </div>
                <div v-if="person.nameZh">
                  <div class="text-sm text-slate-500">中文名</div>
                  <div class="text-slate-900">{{ person.nameZh }}</div>
                </div>
                <div v-if="person.sex">
                  <div class="text-sm text-slate-500">性别</div>
                  <div class="text-slate-900">{{ person.sex }}</div>
                </div>
                <div v-if="person.birth">
                  <div class="text-sm text-slate-500">出生日期</div>
                  <div class="text-slate-900">{{ person.birth }}</div>
                </div>
                <div v-if="person.birthplace">
                  <div class="text-sm text-slate-500">出生地</div>
                  <div class="text-slate-900">{{ person.birthplace }}</div>
                </div>
                <div v-if="person.profession">
                  <div class="text-sm text-slate-500">职业</div>
                  <div class="text-slate-900">{{ person.profession }}</div>
                </div>
              </div>
            </section>
          </div>
        </div>
      </main>
    </template>

    <footer class="bg-slate-900 text-slate-400 py-12 text-center mt-auto">
      <p>&copy; 2026 MovieReviews. 保留所有权利</p>
    </footer>
  </div>
</template>
