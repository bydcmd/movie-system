<script setup lang="ts">
import { computed, reactive, ref, useTemplateRef, watch } from 'vue'
import { NButton, NForm, NFormItem, NInput } from 'naive-ui'
import { useMessage } from 'naive-ui'
import { useUploadImage } from '@/api/endpoints/file-management/file-management'
import type { UpdateUserProfileDTO, UserProfileVO } from '@/api/model'
import { getNameInitial, resolveAssetUrl } from '@/utils/profile'

type ProfileFormState = {
  nickname: string
  avatar: string
  email: string
}

const props = defineProps<{
  profile: UserProfileVO | null
  saving?: boolean
}>()

const emit = defineEmits<{
  submit: [payload: UpdateUserProfileDTO]
  cancel: []
}>()

const form = reactive<ProfileFormState>({
  nickname: '',
  avatar: '',
  email: ''
})

const message = useMessage()
const uploadImageMutation = useUploadImage()
const avatarInputRef = useTemplateRef<HTMLInputElement>('avatarInput')
const avatarUploading = ref(false)
const hasHydratedForm = ref(false)
const syncedProfileId = ref<string | null>(null)

function toFormState(profile: UserProfileVO | null): ProfileFormState {
  return {
    nickname: profile?.nickname ?? '',
    avatar: profile?.avatar ?? '',
    email: profile?.email ?? ''
  }
}

function applyFormState(nextState: ProfileFormState) {
  form.nickname = nextState.nickname
  form.avatar = nextState.avatar
  form.email = nextState.email
}

function isSameFormState(left: ProfileFormState, right: ProfileFormState) {
  return (
    left.nickname === right.nickname &&
    left.avatar === right.avatar &&
    left.email === right.email
  )
}

const normalizedOriginal = computed<ProfileFormState>(() => toFormState(props.profile))

const hasChanges = computed(() => {
  return !isSameFormState(form, normalizedOriginal.value)
})

const avatarPreview = computed(() => resolveAssetUrl(form.avatar))

const avatarFallback = computed(() => getNameInitial(form.nickname || form.email))
const avatarActionText = computed(() => (avatarUploading.value ? '上传中...' : '点击上传头像'))

watch(
  normalizedOriginal,
  (nextState, previousState) => {
    const nextProfileId = props.profile?.id ?? null
    const switchedProfile = nextProfileId !== syncedProfileId.value
    const wasPristine = !previousState || isSameFormState(form, previousState)

    // Only sync parent data back when the user is not actively editing this profile.
    if (!hasHydratedForm.value || switchedProfile || wasPristine) {
      applyFormState(nextState)
      syncedProfileId.value = nextProfileId
      hasHydratedForm.value = true
    }
  },
  { immediate: true }
)

function handleSubmit() {
  emit('submit', {
    nickname: form.nickname.trim(),
    avatar: form.avatar.trim(),
    email: form.email.trim()
  })
}

function resetForm() {
  applyFormState(normalizedOriginal.value)
}

function handleCancel() {
  resetForm()
  emit('cancel')
}

function triggerAvatarPicker() {
  avatarInputRef.value?.click()
}

function normalizeUploadResult(value: unknown): string | null {
  if (typeof value === 'string') {
    return value
  }

  if (!value || typeof value !== 'object') {
    return null
  }

  const record = value as Record<string, unknown>

  if (typeof record.url === 'string') {
    return record.url
  }

  if (typeof record.path === 'string') {
    return record.path
  }

  if (typeof record.data === 'string') {
    return record.data
  }

  return null
}

async function handleAvatarChange(event: Event) {
  const target = event.target as HTMLInputElement | null
  const file = target?.files?.[0]

  if (!file) {
    return
  }

  if (file.size > 5 * 1024 * 1024) {
    message.error('头像大小不能超过 5MB')
    if (target) {
      target.value = ''
    }
    return
  }

  avatarUploading.value = true

  try {
    const result = await uploadImageMutation.mutateAsync({
      data: {
        file
      }
    })
    const avatarUrl = normalizeUploadResult(result)

    if (!avatarUrl) {
      throw new Error('Avatar upload did not return a valid URL')
    }

    form.avatar = avatarUrl
    message.success('头像上传成功')
  } catch (error) {
    console.error('Failed to upload avatar:', error)
    message.error('头像上传失败，请稍后再试')
  } finally {
    avatarUploading.value = false
    if (target) {
      target.value = ''
    }
  }
}
</script>

