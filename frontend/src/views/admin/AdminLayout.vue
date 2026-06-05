<template>
  <div class="admin-layout">
    <!-- Sidebar -->
    <aside class="aside">
      <div class="aside-brand">
        <img src="/Logo.png" alt="云客猫" class="brand-logo" />
        <span class="brand-name">云客猫</span>
      </div>

      <nav class="nav">
        <router-link to="/admin/dashboard" class="nav-item">
          <svg width="16" height="16" viewBox="0 0 16 16" fill="none"><rect x="1.5" y="1.5" width="5" height="5" rx="1" stroke="currentColor" stroke-width="1.4"/><rect x="9.5" y="1.5" width="5" height="5" rx="1" stroke="currentColor" stroke-width="1.4"/><rect x="1.5" y="9.5" width="5" height="5" rx="1" stroke="currentColor" stroke-width="1.4"/><rect x="9.5" y="9.5" width="5" height="5" rx="1" stroke="currentColor" stroke-width="1.4"/></svg>
          概览
        </router-link>
        <router-link to="/admin/documents" class="nav-item">
          <svg width="16" height="16" viewBox="0 0 16 16" fill="none"><path d="M3 2h6l4 4v8a1 1 0 01-1 1H4a1 1 0 01-1-1V3a1 1 0 011-1z" stroke="currentColor" stroke-width="1.4"/><path d="M9 2v4h4" stroke="currentColor" stroke-width="1.4"/></svg>
          文档管理
        </router-link>
        <router-link to="/admin/rules" class="nav-item">
          <svg width="16" height="16" viewBox="0 0 16 16" fill="none"><path d="M2 4h4v4H2V4zM10 4h4v4h-4V4zM2 10h4v4H2v-4zM10 10h4v4h-4v-4z" stroke="currentColor" stroke-width="1.4"/></svg>
          知识分支
        </router-link>
        <router-link to="/admin/kb-config" class="nav-item">
          <svg width="16" height="16" viewBox="0 0 16 16" fill="none"><path d="M2 3h12v2H2V3zM2 7h8v2H2V7zM2 11h10v2H2v-2z" stroke="currentColor" stroke-width="1.4"/></svg>
          知识配置
        </router-link>
        <router-link to="/admin/messages" class="nav-item">
          <svg width="16" height="16" viewBox="0 0 16 16" fill="none"><path d="M2 3h12v8l-3-2H2V3z" stroke="currentColor" stroke-width="1.4"/></svg>
          消息检索
        </router-link>
        <router-link to="/admin/turn-traces" class="nav-item">
          <svg width="16" height="16" viewBox="0 0 16 16" fill="none"><path d="M2 2v12h12M5 11l2.5-3 2 1.5L12 6" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round"/></svg>
          决策轨迹
        </router-link>
        <router-link to="/admin/message-reviews" class="nav-item">
          <svg width="16" height="16" viewBox="0 0 16 16" fill="none"><path d="M2 4h12v8H2V4zM5 7h6M5 10h4" stroke="currentColor" stroke-width="1.4"/></svg>
          消息审核
        </router-link>
        <router-link to="/admin/devices" class="nav-item">
          <svg width="16" height="16" viewBox="0 0 16 16" fill="none"><circle cx="8" cy="8" r="3" stroke="currentColor" stroke-width="1.4"/><path d="M8 1v2M8 13v2M1 8h2M13 8h2M3.05 3.05l1.41 1.41M11.54 11.54l1.41 1.41M3.05 12.95l1.41-1.41M11.54 4.46l1.41-1.41" stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/></svg>
          设备管理
        </router-link>
        <router-link v-if="auth.canManage" to="/admin/accounts" class="nav-item">
          <svg width="16" height="16" viewBox="0 0 16 16" fill="none"><circle cx="6" cy="5" r="2.5" stroke="currentColor" stroke-width="1.4"/><path d="M1.5 14c0-2.5 2-4.5 4.5-4.5s4.5 2 4.5 4.5" stroke="currentColor" stroke-width="1.4"/><path d="M10.5 6a2.5 2.5 0 012.5 2.5M10.5 11h4" stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/></svg>
          账号管理
        </router-link>
      </nav>

      <div class="aside-footer">
        <router-link to="/chat" class="footer-link">
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none"><path d="M11 9.5V2.5a1 1 0 00-1-1H3a1 1 0 00-1 1v7l2.5-2H10a1 1 0 001-1z" stroke="currentColor" stroke-width="1.3"/></svg>
          返回对话
        </router-link>
      </div>
    </aside>

    <!-- Main -->
    <div class="main-area">
      <div class="content-header">
        <span class="page-title">管理后台</span>
        <div class="header-right">
          <span class="user-name">{{ auth.username }}</span>
          <span :class="['role-tag', auth.role]">{{ auth.roleLabel }}</span>
          <button class="header-btn" @click="handleLogout">退出登录</button>
        </div>
      </div>
      <main class="content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { useAdminAuthStore } from '@/stores/adminAuth'

