<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NInput, useMessage } from 'naive-ui'
import AuthForm from '@/components/auth/AuthForm.vue'
import { useRegister } from '@/api/endpoints/auth-management/auth-management'
import type { RegisterDTO } from '@/api/model'

const router = useRouter()
const route = useRoute()
const message = useMessage()
const registerMutation = useRegister()

const form = ref({
  id: '',
  nickname: '',
  email: '',
  password: '',
  confirmPassword: ''
})

const loading = computed(() => registerMutation.isPending.value)

const handleRegister = async () => {
  if (!form.value.id || !form.value.nickname || !form.value.password) {
    message.warning('请填写账号、昵称和密码')
    return
  }

  if (form.value.id.length < 4 || form.value.id.length > 20) {
    message.warning('账号长度应在 4-20 个字符之间')
    return
  }

  if (form.value.password.length < 6) {
    message.warning('密码长度至少为 6 位')
    return
  }

  if (form.value.password !== form.value.confirmPassword) {
    message.error('两次输入的密码不一致')
    return
  }

  try {
    const registerData: RegisterDTO = {
      id: form.value.id,
      password: form.value.password,
      nickname: form.value.nickname,
      email: form.value.email || undefined
    }
    await registerMutation.mutateAsync({ data: registerData })
    message.success('注册成功！请登录')
    router.push('/admin/login')
  } catch (error: any) {
    console.error('Register error:', error)
  }
}
</script>

<template>
  <AuthForm
    title="管理员注册"
    buttonText="创建账号"
    type="register"
    :loading="loading"
    @submit="handleRegister"
  >
    <div class="space-y-4">
      <n-input
        v-model:value="form.id"
        placeholder="账号 (4-20位)"
        size="large"
        :input-props="{ id: 'register-id', name: 'id', autocomplete: 'username' }"
      />
      <n-input
        v-model:value="form.nickname"
        placeholder="昵称"
        size="large"
        :input-props="{ id: 'register-nickname', name: 'nickname', autocomplete: 'name' }"
      />
      <n-input
        v-model:value="form.email"
        placeholder="邮箱 (可选)"
        size="large"
        :input-props="{ id: 'register-email', name: 'email', autocomplete: 'email' }"
      />
      <n-input
        v-model:value="form.password"
        type="password"
        placeholder="密码 (至少6位)"
        size="large"
        show-password-on="click"
        :input-props="{ id: 'register-password', name: 'password', autocomplete: 'new-password' }"
      />
      <n-input
        v-model:value="form.confirmPassword"
        type="password"
        placeholder="确认密码"
        size="large"
        show-password-on="click"
        :input-props="{ id: 'register-confirm-password', name: 'confirmPassword', autocomplete: 'new-password' }"
      />
    </div>

    <template #footer>
      <p>
        已有账号？
        <a
          @click="router.push('/admin/login')"
          class="text-accent cursor-pointer hover:underline font-medium"
        >
          立即登录
        </a>
      </p>
    </template>
  </AuthForm>
</template>
