import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '@/api'
import { isTokenExpired } from '@/utils/jwt'
import {
  ADMIN_KEYS,
  loadSession,
  saveSession,
  clearSession,
  migrateLegacyAuth
} from '@/auth/storage'

migrateLegacyAuth()

const ADMIN_ROLES = ['SUPER_ADMIN', 'ADMIN', 'OPERATOR']

function readAdminSession() {
  const session = loadSession(ADMIN_KEYS)
  if (session.token && isTokenExpired(session.token)) {
    clearSession(ADMIN_KEYS)
    return { token: '', userId: '', username: '', role: '' }
  }
  return session
}

export const useAdminAuthStore = defineStore('adminAuth', () => {
  const initial = readAdminSession()
  const token = ref(initial.token)
  const userId = ref(initial.userId)
  const username = ref(initial.username)
  const role = ref(initial.role)

  const isLoggedIn = computed(() => !!token.value)

  const isAdmin = computed(() => ADMIN_ROLES.includes(role.value))

  const isSuperAdmin = computed(() => role.value === 'SUPER_ADMIN')

  const canManage = computed(() => ['SUPER_ADMIN', 'ADMIN'].includes(role.value))

  const roleLabel = computed(() => {
    const map = {
      SUPER_ADMIN: '超级管理员',
      ADMIN: '管理员',
      OPERATOR: '运营人员'
    }
    return map[role.value] || role.value
  })

  function persist(auth) {
    token.value = auth.token
    userId.value = auth.userId
    username.value = auth.username
    role.value = auth.role
    saveSession(ADMIN_KEYS, auth)
  }

  async function login(form) {
    const res = await authApi.adminLogin(form)
    persist(res.data)
  }

  function logout() {
    token.value = ''
    userId.value = ''
    username.value = ''
    role.value = ''
    clearSession(ADMIN_KEYS)
  }

  return {
    token,
    userId,
    username,
    role,
    isLoggedIn,
    isAdmin,
    isSuperAdmin,
    canManage,
    roleLabel,
    login,
    logout
  }
})
