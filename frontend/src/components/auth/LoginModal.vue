<script setup lang="ts">
import { ref } from 'vue'
import { NInput, NButton, NModal, useMessage } from 'naive-ui'
import { useLogin } from '@/api/endpoints/auth-management/auth-management'
import { useAuthStore } from '@/stores/auth'
import type { LoginDTO } from '@/api/model'

const props = defineProps<{
  show: boolean
}>()

const emit = defineEmits<{
  'update:show': [value: boolean]
}>()

const authStore = useAuthStore()
const loginMutation = useLogin()
const message = useMessage()

const form = ref({
  id: '',
  password: ''
})

const loading = loginMutation.isPending

const handleLogin = async () => {
  if (!form.value.id || !form.value.password) {
    return
  }

  try {
    const loginData: LoginDTO = {
      id: form.value.id,
      password: form.value.password
    }
    const payload = await loginMutation.mutateAsync({ data: loginData })

    if (payload?.accessToken) {
      authStore.setAuth(payload)
      message.success(`欢迎回来，${payload.nickname || payload.id}！`)
      // 登录成功后关闭弹窗并重置表单
      form.value = { id: '', password: '' }
      emit('update:show', false)
    }
  } catch {
    // 错误已在 axios 拦截器中处理
  }
}

const handleClose = () => {
  form.value = { id: '', password: '' }
  emit('update:show', false)
}
</script>

<template>
  <n-modal
    :show="props.show"
    preset="card"
    title="登录"
    :bordered="false"
    class="w-full max-w-sm"
    :mask-closable="true"
    @update:show="emit('update:show', $event)"
    @after-leave="form = { id: '', password: '' }"
  >
    <div class="space-y-4 py-2">
      <n-input
        v-model:value="form.id"
        placeholder="账号"
        size="large"
        :input-props="{ autocomplete: 'username' }"
        @keyup.enter="handleLogin"
      />
      <n-input
        v-model:value="form.password"
        type="password"
        placeholder="密码"
        size="large"
        show-password-on="click"
        :input-props="{ autocomplete: 'current-password' }"
        @keyup.enter="handleLogin"
      />
    </div>

    <template #footer>
      <div class="flex justify-end gap-3">
        <n-button @click="handleClose">取消</n-button>
        <n-button type="primary" :loading="loading" @click="handleLogin">
          登录
        </n-button>
      </div>
    </template>
  </n-modal>
</template>