<script setup lang="ts">
import { computed } from 'vue'
import { NTabs, NTabPane } from 'naive-ui'
import type { Comment, FavoriteFolderVO, MovieItemVO, MyRatingVO } from '@/api/model'
import ProfileCommentList from './ProfileCommentList.vue'
import ProfileFolderList from './ProfileFolderList.vue'
import ProfileMovieGrid from './ProfileMovieGrid.vue'
import ProfileRatingList from './ProfileRatingList.vue'

const props = withDefaults(defineProps<{
  favoriteFolders: FavoriteFolderVO[]
  watchedMovies: MovieItemVO[]
  ratings: MyRatingVO[]
  comments: Comment[]
  totals: {
    favorites: number
    watched: number
    ratings: number
    comments: number
  }
  loading?: boolean
}>(), {
  loading: false
})

const emit = defineEmits<{
  refresh: []
}>()

const favoriteFolderCount = computed(() => props.favoriteFolders.length)
</script>

<template>
  <section class="profile-activity-section">
    <div class="profile-activity-header">
      <h2 class="profile-activity-title">我的记录</h2>
      <p class="profile-activity-description">
        查看你的收藏夹、看过、评分和评论。
      </p>
    </div>

    <n-tabs type="line" animated placement="top" class="profile-tabs">
      <n-tab-pane name="favorites" :tab="`收藏夹管理 (${favoriteFolderCount})`">
        <ProfileFolderList
          :folders="favoriteFolders"
          :total="favoriteFolderCount"
          :loading="loading"
          @refresh="emit('refresh')"
        />
      </n-tab-pane>

      <n-tab-pane name="watched" :tab="`看过 (${totals.watched})`">
        <ProfileMovieGrid
          :items="watchedMovies"
          :total="totals.watched"
          :loading="loading"
          title="最近看过"
          description="继续补齐你的观影履历。"
          empty-description="看过列表还是空的。"
          record-label="记录于"
        />
      </n-tab-pane>

      <n-tab-pane name="ratings" :tab="`评分 (${totals.ratings})`">
        <ProfileRatingList
          :items="ratings"
          :total="totals.ratings"
          :loading="loading"
          @refresh="emit('refresh')"
        />
      </n-tab-pane>

      <n-tab-pane name="comments" :tab="`评论 (${totals.comments})`">
        <ProfileCommentList :items="comments" :total="totals.comments" :loading="loading" />
      </n-tab-pane>
    </n-tabs>
  </section>
</template>

<style scoped>
.profile-activity-section {
  padding-bottom: 1rem;
}

.profile-activity-header {
  margin-bottom: 1.5rem;
}

.profile-activity-title {
  margin: 0;
  font-family: var(--font-display);
  font-size: 1.8rem;
  font-weight: 700;
  color: #0f172a;
}

.profile-activity-description {
  margin: 0.6rem 0 0;
  max-width: 42rem;
  font-size: 0.96rem;
  line-height: 1.7;
  color: #64748b;
}

.profile-tabs :deep(.n-tabs-nav) {
  margin-bottom: 1.25rem;
}

.profile-tabs :deep(.n-tabs-tab) {
  min-height: auto;
  padding: 0.75rem 0.15rem;
  font-weight: 600;
}

.profile-tabs :deep(.n-tabs-rail) {
  background-color: rgba(148, 163, 184, 0.2);
}
</style>
