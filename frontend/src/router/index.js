import { createRouter, createWebHistory } from 'vue-router'
import { usePortalAuthStore } from '@/stores/portalAuth'
import { useAdminAuthStore } from '@/stores/adminAuth'
import { isTokenExpired } from '@/utils/jwt'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/Login.vue'),
      meta: { guest: 'portal' }
    },
    {
      path: '/chat',
      name: 'chat',
      component: () => import('@/views/Chat.vue'),
      meta: { requiresPortalAuth: true }
    },
    {
      path: '/admin/login',
      name: 'adminLogin',
      component: () => import('@/views/admin/AdminLogin.vue'),
      meta: { guest: 'admin' }
    },
    {
      path: '/admin',
      component: () => import('@/views/admin/AdminLayout.vue'),
      meta: { requiresAdminAuth: true },
      children: [
        { path: '', redirect: '/admin/dashboard' },
        { path: 'dashboard', component: () => import('@/views/admin/Dashboard.vue') },
        { path: 'documents', component: () => import('@/views/admin/DocumentManage.vue') },
        { path: 'rules', component: () => import('@/views/admin/RuleManage.vue') },
        { path: 'kb-config', component: () => import('@/views/admin/KbConfigManage.vue') },
        { path: 'messages', component: () => import('@/views/admin/MessageSearch.vue') },
        { path: 'turn-traces', component: () => import('@/views/admin/TurnTrace.vue') },
        { path: 'message-reviews', component: () => import('@/views/admin/MessageReview.vue') },
        { path: 'devices', component: () => import('@/views/admin/DeviceManage.vue') },
        {
          path: 'accounts',
          component: () => import('@/views/admin/AccountManage.vue'),
          meta: { requiresSuperAdmin: true }
        }
      ]
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/chat'
    }
  ]
})

function expirePortalIfNeeded(portal) {
  if (portal.token && isTokenExpired(portal.token)) {
    portal.logout()
  }
}

function expireAdminIfNeeded(admin) {
  if (admin.token && isTokenExpired(admin.token)) {
    admin.logout()
  }
}

router.beforeEach((to, _from, next) => {
  const portal = usePortalAuthStore()
  const admin = useAdminAuthStore()
  expirePortalIfNeeded(portal)
  expireAdminIfNeeded(admin)

  const isAdminArea = to.path.startsWith('/admin') && to.path !== '/admin/login'

  if (to.meta.requiresPortalAuth && !portal.token) {
    return next('/login')
  }

  if (to.meta.requiresAdminAuth || isAdminArea) {
    if (!admin.token) {
      return next('/admin/login')
    }
    if (!admin.isAdmin) {
      admin.logout()
      return next('/admin/login')
    }
  }

  if (to.meta.requiresSuperAdmin && !admin.isSuperAdmin) {
    return next('/admin/dashboard')
  }

  if (to.meta.guest === 'portal' && portal.token) {
    return next('/chat')
  }

  if (to.meta.guest === 'admin' && admin.token) {
    return next('/admin/dashboard')
  }

  next()
})

export default router
