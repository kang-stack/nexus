<template>
  <div class="message-review">
    <div class="page-title-row">
      <h1>消息审核</h1>
      <span class="page-subtitle">情绪高风险 AI 回复的抽检与处置</span>
    </div>

    <div class="info-banner">
      当用户情绪被识别为高风险时，系统会将对应 AI 回复标记为待审核（FLAGGED）。运营人员可在此通过或驳回，并填写备注。
    </div>

    <div class="filter-row">
      <select v-model="statusFilter" @change="load(0)">
        <option value="">全部状态</option>
        <option value="FLAGGED">待处理</option>
        <option value="APPROVED">已通过</option>
        <option value="REJECTED">已驳回</option>
      </select>
      <button class="btn btn-outline btn-sm" @click="load(page)">刷新</button>
    </div>

    <div v-if="loading" class="empty-hint">加载中...</div>
    <table v-else-if="items.length" class="data-table">
      <thead>
        <tr>
          <th>时间</th><th>会话</th><th>消息</th><th>内容</th><th>情绪</th><th>风险</th><th>状态</th><th>备注</th><th></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in items" :key="row.id">
          <td>{{ formatTime(row.createdAt) }}</td>
          <td><code>{{ shortId(row.conversationId) }}</code></td>
          <td><code>{{ shortId(row.messageId) }}</code></td>
          <td class="col-content">{{ row.messageContent || '—' }}</td>
          <td>{{ row.emotionTag || '—' }}</td>
          <td>{{ row.riskLevel || '—' }}</td>
          <td><span :class="['status-badge', row.reviewStatus]">{{ statusText(row.reviewStatus) }}</span></td>
          <td>
            <input v-model="row._note" class="text-input" placeholder="审核备注" :disabled="row.reviewStatus !== 'FLAGGED'" />
          </td>
          <td class="actions">
            <button
              v-if="row.reviewStatus === 'FLAGGED' && auth.canManage"
              class="action-btn edit"
              :disabled="row._saving"
              @click="decide(row, 'APPROVED')"
            >通过</button>
            <button
              v-if="row.reviewStatus === 'FLAGGED' && auth.canManage"
              class="action-btn danger"
              :disabled="row._saving"
              @click="decide(row, 'REJECTED')"
            >驳回</button>
            <span v-if="row.reviewStatus !== 'FLAGGED'" class="muted">已处理</span>
          </td>
        </tr>
      </tbody>
    </table>

    <div v-else class="empty-panel">
      <p class="empty-title">暂无审核记录</p>
      <p class="empty-desc">
        列表为空通常表示尚未产生高风险情绪会话。可在移动端对话中触发情绪检测后，待审核项会自动出现在此处。
      </p>
    </div>

    <div v-if="total > size" class="pager">
      <button class="btn btn-outline btn-sm" :disabled="page <= 0" @click="load(page - 1)">上一页</button>
      <span>第 {{ page + 1 }} 页 / 共 {{ total }} 条</span>
      <button class="btn btn-outline btn-sm" :disabled="(page + 1) * size >= total" @click="load(page + 1)">下一页</button>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { adminApi } from '@/api/index'
import { useAdminAuthStore } from '@/stores/adminAuth'
import { useToast } from '@/stores/toast'

const toast = useToast()
const auth = useAdminAuthStore()

const statusFilter = ref('FLAGGED')
const items = ref([])
const loading = ref(false)
const page = ref(0)
const size = ref(20)
const total = ref(0)

function formatTime(t) {
  if (!t) return '—'
  return String(t).replace('T', ' ').substring(0, 19)
}

function shortId(id) {
  if (!id) return '—'
  return id.length > 12 ? id.slice(0, 8) + '…' : id
}

function statusText(s) {
  const map = { FLAGGED: '待处理', APPROVED: '已通过', REJECTED: '已驳回' }
  return map[s] || s
}

async function load(p) {
  page.value = p
  loading.value = true
  try {
    const params = { page: p, size: size.value }
    if (statusFilter.value) params.status = statusFilter.value
    const { data } = await adminApi.listMessageReviews(params)
    items.value = (data.items || []).map(r => ({ ...r, _note: r.reviewNote || '', _saving: false }))
    total.value = data.total ?? 0
  } catch {
    toast.error('加载审核列表失败')
  } finally {
    loading.value = false
  }
}

async function decide(row, status) {
  row._saving = true
  try {
    const { data } = await adminApi.decideMessageReview(row.id, {
      reviewStatus: status,
      reviewNote: row._note
    })
    Object.assign(row, data, { _note: data.reviewNote || '', _saving: false })
    toast.success(status === 'APPROVED' ? '已通过' : '已驳回')
  } catch {
    toast.error('操作失败')
    row._saving = false
  }
}

onMounted(() => load(0))
</script>

<style scoped>
.message-review { max-width: 1200px; }
.page-title-row { margin-bottom: 12px; }
.page-title-row h1 { font-size: 22px; margin: 0 0 4px; }
.page-subtitle { color: #64748b; font-size: 13px; }
.info-banner {
  background: #fff7ed; border: 1px solid #fed7aa; color: #9a3412;
  padding: 10px 14px; border-radius: 6px; font-size: 13px; margin-bottom: 12px;
}
.filter-row { display: flex; gap: 8px; margin-bottom: 12px; align-items: center; }
.filter-row select {
  padding: 4px 8px; border: 1px solid #cbd5e1; border-radius: 4px; font-size: 13px;
}
.data-table { width: 100%; border-collapse: collapse; font-size: 13px; background: #fff; border: 1px solid #e2e8f0; border-radius: 8px; }
.data-table th, .data-table td {
  padding: 8px 10px; text-align: left; border-bottom: 1px solid #f1f5f9;
}
.data-table th { background: #f8fafc; color: #64748b; }
.text-input { width: 100%; padding: 4px 8px; border: 1px solid #cbd5e1; border-radius: 4px; font-size: 12px; }
.actions { white-space: nowrap; }
.action-btn {
  padding: 3px 8px; font-size: 12px; margin-right: 4px; border-radius: 4px; cursor: pointer; border: 1px solid #cbd5e1; background: #fff;
}
.action-btn.edit { color: #0f766e; border-color: #99f6e4; }
.action-btn.danger { color: #b91c1c; border-color: #fecaca; }
.btn { padding: 6px 12px; border-radius: 4px; font-size: 13px; cursor: pointer; }
.btn-sm { padding: 4px 10px; font-size: 12px; }
.btn-outline { background: #fff; border: 1px solid #cbd5e1; }
.empty-hint { color: #94a3b8; padding: 12px 0; }
.empty-panel {
  text-align: center; padding: 48px 24px; background: #fff;
  border: 1px dashed #cbd5e1; border-radius: 8px;
}
.empty-title { font-size: 15px; color: #334155; margin-bottom: 8px; }
.empty-desc { font-size: 13px; color: #94a3b8; max-width: 480px; margin: 0 auto; line-height: 1.6; }
.status-badge {
  font-size: 11px; padding: 2px 8px; border-radius: 10px; font-weight: 500;
}
.status-badge.FLAGGED { background: #fef3c7; color: #b45309; }
.status-badge.APPROVED { background: #d1fae5; color: #047857; }
.status-badge.REJECTED { background: #fee2e2; color: #b91c1c; }
.muted { font-size: 12px; color: #94a3b8; }
.pager { margin-top: 12px; display: flex; gap: 12px; align-items: center; font-size: 13px; }
code { font-size: 12px; background: #f1f5f9; padding: 1px 5px; border-radius: 3px; }
.col-content { max-width: 280px; word-break: break-word; }
</style>
