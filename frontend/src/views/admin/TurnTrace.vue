<template>
  <div class="turn-trace">
    <div class="page-title-row">
      <h1>决策轨迹</h1>
      <span class="page-subtitle">全平台用户输入按时间倒序展示，点击左侧条目查看 Skill 链详情</span>
    </div>

    <div class="filter-bar">
      <input
        v-model="conversationFilter"
        class="text-input"
        placeholder="筛选会话 ID（可选）"
        @keyup.enter="reload(0)"
      />
      <input
        v-model="turnIdQuery"
        class="text-input"
        placeholder="turnId 定位（可选）"
        @keyup.enter="jumpByTurnId"
      />
      <button class="btn btn-primary" :disabled="loading" @click="turnIdQuery.trim() ? jumpByTurnId() : reload(0)">
        {{ loading ? '加载中...' : '刷新' }}
      </button>
      <button
        v-if="conversationFilter.trim()"
        class="btn btn-outline"
        :disabled="loading"
        @click="clearConversationFilter"
      >
        清除筛选
      </button>
    </div>

    <div v-if="error" class="error-hint">{{ error }}</div>

    <div class="workspace">
      <aside class="trace-sidebar">
        <div class="sidebar-head">
          <span class="sidebar-title">用户输入</span>
          <span v-if="total > 0" class="sidebar-count">共 {{ total }} 条</span>
        </div>

        <div v-if="loading && !items.length" class="sidebar-loading">加载中...</div>

        <ul v-else-if="items.length" class="turn-list">
          <li
            v-for="row in items"
            :key="row.turnId"
            :class="['turn-item', { active: selectedTurnId === row.turnId }]"
            @click="selectTurn(row.turnId)"
          >
            <div class="turn-item-time">{{ formatTime(row.createdAt) }}</div>
            <div v-if="row.username || row.userId" class="turn-item-user">
              <span v-if="row.username" class="user-name">{{ row.username }}</span>
              <code v-if="row.userId" class="user-biz-id" :title="row.userId">{{ shortId(row.userId) }}</code>
            </div>
            <div class="turn-item-text">{{ row.userText || '（无用户文本）' }}</div>
            <div class="turn-item-meta">
              <code class="turn-id" :title="row.turnId">{{ shortId(row.turnId) }}</code>
              <span v-if="row.outcome" :class="['outcome-dot', row.outcome]">{{ row.outcome }}</span>
            </div>
            <div v-if="showConvInList" class="turn-item-conv" :title="row.conversationId">
              会话 {{ shortId(row.conversationId) }}
            </div>
          </li>
        </ul>

        <div v-else-if="!loading" class="sidebar-empty">暂无决策轨迹记录</div>

        <div v-if="total > pageSize" class="sidebar-pager">
          <Pagination
            :page-num="page + 1"
            :page-size="pageSize"
            :total="total"
            @change="onPageChange"
          />
        </div>
      </aside>

      <main class="trace-main">
        <div v-if="detailLoading" class="main-placeholder">加载轨迹详情...</div>
        <div v-else-if="selectedDetail" class="detail-panel">
          <TraceDetail :trace="selectedDetail" />
        </div>
        <div v-else class="main-placeholder">请在左侧选择一条用户输入查看决策轨迹</div>
      </main>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { adminApi } from '@/api/index'
import { useToast } from '@/stores/toast'
import TraceDetail from '@/components/admin/TraceDetail.vue'
import Pagination from '@/components/Pagination.vue'

const route = useRoute()
const router = useRouter()
const toast = useToast()

const conversationFilter = ref(route.query.conversationId || '')
const turnIdQuery = ref(route.query.turnId || '')
const items = ref([])
const total = ref(0)
const page = ref(0)
const pageSize = 15
const selectedTurnId = ref('')
const selectedDetail = ref(null)
const loading = ref(false)
const detailLoading = ref(false)
const error = ref('')

const showConvInList = computed(() => !conversationFilter.value.trim())

