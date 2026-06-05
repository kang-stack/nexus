<template>
  <div class="message-search">
    <div class="page-title-row">
      <h1>消息检索</h1>
      <span class="page-subtitle">检索 cs_message 历史（优先 Elasticsearch，不可用时自动回退 MySQL）</span>
    </div>

    <div class="search-bar">
      <input
        v-model="keyword"
        class="text-input wide"
        placeholder="搜索消息内容（留空则列出最近消息）..."
        @keyup.enter="doSearch(0)"
      />
      <input
        v-model="conversationId"
        class="text-input"
        placeholder="会话 ID（可选）"
      />
      <select v-model="role" class="select-input">
        <option value="">全部角色</option>
        <option value="USER">用户</option>
        <option value="ASSISTANT">助手</option>
      </select>
      <button class="btn btn-primary" :disabled="loading" @click="doSearch(0)">
        {{ loading ? '检索中...' : '检索' }}
      </button>
      <router-link
        v-if="conversationId.trim()"
        :to="{ path: '/admin/turn-traces', query: { conversationId: conversationId.trim() } }"
        class="btn btn-outline trace-link"
      >
        决策轨迹
      </router-link>
    </div>

    <div v-if="warning" class="warn-hint">{{ warning }}</div>
    <div v-if="error" class="error-hint">{{ error }}</div>

    <div class="result-meta" v-if="searched">
      共 {{ total }} 条结果
      <span v-if="source" class="source-tag">{{ sourceLabel }}</span>
    </div>

    <table v-if="items.length" class="data-table">
      <thead>
        <tr>
          <th>时间</th>
          <th>会话</th>
          <th>角色</th>
          <th>来源</th>
          <th>意图</th>
          <th>内容</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in items" :key="row.id">
          <td class="col-time">{{ formatTime(row.createdAt) }}</td>
          <td class="col-conv"><code>{{ shortId(row.conversationId) }}</code></td>
          <td>{{ row.role }}</td>
          <td>{{ row.replySource || '—' }}</td>
          <td><code v-if="row.intentCode">{{ row.intentCode }}</code><span v-else>—</span></td>
          <td class="col-content">{{ row.content }}</td>
        </tr>
      </tbody>
    </table>

    <div v-else-if="searched && !loading" class="empty-hint">
      无匹配消息。请先在移动端或对话页产生会话记录后再检索。
    </div>

    <div v-if="total > size" class="pager">
      <button class="btn btn-outline btn-sm" :disabled="page <= 0 || loading" @click="doSearch(page - 1)">上一页</button>
      <span>第 {{ page + 1 }} / {{ totalPages }} 页</span>
      <button class="btn btn-outline btn-sm" :disabled="page + 1 >= totalPages || loading" @click="doSearch(page + 1)">下一页</button>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { adminApi } from '@/api/index'
import { useToast } from '@/stores/toast'

const toast = useToast()

const keyword = ref('')
const conversationId = ref('')
const role = ref('')
const page = ref(0)
const size = ref(20)
const total = ref(0)
const items = ref([])
const loading = ref(false)
const searched = ref(false)
const error = ref('')
const warning = ref('')
const source = ref('')

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size.value)))

const sourceLabel = computed(() => {
  if (source.value === 'mysql') return 'MySQL'
  if (source.value === 'elasticsearch') return 'Elasticsearch'
  return source.value
})

function formatTime(t) {
  if (!t) return '—'
  return String(t).replace('T', ' ').substring(0, 19)
}

function shortId(id) {
  if (!id) return '—'
  return id.length > 12 ? id.slice(0, 8) + '…' : id
}

async function doSearch(nextPage) {
  page.value = nextPage
  loading.value = true
  error.value = ''
  warning.value = ''
  searched.value = true
  try {
    const { data } = await adminApi.searchMessages({
      keyword: keyword.value || undefined,
      conversationId: conversationId.value || undefined,
      role: role.value || undefined,
      page: page.value,
      size: size.value
    })
    total.value = data.total || 0
    items.value = data.items || []
    source.value = data.source || ''
    if (data.warning) {
      warning.value = data.warning
    } else if (data.error && !data.fallback) {
      error.value = data.error
    }
  } catch (e) {
    toast.error('检索失败')
    error.value = e.response?.data?.message || e.message
    items.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

onMounted(() => doSearch(0))
</script>

<style scoped>
.message-search { max-width: 1200px; }
.page-title-row { margin-bottom: 20px; }
.page-title-row h1 { font-size: 22px; font-weight: 600; margin: 0 0 4px; }
.page-subtitle { color: #64748b; font-size: 13px; }

.search-bar {
  display: flex; flex-wrap: wrap; gap: 8px; align-items: center;
  margin-bottom: 16px;
}
.text-input {
  padding: 6px 10px; border: 1px solid #cbd5e1; border-radius: 4px; font-size: 13px;
}
.text-input.wide { flex: 1; min-width: 200px; }
.select-input {
  padding: 6px 10px; border: 1px solid #cbd5e1; border-radius: 4px; font-size: 13px;
}

.btn {
  padding: 6px 14px; border-radius: 4px; font-size: 13px; cursor: pointer; border: none;
}
.btn-primary { background: #0f766e; color: #fff; }
.btn-primary:disabled { opacity: 0.6; cursor: not-allowed; }
.btn-outline { background: #fff; border: 1px solid #cbd5e1; color: #334155; }
.btn-sm { padding: 4px 10px; font-size: 12px; }
.trace-link { text-decoration: none; display: inline-flex; align-items: center; }

.result-meta { font-size: 13px; color: #64748b; margin-bottom: 8px; display: flex; align-items: center; gap: 8px; }
.source-tag {
  font-size: 11px; padding: 2px 8px; border-radius: 10px;
  background: #f1f5f9; color: #475569;
}
.warn-hint { color: #b45309; font-size: 13px; margin-bottom: 8px; background: #fffbeb; padding: 8px 12px; border-radius: 4px; }
.error-hint { color: #dc2626; font-size: 13px; margin-bottom: 8px; }
.empty-hint { color: #94a3b8; font-size: 13px; padding: 24px 0; text-align: center; }

.data-table { width: 100%; border-collapse: collapse; font-size: 13px; background: #fff; border: 1px solid #e2e8f0; border-radius: 8px; overflow: hidden; }
.data-table th, .data-table td { padding: 8px 12px; border-bottom: 1px solid #f1f5f9; text-align: left; vertical-align: top; }
.data-table th { background: #f8fafc; font-weight: 600; color: #475569; }
.col-time { white-space: nowrap; width: 140px; }
.col-conv { width: 100px; }
.col-content { max-width: 420px; word-break: break-word; }
code { font-size: 12px; background: #f1f5f9; padding: 1px 5px; border-radius: 3px; }

.pager {
  display: flex; align-items: center; gap: 12px; margin-top: 16px; font-size: 13px;
}
</style>
