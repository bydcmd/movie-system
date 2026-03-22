<script setup lang="ts">
import { useRouter } from 'vue-router'
import { NButton, NEmpty, NTag } from 'naive-ui'
import type { Comment } from '@/api/model'
import { getCommentPreviewText, getCommentTypeLabel, isLongReview } from '@/utils/comment'
import { formatDateTimeLabel } from '@/utils/profile'

defineProps<{
  items: Comment[]
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
  <div class="profile-comment-list">
    <div class="profile-comment-header">
      <div>
        <h3 class="profile-comment-title">评论历史</h3>
        <p class="profile-comment-description">保留你参与讨论的痕迹，方便回看每一次表达。</p>
      </div>
      <div class="profile-comment-total">
        共 {{ total }} 条
      </div>
    </div>

    <div v-if="loading" class="profile-comment-loading">
      <div
        v-for="index in 4"
        :key="index"
        class="profile-comment-skeleton"
      />
    </div>

    <n-empty
      v-else-if="items.length === 0"
      description="你还没有发过评论，去留下一句观后感吧。"
      class="profile-comment-empty"
    />

    <div v-else class="profile-comment-rows">
      <article
        v-for="item in items"
        :key="item.id"
        class="profile-comment-row"
      >
        <div class="profile-comment-top">
          <div class="profile-comment-heading">
            <div class="profile-comment-meta">
              <n-tag round :type="item.type === 2 ? 'warning' : 'default'">
                {{ getCommentTypeLabel(item.type) }}
              </n-tag>
              <span class="profile-comment-time">
                发布于 {{ formatDateTimeLabel(item.commentTime) }}
              </span>
            </div>
            <h4 class="profile-comment-name">
              {{ item.title || (isLongReview(item.type) ? '未命名长评' : '我的短评') }}
            </h4>
          </div>
          <div class="profile-comment-votes">
            获赞 {{ item.votes || 0 }}
          </div>
        </div>

        <p class="profile-comment-body">
          {{ getCommentPreviewText(item, 180) }}
        </p>

        <div class="profile-comment-foot">
          <span class="profile-comment-movie">
            所属电影 {{ item.movieId ? `#${item.movieId}` : '暂不可用' }}
          </span>
          <n-button text type="primary" :disabled="!item.movieId" @click="openMovie(item.movieId)">
            查看对应电影
          </n-button>
        </div>
      </article>
    </div>
  </div>
</template>

<style scoped>
.profile-comment-list {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.profile-comment-header {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}

.profile-comment-title {
  margin: 0;
  font-family: var(--font-display);
  font-size: 1.15rem;
  font-weight: 700;
  color: #0f172a;
}

.profile-comment-description {
  margin: 0.35rem 0 0;
  font-size: 0.92rem;
  line-height: 1.6;
  color: #64748b;
}

.profile-comment-total {
  font-size: 0.92rem;
  font-weight: 600;
  color: #475569;
}

.profile-comment-loading {
  display: grid;
  gap: 0.75rem;
}

.profile-comment-skeleton {
  height: 7rem;
  border-radius: 1rem;
  background: rgba(226, 232, 240, 0.72);
}

.profile-comment-empty {
  border-radius: 1rem;
  border: 1px dashed rgba(148, 163, 184, 0.36);
  padding: 3.5rem 0;
}

.profile-comment-rows {
  border-top: 1px solid rgba(148, 163, 184, 0.2);
}

.profile-comment-row {
  border-bottom: 1px solid rgba(148, 163, 184, 0.2);
  padding: 1rem 0;
}

.profile-comment-top {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 0.75rem;
}

.profile-comment-heading {
  min-width: 0;
}

.profile-comment-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem;
}

.profile-comment-time {
  font-size: 0.9rem;
  color: #64748b;
}

.profile-comment-name {
  margin: 0.65rem 0 0;
  color: #0f172a;
  font-size: 1.05rem;
  font-weight: 700;
}

.profile-comment-votes {
  font-size: 0.9rem;
  color: #475569;
}

.profile-comment-body {
  margin: 0.9rem 0 0;
  font-size: 0.94rem;
  line-height: 1.8;
  color: #475569;
}

.profile-comment-foot {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  margin-top: 0.9rem;
}

.profile-comment-movie {
  font-size: 0.88rem;
  color: #94a3b8;
}
</style>
