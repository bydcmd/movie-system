<script setup lang="ts">
import {
  NAlert,
  NAvatar,
  NButton,
  NEmpty,
  NInput,
  NPagination,
  NSelect,
  NSpin,
  NTag
} from 'naive-ui'
import { useAdminUsers } from '@/composables/admin/useAdminUsers'
import { formatDateTimeLabel, resolveAssetUrl } from '@/utils/profile'

const {
  state,
  users,
  total,
  loading,
  hasLoadError,
  lastUpdatedText,
  userStatusFilterOptions,
  formatCount,
  getRoleLabel,
  getUserStatusLabel,
  getUserStatusType,
  getUserInitial,
  getUserStatusActionLabel,
  getUserStatusActionType,
  canToggleUserStatus,
  canDeleteUser,
  applyFilters,
  applyStatusFilter,
  resetFilters,
  refreshUsers,
  requestToggleUserStatus,
  requestDeleteUser,
  refreshUserRecommendations,
  isDeletingUser,
  isRefreshingUserRecommendations,
  isFreezingUser,
  isUnfreezingUser
} = useAdminUsers()
</script>

<template>
  <section class="space-y-5">
    <div class="flex flex-wrap items-start justify-between gap-4">
      <div>
        <h2 class="font-display text-2xl font-bold text-slate-900">用户管理</h2>
      </div>

      <div class="flex flex-wrap items-center gap-3">
        <span class="rounded-full bg-slate-100 px-4 py-2 text-sm text-slate-600">
          最近同步 {{ lastUpdatedText }}
        </span>
        <n-button secondary @click="refreshUsers">
          刷新列表
        </n-button>
      </div>
    </div>

    <n-alert
      v-if="hasLoadError"
      type="warning"
      title="用户数据加载失败"
      class="rounded-3xl"
    >
      可以重试刷新列表，或调整搜索条件后重新查询。
    </n-alert>

    <section class="rounded-[28px] border border-slate-200 bg-white/90 p-5 shadow-sm">
      <div class="flex flex-wrap items-center gap-3">
        <n-input
          v-model:value="state.keywordInput"
          clearable
          placeholder="搜索用户 ID 或昵称"
          class="min-w-[220px] flex-1"
          @keyup.enter="applyFilters"
        />

        <n-select
          v-model:value="state.status"
          :options="userStatusFilterOptions"
          class="w-full sm:w-[180px]"
          @update:value="applyStatusFilter"
        />

        <div class="flex gap-3">
          <n-button type="primary" @click="applyFilters">
            搜索
          </n-button>
          <n-button quaternary @click="resetFilters">
            清空
          </n-button>
        </div>
      </div>

      <n-spin :show="loading" class="mt-5 block">
        <div v-if="users.length > 0" class="space-y-4">
          <article
            v-for="(entry, index) in users"
            :key="entry.id || entry.email || entry.nickname || `user-${index}`"
            class="flex flex-wrap items-start justify-between gap-4 rounded-[24px] border border-slate-200 bg-slate-50/70 p-4"
          >
            <div class="flex min-w-0 flex-1 items-start gap-4">
              <n-avatar
                round
                :size="52"
                :src="resolveAssetUrl(entry.avatar) || undefined"
              >
                {{ getUserInitial(entry) }}
              </n-avatar>

              <div class="min-w-0 flex-1">
                <div class="flex flex-wrap items-center gap-2">
                  <h3 class="text-base font-semibold text-slate-900">
                    {{ entry.nickname || entry.id || '未命名用户' }}
                  </h3>

                  <n-tag size="small" :type="getUserStatusType(entry.status)">
                    {{ getUserStatusLabel(entry.status) }}
                  </n-tag>

                  <n-tag size="small" :type="entry.role === 0 ? 'warning' : 'default'">
                    {{ getRoleLabel(entry.role) }}
                  </n-tag>
                </div>

                <p class="mt-2 text-sm leading-7 text-slate-600">
                  ID：{{ entry.id || '未知' }} · 邮箱：{{ entry.email || '未绑定' }}
                </p>
                <p class="text-sm leading-7 text-slate-600">
                  创建于 {{ formatDateTimeLabel(entry.createTime) }} · 更新于 {{ formatDateTimeLabel(entry.updateTime) }}
                </p>
              </div>
            </div>

            <div class="flex flex-wrap justify-end gap-3">
              <n-button
                secondary
                size="small"
                :type="getUserStatusActionType(entry)"
                :disabled="!canToggleUserStatus(entry)"
                :loading="isFreezingUser(entry.id) || isUnfreezingUser(entry.id)"
                @click="requestToggleUserStatus(entry)"
              >
                {{ getUserStatusActionLabel(entry) }}
              </n-button>

              <n-button
                secondary
                strong
                type="error"
                size="small"
                :disabled="!canDeleteUser(entry)"
                :loading="isDeletingUser(entry.id)"
                @click="requestDeleteUser(entry)"
              >
                注销用户
              </n-button>
            </div>
          </article>
        </div>

        <n-empty
          v-else
          description="没有匹配的用户"
          class="py-12"
        />
      </n-spin>

      <div class="mt-5 flex flex-wrap items-center justify-between gap-4 border-t border-slate-200 pt-5">
        <span class="text-sm text-slate-500">共 {{ formatCount(total) }} 个用户</span>

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
