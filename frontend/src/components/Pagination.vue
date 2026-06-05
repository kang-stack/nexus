<template>
  <div class="pagination" v-if="total > 0">
    <span class="page-info">共 {{ total }} 条</span>
    <div class="page-btns">
      <button :disabled="pageNum <= 1" @click="$emit('change', pageNum - 1)">
        <svg width="14" height="14" viewBox="0 0 14 14" fill="none"><path d="M9 3L5 7l4 4" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/></svg>
      </button>
      <button
        v-for="p in visiblePages"
        :key="p"
        :class="{ active: p === pageNum, ellipsis: p < 0 }"
        :disabled="p < 0"
        @click="p > 0 && $emit('change', p)"
      >{{ p < 0 ? '…' : p }}</button>
      <button :disabled="pageNum >= pages" @click="$emit('change', pageNum + 1)">
        <svg width="14" height="14" viewBox="0 0 14 14" fill="none"><path d="M5 3l4 4-4 4" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/></svg>
      </button>
    </div>
    <span class="page-info">{{ pageNum }} / {{ pages }} 页</span>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({ pageNum: Number, pageSize: Number, total: Number })
defineEmits(['change'])

const pages = computed(() => Math.max(1, Math.ceil(props.total / props.pageSize)))

const visiblePages = computed(() => {
  const p = pages.value
  const cur = props.pageNum
  if (p <= 7) return Array.from({ length: p }, (_, i) => i + 1)
  const range = [1]
  if (cur > 3) range.push(-1)
  for (let i = Math.max(2, cur - 1); i <= Math.min(p - 1, cur + 1); i++) range.push(i)
  if (cur < p - 2) range.push(-2)
  range.push(p)
  return range
})
</script>

<style scoped>
.pagination {
  display: flex; align-items: center; justify-content: center;
  gap: 10px; margin-top: 20px; font-size: 13px;
}
.page-info { color: var(--text-muted); font-size: 12px; }
.page-btns { display: flex; gap: 4px; }
.page-btns button {
  min-width: 34px; height: 34px; padding: 0 8px;
  border: 1px solid var(--border);
  background: #fff; border-radius: var(--radius-xs);
  cursor: pointer; color: var(--text-secondary); transition: all 0.2s;
  display: flex; align-items: center; justify-content: center;
  font-size: 13px; font-family: var(--font-body);
}
.page-btns button:hover:not(:disabled):not(.active):not(.ellipsis) {
  border-color: var(--primary); color: var(--primary);
  background: rgba(56,189,248,0.06);
}
.page-btns button.active {
  background: var(--primary); color: #fff; border-color: var(--primary); font-weight: 600;
}
.page-btns button:disabled { color: #d0cdd8; cursor: not-allowed; border-color: transparent; background: transparent; }
.page-btns button.ellipsis { border-color: transparent; cursor: default; color: var(--text-muted); background: transparent; }
</style>
