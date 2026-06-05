<template>
  <div class="navbar">
    <span class="nav-user">{{ auth.username }}</span>
    <span class="nav-title">智能客服</span>
    <div class="nav-right">
      <button class="nav-btn" @click="$emit('transfer')">人工</button>
      <button class="nav-btn nav-logout" @click="handleLogout">退出</button>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { usePortalAuthStore } from '@/stores/portalAuth'

defineEmits(['transfer'])

const router = useRouter()
const auth = usePortalAuthStore()

function handleLogout() {
  auth.logout()
  router.push('/login')
}
</script>

<style scoped>
.navbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 44px;
  padding: 0 12px;
  background: #fff;
  border-bottom: 1px solid #eee;
  position: sticky;
  top: 0;
  z-index: 10;
}
.nav-user {
  font-size: 13px;
  color: #888;
  max-width: 80px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.nav-title {
  font-size: 16px;
  font-weight: 600;
  color: #333;
}
.nav-btn {
  background: none;
  border: none;
  font-size: 14px;
  color: #333;
  padding: 6px 8px;
  cursor: pointer;
}
.nav-btn:active {
  opacity: 0.6;
}
.nav-logout {
  color: #999;
  font-size: 13px;
}
.nav-right {
  display: flex;
  gap: 4px;
}
</style>
