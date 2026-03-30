<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { NAlert, NButton } from 'naive-ui'
import NavBar from '@/components/layout/NavBar.vue'
import ProfileActivityTabs from '@/components/profile/ProfileActivityTabs.vue'
import ProfileEditForm from '@/components/profile/ProfileEditForm.vue'
import { useProfileDashboard } from '@/composables/useProfileDashboard'
import type { UpdateUserProfileDTO } from '@/api/model'
import { formatDateTimeLabel, getNameInitial, resolveAssetUrl } from '@/utils/profile'

const dashboard = useProfileDashboard()
const isEditingProfile = ref(false)

const displayName = computed(() => dashboard.profile.value?.nickname?.trim() || '未设置昵称')

const displayEmail = computed(() => dashboard.profile.value?.email?.trim() || '未设置邮箱')

const profileAvatarUrl = computed(() => resolveAssetUrl(dashboard.profile.value?.avatar))

const profileInitial = computed(() => getNameInitial(dashboard.profile.value?.nickname || dashboard.profile.value?.email))

const activitySummaryText = computed(() => {
  const totals = dashboard.totals.value
  return `收藏 ${totals.favorites} · 看过 ${totals.watched} · 评分 ${totals.ratings} · 评论 ${totals.comments}`
})

const lastUpdatedText = computed(() => {
  return dashboard.lastUpdatedAt.value
    ? `最近同步 ${formatDateTimeLabel(dashboard.lastUpdatedAt.value)}`
    : '尚未同步资料'
})

async function handleSubmit(payload: UpdateUserProfileDTO) {
  await dashboard.saveProfile(payload)
  isEditingProfile.value = false
}

function openProfileEditor() {
  isEditingProfile.value = true
}

function closeProfileEditor() {
  isEditingProfile.value = false
}

function refreshActivity() {
  void dashboard.loadDashboard({ silent: true })
}

onMounted(() => {
  void dashboard.loadDashboard()
})
</script>

<template>
  <div class="profile-view min-h-screen text-slate-900">
    <NavBar />

    <main class="mx-auto flex w-full max-w-6xl flex-col gap-8 px-4 py-8 pb-16 sm:px-6 lg:px-8">
      <section class="profile-overview">
        <n-alert
          v-if="dashboard.loadError.value"
          type="warning"
          title="资料未完全同步"
          class="profile-alert"
        >
          {{ dashboard.loadError.value }}
        </n-alert>

        <div class="profile-overview-header">
          <div>
            <h2 class="profile-overview-title">个人资料</h2>
            <p class="profile-overview-description">
              查看当前昵称、联系方式和最近同步状态。
            </p>
          </div>

          <div class="profile-actions">
            <NButton
              type="primary"
              secondary
              class="rounded-full px-6"
              @click="isEditingProfile ? closeProfileEditor() : openProfileEditor()"
            >
              {{ isEditingProfile ? '取消编辑' : '编辑资料' }}
            </NButton>
          </div>
        </div>

        <div class="profile-header">
          <div class="profile-identity">
            <div class="profile-avatar">
              <img
                v-if="profileAvatarUrl"
                :src="profileAvatarUrl || undefined"
                :alt="displayName"
                class="profile-avatar-image"
              />
              <span v-else class="profile-avatar-fallback">{{ profileInitial }}</span>
            </div>

            <div class="profile-copy">
              <h3 class="profile-name">{{ displayName }}</h3>

              <dl class="profile-meta-list">
                <div class="profile-meta-item">
                  <dt class="profile-meta-label">邮箱</dt>
                  <dd class="profile-meta-value">{{ displayEmail }}</dd>
                </div>

                <div class="profile-meta-item">
                  <dt class="profile-meta-label">资料同步</dt>
                  <dd class="profile-meta-value">{{ lastUpdatedText }}</dd>
                </div>

                <div class="profile-meta-item">
                  <dt class="profile-meta-label">观影统计</dt>
                  <dd class="profile-meta-value">{{ activitySummaryText }}</dd>
                </div>
              </dl>
            </div>
          </div>
        </div>
      </section>

      <div v-if="isEditingProfile">
        <ProfileEditForm
          :profile="dashboard.profile.value"
          :saving="dashboard.savingProfile.value"
          @submit="handleSubmit"
          @cancel="closeProfileEditor"
        />
      </div>

      <ProfileActivityTabs
        :favorite-folders="dashboard.favoriteFolders.value"
        :watched-movies="dashboard.watchedMovies.value"
        :ratings="dashboard.ratings.value"
        :comments="dashboard.comments.value"
        :totals="dashboard.totals.value"
        :loading="dashboard.loading.value"
        @refresh="refreshActivity"
      />
    </main>
  </div>
</template>

<style scoped>
.profile-view {
  background:
    linear-gradient(180deg, rgba(248, 245, 240, 0.96) 0%, rgba(252, 250, 247, 0.98) 20%, #ffffff 100%);
}

.profile-overview {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
  padding-bottom: 1rem;
}

.profile-alert {
  border-radius: 1rem;
}

.profile-overview-header {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}

.profile-overview-title {
  margin: 0;
  font-family: var(--font-display);
  font-size: 1.8rem;
  font-weight: 700;
  color: #0f172a;
}

.profile-overview-description {
  margin: 0.6rem 0 0;
  max-width: 42rem;
  font-size: 0.96rem;
  line-height: 1.7;
  color: #64748b;
}

.profile-header {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  gap: 1.25rem;
  border-top: 1px solid rgba(148, 163, 184, 0.24);
  padding-top: 1.25rem;
}

.profile-identity {
  display: flex;
  min-width: 0;
  flex: 1;
  align-items: flex-start;
  gap: 1rem;
}

.profile-avatar {
  display: flex;
  height: 5rem;
  width: 5rem;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border: 1px solid rgba(148, 163, 184, 0.22);
  border-radius: 1.25rem;
  background: linear-gradient(135deg, #1f2937 0%, #475569 100%);
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.08);
  color: #ffffff;
}

.profile-avatar-image {
  height: 100%;
  width: 100%;
  object-fit: cover;
}

.profile-avatar-fallback {
  font-family: var(--font-display);
  font-size: 1.5rem;
  font-weight: 700;
}

.profile-copy {
  min-width: 0;
  flex: 1;
}

.profile-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
}

.profile-name {
  margin: 0;
  font-family: var(--font-display);
  font-size: clamp(1.6rem, 2.4vw, 2.1rem);
  font-weight: 700;
  line-height: 1.1;
  color: #0f172a;
}

.profile-meta-list {
  display: grid;
  gap: 0.85rem;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin: 1rem 0 0;
}

.profile-meta-item {
  min-width: 0;
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 1rem;
  background: rgba(255, 255, 255, 0.7);
  padding: 0.9rem 1rem;
}

.profile-meta-label {
  margin: 0;
  font-size: 0.74rem;
  font-weight: 700;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: #94a3b8;
}

.profile-meta-value {
  margin: 0.45rem 0 0;
  font-size: 0.94rem;
  line-height: 1.65;
  color: #475569;
}

@media (max-width: 639px) {
  .profile-actions {
    width: 100%;
    justify-content: flex-start;
  }

  .profile-meta-list {
    grid-template-columns: minmax(0, 1fr);
  }

  .profile-avatar {
    height: 4rem;
    width: 4rem;
    border-radius: 1.25rem;
  }
}
</style>
