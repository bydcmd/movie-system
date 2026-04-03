<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { NEmpty } from 'naive-ui'
import type { MovieItemVO } from '@/api/model'
import MoviePlaceholder from '@/components/movie/MoviePlaceholder.vue'
import { formatDateLabel, resolveAssetUrl, splitCsvLike, truncateText } from '@/utils/profile'

const props = withDefaults(
  defineProps<{
    items: MovieItemVO[]
    total: number
    loading?: boolean
    title: string
    description: string
    emptyDescription: string
    recordLabel?: string
  }>(),
  {
    recordLabel: '最近记录'
  }
)

const router = useRouter()

// Track which movie posters failed to load
const failedPosterIds = ref<Set<number>>(new Set())

function openMovie(movieId?: number) {
  if (!movieId) {
    return
  }

  void router.push(`/movie/${movieId}`)
}

function getPoster(movie: MovieItemVO): string | null {
  const url = resolveAssetUrl(movie.cover)
  // Ensure we don't return empty strings or invalid URLs
  return url && url.trim() ? url : null
}

function getGenres(movie: MovieItemVO) {
  return splitCsvLike(movie.genres).slice(0, 3)
}

function handlePosterError(movieId: number) {
  if (movieId) {
    failedPosterIds.value.add(movieId)
  }
}

function shouldShowPlaceholder(movie: MovieItemVO): boolean {
  return !getPoster(movie) || failedPosterIds.value.has(movie.movieId ?? 0)
}
</script>

<template>
  <div class="profile-list">
    <div class="profile-list-header">
      <div>
        <h3 class="profile-list-title">{{ title }}</h3>
        <p class="profile-list-description">{{ description }}</p>
      </div>
      <div class="profile-list-total">
        共 {{ total }} 部
      </div>
    </div>

    <div v-if="loading" class="profile-list-loading">
      <div
        v-for="index in 4"
        :key="index"
        class="profile-list-skeleton"
      />
    </div>

    <n-empty
      v-else-if="items.length === 0"
      :description="emptyDescription"
      class="profile-list-empty"
    />

    <div v-else class="profile-movie-list">
      <article
        v-for="movie in items"
        :key="movie.movieId || movie.movieName"
        class="profile-movie-row"
      >
        <button
          type="button"
          class="profile-movie-poster"
          @click="openMovie(movie.movieId)"
        >
          <MoviePlaceholder
            v-if="shouldShowPlaceholder(movie)"
            :title="movie.movieName"
            size="small"
            class="profile-movie-placeholder"
          />
          <img
            v-else
            :src="getPoster(movie) || undefined"
            :alt="movie.movieName"
            class="profile-movie-poster-image"
            loading="lazy"
            @error="handlePosterError(movie.movieId ?? 0)"
          />
        </button>

        <div class="profile-movie-content">
          <div class="profile-movie-top">
            <div class="profile-movie-heading">
              <button
                type="button"
                class="profile-movie-title"
                @click="openMovie(movie.movieId)"
              >
                {{ movie.movieName || '未命名电影' }}
              </button>

              <div class="profile-movie-meta">
                <span v-if="movie.year" class="profile-movie-chip">
                  {{ movie.year }}
                </span>
                <span
                  v-for="genre in getGenres(movie)"
                  :key="genre"
                  class="profile-movie-chip"
                >
                  {{ genre }}
                </span>
              </div>
            </div>

            <div class="profile-movie-score">
                {{ typeof movie.score === 'number' ? movie.score.toFixed(1) : '--' }}
            </div>
          </div>

          <p class="profile-movie-story">
            {{ truncateText(movie.storyline, 92) }}
          </p>

          <div class="profile-movie-foot">
            {{ recordLabel }} {{ formatDateLabel(movie.favoriteTime) }}
          </div>
        </div>
      </article>
    </div>
  </div>
</template>

<style scoped>
.profile-list {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.profile-list-header {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}

.profile-list-title {
  margin: 0;
  font-family: var(--font-display);
  font-size: 1.15rem;
  font-weight: 700;
  color: #0f172a;
}

.profile-list-description {
  margin: 0.35rem 0 0;
  font-size: 0.92rem;
  line-height: 1.6;
  color: #64748b;
}

.profile-list-total {
  font-size: 0.92rem;
  font-weight: 600;
  color: #475569;
}

.profile-list-loading {
  display: grid;
  gap: 0.75rem;
}

.profile-list-skeleton {
  height: 6.5rem;
  border-radius: 1rem;
  background: rgba(226, 232, 240, 0.72);
}

.profile-list-empty {
  border-radius: 1rem;
  border: 1px dashed rgba(148, 163, 184, 0.36);
  padding: 3.5rem 0;
}

.profile-movie-list {
  border-top: 1px solid rgba(148, 163, 184, 0.2);
}

.profile-movie-row {
  display: flex;
  gap: 1rem;
  border-bottom: 1px solid rgba(148, 163, 184, 0.2);
  padding: 1rem 0;
}

.profile-movie-poster {
  position: relative;
  height: 8.5rem;
  width: 5.75rem;
  flex-shrink: 0;
  overflow: hidden;
  border-radius: 1rem;
  background: #e2e8f0;
  text-align: left;
}

.profile-movie-placeholder,
.profile-movie-poster-image {
  height: 100%;
  width: 100%;
  object-fit: cover;
}

.profile-movie-content {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 0.75rem;
}

.profile-movie-top {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 0.75rem;
}

.profile-movie-heading {
  min-width: 0;
}

.profile-movie-title {
  color: #0f172a;
  font-size: 1.05rem;
  font-weight: 700;
  text-align: left;
  transition: color 0.2s ease;
}

.profile-movie-title:hover {
  color: #b45309;
}

.profile-movie-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 0.45rem;
  margin-top: 0.45rem;
}

.profile-movie-chip {
  font-size: 0.8rem;
  color: #475569;
}

.profile-movie-score {
  font-family: var(--font-display);
  font-size: 1.1rem;
  font-weight: 700;
  color: #b45309;
}

.profile-movie-story {
  margin: 0;
  font-size: 0.94rem;
  line-height: 1.75;
  color: #475569;
}

.profile-movie-foot {
  font-size: 0.82rem;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #94a3b8;
}

@media (max-width: 639px) {
  .profile-movie-row {
    align-items: flex-start;
  }

  .profile-movie-poster {
    height: 7.75rem;
    width: 5.25rem;
  }
}
</style>
