import { createRouter, createWebHistory } from 'vue-router'
import { AUTH_ROLE, type AuthRole, useAuthStore } from '@/stores/auth'

const HomeView = () => import('@/views/HomeView.vue')
const LoginView = () => import('@/views/LoginView.vue')
const RegisterView = () => import('@/views/RegisterView.vue')
const MoviesView = () => import('@/views/MoviesView.vue')
const MovieDetailView = () => import('@/views/MovieDetailView.vue')
const LongReviewDetailView = () => import('@/views/LongReviewDetailView.vue')
const ProfileView = () => import('@/views/ProfileView.vue')
const AdminView = () => import('@/views/AdminView.vue')
const ForbiddenView = () => import('@/views/ForbiddenView.vue')

declare module 'vue-router' {
  interface RouteMeta {
    requiresAuth?: boolean
    guestOnly?: boolean
    allowedRoles?: AuthRole[]
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
      path: '/profile',
      name: 'profile',
      component: ProfileView,
      meta: { requiresAuth: true }
    },
    {
      path: '/admin',
      name: 'admin',
      component: AdminView,
      meta: {
        requiresAuth: true,
        allowedRoles: [AUTH_ROLE.ADMIN]
      }
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

  if (!authStore.initialized) {
    await authStore.initializeAuth()
  }

  const requiresAuth = Boolean(to.meta.requiresAuth || to.meta.allowedRoles?.length)

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
