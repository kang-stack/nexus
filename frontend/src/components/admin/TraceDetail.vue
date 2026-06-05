<template>
  <div class="trace-detail" v-if="trace">
    <div class="summary-grid">
      <div class="summary-item">
        <span class="label">turnId</span>
        <code>{{ trace.turnId }}</code>
      </div>
      <div class="summary-item">
        <span class="label">用户</span>
        <span>{{ trace.username || '—' }}</span>
      </div>
      <div class="summary-item">
        <span class="label">用户 ID</span>
        <code>{{ trace.userId || '—' }}</code>
      </div>
      <div class="summary-item">
        <span class="label">会话</span>
        <code>{{ trace.conversationId }}</code>
      </div>
      <div class="summary-item">
        <span class="label">路径</span>
        <span :class="['outcome-badge', trace.outcome]">{{ trace.outcome }}</span>
      </div>
      <div class="summary-item">
        <span class="label">意图</span>
        <span>{{ trace.resolvedIntent || '—' }}</span>
      </div>
      <div class="summary-item">
        <span class="label">根因</span>
        <span>{{ trace.primaryRootCause || '—' }}</span>
      </div>
      <div class="summary-item">
        <span class="label">耗时</span>
        <span>{{ trace.totalCostMs != null ? trace.totalCostMs + ' ms' : '—' }}</span>
      </div>
    </div>

    <div v-if="trace.userText" class="user-text">
      <span class="label">用户输入</span>
      <p>{{ trace.userText }}</p>
    </div>

    <h3 class="section-title">
      Skill 链步骤
      <span v-if="traceUserLabel" class="trace-user-label">{{ traceUserLabel }}</span>
    </h3>
    <div v-if="skills.length" class="skill-timeline">
      <div
        v-for="(step, idx) in skills"
        :key="idx"
        :class="['skill-step', step.action === 'SKIP' ? 'skip' : 'run']"
      >
        <div class="step-head">
          <span class="step-name">{{ step.name }}</span>
          <span class="step-order">order={{ step.order }}</span>
          <span v-if="step.costMs != null" class="step-cost">{{ step.costMs }}ms</span>
          <span :class="['step-outcome', isHit(step.outcome) ? 'hit' : '']">{{ step.outcome }}</span>
        </div>
        <pre v-if="step.detail && Object.keys(step.detail).length" class="step-detail">{{ formatDetail(step.detail) }}</pre>
      </div>
    </div>
    <div v-else class="empty-skills">无 Skill 步骤记录</div>

    <details v-if="traceRaw" class="raw-json">
      <summary>原始 trace JSON</summary>
      <pre>{{ traceRaw }}</pre>
    </details>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  trace: { type: Object, required: true }
})

const skills = computed(() => {
  const t = props.trace?.trace
  if (!t || !Array.isArray(t.skills)) return []
  return t.skills
})

const traceUserLabel = computed(() => {
  const name = props.trace?.username
  const id = props.trace?.userId
  if (name && id) return `（${name} / ${id}）`
  if (name) return `（${name}）`
  if (id) return `（${id}）`
  const t = props.trace?.trace
  if (t?.username && t?.userId) return `（${t.username} / ${t.userId}）`
  return ''
})

const traceRaw = computed(() => {
  const t = props.trace?.trace
  if (!t) return props.trace?.traceRaw ? JSON.stringify(props.trace.traceRaw, null, 2) : ''
  return JSON.stringify(t, null, 2)
})

function isHit(outcome) {
  if (!outcome) return false
  return String(outcome).startsWith('HIT')
}

function formatDetail(detail) {
  return JSON.stringify(detail, null, 2)
}
</script>

<style scoped>
.summary-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 12px; margin-bottom: 16px;
}
.summary-item { font-size: 13px; }
.summary-item .label {
  display: block; font-size: 11px; color: #64748b; margin-bottom: 2px;
}
code { font-size: 12px; background: #f1f5f9; padding: 1px 5px; border-radius: 3px; word-break: break-all; }

.outcome-badge {
  display: inline-block; font-size: 11px; font-weight: 600;
  padding: 2px 8px; border-radius: 10px;
}
.outcome-badge.BRANCH { background: #dcfce7; color: #166534; }
.outcome-badge.RAG { background: #fef3c7; color: #92400e; }
.outcome-badge.CHITCHAT { background: #dbeafe; color: #1e40af; }
.outcome-badge.ERROR { background: #fee2e2; color: #991b1b; }
.outcome-badge.DUPLICATE { background: #f1f5f9; color: #475569; }

.user-text {
  margin-bottom: 16px; padding: 10px 12px; background: #f8fafc; border-radius: 6px;
}
.user-text .label { font-size: 11px; color: #64748b; }
.user-text p { margin: 4px 0 0; font-size: 13px; }

.section-title { font-size: 14px; font-weight: 600; margin: 0 0 10px; color: #334155; }
.trace-user-label { font-size: 12px; font-weight: 400; color: #64748b; margin-left: 6px; }

.skill-timeline { display: flex; flex-direction: column; gap: 8px; }
.skill-step {
  border: 1px solid #e2e8f0; border-radius: 6px; padding: 10px 12px; font-size: 13px;
}
.skill-step.skip { background: #fafafa; opacity: 0.85; }
.skill-step.run { background: #fff; }

.step-head { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
.step-name { font-weight: 600; color: #1e293b; }
.step-order { font-size: 11px; color: #94a3b8; }
.step-cost { font-size: 11px; color: #64748b; background: #f1f5f9; padding: 1px 6px; border-radius: 8px; }
.step-outcome { font-size: 11px; color: #64748b; margin-left: auto; }
.step-outcome.hit { color: #0f766e; font-weight: 600; }

.step-detail {
  margin: 8px 0 0; padding: 8px; background: #f8fafc; border-radius: 4px;
  font-size: 11px; overflow-x: auto; white-space: pre-wrap; word-break: break-all;
}

.empty-skills { color: #94a3b8; font-size: 13px; padding: 12px 0; }

.raw-json { margin-top: 16px; font-size: 12px; }
.raw-json summary { cursor: pointer; color: #64748b; margin-bottom: 8px; }
.raw-json pre {
  background: #1e293b; color: #e2e8f0; padding: 12px; border-radius: 6px;
  overflow-x: auto; font-size: 11px; max-height: 400px;
}
</style>
