<template>
  <div class="login-page">
    <!-- Floating shapes -->
    <div class="floating-shapes">
      <span v-for="i in 5" :key="i" class="shape" :style="shapeStyle(i)"></span>
    </div>

    <div class="login-card">
      <div class="brand">
        <img src="/Logo.png" alt="云客猫" class="brand-logo" />
        <h1 class="brand-name">云客猫</h1>
      </div>
      <p class="subtitle">{{ isRegister ? '创建您的云上空间' : '欢迎回到云上空间' }}</p>

      <form @submit.prevent="handleSubmit" class="login-form" novalidate>
        <div class="form-group" :class="{ 'has-error': touched.username && errors.username }">
          <label>用户名</label>
          <input
            v-model="form.username"
            type="text"
            placeholder="3-20位字母、数字或下划线"
            maxlength="20"
            @blur="touched.username = true; validateUsername()"
            @input="touched.username && validateUsername()"
          />
          <p v-if="touched.username && errors.username" class="field-error">{{ errors.username }}</p>
        </div>

        <div class="form-group" :class="{ 'has-error': touched.password && errors.password }">
          <label>密码</label>
          <input
            v-model="form.password"
            type="password"
            :placeholder="isRegister ? '6-20位密码' : '请输入密码'"
            maxlength="20"
            @blur="touched.password = true; validatePassword()"
            @input="touched.password && validatePassword()"
          />
          <p v-if="touched.password && errors.password" class="field-error">{{ errors.password }}</p>
        </div>

        <div v-if="isRegister" class="form-group" :class="{ 'has-error': touched.nickname && errors.nickname }">
          <label>昵称 <span class="optional">选填</span></label>
          <input
            v-model="form.nickname"
            type="text"
            placeholder="给自己起个昵称"
            maxlength="20"
            @blur="touched.nickname = true; validateNickname()"
            @input="touched.nickname && validateNickname()"
          />
          <p v-if="touched.nickname && errors.nickname" class="field-error">{{ errors.nickname }}</p>
        </div>

        <div v-if="isRegister" class="form-group" :class="{ 'has-error': touched.phone && errors.phone }">
          <label>手机号 <span class="optional">选填</span></label>
          <input
            v-model="form.phone"
            type="tel"
            placeholder="请输入11位手机号"
            maxlength="11"
            @blur="touched.phone = true; validatePhone()"
            @input="touched.phone && validatePhone()"
          />
          <p v-if="touched.phone && errors.phone" class="field-error">{{ errors.phone }}</p>
        </div>

        <p v-if="errorMsg" class="error-msg">{{ errorMsg }}</p>
        <button type="submit" class="btn-primary" :disabled="loading">
          {{ loading ? '处理中...' : (isRegister ? '开启云之旅' : '登入云端') }}
        </button>
      </form>

      <p class="toggle-link">
        {{ isRegister ? '已有账号？' : '初来乍到？' }}
        <a href="#" @click.prevent="toggleMode">{{ isRegister ? '去登录' : '注册账号' }}</a>
      </p>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { usePortalAuthStore } from '@/stores/portalAuth'

const router = useRouter()
const auth = usePortalAuthStore()
const isRegister = ref(false)
const loading = ref(false)
const errorMsg = ref('')

const form = reactive({
  username: '', password: '', nickname: '', phone: ''
})

const errors = reactive({})
const touched = reactive({})

function shapeStyle(i) {
  const size = 80 + Math.sin(i * 2.1) * 50
  return {
    width: `${size}px`,
    height: `${size}px`,
    left: `${8 + (i - 1) * 20 + Math.sin(i * 1.8) * 8}%`,
    top: `${8 + (i % 3) * 32 + Math.cos(i * 2.2) * 12}%`,
    animationDelay: `${i * 1.3}s`,
    animationDuration: `${10 + i * 2}s`,
    opacity: 0.25 + i * 0.05
  }
}

