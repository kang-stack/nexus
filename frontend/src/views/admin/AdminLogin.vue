<template>
  <div class="admin-login">
    <!-- Left: Brand panel -->
    <div class="login-left">
      <div class="left-pattern"></div>
      <div class="left-content">
        <div class="admin-badge">ADMIN</div>
        <h1 class="system-title">云客猫</h1>
        <p class="system-subtitle">智能随身WiFi · 管理后台</p>
        <div class="feature-list">
          <div class="feature-item">
            <span class="feature-icon">○</span>
            <span>设备与用户管理</span>
          </div>
          <div class="feature-item">
            <span class="feature-icon">○</span>
            <span>知识库文档维护</span>
          </div>
          <div class="feature-item">
            <span class="feature-icon">○</span>
            <span>数据统计与监控</span>
          </div>
        </div>
      </div>
    </div>

    <!-- Right: Login form -->
    <div class="login-right">
      <div class="form-container">
        <div class="form-header">
          <h2>管理员登录</h2>
          <p>请使用管理员账号登录后台系统</p>
        </div>

        <form @submit.prevent="handleLogin" novalidate>
          <div class="field" :class="{ 'field-error': touched.username && errors.username }">
            <label>账号</label>
            <input
              v-model="form.username"
              type="text"
              placeholder="请输入管理员账号"
              autocomplete="username"
              @blur="touched.username = true; validateUsername()"
              @input="touched.username && validateUsername()"
            />
            <span v-if="touched.username && errors.username" class="err-msg">{{ errors.username }}</span>
          </div>

          <div class="field" :class="{ 'field-error': touched.password && errors.password }">
            <label>密码</label>
            <input
              v-model="form.password"
              type="password"
              placeholder="请输入密码"
              autocomplete="current-password"
              @blur="touched.password = true; validatePassword()"
              @input="touched.password && validatePassword()"
            />
            <span v-if="touched.password && errors.password" class="err-msg">{{ errors.password }}</span>
          </div>

          <p v-if="errorMsg" class="login-error">{{ errorMsg }}</p>

          <button type="submit" class="btn-login" :disabled="loading">
            <span v-if="loading" class="btn-spinner"></span>
            {{ loading ? '验证中...' : '登 录' }}
          </button>
        </form>

        <p class="back-link">
          <a @click="$router.push('/login')">← 返回用户登录</a>
        </p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useAdminAuthStore } from '@/stores/adminAuth'

const router = useRouter()
const auth = useAdminAuthStore()
const loading = ref(false)
const errorMsg = ref('')

const form = reactive({ username: '', password: '' })
const errors = reactive({})
const touched = reactive({})

function validateUsername() {
  const v = form.username
  if (!v) { errors.username = '请输入账号'; return }
  if (v.length < 2) { errors.username = '账号至少2位'; return }
  errors.username = ''
}

function validatePassword() {
  const v = form.password
  if (!v) { errors.password = '请输入密码'; return }
  if (v.length < 4) { errors.password = '密码至少4位'; return }
  errors.password = ''
}

function validateForm() {
  validateUsername(); validatePassword()
  return !errors.username && !errors.password
}

async function handleLogin() {
  if (!validateForm()) {
    touched.username = true; touched.password = true
    return
  }
  loading.value = true; errorMsg.value = ''
  try {
    await auth.login({ username: form.username, password: form.password })
    router.push('/admin/dashboard')
  } catch (e) {
    errorMsg.value = e.response?.data?.error || '登录失败，请重试'
  } finally { loading.value = false }
}
</script>

<style scoped>
/* ====== Layout ====== */
.admin-login {
  display: flex; min-height: 100vh;
  font-family: var(--font-body);
}

