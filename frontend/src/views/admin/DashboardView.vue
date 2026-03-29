<script setup lang="ts">
import { useRouter } from 'vue-router'
import { NAlert, NButton, NEmpty, NSpin } from 'naive-ui'
import { useAdminOverview } from '@/composables/admin/useAdminOverview'

const router = useRouter()
const {
  overview,
  overviewCards,
  trendPanels,
  loading,
  hasLoadError,
  lastUpdatedText,
  formatCount,
  isRefreshingOverview,
  isRefreshingAllRecommendations,
  refreshOverview,
  requestRefreshAllRecommendations
} = useAdminOverview()

function goToUsers() {
  void router.push({ name: 'admin-users' })
}

function goToComments() {
  void router.push({ name: 'admin-comments' })
}

function goToMovies() {
  void router.push({ name: 'admin-movies' })
}
</script>

<template>
  <section class="space-y-6">
    <div class="dashboard-hero-grid">
      <article class="dashboard-hero">
        <span class="dashboard-badge">OVERVIEW</span>
        <h2 class="dashboard-title">管理后台总览</h2>

        <div class="dashboard-meta">
          <span class="dashboard-chip">最近同步 {{ lastUpdatedText }}</span>
        </div>

        <div class="dashboard-actions">
          <n-button
            type="primary"
            class="rounded-full px-6"
            :loading="isRefreshingOverview"
            @click="refreshOverview"
          >
            刷新仪表盘
          </n-button>
        </div>
      </article>

      <article class="dashboard-summary">
        <div class="dashboard-summary-row">
          <span class="dashboard-summary-label">注册用户</span>
          <strong class="dashboard-summary-value">{{ formatCount(overview.totalUsers) }}</strong>
        </div>
        <div class="dashboard-summary-row">
          <span class="dashboard-summary-label">电影资产</span>
          <strong class="dashboard-summary-value">{{ formatCount(overview.totalMovies) }}</strong>
        </div>
        <div class="dashboard-summary-row">
          <span class="dashboard-summary-label">已发布评论</span>
          <strong class="dashboard-summary-value">{{ formatCount(overview.publishedCommentCount) }}</strong>
        </div>
        <div class="dashboard-summary-row">
          <span class="dashboard-summary-label">互动总量</span>
          <strong class="dashboard-summary-value">{{ formatCount(overview.totalCommentLikes) }}</strong>
        </div>

        <div class="dashboard-shortcuts">
          <button type="button" class="dashboard-shortcut" @click="goToUsers">
            进入用户管理
          </button>
          <button type="button" class="dashboard-shortcut" @click="goToComments">
            进入评论管理
          </button>
          <button type="button" class="dashboard-shortcut" @click="goToMovies">
            进入电影管理
          </button>
        </div>
      </article>
    </div>

    <n-alert
      v-if="hasLoadError"
      type="warning"
      title="仪表盘数据加载不完整"
      class="rounded-3xl"
    >
      当前展示的是已成功返回的数据，可以点击“刷新仪表盘”重新同步。
    </n-alert>

    <n-spin :show="loading">
      <div class="stat-grid">
        <article
          v-for="card in overviewCards"
          :key="card.key"
          :class="['stat-card', `stat-card--${card.tone}`]"
        >
          <span class="stat-label">{{ card.label }}</span>
          <strong class="stat-value">{{ formatCount(card.value) }}</strong>
          <p class="stat-description">{{ card.description }}</p>
        </article>
      </div>

      <div class="trend-grid">
        <article
          v-for="panel in trendPanels"
          :key="panel.key"
          :class="['trend-card', `trend-card--${panel.tone}`]"
        >
          <div class="trend-header">
            <div>
              <h3 class="trend-title">{{ panel.label }}</h3>
              <p class="trend-description">{{ panel.description }}</p>
            </div>
            <strong class="trend-total">{{ formatCount(panel.total) }}</strong>
          </div>

          <div v-if="panel.points.length > 0" class="trend-chart">
            <div
              v-for="point in panel.points"
              :key="`${panel.key}-${point.date}`"
              class="trend-slot"
            >
              <span class="trend-slot-label">{{ point.shortDate }}</span>
              <div class="trend-track">
                <div
                  class="trend-fill"
                  :style="{ height: `${Math.max(point.ratio * 100, point.value > 0 ? 18 : 8)}%` }"
                />
              </div>
              <span class="trend-slot-value">{{ point.value }}</span>
            </div>
          </div>

          <n-empty
            v-else
            description="暂无趋势数据"
            size="small"
            class="py-6"
          />
        </article>
      </div>
    </n-spin>
  </section>
</template>

<style scoped>
.dashboard-hero-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.45fr) minmax(300px, 0.9fr);
  gap: 1rem;
}

.dashboard-hero,
.dashboard-summary {
  border: 1px solid rgba(148, 163, 184, 0.2);
  border-radius: 1.75rem;
  padding: 1.5rem;
  box-shadow: 0 18px 50px rgba(15, 23, 42, 0.06);
}

.dashboard-hero {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  background:
    radial-gradient(circle at top right, rgba(245, 158, 11, 0.18), transparent 26%),
    linear-gradient(135deg, rgba(15, 23, 42, 0.98), rgba(30, 41, 59, 0.94));
  color: #f8fafc;
}

.dashboard-badge {
  display: inline-flex;
  align-self: flex-start;
  padding: 0.45rem 0.8rem;
  border-radius: 999px;
  background: rgba(245, 158, 11, 0.16);
  color: #fcd34d;
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.22em;
}

