<script setup lang="ts">
import { ref, onMounted, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  NButton,
  NInput,
  NSelect,
  NPagination,
  NSpin,
  NEmpty,
  NTag,
  NSlider,
  NCollapse,
  NCollapseItem,
  NSpace
} from 'naive-ui'
import NavBar from '@/components/layout/NavBar.vue'
import MovieCard from '@/components/movie/MovieCard.vue'
import {
  getAllGenres,
  getAllRegions,
  getMovieCatalog
} from '@/api/endpoints/movie-management/movie-management'
import type { Movie, CatalogQueryDTO, PageInfoMovie } from '@/api/model'
import { getMovieId } from '@/utils/movie'

const route = useRoute()
const router = useRouter()

// 状态
const movies = ref<Movie[]>([])
const total = ref(0)
const loading = ref(false)
const genres = ref<string[]>([])
const regions = ref<string[]>([])

// 筛选条件
const filters = ref<CatalogQueryDTO>({
  page: 1,
  size: 20,
  genres: undefined,
  regions: undefined,
  minScore: undefined,
  maxScore: undefined,
  startYear: undefined,
  endYear: undefined,
  sortBy: 'score',
  sortOrder: 'desc'
})

const searchKeyword = ref('')

// 排序选项
const sortOptions = [
  { label: '评分最高', value: 'score' },
  { label: '最新上映', value: 'year' },
  { label: '热度最高', value: 'votes' }
]

// 评分范围
const scoreRange = ref<[number, number]>([0, 10])

// 选中的类型和地区
const selectedGenres = ref<string[]>([])
const selectedRegions = ref<string[]>([])

// 从URL参数初始化筛选
const initFromQuery = () => {
  const query = route.query
  filters.value.page = query.page ? parseInt(query.page as string) : 1
  filters.value.size = query.size ? parseInt(query.size as string) : 20
  filters.value.sortBy = (query.sortBy as string) || 'score'
  filters.value.sortOrder = (query.sortOrder as string) || 'desc'
  filters.value.startYear = query.startYear ? parseInt(query.startYear as string) : undefined
  filters.value.endYear = query.endYear ? parseInt(query.endYear as string) : undefined
  filters.value.minScore = query.minScore ? parseFloat(query.minScore as string) : undefined
  filters.value.maxScore = query.maxScore ? parseFloat(query.maxScore as string) : undefined
  
  if (query.genres) {
    selectedGenres.value = (query.genres as string).split(',')
  }
  if (query.regions) {
    selectedRegions.value = (query.regions as string).split(',')
  }
  if (query.keyword) {
    searchKeyword.value = query.keyword as string
  }
  
  scoreRange.value = [
    filters.value.minScore || 0,
    filters.value.maxScore || 10
  ]
}

// 获取筛选数据
const fetchFilterData = async () => {
  const [genreRaw, regionRaw] = await Promise.all([
    getAllGenres().catch(() => []),
    getAllRegions().catch(() => [])
  ])

  const genreList = Array.isArray(genreRaw) ? (genreRaw as string[]) : []
  const regionList = Array.isArray(regionRaw) ? (regionRaw as string[]) : []

  if (genreList.length > 0) genres.value = genreList
  if (regionList.length > 0) regions.value = regionList
}

const splitMultiValue = (value?: string): string[] => {
  if (!value) return []
  return value
    .split(/[，,/|]/)
    .map((item) => item.trim())
    .filter(Boolean)
}

// 获取电影列表
const fetchMovies = async () => {
  loading.value = true
  try {
    // 构建查询参数
    const params: Record<string, unknown> = {
      page: filters.value.page,
      size: filters.value.size,
      sortBy: filters.value.sortBy,
      sortOrder: filters.value.sortOrder
    }
    
    if (selectedGenres.value.length > 0) {
      params.genres = selectedGenres.value.join(',')
    }
    if (selectedRegions.value.length > 0) {
      params.regions = selectedRegions.value.join(',')
    }
    if (scoreRange.value[0] > 0) {
      params.minScore = scoreRange.value[0]
    }
    if (scoreRange.value[1] < 10) {
      params.maxScore = scoreRange.value[1]
    }
    if (filters.value.startYear) {
      params.startYear = filters.value.startYear
    }
    if (filters.value.endYear) {
      params.endYear = filters.value.endYear
    }
    
    const pageInfo = await getMovieCatalog(params as never) as PageInfoMovie | null
    if (pageInfo) {
      const movieList = pageInfo.list || []
      movies.value = movieList
      total.value = pageInfo.total || 0

      // 当后端筛选元数据接口超时时，使用当前页数据兜底填充筛选项
      if (genres.value.length === 0 || regions.value.length === 0) {
        const genreSet = new Set<string>()
        const regionSet = new Set<string>()

        movieList.forEach((movie) => {
          splitMultiValue(movie.genres).forEach((item) => genreSet.add(item))
          splitMultiValue(movie.regions).forEach((item) => regionSet.add(item))
        })

        if (genres.value.length === 0 && genreSet.size > 0) {
          genres.value = Array.from(genreSet)
        }
        if (regions.value.length === 0 && regionSet.size > 0) {
          regions.value = Array.from(regionSet)
        }
      }
    }
  } catch (error) {
    console.error('Failed to fetch movies:', error)
  } finally {
    loading.value = false
  }
}

