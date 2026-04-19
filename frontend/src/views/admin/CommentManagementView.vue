<script setup lang="ts">
import { useRouter } from 'vue-router'
import {
  NAlert,
  NButton,
  NEmpty,
  NInput,
  NPagination,
  NSpin,
  NTag
} from 'naive-ui'
import { useAdminComments } from '@/composables/admin/useAdminComments'
import { formatDateTimeLabel } from '@/utils/profile'
import type { MovieId } from '@/utils/movie'

const router = useRouter()
const {
  state,
  comments,
  total,
  loading,
  hasLoadError,
  lastUpdatedText,
  formatCount,
  getCommentTypeLabel,
  getCommentStatusLabel,
  getCommentStatusType,
  summarizeComment,
  applyKeyword,
  resetFilters,
  refreshComments,
  requestDeleteComment,
  isDeletingComment
} = useAdminComments()

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
        <h2 class="font-display text-2xl font-bold text-slate-900">评论管理</h2>
        <p class="mt-2 max-w-3xl text-sm leading-7 text-slate-600">
          统一查看全部评论内容，用于定位违规评论、低质内容和异常数据。
        </p>
      </div>

      <div class="flex flex-wrap items-center gap-3">
        <span class="rounded-full bg-slate-100 px-4 py-2 text-sm text-slate-600">
          最近同步 {{ lastUpdatedText }}
        </span>
        <n-button secondary @click="refreshComments">
          刷新列表
        </n-button>
      </div>
    </div>

    <n-alert
      v-if="hasLoadError"
      type="warning"
      title="评论数据加载失败"
      class="rounded-3xl"
    >
      可以重试刷新列表，或调整搜索条件后重新查询。
    </n-alert>

    <section class="rounded-[28px] border border-slate-200 bg-white/90 p-5 shadow-sm">
      <div class="flex flex-wrap items-center gap-3">
        <n-input
          v-model:value="state.keywordInput"
          clearable
          placeholder="按评论正文搜索"
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
        <div v-if="comments.length > 0" class="space-y-4">
          <article
            v-for="entry in comments"
            :key="entry.id || `${entry.movieId}-${entry.userId}`"
            class="flex flex-wrap items-start justify-between gap-4 rounded-[24px] border border-slate-200 bg-slate-50/70 p-4"
          >
            <div class="min-w-0 flex-1 space-y-3">
              <div class="flex flex-wrap items-center gap-2">
                <h3 class="text-base font-semibold text-slate-900">
                  {{ entry.title || `${getCommentTypeLabel(entry.type)} #${entry.id || '--'}` }}
                </h3>

                <n-tag size="small" :type="getCommentStatusType(entry.status)">
                  {{ getCommentStatusLabel(entry.status) }}
                </n-tag>

                <n-tag size="small" type="info">
                  {{ getCommentTypeLabel(entry.type) }}
                </n-tag>
              </div>

              <p class="text-sm leading-7 text-slate-700">
                {{ summarizeComment(entry) }}
              </p>

              <p class="text-sm leading-7 text-slate-600">
                用户 {{ entry.userId || '未知' }} · 电影 {{ entry.movieId || '--' }} · 点赞 {{ formatCount(entry.votes) }}
                · 发布于 {{ formatDateTimeLabel(entry.commentTime) }}
              </p>
            </div>

            <div class="flex flex-wrap justify-end gap-3">
              <n-button
                secondary
                size="small"
                :disabled="!entry.movieId"
                @click="openMovieDetail(entry.movieId)"
              >
                查看电影
              </n-button>

              <n-button
                secondary
                strong
                type="error"
                size="small"
                :loading="isDeletingComment(entry.id)"
                @click="requestDeleteComment(entry)"
              >
                删除评论
              </n-button>
            </div>
          </article>
        </div>

        <n-empty
          v-else
          description="没有匹配的评论"
          class="py-12"
        />
      </n-spin>

      <div class="mt-5 flex flex-wrap items-center justify-between gap-4 border-t border-slate-200 pt-5">
        <span class="text-sm text-slate-500">共 {{ formatCount(total) }} 条评论</span>

        <n-pagination
          v-model:page="state.page"
          :page-size="state.size"
          :item-count="total"
          simple
        />
      </div>
    </section>
  </section>
</template>
