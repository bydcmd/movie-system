<script setup lang="ts">
import { computed } from 'vue'
import { NAvatar, NButton, NRate, NTag } from 'naive-ui'
import type { CommentVO, Movie } from '@/api/model'
import TiptapEditor from '@/components/comment/TiptapEditor.vue'
import {
  countReadableCharacters,
  createEmptyTiptapDocument,
  estimateReadingMinutes,
  extractTiptapText,
  getCommentPreviewText,
  hasRichTextContent
} from '@/utils/comment'
import { formatDateTimeLabel, getNameInitial, resolveAssetUrl } from '@/utils/profile'

const props = withDefaults(
  defineProps<{
    review: CommentVO
    movie?: Movie | null
    likeLoading?: boolean
  }>(),
  {
    movie: null,
    likeLoading: false
  }
)

const emit = defineEmits<{
  toggleLike: []
}>()

const publishedAt = computed(() => formatDateTimeLabel(props.review.commentTime))
const fullText = computed(() => extractTiptapText(props.review.content))
const previewText = computed(() => getCommentPreviewText(props.review, 260))
const readableCharacters = computed(() => countReadableCharacters(fullText.value))
const readingMinutes = computed(() => estimateReadingMinutes(fullText.value))
const canRenderRichText = computed(() => hasRichTextContent(props.review.content))
const hasLead = computed(() => previewText.value.trim().length > 0 && previewText.value !== fullText.value)
const posterUrl = computed(() => resolveAssetUrl(props.movie?.cover))
const backRoute = computed(() => ({
  name: 'movie-detail',
  params: {
    id: props.review.movieId
  },
  query: {
    tab: 'comments',
    filter: 'long'
  }
}))
const movieMetaItems = computed(() => {
  const items: string[] = []

  if (props.movie?.year) {
    items.push(`${props.movie.year} 年`)
  }

  if (props.movie?.genres?.trim()) {
    items.push(props.movie.genres.trim())
  }

  if (props.movie?.mins?.trim()) {
    items.push(props.movie.mins.trim())
  }

  return items
})
const readonlyContent = computed({
  get: () => props.review.content || createEmptyTiptapDocument(),
  set: () => undefined
})
</script>

<template>
  <article class="long-review-article">
    <RouterLink :to="backRoute" class="long-review-back-link">
      返回电影评论
    </RouterLink>

    <header class="long-review-header">
      <div class="long-review-label-row">
        <n-tag size="small" type="warning">长评</n-tag>
        <span class="long-review-date">{{ publishedAt }}</span>
      </div>

      <h1 class="long-review-title">
        {{ props.review.title || '未命名长评' }}
      </h1>

      <div class="long-review-byline">
        <div class="long-review-author">
          <n-avatar :src="props.review.userAvatar || undefined" :fallback-src="undefined" round>
            {{ getNameInitial(props.review.userNickname) }}
          </n-avatar>
          <div class="long-review-author-meta">
            <span class="long-review-author-name">{{ props.review.userNickname || '匿名用户' }}</span>
            <div class="long-review-reading-meta">
              <span v-if="readableCharacters > 0">{{ readableCharacters }} 字</span>
              <span v-if="readingMinutes > 0">约 {{ readingMinutes }} 分钟读完</span>
            </div>
          </div>
        </div>

        <div class="long-review-actions">
          <n-rate v-if="props.review.rating" :value="props.review.rating" readonly size="small" />
          <n-button
            quaternary
            :type="props.review.isLiked ? 'primary' : 'default'"
            :loading="props.likeLoading"
            @click="emit('toggleLike')"
          >
            {{ props.review.isLiked ? '已赞' : '点赞' }} · {{ props.review.votes || 0 }}
          </n-button>
        </div>
      </div>
    </header>

    <div class="long-review-layout">
      <div class="long-review-main">
        <div v-if="hasLead" class="long-review-lead">
          {{ previewText }}
        </div>

        <TiptapEditor
          v-if="canRenderRichText"
          class="long-review-rich-content"
          v-model="readonlyContent"
          :editable="false"
          :min-height="0"
        />

        <p v-else class="long-review-plain-content">
          {{ fullText || '暂无内容' }}
        </p>
      </div>

      <aside v-if="props.movie" class="long-review-aside">
        <div v-if="posterUrl" class="long-review-poster-wrap">
          <img :src="posterUrl" :alt="props.movie.name || '电影海报'" class="long-review-poster" />
        </div>

        <div class="long-review-movie-block">
          <p class="long-review-movie-kicker">关联电影</p>
          <RouterLink :to="backRoute" class="long-review-movie-title">
            {{ props.movie.name || `电影 #${props.review.movieId}` }}
          </RouterLink>
          <p v-if="props.movie.alias" class="long-review-movie-alias">{{ props.movie.alias }}</p>
          <div v-if="movieMetaItems.length > 0" class="long-review-movie-meta">
            <span v-for="item in movieMetaItems" :key="item">{{ item }}</span>
          </div>
          <p v-if="props.movie.storyline" class="long-review-movie-storyline">
            {{ props.movie.storyline }}
          </p>
        </div>
      </aside>
    </div>
  </article>
