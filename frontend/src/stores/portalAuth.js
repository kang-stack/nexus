import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '@/api'
import { isTokenExpired } from '@/utils/jwt'
import {
  PORTAL_KEYS,
  loadSession,
  saveSession,
  clearSession,
  migrateLegacyAuth
} from '@/auth/storage'

migrateLegacyAuth()

function readPortalSession() {
  const session = loadSession(PORTAL_KEYS)
  if (session.token && isTokenExpired(session.token)) {
    clearSession(PORTAL_KEYS)
    return { token: '', userId: '', username: '', role: '' }
  }
  return session
}

export const usePortalAuthStore = defineStore('portalAuth', () => {
  const initial = readPortalSession()
  const token = ref(initial.token)
  const userId = ref(initial.userId)
  const username = ref(initial.username)
  const role = ref(initial.role)

  const isLoggedIn = computed(() => !!token.value)

  function persist(auth) {
    token.value = auth.token
    userId.value = auth.userId
    username.value = auth.username
    role.value = auth.role
    saveSession(PORTAL_KEYS, auth)
  }

  async function login(form) {
    const res = await authApi.login(form)
    persist(res.data)
  }

  async function register(form) {
    const res = await authApi.register(form)
    persist(res.data)
  }

  function logout() {
    token.value = ''
    userId.value = ''
    username.value = ''
    role.value = ''
    clearSession(PORTAL_KEYS)
  }

  return { token, userId, username, role, isLoggedIn, login, register, logout }
})
