<template>
  <div class="toast-container">
    <transition-group name="toast">
      <div
        v-for="toast in toasts"
        :key="toast.id"
        :class="['toast-item', toast.type]"
      >
        <span class="toast-icon">{{ iconMap[toast.type] }}</span>
        <span class="toast-msg">{{ toast.message }}</span>
        <button class="toast-close" @click="remove(toast.id)">&times;</button>
      </div>
    </transition-group>
  </div>
</template>

<script setup>
import { useToast } from '@/stores/toast'

const { toasts, remove } = useToast()

const iconMap = {
  success: '✓',
  error: '✕',
  warning: '⚠',
  info: 'ℹ'
}
</script>

<style scoped>
.toast-container {
  position: fixed; top: 24px; right: 24px; z-index: 9999;
  display: flex; flex-direction: column; gap: 10px; max-width: 380px;
}
.toast-item {
  display: flex; align-items: center; gap: 12px;
  padding: 14px 18px; border-radius: var(--radius);
  font-size: 14px; color: #fff;
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(255,255,255,0.15);
  box-shadow: 0 8px 32px rgba(0,0,0,0.12);
  pointer-events: auto;
}
.toast-item.success { background: rgba(16, 185, 129, 0.9); }
.toast-item.error { background: rgba(239, 68, 68, 0.9); }
.toast-item.warning { background: rgba(245, 158, 11, 0.9); }
.toast-item.info { background: rgba(91, 156, 245, 0.9); }

.toast-icon { flex-shrink: 0; font-weight: 700; font-size: 16px; width: 22px; text-align: center; }
.toast-msg { flex: 1; line-height: 1.4; }
.toast-close {
  background: none; border: none; color: rgba(255,255,255,0.6);
  cursor: pointer; font-size: 20px; padding: 0 2px; line-height: 1;
  transition: color 0.15s; flex-shrink: 0;
}
.toast-close:hover { color: #fff; }

.toast-enter-active { transition: all 0.35s cubic-bezier(0.16, 1, 0.3, 1); }
.toast-leave-active { transition: all 0.2s ease; }
.toast-enter-from { opacity: 0; transform: translateX(60px) scale(0.9); }
.toast-leave-to { opacity: 0; transform: translateX(60px) scale(0.9); }
</style>
