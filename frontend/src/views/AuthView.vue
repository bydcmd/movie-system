<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NInput, useMessage } from 'naive-ui'
import AuthForm from '@/components/auth/AuthForm.vue'
import { useLogin, useRegister } from '@/api/endpoints/auth-management/auth-management'
import { useAuthStore } from '@/stores/auth'
import type { LoginDTO, RegisterDTO } from '@/api/model'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const authStore = useAuthStore()

const loginMutation = useLogin()
const registerMutation = useRegister()

const isLogin = computed(() => route.name === 'login')
const redirectTarget = computed(() => (typeof route.query.redirect === 'string' ? route.query.redirect : '/'))

// Login form
const loginForm = ref({
  id: '',
  password: ''
})

// Register form
const registerForm = ref({
  id: '',
  nickname: '',
  email: '',
  password: '',
  confirmPassword: ''
})

const loading = computed(() => loginMutation.isPending.value || registerMutation.isPending.value)

const handleLogin = async () => {
  if (!loginForm.value.id || !loginForm.value.password) {
    message.warning('请填写账号和密码')
    return
  }

  try {
    const loginData: LoginDTO = {
      id: loginForm.value.id,
      password: loginForm.value.password
    }
    const payload = await loginMutation.mutateAsync({ data: loginData })

    if (payload?.accessToken) {
      // Admin cannot login here - redirect to admin login page
      if (payload.role === 0) {
        message.warning('管理员请前往管理员登录页面')
        authStore.clearAuth()
        router.replace('/admin/login')
        return
      }

      authStore.setAuth(payload)
      message.success(`欢迎回来，${payload.nickname || payload.id}！`)
      router.replace(redirectTarget.value)
    } else {
      message.error('登录失败：未获取到令牌')
    }
  } catch (error: any) {
    console.error('Login error:', error)
  }
}

const handleRegister = async () => {
  const form = registerForm.value

  if (!form.id || !form.nickname || !form.password) {
    message.warning('请填写账号、昵称和密码')
    return
  }

  if (form.id.length < 4 || form.id.length > 20) {
    message.warning('账号长度应在 4-20 个字符之间')
    return
  }

  if (form.password.length < 6) {
    message.warning('密码长度至少为 6 位')
    return
  }

  if (form.password !== form.confirmPassword) {
    message.error('两次输入的密码不一致')
    return
  }

  try {
    const registerData: RegisterDTO = {
      id: form.id,
      password: form.password,
      nickname: form.nickname,
      email: form.email || undefined
    }
    await registerMutation.mutateAsync({ data: registerData })
    message.success('注册成功！请登录')
    router.push({
      name: 'login',
      query: redirectTarget.value ? { redirect: redirectTarget.value } : undefined
    })
  } catch (error: any) {
    console.error('Register error:', error)
  }
}

const switchToLogin = () => {
  router.push({
    name: 'login',
    query: redirectTarget.value ? { redirect: redirectTarget.value } : undefined
  })
}

const switchToRegister = () => {
  router.push({
    name: 'register',
    query: redirectTarget.value ? { redirect: redirectTarget.value } : undefined
  })
}
</script>

<template>
  <AuthForm
    :title="isLogin ? '登录' : '加入社区'"
    :button-text="isLogin ? '登录' : '创建账号'"
    type="login"
    :loading="loading"
    @submit="isLogin ? handleLogin : handleRegister"
  >
    <div v-if="isLogin" class="space-y-4">
      <n-input
        v-model:value="loginForm.id"
        placeholder="账号"
        size="large"
        :input-props="{ id: 'login-id', name: 'id', autocomplete: 'username' }"
      />
      <n-input
        v-model:value="loginForm.password"
        type="password"
        placeholder="密码"
        size="large"
        show-password-on="click"
        :input-props="{ id: 'login-password', name: 'password', autocomplete: 'current-password' }"
      />
    </div>

    <div v-else class="space-y-4">
      <n-input
        v-model:value="registerForm.id"
        placeholder="账号 (4-20位)"
        size="large"
        :input-props="{ id: 'register-id', name: 'id', autocomplete: 'username' }"
      />
      <n-input
        v-model:value="registerForm.nickname"
        placeholder="昵称"
        size="large"
        :input-props="{ id: 'register-nickname', name: 'nickname', autocomplete: 'name' }"
      />
      <n-input
        v-model:value="registerForm.email"
        placeholder="邮箱 (可选)"
        size="large"
        :input-props="{ id: 'register-email', name: 'email', autocomplete: 'email' }"
      />
      <n-input
        v-model:value="registerForm.password"
        type="password"
        placeholder="密码 (至少6位)"
        size="large"
        show-password-on="click"
        :input-props="{ id: 'register-password', name: 'password', autocomplete: 'new-password' }"
      />
      <n-input
        v-model:value="registerForm.confirmPassword"
        type="password"
        placeholder="确认密码"
        size="large"
        show-password-on="click"
        :input-props="{ id: 'register-confirm-password', name: 'confirmPassword', autocomplete: 'new-password' }"
      />
    </div>

    <template #footer>
      <p v-if="isLogin">
        没有账号？
        <a
          @click="switchToRegister"
          class="text-accent cursor-pointer hover:underline font-medium"
        >
          立即注册
        </a>
      </p>
      <p v-else>
        已有账号？
        <a
          @click="switchToLogin"
          class="text-accent cursor-pointer hover:underline font-medium"
        >
          立即登录
        </a>
      </p>
      <p class="mt-2">
        <a
          @click="router.push('/')"
          class="text-slate-500 cursor-pointer hover:underline font-medium"
        >
          返回主页
        </a>
      </p>
    </template>
  </AuthForm>
</template>