function validateUsername() {
  const v = form.username
  if (!v) { errors.username = '请输入用户名'; return }
  if (v.length < 3) { errors.username = '用户名至少3位'; return }
  if (v.length > 20) { errors.username = '用户名最多20位'; return }
  if (!/^[a-zA-Z0-9_]+$/.test(v)) { errors.username = '只能包含字母、数字和下划线'; return }
  errors.username = ''
}
function validatePassword() {
  const v = form.password
  if (!v) { errors.password = '请输入密码'; return }
  if (v.length < 6) { errors.password = '密码至少6位'; return }
  if (v.length > 20) { errors.password = '密码最多20位'; return }
  errors.password = ''
}
function validateNickname() {
  if (form.nickname && form.nickname.length > 20) { errors.nickname = '昵称最多20位'; return }
  errors.nickname = ''
}
function validatePhone() {
  if (form.phone && !/^1[3-9]\d{9}$/.test(form.phone)) { errors.phone = '手机号格式不正确'; return }
  errors.phone = ''
}
function validateForm() {
  isRegister.value ? validateNickname() : (errors.nickname = '')
  isRegister.value ? validatePhone() : (errors.phone = '')
  validateUsername(); validatePassword()
  return !errors.username && !errors.password && !errors.nickname && !errors.phone
}
function toggleMode() {
  isRegister.value = !isRegister.value
  errorMsg.value = ''
  Object.keys(errors).forEach(k => errors[k] = '')
  Object.keys(touched).forEach(k => touched[k] = false)
}
async function handleSubmit() {
  if (!validateForm()) {
    touched.username = true; touched.password = true
    if (isRegister.value) { touched.nickname = true; touched.phone = true }
    return
  }
  loading.value = true; errorMsg.value = ''
  try {
    if (isRegister.value) {
      await auth.register({ username: form.username, password: form.password, nickname: form.nickname || undefined, phone: form.phone || undefined })
    } else {
      await auth.login({ username: form.username, password: form.password })
    }
    router.push('/chat')
  } catch (e) {
    errorMsg.value = e.response?.data?.error || '操作失败，请重试'
  } finally { loading.value = false }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
  background: linear-gradient(160deg, #fafaf8 0%, #f0f2f5 40%, #edf2f8 100%);
}

/* Floating shapes */
.floating-shapes { position: absolute; inset: 0; pointer-events: none; }
.shape {
  position: absolute;
  border-radius: 50%;
  background: radial-gradient(circle at 35% 35%, rgba(56, 189, 248, 0.12), transparent 70%);
  filter: blur(35px);
  animation: shapeFloat linear infinite alternate;
}
.shape:nth-child(odd) {
  background: radial-gradient(circle at 35% 35%, rgba(30, 45, 61, 0.1), transparent 70%);
}
@keyframes shapeFloat {
  0% { transform: translate(0, 0) scale(1); }
  100% { transform: translate(25px, -35px) scale(1.12); }
}

/* Card */
.login-card {
  position: relative; z-index: 1;
  background: #fff;
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  padding: 44px 40px 36px;
  width: 420px; max-width: 92vw;
  text-align: center;
  box-shadow: var(--shadow-md), 0 0 80px rgba(30, 45, 61, 0.04);
  animation: cardIn 0.6s ease-out;
}
@keyframes cardIn {
  from { opacity: 0; transform: translateY(24px) scale(0.97); }
  to { opacity: 1; transform: translateY(0) scale(1); }
}

/* Brand */
.brand { margin-bottom: 4px; }
.brand-logo {
  height: 52px; margin-bottom: 8px;
  filter: drop-shadow(0 4px 12px rgba(30, 45, 61, 0.12));
  animation: logoFloat 3s ease-in-out infinite;
}
@keyframes logoFloat {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-5px); }
}
.brand-name {
  font-family: var(--font-display);
  font-size: 28px; font-weight: 400;
  color: #1e2d3d;
  letter-spacing: 3px;
}
.subtitle {
  color: var(--text-secondary);
  margin-bottom: 28px; font-size: 14px;
  letter-spacing: 0.5px;
}

/* Form */
.form-group { margin-bottom: 18px; text-align: left; }
.form-group label {
  display: block; font-size: 13px; color: var(--text-secondary);
  margin-bottom: 6px; font-weight: 500;
}
.optional { font-weight: 400; color: var(--text-muted); font-size: 11px; }
.form-group input {
  width: 100%; padding: 11px 14px;
  background: #fff;
  border: 1px solid #e4e8ec;
  border-radius: 4px;
  font-size: 14px; color: var(--text); outline: none;
  transition: all 0.25s; font-family: var(--font-body);
}
.form-group input::placeholder { color: var(--text-muted); }
.form-group input:focus {
  border-color: var(--primary);
  box-shadow: 0 0 0 3px var(--primary-glow);
  background: #fff;
}
.form-group.has-error input {
  border-color: var(--error);
  box-shadow: 0 0 0 3px rgba(239, 68, 68, 0.1);
}
.field-error { color: var(--error); font-size: 12px; margin: 5px 0 0 0; }
.error-msg {
  color: var(--error); font-size: 13px; margin-bottom: 14px;
  padding: 10px; background: var(--error-bg);
  border-radius: var(--radius-xs);
}

/* Button */
.btn-primary {
  width: 100%; padding: 12px;
  background: #1e2d3d;
  color: #fff; border: none;
  border-radius: 4px;
  font-size: 15px; font-weight: 600;
  cursor: pointer; transition: all 0.3s;
  letter-spacing: 1px;
  position: relative; overflow: hidden;
}
.btn-primary::before {
  content: '';
  position: absolute; inset: 0;
  background: linear-gradient(135deg, transparent 40%, rgba(255,255,255,0.1) 50%, transparent 60%);
  transform: translateX(-100%);
  transition: transform 0.6s;
}
.btn-primary:hover::before { transform: translateX(100%); }
.btn-primary:hover {
  background: #243447;
  box-shadow: 0 4px 20px rgba(30, 45, 61, 0.25);
  transform: translateY(-1px);
}
.btn-primary:active { transform: translateY(0); }
.btn-primary:disabled {
  background: #c4cdd4; color: #fff;
  cursor: not-allowed; box-shadow: none; transform: none;
}
.btn-primary:disabled::before { display: none; }

/* Toggle */
.toggle-link {
  text-align: center; margin-top: 20px;
  font-size: 13px; color: var(--text-secondary);
}
.toggle-link a { color: var(--primary); font-weight: 500; }
.toggle-link a:hover { color: var(--primary-hover); }
</style>
