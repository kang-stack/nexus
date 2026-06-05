import { describe, it, expect } from 'vitest'
import { isTokenExpired, decodeJwt } from '../jwt'

function makeToken(payload) {
  const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }))
  const body = btoa(JSON.stringify(payload))
  return `${header}.${body}.fakesignature`
}

describe('decodeJwt', () => {
  it('returns null for garbage input', () => {
    expect(decodeJwt('not-a-jwt')).toBeNull()
  })

  it('returns null for token with wrong number of segments', () => {
    expect(decodeJwt('a.b')).toBeNull()
    expect(decodeJwt('a.b.c.d')).toBeNull()
  })

  it('decodes a valid-looking token payload', () => {
    const token = makeToken({ sub: 'user123', exp: 9999999999 })
    const payload = decodeJwt(token)
    expect(payload).not.toBeNull()
    expect(payload.sub).toBe('user123')
  })
})

describe('isTokenExpired', () => {
  it('returns true for garbage (malformed) token', () => {
    // Malformed tokens should be treated as expired — safer default
    expect(isTokenExpired('garbage')).toBe(true)
  })

  it('returns true for token with unparseable payload', () => {
    expect(isTokenExpired('header.!@#$%.signature')).toBe(true)
  })

  it('returns true for token with no exp claim', () => {
    // Token without exp claim should be treated as expired
    const token = makeToken({ sub: 'user123' })
    expect(isTokenExpired(token)).toBe(true)
  })

  it('returns true for expired token', () => {
    const pastExp = Math.floor(Date.now() / 1000) - 3600
    const token = makeToken({ sub: 'user123', exp: pastExp })
    expect(isTokenExpired(token)).toBe(true)
  })

  it('returns false for valid future token', () => {
    const futureExp = Math.floor(Date.now() / 1000) + 3600
    const token = makeToken({ sub: 'user123', exp: futureExp })
    expect(isTokenExpired(token)).toBe(false)
  })
})
