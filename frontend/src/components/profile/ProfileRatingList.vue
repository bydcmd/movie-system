<script setup lang="ts">
import { useRouter } from 'vue-router'
import { NButton, NEmpty, NRate } from 'naive-ui'
import type { MyRatingVO } from '@/api/model'
import { formatDateLabel, resolveAssetUrl, truncateText } from '@/utils/profile'

defineProps<{
  items: MyRatingVO[]
  total: number
  loading?: boolean
}>()

const router = useRouter()

function openMovie(movieId?: number) {
  if (!movieId) {
    return
  }

  void router.push(`/movie/${movieId}`)
}
</script>

<template>
  <div class="profile-rating-list">
    <div class="profile-rating-header">
      <div>
        <h3 class="profile-rating-title">评分记录</h3>
        <p class="profile-rating-description">回看自己的打分轨迹，能更清楚地看到口味偏向。</p>
      </div>
      <div class="profile-rating-total">
        共 {{ total }} 条
      </div>
    </div>

    <div v-if="loading" class="profile-rating-loading">
      <div
        v-for="index in 4"
        :key="index"
        class="profile-rating-skeleton"
      />
    </div>

    <n-empty
      v-else-if="items.length === 0"
      description="还没有评分记录，先去给喜欢的电影打分吧。"
      class="profile-rating-empty"
    />

    <div v-else class="profile-rating-rows">
      <article
        v-for="item in items"
        :key="item.id || item.movieId"
        class="profile-rating-row"
      >
        <button
          type="button"
          class="profile-rating-poster"
          @click="openMovie(item.movieId)"
        >
          <img
            v-if="resolveAssetUrl(item.posterUrl)"
            :src="resolveAssetUrl(item.posterUrl) || undefined"
            :alt="item.movieName"
            class="profile-rating-poster-image"
            loading="lazy"
          />
          <div
            v-else
            class="profile-rating-poster-fallback"
          >
            评分
          </div>
        </button>

        <div class="profile-rating-content">
          <div class="profile-rating-top">
            <div>
              <button
                type="button"
                class="profile-rating-movie"
                @click="openMovie(item.movieId)"
              >
                {{ item.movieName || '未命名电影' }}
              </button>
              <div class="profile-rating-meta">
                评分于 {{ formatDateLabel(item.ratingTime) }}
              </div>
            </div>
            <n-rate :value="item.rating || 0" readonly />
          </div>

          <p class="profile-rating-body">
            {{ truncateText(item.description, 120) }}
          </p>

          <n-button text type="primary" class="profile-rating-link" @click="openMovie(item.movieId)">
            查看影片详情
          </n-button>
        </div>
      </article>
    </div>
  </div>
</template>

<style scoped>
.profile-rating-list {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.profile-rating-header {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}

.profile-rating-title {
  margin: 0;
  font-family: var(--font-display);
  font-size: 1.15rem;
  font-weight: 700;
  color: #0f172a;
}

.profile-rating-description {
  margin: 0.35rem 0 0;
  font-size: 0.92rem;
  line-height: 1.6;
  color: #64748b;
}

.profile-rating-total {
  font-size: 0.92rem;
  font-weight: 600;
  color: #475569;
}

.profile-rating-loading {
  display: grid;
  gap: 0.75rem;
}

.profile-rating-skeleton {
  height: 6rem;
  border-radius: 1rem;
  background: rgba(226, 232, 240, 0.72);
}

.profile-rating-empty {
  border-radius: 1rem;
  border: 1px dashed rgba(148, 163, 184, 0.36);
  padding: 3.5rem 0;
}

.profile-rating-rows {
  border-top: 1px solid rgba(148, 163, 184, 0.2);
}

.profile-rating-row {
  display: flex;
  gap: 1rem;
  border-bottom: 1px solid rgba(148, 163, 184, 0.2);
  padding: 1rem 0;
}

.profile-rating-poster {
  height: 7.75rem;
  width: 5.25rem;
  flex-shrink: 0;
  overflow: hidden;
  border-radius: 1rem;
  background: #e2e8f0;
  text-align: left;
}

.profile-rating-poster-image {
  height: 100%;
  width: 100%;
  object-fit: cover;
}

.profile-rating-poster-fallback {
  display: flex;
  height: 100%;
  width: 100%;
  align-items: center;
  justify-content: center;
  background: #0f172a;
  color: #ffffff;
  font-size: 0.78rem;
  font-weight: 700;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.profile-rating-content {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 0.75rem;
}

.profile-rating-top {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
}

.profile-rating-movie {
  color: #0f172a;
  font-size: 1.05rem;
  font-weight: 700;
  text-align: left;
  transition: color 0.2s ease;
}

.profile-rating-movie:hover {
  color: #b45309;
}

.profile-rating-meta {
  margin-top: 0.35rem;
  font-size: 0.9rem;
  color: #64748b;
}

.profile-rating-body {
  margin: 0;
  font-size: 0.94rem;
  line-height: 1.75;
  color: #475569;
}

.profile-rating-link {
  align-self: flex-start;
}
</style>