function formatTime(t) {
  if (!t) return '—'
  return String(t).replace('T', ' ').substring(0, 19)
}

function shortId(id) {
  if (!id) return '—'
  return id.length > 12 ? id.slice(0, 10) + '…' : id
}

function buildListParams() {
  const params = { page: page.value, size: pageSize }
  const conv = conversationFilter.value.trim()
  if (conv) params.conversationId = conv
  return params
}

function syncQuery() {
  const q = {}
  const conv = conversationFilter.value.trim()
  if (conv) q.conversationId = conv
  if (turnIdQuery.value.trim()) q.turnId = turnIdQuery.value.trim()
  if (page.value > 0) q.page = String(page.value)
  router.replace({ query: q })
}

async function reload(pageIndex = 0, autoSelectFirst = true) {
  loading.value = true
  error.value = ''
  page.value = pageIndex
  try {
    const { data } = await adminApi.listTurnTraces(buildListParams())
    items.value = data.items || []
    total.value = data.total ?? 0
    syncQuery()

    const stillOnPage = items.value.some((r) => r.turnId === selectedTurnId.value)
    if (autoSelectFirst && items.value.length) {
      const prefer = turnIdQuery.value.trim()
      const hit = prefer && items.value.find((r) => r.turnId === prefer)
      await selectTurn(hit ? hit.turnId : items.value[0].turnId)
    } else if (!stillOnPage) {
      selectedTurnId.value = ''
      selectedDetail.value = null
    }
  } catch (e) {
    error.value = e.response?.data?.message || e.message || '加载失败'
    items.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function onPageChange(pageNum) {
  reload(pageNum - 1, false)
}

function clearConversationFilter() {
  conversationFilter.value = ''
  reload(0)
}

async function selectTurn(turnId) {
  if (!turnId) return
  if (selectedTurnId.value === turnId && selectedDetail.value && !detailLoading.value) {
    return
  }
  selectedTurnId.value = turnId
  detailLoading.value = true
  error.value = ''
  try {
    const { data } = await adminApi.getTurnTrace(turnId)
    if (data.error) {
      toast.error(data.error)
      selectedDetail.value = null
      return
    }
    selectedDetail.value = data
  } catch {
    toast.error('加载详情失败')
    selectedDetail.value = null
  } finally {
    detailLoading.value = false
  }
}

async function jumpByTurnId() {
  const id = turnIdQuery.value.trim()
  if (!id) {
    return reload(0)
  }
  detailLoading.value = true
  error.value = ''
  try {
    const { data } = await adminApi.getTurnTrace(id)
    if (data.error) {
      error.value = data.error
      toast.error(data.error)
      return
    }
    selectedTurnId.value = id
    selectedDetail.value = data
    if (data.conversationId) {
      conversationFilter.value = data.conversationId
    }
    await reload(0, false)
    if (!items.value.some((r) => r.turnId === id)) {
      selectedTurnId.value = id
      selectedDetail.value = data
    } else {
      selectedTurnId.value = id
    }
    syncQuery()
  } catch (e) {
    error.value = e.response?.data?.message || e.message || '定位失败'
  } finally {
    detailLoading.value = false
  }
}

onMounted(() => {
  const p = parseInt(route.query.page, 10)
  const pageIndex = Number.isFinite(p) && p >= 0 ? p : 0
  if (turnIdQuery.value.trim()) {
    jumpByTurnId()
  } else {
    reload(pageIndex)
  }
})
</script>

<style scoped>
.turn-trace {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 120px);
  min-height: 480px;
  max-width: none;
}
.page-title-row { margin-bottom: 12px; flex-shrink: 0; }
.page-title-row h1 { font-size: 22px; font-weight: 600; margin: 0 0 4px; }
.page-subtitle { color: #64748b; font-size: 13px; }

.filter-bar {
  display: flex; flex-wrap: wrap; gap: 8px; align-items: center;
  margin-bottom: 12px; flex-shrink: 0;
}
.text-input {
  padding: 6px 10px; border: 1px solid #cbd5e1; border-radius: 4px; font-size: 13px;
  min-width: 160px;
}

.btn {
  padding: 6px 14px; border-radius: 4px; font-size: 13px; cursor: pointer; border: none;
}
.btn-primary { background: #0f766e; color: #fff; }
.btn-primary:disabled { opacity: 0.6; cursor: not-allowed; }
.btn-outline { background: #fff; border: 1px solid #cbd5e1; color: #334155; }
.btn-outline:disabled { opacity: 0.6; cursor: not-allowed; }

.error-hint { color: #dc2626; font-size: 13px; margin-bottom: 8px; flex-shrink: 0; }

.workspace {
  flex: 1;
  display: flex;
  min-height: 0;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #fff;
  overflow: hidden;
}

.trace-sidebar {
  width: 320px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  border-right: 1px solid #e2e8f0;
  background: #f8fafc;
}

.sidebar-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  border-bottom: 1px solid #e2e8f0;
  flex-shrink: 0;
}
.sidebar-title { font-size: 13px; font-weight: 600; color: #334155; }
.sidebar-count { font-size: 12px; color: #64748b; }

.turn-list {
  list-style: none;
  margin: 0;
  padding: 6px 0;
  overflow-y: auto;
  flex: 1;
  min-height: 0;
}

.turn-item {
  padding: 10px 14px;
  cursor: pointer;
  border-bottom: 1px solid #f1f5f9;
  transition: background 0.15s;
}
.turn-item:hover { background: #f1f5f9; }
.turn-item.active {
  background: #ecfdf5;
  border-left: 3px solid #0f766e;
  padding-left: 11px;
}

.turn-item-time {
  font-size: 11px;
  color: #64748b;
  margin-bottom: 4px;
}
.turn-item-user {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
  flex-wrap: wrap;
}
.user-name {
  font-size: 12px;
  font-weight: 600;
  color: #0f766e;
}
.user-biz-id {
  font-size: 10px;
  background: #e2e8f0;
  padding: 0 4px;
  border-radius: 3px;
  color: #64748b;
}
.turn-item-text {
  font-size: 13px;
  color: #1e293b;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  word-break: break-word;
}
.turn-item-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  margin-top: 6px;
}
.turn-item-conv {
  font-size: 11px;
  color: #94a3b8;
  margin-top: 4px;
}
.turn-id {
  font-size: 11px;
  background: #e2e8f0;
  padding: 1px 5px;
  border-radius: 3px;
  color: #475569;
}

.outcome-dot {
  font-size: 10px;
  font-weight: 600;
  padding: 1px 6px;
  border-radius: 8px;
  flex-shrink: 0;
}
.outcome-dot.BRANCH { background: #dcfce7; color: #166534; }
.outcome-dot.RAG { background: #fef3c7; color: #92400e; }
.outcome-dot.CHITCHAT { background: #dbeafe; color: #1e40af; }
.outcome-dot.ERROR { background: #fee2e2; color: #991b1b; }
.outcome-dot.DUPLICATE { background: #f1f5f9; color: #475569; }

.sidebar-loading,
.sidebar-empty {
  padding: 24px 14px;
  font-size: 13px;
  color: #94a3b8;
  text-align: center;
}

.sidebar-pager {
  flex-shrink: 0;
  padding: 8px 4px 12px;
  border-top: 1px solid #e2e8f0;
  background: #f8fafc;
}
.sidebar-pager :deep(.pagination) {
  margin-top: 0;
  flex-wrap: wrap;
  gap: 6px;
}
.sidebar-pager :deep(.page-info) { display: none; }
.sidebar-pager :deep(.page-btns button) {
  min-width: 28px;
  height: 28px;
  font-size: 12px;
}

.trace-main {
  flex: 1;
  min-width: 0;
  overflow-y: auto;
  padding: 16px 20px;
}

.detail-panel { min-height: 100%; }

.main-placeholder {
  color: #94a3b8;
  font-size: 14px;
  padding: 48px 24px;
  text-align: center;
}
</style>
