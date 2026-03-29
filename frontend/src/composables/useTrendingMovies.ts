import { computed, unref, type MaybeRef } from 'vue'
import { useGetTrendingMovies } from '@/api/endpoints/analytics/analytics'
import {
  GetTrendingMoviesPeriod,
  type GetTrendingMoviesPeriod as TrendingPeriod,
  type TrendingMovieDTO,
} from '@/api/model'

export const TRENDING_PERIOD_OPTIONS: Array<{ label: string; value: TrendingPeriod }> = [
  { label: '日榜', value: GetTrendingMoviesPeriod.DAILY },
  { label: '周榜', value: GetTrendingMoviesPeriod.WEEKLY },
  { label: '月榜', value: GetTrendingMoviesPeriod.MONTHLY },
  { label: '总榜', value: GetTrendingMoviesPeriod.TOTAL },
]

export const normalizeTrendingMovies = (payload: unknown): TrendingMovieDTO[] => {
  return Array.isArray(payload) ? (payload as TrendingMovieDTO[]) : []
}

export const getTrendingPeriodLabel = (period: TrendingPeriod): string => {
  switch (period) {
    case GetTrendingMoviesPeriod.WEEKLY:
      return '周榜'
    case GetTrendingMoviesPeriod.MONTHLY:
      return '月榜'
    case GetTrendingMoviesPeriod.TOTAL:
      return '总榜'
    case GetTrendingMoviesPeriod.DAILY:
    default:
      return '日榜'
  }
}

export const isTrendingPeriod = (value: unknown): value is TrendingPeriod => {
  return typeof value === 'string' && Object.values(GetTrendingMoviesPeriod).includes(value as TrendingPeriod)
}

type UseTrendingMoviesOptions = {
  period: MaybeRef<TrendingPeriod>
  limit: MaybeRef<number>
}

export const useTrendingMovies = ({ period, limit }: UseTrendingMoviesOptions) => {
  const query = useGetTrendingMovies(computed(() => ({
    period: unref(period),
    limit: unref(limit),
  })))

  const movies = computed<TrendingMovieDTO[]>(() => normalizeTrendingMovies(query.data.value))
  const isLoading = computed(() => query.isLoading.value || query.isFetching.value)

  return {
    query,
    movies,
    isLoading,
  }
}