// 更新URL参数
const hasQueryChanged = (query: Record<string, string>): boolean => {
  const routeQuery = route.query as Record<string, string | string[] | null | undefined>
  const allKeys = new Set([...Object.keys(routeQuery), ...Object.keys(query)])
  for (const key of allKeys) {
    const currentValue = routeQuery[key]
    const normalizedCurrent =
      typeof currentValue === 'string'
        ? currentValue
        : Array.isArray(currentValue)
          ? currentValue.join(',')
          : ''
    const nextValue = query[key] ?? ''
    if (normalizedCurrent !== nextValue) {
      return true
    }
  }
  return false
}

const updateQueryParams = () => {
  const query: Record<string, string> = {}
  
  if (filters.value.page && filters.value.page > 1) query.page = String(filters.value.page)
  if (filters.value.size && filters.value.size !== 20) query.size = String(filters.value.size)
  if (filters.value.sortBy && filters.value.sortBy !== 'score') query.sortBy = filters.value.sortBy
  if (filters.value.sortOrder && filters.value.sortOrder !== 'desc') query.sortOrder = filters.value.sortOrder
  if (selectedGenres.value.length > 0) query.genres = selectedGenres.value.join(',')
  if (selectedRegions.value.length > 0) query.regions = selectedRegions.value.join(',')
  if (scoreRange.value[0] > 0) query.minScore = String(scoreRange.value[0])
  if (scoreRange.value[1] < 10) query.maxScore = String(scoreRange.value[1])
  if (filters.value.startYear) query.startYear = String(filters.value.startYear)
  if (filters.value.endYear) query.endYear = String(filters.value.endYear)
  if (searchKeyword.value) query.keyword = searchKeyword.value
  
  const changed = hasQueryChanged(query)
  if (changed) {
    void router.replace({ query })
  }
  return changed
}

// 处理筛选变化
const handleFilterChange = () => {
  filters.value.page = 1
  const changed = updateQueryParams()
  if (!changed) {
    fetchMovies()
  }
}

