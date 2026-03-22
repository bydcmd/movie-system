<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { NInput, useMessage } from 'naive-ui'
import { useAuthStore } from '@/stores/auth'
import AuthForm from '@/components/auth/AuthForm.vue'
import { useLogin } from '@/api/endpoints/auth-management/auth-management'
import type { LoginDTO } from '@/api/model'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const message = useMessage()
const loginMutation = useLogin()

const form = ref({
  id: '',
  password: ''
})

const loading = computed(() => loginMutation.isPending.value)
const redirectTarget = computed(() => (typeof route.query.redirect === 'string' ? route.query.redirect : '/'))

const handleLogin = async () => {
  if (!form.value.id || !form.value.password) {
    message.warning('请填写账号和密码')
    return
  }

  try {
    const loginData: LoginDTO = {
      id: form.value.id,
      password: form.value.password
    }
    const payload = await loginMutation.mutateAsync({ data: loginData })
    
    // 登录成功，保存 token 和用户信息
    if (payload?.accessToken) {
      authStore.setAuth(payload)
      const displayName =
        payload.nickname ||
        payload.id ||
        '用户'
      message.success(`欢迎回来，${displayName}！`)
      
      // 如果有 redirect 参数，登录后跳转到原页面
      router.replace(redirectTarget.value)
    } else {
      message.error('登录失败：未获取到令牌')
    }
  } catch (error: any) {
    // 错误已在 axios 拦截器中处理，这里可以额外处理特定逻辑
    console.error('Login error:', error)
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
      <n-input
        v-model:value="form.id"
        placeholder="账号"
        size="large"
        :input-props="{ id: 'login-id', name: 'id', autocomplete: 'username' }"
      />
      <n-input
        v-model:value="form.password"
        type="password"
        placeholder="密码"
        size="large"
        show-password-on="click"
        :input-props="{ id: 'login-password', name: 'password', autocomplete: 'current-password' }"
      />
    </div>

    <template #footer>
      <p>
        还没有账号？
        <a
          @click="router.push({ path: '/register', query: redirectTarget === '/' ? undefined : { redirect: redirectTarget } })"
          class="text-accent cursor-pointer hover:underline font-medium"
        >
          立即注册
        </a>
      </p>
    </template>
  </AuthForm>
</template>