/* ====== Left Panel ====== */
.login-left {
  flex: 0 0 42%;
  background: linear-gradient(160deg, #1e2d3d 0%, #243447 40%, #1a3a4a 100%);
  position: relative; overflow: hidden;
  display: flex; align-items: center; justify-content: center;
  color: #fff;
}
.left-pattern {
  position: absolute; inset: 0;
  background-image:
    linear-gradient(rgba(255,255,255,0.03) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255,255,255,0.03) 1px, transparent 1px);
  background-size: 48px 48px;
  mask-image: radial-gradient(ellipse 80% 70% at 40% 50%, black 30%, transparent 70%);
}
.left-pattern::after {
  content: '';
  position: absolute; inset: 0;
  background:
    radial-gradient(circle at 20% 30%, rgba(56, 189, 248, 0.12) 0%, transparent 50%),
    radial-gradient(circle at 80% 70%, rgba(250, 204, 21, 0.08) 0%, transparent 50%);
}
.left-content {
  position: relative; z-index: 1;
  text-align: center; padding: 40px;
}
.admin-badge {
  display: inline-block;
  font-size: 11px; font-weight: 700;
  letter-spacing: 4px;
  padding: 5px 14px;
  border: 1px solid rgba(255,255,255,0.25);
  border-radius: 2px;
  margin-bottom: 28px;
  color: rgba(255,255,255,0.7);
}
.system-title {
  font-family: var(--font-display);
  font-size: 38px; font-weight: 400;
  letter-spacing: 6px;
  margin-bottom: 8px;
  color: #fff;
}
.system-subtitle {
  font-size: 14px;
  color: rgba(255,255,255,0.5);
  letter-spacing: 1px;
  margin-bottom: 44px;
}
.feature-list {
  text-align: left;
  display: inline-flex; flex-direction: column; gap: 14px;
}
.feature-item {
  display: flex; align-items: center; gap: 12px;
  font-size: 14px; color: rgba(255,255,255,0.6);
}
.feature-icon {
  width: 7px; height: 7px;
  border: 1.5px solid rgba(56, 189, 248, 0.6);
  border-radius: 50%;
  flex-shrink: 0;
}

/* ====== Right Panel ====== */
.login-right {
  flex: 1;
  display: flex; align-items: center; justify-content: center;
  background: #fafaf8;
}
.form-container {
  width: 380px; max-width: 88vw;
}
.form-header {
  margin-bottom: 36px;
}
.form-header h2 {
  font-family: var(--font-display);
  font-size: 26px; font-weight: 400;
  color: #1e2d3d;
  letter-spacing: 2px;
  margin-bottom: 8px;
}
.form-header p {
  font-size: 13px; color: #8899a6;
  letter-spacing: 0.5px;
}

/* Fields */
.field {
  margin-bottom: 22px;
}
.field label {
  display: block;
  font-size: 12px; font-weight: 600;
  color: #4a5c6c;
  margin-bottom: 6px;
  text-transform: uppercase;
  letter-spacing: 1px;
}
.field input {
  width: 100%;
  padding: 12px 14px;
  background: #fff;
  border: 1px solid #e4e8ec;
  border-radius: 4px;
  font-size: 14px; color: #1e2d3d;
  outline: none; transition: all 0.25s;
  font-family: var(--font-body);
}
.field input::placeholder { color: #bcc5cd; }
.field input:focus {
  border-color: #38bdf8;
  box-shadow: 0 0 0 3px rgba(56, 189, 248, 0.1);
}
.field-error input {
  border-color: #ef4444;
  box-shadow: 0 0 0 3px rgba(239, 68, 68, 0.06);
}
.err-msg {
  display: block;
  font-size: 12px; color: #ef4444;
  margin-top: 6px;
}

/* Error */
.login-error {
  font-size: 13px; color: #ef4444;
  padding: 10px 14px;
  background: rgba(239, 68, 68, 0.06);
  border-radius: 4px;
  margin-bottom: 18px;
  border-left: 3px solid #ef4444;
}

/* Button */
.btn-login {
  width: 100%;
  padding: 13px;
  background: #1e2d3d;
  color: #fff;
  border: none;
  border-radius: 4px;
  font-size: 14px; font-weight: 600;
  cursor: pointer;
  transition: all 0.25s;
  letter-spacing: 4px;
  font-family: var(--font-body);
  display: flex; align-items: center; justify-content: center; gap: 8px;
}
.btn-login:hover {
  background: #243447;
  box-shadow: 0 4px 20px rgba(30, 45, 61, 0.25);
}
.btn-login:active { transform: scale(0.99); }
.btn-login:disabled {
  background: #c4cdd4; color: #fff;
  cursor: not-allowed; box-shadow: none; transform: none;
}
.btn-spinner {
  width: 16px; height: 16px;
  border: 2px solid rgba(255,255,255,0.3);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

/* Back link */
.back-link {
  text-align: center; margin-top: 24px;
}
.back-link a {
  font-size: 12px; color: #8899a6;
  cursor: pointer; text-decoration: none;
  transition: color 0.2s;
  letter-spacing: 0.5px;
}
.back-link a:hover { color: #4a5c6c; }
</style>
