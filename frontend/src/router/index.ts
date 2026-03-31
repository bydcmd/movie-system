import { createRouter, createWebHistory } from 'vue-router'
import { AUTH_ROLE, type AuthRole, useAuthStore } from '@/stores/auth'

const HomeView = () => import('@/views/HomeView.vue')
const LoginView = () => import('@/views/LoginView.vue')
const RegisterView = () => import('@/views/RegisterView.vue')
const MoviesView = () => import('@/views/MoviesView.vue')
const TrendingMoviesView = () => import('@/views/TrendingMoviesView.vue')
const MovieDetailView = () => import('@/views/MovieDetailView.vue')
const LongReviewDetailView = () => import('@/views/LongReviewDetailView.vue')
const LongReviewEditorView = () => import('@/views/LongReviewEditorView.vue')
const ProfileView = () => import('@/views/ProfileView.vue')
const AdminView = () => import('@/views/AdminView.vue')
const AdminDashboardView = () => import('@/views/admin/DashboardView.vue')
const UserManagementView = () => import('@/views/admin/UserManagementView.vue')
const CommentManagementView = () => import('@/views/admin/CommentManagementView.vue')
const MovieManagementView = () => import('@/views/admin/MovieManagementView.vue')
const ForbiddenView = () => import('@/views/ForbiddenView.vue')

declare module 'vue-router' {
  interface RouteMeta {
    requiresAuth?: boolean
    guestOnly?: boolean
    allowedRoles?: AuthRole[]
    adminTitle?: string
    adminDescription?: string
  }
}

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: HomeView
    },
    {
      path: '/movies',
      name: 'movies',
      component: MoviesView
    },
    {
      path: '/trending',
      name: 'trending',
      component: TrendingMoviesView
    },
    {
      path: '/login',
      name: 'login',
      component: LoginView,
      meta: { guestOnly: true }
    },
    {
      path: '/register',
      name: 'register',
      component: RegisterView,
      meta: { guestOnly: true }
    },
    {
      path: '/movie/:id',
      name: 'movie-detail',
      component: MovieDetailView,
      props: true
    },
    {
      path: '/movie/:id/reviews/:commentId',
      name: 'movie-review-detail',
      component: LongReviewDetailView
    },
    {
      path: '/movie/:movieId/review/edit',
      name: 'long-review-editor',
      component: LongReviewEditorView,
      meta: { requiresAuth: true }
    },
    {
      path: '/movie/:movieId/review/edit/:commentId',
      name: 'long-review-editor-edit',
      component: LongReviewEditorView,
      meta: { requiresAuth: true }
    },
    {
      path: '/profile',
      name: 'profile',
      component: ProfileView,
      meta: { requiresAuth: true }
    },
    {
      path: '/admin',
      name: 'admin',
      component: AdminView,
      redirect: { name: 'admin-dashboard' },
      meta: {
        requiresAuth: true,
        allowedRoles: [AUTH_ROLE.ADMIN]
      },
      children: [
        {
          path: 'dashboard',
          name: 'admin-dashboard',
          component: AdminDashboardView,
          meta: {
            adminTitle: '后台仪表盘',
            adminDescription: '查看总览统计、近 7 天趋势，以及全量推荐缓存管理动作。'
          }
        },
        {
          path: 'users',
          name: 'admin-users',
          component: UserManagementView,
          meta: {
            adminTitle: '用户管理',
            adminDescription: '检索注册用户、核对账号状态，并清除指定用户的猜你喜欢缓存。'
          }
        },
        {
          path: 'comments',
          name: 'admin-comments',
          component: CommentManagementView,
          meta: {
            adminTitle: '评论管理',
            adminDescription: '审核短评与长评内容，必要时执行管理员强制删除。'
          }
        },
        {
          path: 'movies',
          name: 'admin-movies',
          component: MovieManagementView,
          meta: {
            adminTitle: '电影管理',
            adminDescription: '检索电影信息，快速核对封面、简介、评分和分类数据。'
          }
        }
      ]
    },
    {
      path: '/forbidden',
      name: 'forbidden',
      component: ForbiddenView,
      meta: { requiresAuth: true }
    }
  ]
})

router.beforeEach(async (to) => {
  const authStore = useAuthStore()
  const requiresAuth = Boolean(to.meta.requiresAuth || to.meta.allowedRoles?.length)
  const needsResolvedAuthState = requiresAuth || Boolean(to.meta.guestOnly)

  if (!authStore.initialized) {
    if (needsResolvedAuthState) {
      await authStore.initializeAuth()
    } else {
      void authStore.initializeAuth()
    }
  }

  if (requiresAuth && !authStore.isAuthenticated) {
    return {
      name: 'login',
      query: { redirect: to.fullPath }
    }
  }

  if (to.meta.allowedRoles?.length && !authStore.hasRole(...to.meta.allowedRoles)) {
    return {
      name: 'forbidden',
      query: { redirect: to.fullPath }
    }
  }

  if (to.meta.guestOnly && authStore.isAuthenticated) {
    const redirect = typeof to.query.redirect === 'string' ? to.query.redirect : '/'
    return redirect
  }

  return true
})

export default router