.dashboard-title {
  margin: 0;
  font-family: var(--font-display);
  font-size: clamp(1.8rem, 3vw, 2.5rem);
  line-height: 1.08;
  font-weight: 700;
}

.dashboard-description {
  margin: 0;
  max-width: 48rem;
  color: rgba(226, 232, 240, 0.88);
  font-size: 1rem;
  line-height: 1.8;
}

.dashboard-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 0.65rem;
}

.dashboard-chip {
  display: inline-flex;
  align-items: center;
  padding: 0.55rem 0.85rem;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.08);
  color: rgba(226, 232, 240, 0.9);
  font-size: 0.86rem;
}

.dashboard-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.8rem;
  margin-top: auto;
}

.dashboard-summary {
  display: flex;
  flex-direction: column;
  gap: 0.8rem;
  background: rgba(255, 255, 255, 0.94);
}

.dashboard-summary-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding-bottom: 0.8rem;
  border-bottom: 1px solid rgba(148, 163, 184, 0.16);
}

.dashboard-summary-label {
  color: #64748b;
  font-size: 0.92rem;
  font-weight: 600;
}

.dashboard-summary-value {
  font-family: var(--font-display);
  font-size: 1.5rem;
  font-weight: 700;
  color: #0f172a;
}

.dashboard-shortcuts {
  display: flex;
  flex-direction: column;
  gap: 0.65rem;
  margin-top: auto;
}

.dashboard-shortcut {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 2.8rem;
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 999px;
  background: rgba(248, 250, 252, 0.96);
  color: #0f172a;
  font-weight: 600;
  transition: transform 0.16s ease, border-color 0.16s ease;
}

.dashboard-shortcut:hover {
  transform: translateY(-1px);
  border-color: rgba(15, 23, 42, 0.24);
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 1rem;
}

.stat-card {
  display: flex;
  flex-direction: column;
  gap: 0.7rem;
  min-height: 10rem;
  padding: 1.15rem;
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 1.5rem;
}

.stat-label {
  color: #64748b;
  font-size: 0.8rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.stat-value {
  font-family: var(--font-display);
  font-size: 1.95rem;
  line-height: 1;
  font-weight: 700;
  color: #0f172a;
}

.stat-description {
  margin: 0;
  color: #475569;
  line-height: 1.7;
}

.stat-card--amber {
  background: linear-gradient(180deg, rgba(255, 251, 235, 0.96), rgba(255, 255, 255, 0.98));
}

.stat-card--emerald {
  background: linear-gradient(180deg, rgba(236, 253, 245, 0.96), rgba(255, 255, 255, 0.98));
}

.stat-card--sky {
  background: linear-gradient(180deg, rgba(240, 249, 255, 0.96), rgba(255, 255, 255, 0.98));
}

.stat-card--rose {
  background: linear-gradient(180deg, rgba(255, 241, 242, 0.96), rgba(255, 255, 255, 0.98));
}

.stat-card--slate {
  background: linear-gradient(180deg, rgba(248, 250, 252, 0.96), rgba(255, 255, 255, 0.98));
}

.stat-card--indigo {
  background: linear-gradient(180deg, rgba(238, 242, 255, 0.96), rgba(255, 255, 255, 0.98));
}

.trend-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1rem;
  margin-top: 1rem;
}

.trend-card {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  padding: 1.15rem;
  border: 1px solid rgba(148, 163, 184, 0.16);
  border-radius: 1.5rem;
  background: rgba(255, 255, 255, 0.94);
}

.trend-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
}

.trend-title {
  margin: 0;
  font-size: 1.02rem;
  font-weight: 700;
  color: #0f172a;
}

.trend-description {
  margin: 0.35rem 0 0;
  color: #64748b;
  line-height: 1.6;
}

.trend-total {
  font-family: var(--font-display);
  font-size: 1.65rem;
  line-height: 1;
  font-weight: 700;
}

.trend-chart {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 0.7rem;
  align-items: end;
}

.trend-slot {
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
  align-items: center;
}

.trend-slot-label,
.trend-slot-value {
  color: #64748b;
  font-size: 0.76rem;
}

.trend-track {
  display: flex;
  align-items: flex-end;
  justify-content: center;
  width: 100%;
  height: 7rem;
  padding: 0.35rem;
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.12);
}

.trend-fill {
  width: 100%;
  min-height: 0.5rem;
  border-radius: 999px;
  background: linear-gradient(180deg, #0f172a 0%, #334155 100%);
}

.trend-card--amber .trend-fill {
  background: linear-gradient(180deg, #f59e0b 0%, #d97706 100%);
}

.trend-card--emerald .trend-fill {
  background: linear-gradient(180deg, #10b981 0%, #059669 100%);
}

.trend-card--sky .trend-fill {
  background: linear-gradient(180deg, #0ea5e9 0%, #0284c7 100%);
}

.trend-card--rose .trend-fill {
  background: linear-gradient(180deg, #f43f5e 0%, #e11d48 100%);
}

.trend-card--slate .trend-fill {
  background: linear-gradient(180deg, #64748b 0%, #334155 100%);
}

.trend-card--indigo .trend-fill {
  background: linear-gradient(180deg, #6366f1 0%, #4f46e5 100%);
}

@media (max-width: 1100px) {
  .dashboard-hero-grid,
  .stat-grid,
  .trend-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .dashboard-actions {
    flex-direction: column;
  }

  .trend-chart {
    gap: 0.45rem;
  }
}
</style>
