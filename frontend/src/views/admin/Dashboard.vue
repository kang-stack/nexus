<template>
  <div class="dashboard">
    <div class="page-title-row">
      <h1>管理概览</h1>
      <span class="page-subtitle">平台运行状态一览</span>
    </div>

    <div class="stats">
      <div class="stat-card" v-for="(card, idx) in statCards" :key="card.label" :style="{ animationDelay: `${idx * 0.06}s` }">
        <div class="stat-icon-wrap" :style="{ background: card.glow }">
          <span class="stat-icon">{{ card.icon }}</span>
        </div>
        <div class="stat-num">{{ loading ? '—' : card.value }}</div>
        <div class="stat-label">{{ card.label }}</div>
      </div>
    </div>

    <h2 class="section-title">运营功能</h2>
    <div class="quick-links">
      <router-link to="/admin/kb-config" class="quick-card">
        <span class="quick-icon">⚙️</span>
        <span class="quick-label">知识配置</span>
        <span class="quick-desc">DMN、寒暄、一键服务、人工路由</span>
      </router-link>
      <router-link to="/admin/messages" class="quick-card">
        <span class="quick-icon">🔍</span>
        <span class="quick-label">消息检索</span>
        <span class="quick-desc">按关键词或会话 ID 查历史消息</span>
      </router-link>
      <router-link to="/admin/turn-traces" class="quick-card">
        <span class="quick-icon">🔀</span>
        <span class="quick-label">决策轨迹</span>
        <span class="quick-desc">Skill 链命中路径、分支 vs RAG 复盘</span>
      </router-link>
      <router-link to="/admin/message-reviews" class="quick-card">
        <span class="quick-icon">✅</span>
        <span class="quick-label">消息审核</span>
        <span class="quick-desc">高风险情绪回复抽检</span>
      </router-link>
      <router-link to="/admin/rules" class="quick-card">
        <span class="quick-icon">📋</span>
        <span class="quick-label">规则管理</span>
        <span class="quick-desc">意图分支与 Excel 导入</span>
      </router-link>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { adminApi } from '@/api'

const loading = ref(true)
const docCount = ref(0)
const deviceCount = ref(0)

const statCards = computed(() => [
  { icon: '📄', label: '知识文档', value: loading.value ? '—' : docCount.value, glow: 'rgba(56,189,248,0.08)' },
  { icon: '📡', label: 'WiFi 设备', value: loading.value ? '—' : deviceCount.value, glow: 'rgba(30,45,61,0.08)' },
  { icon: '💬', label: '今日咨询', value: '—', glow: 'rgba(16,185,129,0.08)' },
  { icon: '🟢', label: '活跃会话', value: '—', glow: 'rgba(245,158,11,0.08)' }
])

onMounted(async () => {
  try {
    const [dr, devr] = await Promise.all([
      adminApi.listDocuments(1, 1),
      adminApi.listDevices(1, 1)
    ])
    docCount.value = dr.data.total || 0
    deviceCount.value = devr.data.total || 0
  } catch { /* keep defaults */ }
  finally { loading.value = false }
})
</script>

<style scoped>
.page-title-row { margin-bottom: 28px; }
.page-title-row h1 {
  font-family: var(--font-display);
  font-size: 26px; font-weight: 400; color: var(--text);
  letter-spacing: 1px; margin-bottom: 4px;
}
.page-subtitle { font-size: 13px; color: var(--text-muted); }

.stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 18px;
}

.stat-card {
  background: #fff;
  border: 1px solid var(--border);
  padding: 28px 24px;
  border-radius: var(--radius);
  transition: all 0.3s;
  animation: cardIn 0.5s ease-out both;
  position: relative;
  overflow: hidden;
}
.stat-card::before {
  content: '';
  position: absolute; top: 0; left: 0; right: 0; height: 2px;
  background: linear-gradient(90deg, transparent, var(--primary), var(--accent), transparent);
  opacity: 0; transition: opacity 0.3s;
}
.stat-card:hover::before { opacity: 1; }
.stat-card:hover {
  border-color: var(--border-strong);
  box-shadow: var(--shadow-md);
  transform: translateY(-2px);
}

@keyframes cardIn {
  from { opacity: 0; transform: translateY(16px); }
  to { opacity: 1; transform: translateY(0); }
}

.stat-icon-wrap {
  width: 44px; height: 44px; border-radius: 12px;
  display: flex; align-items: center; justify-content: center;
  margin-bottom: 14px;
}
.stat-icon { font-size: 22px; }
.stat-num {
  font-size: 34px; font-weight: 700;
  color: #1e2d3d;
  margin-bottom: 2px;
}
.stat-label { font-size: 13px; color: #4a5c6c; }

.section-title {
  font-size: 16px; font-weight: 600; color: #1e2d3d;
  margin: 32px 0 14px;
}
.quick-links {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 14px;
}
.quick-card {
  display: flex; flex-direction: column; gap: 4px;
  padding: 18px 20px; background: #fff; border: 1px solid var(--border);
  border-radius: var(--radius); text-decoration: none; color: inherit;
  transition: all 0.2s;
}
.quick-card:hover {
  border-color: var(--primary);
  box-shadow: var(--shadow-md);
  transform: translateY(-2px);
}
.quick-icon { font-size: 22px; margin-bottom: 4px; }
.quick-label { font-size: 15px; font-weight: 600; color: #1e2d3d; }
.quick-desc { font-size: 12px; color: #8899a6; }
</style>
