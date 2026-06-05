/** C 端客服 / 门户会话 */
export const PORTAL_KEYS = {
  token: 'icc_portal_token',
  userId: 'icc_portal_userId',
  username: 'icc_portal_username',
  role: 'icc_portal_role'
}

/** 管理后台会话（sys_admin） */
export const ADMIN_KEYS = {
  token: 'icc_admin_token',
  userId: 'icc_admin_userId',
  username: 'icc_admin_username',
  role: 'icc_admin_role'
}

const LEGACY_KEYS = ['token', 'userId', 'username', 'role']

export function getPortalToken() {
  return localStorage.getItem(PORTAL_KEYS.token) || ''
}

export function getAdminToken() {
  return localStorage.getItem(ADMIN_KEYS.token) || ''
}

export function loadSession(keys) {
  return {
    token: localStorage.getItem(keys.token) || '',
    userId: localStorage.getItem(keys.userId) || '',
    username: localStorage.getItem(keys.username) || '',
    role: localStorage.getItem(keys.role) || ''
  }
}

export function saveSession(keys, auth) {
  localStorage.setItem(keys.token, auth.token)
  localStorage.setItem(keys.userId, auth.userId)
  localStorage.setItem(keys.username, auth.username)
  localStorage.setItem(keys.role, auth.role)
}

export function clearSession(keys) {
  for (const k of Object.values(keys)) {
    localStorage.removeItem(k)
  }
}

function removeLegacyKeys() {
  for (const k of LEGACY_KEYS) {
    localStorage.removeItem(k)
  }
}

/**
 * 将旧版单一 token 迁移到 portal / admin 之一，避免升级后仍串台。
 */
export function migrateLegacyAuth() {
  const legacyToken = localStorage.getItem('token')
  if (!legacyToken) return

  if (getPortalToken() || getAdminToken()) {
    removeLegacyKeys()
    return
  }

  const payload = {
    token: legacyToken,
    userId: localStorage.getItem('userId') || '',
    username: localStorage.getItem('username') || '',
    role: localStorage.getItem('role') || ''
  }
  const adminRoles = ['SUPER_ADMIN', 'ADMIN', 'OPERATOR']
  if (adminRoles.includes(payload.role)) {
    saveSession(ADMIN_KEYS, payload)
  } else {
    saveSession(PORTAL_KEYS, payload)
  }
  removeLegacyKeys()
}