<template>
  <section class="profile-edit-section">
    <div class="profile-edit-header">
      <div>
        <h2 class="profile-edit-title">编辑资料</h2>
        <p class="profile-edit-description">
          修改昵称、邮箱和头像，保存后会同步到个人页。
        </p>
      </div>
    </div>

    <div class="profile-edit-layout">
      <div class="profile-avatar-panel">
        <button
          type="button"
          class="profile-avatar-frame"
          :disabled="avatarUploading"
          :aria-label="avatarActionText"
          :aria-busy="avatarUploading"
          @click="triggerAvatarPicker"
        >
          <img
            v-if="avatarPreview"
            :src="avatarPreview || undefined"
            :alt="form.nickname || '用户头像'"
            class="profile-avatar-image"
          />
          <span v-else class="profile-avatar-fallback">{{ avatarFallback }}</span>

          <div class="profile-avatar-overlay">
            <span class="profile-avatar-overlay-text">{{ avatarActionText }}</span>
          </div>
        </button>

        <input
          ref="avatarInput"
          type="file"
          accept=".jpg,.jpeg,.png,.gif,.webp"
          class="profile-avatar-input"
          @change="handleAvatarChange"
        />

        <p class="profile-avatar-hint">
          支持 JPG、PNG、GIF、WEBP，单张不超过 5MB。
        </p>
      </div>

      <n-form label-placement="top" class="profile-form">
        <n-form-item label="昵称">
          <n-input
            v-model:value="form.nickname"
            placeholder="例如：胶片夜航员"
            maxlength="50"
            show-count
          />
        </n-form-item>

        <n-form-item label="邮箱">
          <n-input
            v-model:value="form.email"
            placeholder="用于展示或联系"
            maxlength="100"
          />
        </n-form-item>

        <div class="profile-form-actions">
          <n-button
            type="primary"
            class="rounded-full px-6"
            :loading="saving"
            :disabled="!hasChanges || avatarUploading"
            @click="handleSubmit"
          >
            保存资料
          </n-button>
          <n-button
            tertiary
            class="rounded-full px-6"
            :disabled="saving || avatarUploading || !hasChanges"
            @click="resetForm"
          >
            还原修改
          </n-button>
          <n-button
            secondary
            class="rounded-full px-6"
            :disabled="saving || avatarUploading"
            @click="handleCancel"
          >
            取消编辑
          </n-button>
        </div>
      </n-form>
    </div>
  </section>
</template>

<style scoped>
.profile-edit-section {
  border-bottom: 1px solid rgba(148, 163, 184, 0.24);
  padding-bottom: 2rem;
}

.profile-edit-header {
  margin-bottom: 1.5rem;
}

.profile-edit-title {
  margin: 0;
  font-family: var(--font-display);
  font-size: 1.8rem;
  font-weight: 700;
  color: #0f172a;
}

.profile-edit-description {
  margin: 0.6rem 0 0;
  max-width: 42rem;
  font-size: 0.96rem;
  line-height: 1.7;
  color: #64748b;
}

.profile-edit-layout {
  display: grid;
  gap: 2rem;
  grid-template-columns: minmax(0, 14rem) minmax(0, 1fr);
}

.profile-avatar-panel {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 1rem;
}

.profile-avatar-frame {
  position: relative;
  display: flex;
  height: 9.5rem;
  width: 9.5rem;
  padding: 0;
  border: none;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border-radius: 2rem;
  background:
    linear-gradient(135deg, rgba(15, 23, 42, 0.96) 0%, rgba(71, 85, 105, 0.92) 100%);
  color: #ffffff;
  cursor: pointer;
  transition:
    transform 0.2s ease,
    box-shadow 0.2s ease;
}

.profile-avatar-frame:hover:not(:disabled),
.profile-avatar-frame:focus-visible {
  transform: translateY(-1px);
  box-shadow: 0 18px 36px rgba(15, 23, 42, 0.2);
}

.profile-avatar-frame:focus-visible {
  outline: 2px solid rgba(245, 158, 11, 0.9);
  outline-offset: 4px;
}

.profile-avatar-frame:disabled {
  cursor: progress;
}

.profile-avatar-image {
  height: 100%;
  width: 100%;
  object-fit: cover;
}

.profile-avatar-overlay {
  position: absolute;
  inset: auto 0 0 0;
  display: flex;
  justify-content: center;
  padding: 0.85rem 0.75rem 0.75rem;
  background: linear-gradient(180deg, rgba(15, 23, 42, 0) 0%, rgba(15, 23, 42, 0.78) 100%);
  pointer-events: none;
}

.profile-avatar-overlay-text {
  font-size: 0.82rem;
  font-weight: 600;
  letter-spacing: 0.02em;
}

.profile-avatar-fallback {
  font-family: var(--font-display);
  font-size: 2.2rem;
  font-weight: 700;
}

.profile-avatar-input {
  display: none;
}

.profile-avatar-hint {
  margin: 0;
  max-width: 14rem;
  font-size: 0.88rem;
  line-height: 1.6;
  color: #64748b;
}

.profile-form {
  display: grid;
  gap: 0.25rem;
}

.profile-form-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
  padding-top: 0.5rem;
}

@media (max-width: 767px) {
  .profile-edit-layout {
    grid-template-columns: minmax(0, 1fr);
  }

  .profile-avatar-panel {
    align-items: center;
  }

  .profile-avatar-hint {
    max-width: none;
    text-align: center;
  }
}
</style>
