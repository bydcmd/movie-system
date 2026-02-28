<script setup lang="ts">
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { NInput, useMessage } from 'naive-ui'
import { useAuthStore } from '@/stores/auth'
import AuthForm from '@/components/auth/AuthForm.vue'
import { login as loginRequest } from '@/api/endpoints/auth-management/auth-management'
import type { LoginDTO } from '@/api/model'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const message = useMessage()

const form = ref({
  id: '',
  password: ''
})

const loading = ref(false)

const handleLogin = async () => {
  if (!form.value.id || !form.value.password) {
    message.warning('请填写账号和密码')
    return
  }

  loading.value = true
  try {
    const loginData: LoginDTO = {
      id: form.value.id,
      password: form.value.password
    }
    const payload = await loginRequest(loginData) as Record<string, unknown> | null
    
    // 登录成功，保存 token 和用户信息
    if (payload?.accessToken && typeof payload.accessToken === 'string') {
      authStore.setAuth({
        accessToken: payload.accessToken,
        id: typeof payload.id === 'string' ? payload.id : undefined,
        nickname: typeof payload.nickname === 'string' ? payload.nickname : undefined,
        avatar: typeof payload.avatar === 'string' ? payload.avatar : undefined,
        email: typeof payload.email === 'string' ? payload.email : undefined,
        url: typeof payload.url === 'string' ? payload.url : undefined,
        role: typeof payload.role === 'number' ? payload.role : undefined
      })
      const displayName =
        (typeof payload.nickname === 'string' && payload.nickname) ||
        (typeof payload.id === 'string' && payload.id) ||
        '用户'
      message.success(`欢迎回来，${displayName}！`)
      
      // 如果有 redirect 参数，登录后跳转到原页面
      const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
      router.replace(redirect)
    } else {
      message.error('登录失败：未获取到令牌')
    }
  } catch (error: any) {
    // 错误已在 axios 拦截器中处理，这里可以额外处理特定逻辑
    console.error('Login error:', error)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <AuthForm 
    title="欢迎回来" 
    buttonText="登录" 
    type="login" 
    :loading="loading"
    @submit="handleLogin"
  >
    <div class="space-y-4">
      <n-input v-model:value="form.id" placeholder="账号" size="large" />
      <n-input v-model:value="form.password" type="password" placeholder="密码" size="large" show-password-on="click" />
    </div>

    <template #footer>
      <p>
        还没有账号？
        <a @click="router.push('/register')" class="text-accent cursor-pointer hover:underline font-medium">立即注册</a>
      </p>
    </template>
  </AuthForm>
</template>