// 处理页码变化
const handlePageChange = (page: number) => {
  filters.value.page = page
  const changed = updateQueryParams()
  if (!changed) {
    fetchMovies()
  }
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

// 处理搜索
const handleSearch = () => {
  // 搜索功能需要使用 search API，这里简化处理
  handleFilterChange()
}

// 切换类型选择
const toggleGenre = (genre: string) => {
  const index = selectedGenres.value.indexOf(genre)
  if (index > -1) {
    selectedGenres.value.splice(index, 1)
  } else {
    selectedGenres.value.push(genre)
  }
  handleFilterChange()
}

// 切换地区选择
const toggleRegion = (region: string) => {
  const index = selectedRegions.value.indexOf(region)
  if (index > -1) {
    selectedRegions.value.splice(index, 1)
  } else {
    selectedRegions.value.push(region)
  }
  handleFilterChange()
}

// 清空筛选
const clearFilters = () => {
  selectedGenres.value = []
  selectedRegions.value = []
  scoreRange.value = [0, 10]
  filters.value = {
    page: 1,
    size: 20,
    sortBy: 'score',
    sortOrder: 'desc'
  }
  searchKeyword.value = ''
  handleFilterChange()
}

// 计算总页数
const totalPages = computed(() => Math.ceil(total.value / (filters.value.size || 20)))

// 年份范围选项
const yearOptions = computed(() => {
  const options: Array<{ label: string; value: number }> = []
  const currentYear = new Date().getFullYear()
  for (let year = currentYear; year >= 1900; year--) {
    options.push({
      label: String(year),
      value: year
    })
  }
  return options
})

// 监听路由变化
watch(() => route.query, () => {
  initFromQuery()
  fetchMovies()
}, { immediate: false })

onMounted(() => {
  initFromQuery()
  fetchFilterData()
  fetchMovies()
})
</script>

<template>
  <div class="min-h-screen bg-slate-50 flex flex-col">
    <NavBar />

    <!-- Main Content -->
    <main class="container mx-auto px-4 py-8 flex-1">
      <div class="flex flex-col lg:flex-row gap-8">
        <!-- Sidebar Filters -->
        <aside class="lg:w-64 flex-shrink-0">
          <div class="bg-white rounded-xl shadow-sm border border-slate-200 p-6 sticky top-24">
            <div class="flex items-center justify-between mb-4">
              <h3 class="font-bold text-slate-900">筛选条件</h3>
              <n-button text size="small" @click="clearFilters">
                清空
              </n-button>
            </div>

            <n-collapse :default-expanded-names="['genre', 'region', 'score', 'year']">
              <!-- Genre Filter -->
              <n-collapse-item title="电影类型" name="genre">
                <div class="flex flex-wrap gap-2">
                  <n-tag
                    v-for="genre in genres"
                    :key="genre"
                    :type="selectedGenres.includes(genre) ? 'primary' : 'default'"
                    :checkable="true"
                    :checked="selectedGenres.includes(genre)"
                    @click="toggleGenre(genre)"
                    size="small"
                  >
                    {{ genre }}
                  </n-tag>
                </div>
              </n-collapse-item>

              <!-- Region Filter -->
              <n-collapse-item title="制片地区" name="region">
                <div class="flex flex-wrap gap-2">
                  <n-tag
                    v-for="region in regions"
                    :key="region"
                    :type="selectedRegions.includes(region) ? 'primary' : 'default'"
                    :checkable="true"
                    :checked="selectedRegions.includes(region)"
                    @click="toggleRegion(region)"
                    size="small"
                  >
                    {{ region }}
                  </n-tag>
                </div>
              </n-collapse-item>

              <!-- Score Filter -->
              <n-collapse-item title="豆瓣评分" name="score">
                <div class="px-2">
                  <n-slider
                    v-model:value="scoreRange"
                    range
                    :min="0"
                    :max="10"
                    :step="0.1"
                    @update:value="handleFilterChange"
                  />
                  <div class="flex justify-between text-xs text-slate-500 mt-2">
                    <span>{{ scoreRange[0] }}分</span>
                    <span>{{ scoreRange[1] }}分</span>
                  </div>
                </div>
              </n-collapse-item>

              <!-- Year Filter -->
              <n-collapse-item title="上映年份" name="year">
                <n-space vertical>
                  <n-select
                    v-model:value="filters.startYear"
                    placeholder="起始年份"
                    :options="yearOptions"
                    clearable
                    @update:value="handleFilterChange"
                  />
                  <n-select
                    v-model:value="filters.endYear"
                    placeholder="结束年份"
                    :options="yearOptions"
                    clearable
                    @update:value="handleFilterChange"
                  />
                </n-space>
              </n-collapse-item>
            </n-collapse>
          </div>
        </aside>

        <!-- Movie Grid -->
        <div class="flex-1">
          <!-- Toolbar -->
          <div class="flex items-center justify-between mb-6 bg-white p-4 rounded-xl shadow-sm border border-slate-200">
            <div class="text-slate-600">
              共 <span class="font-bold text-slate-900">{{ total }}</span> 部电影
            </div>
            <div class="flex items-center gap-4">
              <span class="text-sm text-slate-500">排序：</span>
              <n-select
                v-model:value="filters.sortBy"
                :options="sortOptions"
                size="small"
                style="width: 120px"
                @update:value="handleFilterChange"
              />
              <n-button
                text
                size="small"
                @click="filters.sortOrder = filters.sortOrder === 'desc' ? 'asc' : 'desc'; handleFilterChange()"
              >
                {{ filters.sortOrder === 'desc' ? '降序 ↓' : '升序 ↑' }}
              </n-button>
            </div>
          </div>

          <!-- Loading State -->
          <div v-if="loading" class="flex justify-center items-center py-20">
            <n-spin size="large" />
          </div>

          <!-- Empty State -->
          <div v-else-if="movies.length === 0" class="flex justify-center items-center py-20">
            <n-empty description="暂无符合条件的电影">
              <template #extra>
                <n-button @click="clearFilters">清空筛选</n-button>
              </template>
            </n-empty>
          </div>

          <!-- Movie Grid -->
          <div v-else class="grid grid-cols-2 md:grid-cols-3 xl:grid-cols-4 gap-6">
            <MovieCard
              v-for="movie in movies"
              :key="movie.id"
              :movie="{
                movieId: getMovieId(movie) ?? 0,
                name: movie.name ?? '',
                cover: movie.cover,
                doubanScore: movie.doubanScore,
                year: movie.year
              }"
            />
          </div>

          <!-- Pagination -->
          <div v-if="totalPages > 1" class="flex justify-center mt-10">
            <n-pagination
              v-model:page="filters.page"
              :page-count="totalPages"
              :page-size="filters.size"
              show-size-picker
              :page-sizes="[12, 20, 40, 60]"
              @update:page="handlePageChange"
              @update:page-size="(size) => { filters.size = size; handleFilterChange() }"
            />
          </div>
        </div>
      </div>
    </main>

    <footer class="bg-slate-900 text-slate-400 py-12 text-center mt-auto">
      <p>&copy; 2026 MovieReviews. 保留所有权利</p>
    </footer>
  </div>
</template>

<style scoped>
/* Custom styles for the movies page */
</style>
