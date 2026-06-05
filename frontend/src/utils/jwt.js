/** 解码 JWT payload，不做签名验证 */
export function decodeJwt(token) {
  try {
    const parts = token.split('.')
    if (parts.length !== 3) return null
    return JSON.parse(atob(parts[1]))
  } catch {
    return null
  }
}

/** 检查 JWT 是否已过期（基于 exp 字段） */
export function isTokenExpired(token) {
  const payload = decodeJwt(token)
  if (!payload || !payload.exp) return true
  return Date.now() >= payload.exp * 1000
}