const router = useRouter()
const auth = useAdminAuthStore()

function handleLogout() {
  auth.logout()
  router.push('/admin/login')
}
</script>

<style scoped>
.admin-layout { display: flex; height: 100vh; }

/* ====== Sidebar ====== */
.aside {
  width: 200px;
  background: linear-gradient(160deg, #1e2d3d 0%, #243447 40%, #1a3a4a 100%);
  display: flex; flex-direction: column; flex-shrink: 0;
  border-right: 1px solid rgba(255,255,255,0.06);
  position: relative; overflow: hidden;
}
.aside::before {
  content: '';
  position: absolute; inset: 0; pointer-events: none;
  background-image:
    linear-gradient(rgba(255,255,255,0.03) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255,255,255,0.03) 1px, transparent 1px);
  background-size: 48px 48px;
  mask-image: radial-gradient(ellipse 80% 70% at 40% 50%, black 30%, transparent 70%);
}
.aside::after {
  content: '';
  position: absolute; inset: 0; pointer-events: none;
  background:
    radial-gradient(circle at 20% 30%, rgba(56, 189, 248, 0.08) 0%, transparent 50%),
    radial-gradient(circle at 80% 70%, rgba(250, 204, 21, 0.04) 0%, transparent 50%);
}

.aside-brand {
  display: flex; align-items: center; gap: 10px;
  padding: 18px 16px 14px;
  position: relative; z-index: 1;
}
.brand-logo { height: 26px; width: auto; }
.brand-name {
  font-family: var(--font-display);
  font-size: 17px;
  color: #fff;
  letter-spacing: 2px;
}

/* Nav */
.nav { flex: 1; padding: 8px; position: relative; z-index: 1; }
.nav-item {
  display: flex; align-items: center; gap: 10px;
  padding: 10px 14px; font-size: 14px; color: rgba(255,255,255,0.6);
  text-decoration: none; transition: all 0.15s;
  border-radius: 2px; margin-bottom: 2px;
}
.nav-item:hover { background: rgba(255,255,255,0.05); color: rgba(255,255,255,0.85); }
.nav-item.router-link-exact-active {
  background: rgba(56, 189, 248, 0.12);
  color: #38bdf8;
  font-weight: 500;
}

.aside-footer { padding: 14px 16px; border-top: 1px solid rgba(255,255,255,0.06); position: relative; z-index: 1; }
.footer-link {
  font-size: 13px; color: rgba(255,255,255,0.4); text-decoration: none;
  transition: color 0.15s; display: flex; align-items: center; gap: 6px;
}
.footer-link:hover { color: #38bdf8; }

/* ====== Main ====== */
.main-area { flex: 1; display: flex; flex-direction: column; min-width: 0; background: #fafaf8; }

.content-header {
  height: var(--header-height); background: #fff;
  border-bottom: 1px solid #e4e8ec;
  display: flex; align-items: center; justify-content: space-between;
  padding: 0 24px; flex-shrink: 0;
}
.page-title { font-size: 15px; font-weight: 500; color: #1e2d3d; }
.header-right { display: flex; align-items: center; gap: 12px; font-size: 13px; }
.user-name { color: #1e2d3d; }

.role-tag {
  padding: 2px 10px; border-radius: 10px; font-size: 11px; font-weight: 500;
}
.role-tag.SUPER_ADMIN { background: rgba(56,189,248,0.1); color: #38bdf8; }
.role-tag.ADMIN { background: var(--success-bg); color: var(--success); }
.role-tag.OPERATOR { background: var(--info-bg); color: var(--info); }

.header-btn {
  background: none; border: 1px solid var(--border-strong);
  padding: 5px 14px; border-radius: 2px;
  font-size: 12px; color: #8899a6; cursor: pointer;
  transition: all 0.2s; font-family: var(--font-body);
}
.header-btn:hover { color: var(--error); border-color: rgba(239,68,68,0.25); background: var(--error-bg); }

.content {
  flex: 1; padding: 28px; overflow-y: auto;
}
</style>