</template>

<style scoped>
.long-review-article {
  max-width: 74rem;
  margin: 0 auto;
  padding: clamp(1.5rem, 2vw, 2rem) 1.5rem 4rem;
}

.long-review-back-link {
  display: inline-flex;
  align-items: center;
  margin-bottom: 1.5rem;
  color: rgb(120 53 15);
  font-size: 0.92rem;
  font-weight: 600;
  text-decoration: none;
}

.long-review-back-link:hover {
  color: rgb(146 64 14);
}

.long-review-header {
  max-width: 52rem;
  margin-bottom: 2rem;
  padding-bottom: 1.6rem;
  border-bottom: 1px solid rgb(226 232 240);
}

.long-review-label-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.75rem;
}

.long-review-date {
  color: rgb(100 116 139);
  font-size: 0.9rem;
}

.long-review-title {
  margin: 1rem 0 1.2rem;
  color: rgb(15 23 42);
  font-family: 'Iowan Old Style', 'Palatino Linotype', 'Noto Serif SC', serif;
  font-size: clamp(2rem, 2.8vw, 3.35rem);
  font-weight: 600;
  line-height: 1.28;
  letter-spacing: 0.01em;
}

.long-review-byline {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}

.long-review-author {
  display: flex;
  align-items: center;
  gap: 0.85rem;
  min-width: 0;
}

.long-review-author-meta {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 0.25rem;
}

.long-review-author-name {
  color: rgb(15 23 42);
  font-size: 0.98rem;
  font-weight: 700;
}

.long-review-reading-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
  color: rgb(100 116 139);
  font-size: 0.88rem;
}

.long-review-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.75rem;
}

.long-review-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(16rem, 18rem);
  gap: clamp(2rem, 4vw, 4rem);
  align-items: start;
}

.long-review-main {
  max-width: 46rem;
}

.long-review-lead {
  margin-bottom: 1.8rem;
  padding-left: 1rem;
  border-left: 3px solid rgb(251 191 36);
  color: rgb(71 85 105);
  font-size: 1.02rem;
  line-height: 1.95;
  white-space: pre-line;
}

.long-review-plain-content {
  margin: 0;
  color: rgb(30 41 59);
  font-family: 'Iowan Old Style', 'Palatino Linotype', 'Noto Serif SC', serif;
  font-size: 1.05rem;
  line-height: 1.95;
  white-space: pre-line;
}

.long-review-aside {
  position: sticky;
  top: 6.5rem;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.long-review-poster-wrap {
  overflow: hidden;
  border-radius: 1rem;
  background: rgb(226 232 240);
  box-shadow: 0 16px 30px rgba(15, 23, 42, 0.08);
}

.long-review-poster {
  display: block;
  width: 100%;
  aspect-ratio: 2 / 3;
  object-fit: cover;
}

.long-review-movie-block {
  padding-top: 0.4rem;
}

.long-review-movie-kicker {
  margin: 0 0 0.5rem;
  color: rgb(148 163 184);
  font-size: 0.76rem;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.long-review-movie-title {
  color: rgb(15 23 42);
  font-family: var(--font-display);
  font-size: 1.15rem;
  font-weight: 700;
  text-decoration: none;
}

.long-review-movie-title:hover {
  color: rgb(180 83 9);
}

.long-review-movie-alias {
  margin: 0.45rem 0 0;
  color: rgb(100 116 139);
  font-size: 0.92rem;
  line-height: 1.6;
}

.long-review-movie-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem 0.75rem;
  margin-top: 0.85rem;
  color: rgb(71 85 105);
  font-size: 0.88rem;
}

.long-review-movie-storyline {
  margin: 1rem 0 0;
  color: rgb(100 116 139);
  font-size: 0.92rem;
  line-height: 1.8;
}

.long-review-main :deep(.long-review-rich-content .ProseMirror) {
  color: rgb(30 41 59);
  font-family: 'Iowan Old Style', 'Palatino Linotype', 'Noto Serif SC', serif;
  font-size: 1.05rem;
  letter-spacing: 0.01em;
  line-height: 1.95;
}

.long-review-main :deep(.long-review-rich-content .ProseMirror h2),
.long-review-main :deep(.long-review-rich-content .ProseMirror h3) {
  font-family: 'Iowan Old Style', 'Palatino Linotype', 'Noto Serif SC', serif;
  color: rgb(15 23 42);
  line-height: 1.5;
}

@media (max-width: 980px) {
  .long-review-layout {
    grid-template-columns: 1fr;
  }

  .long-review-main {
    max-width: none;
  }

  .long-review-aside {
    position: static;
    max-width: 22rem;
  }
}

@media (max-width: 700px) {
  .long-review-article {
    padding: 1.25rem 1rem 3rem;
  }

  .long-review-title {
    font-size: clamp(1.7rem, 8vw, 2.4rem);
  }

  .long-review-byline {
    align-items: flex-start;
  }

  .long-review-actions {
    width: 100%;
    justify-content: flex-start;
  }
}
</style>
