<template>
  <div class="quick-service-bar">
    <div class="quick-service-scroll">
      <button
        v-for="item in items"
        :key="item.code"
        class="quick-service-item"
        :disabled="disabled"
        @click="$emit('select', item)"
      >
        <span class="qs-icon">{{ item.icon }}</span>
        <span class="qs-label">{{ item.label }}</span>
      </button>
    </div>
  </div>
</template>

<script setup>
defineProps({
  items: Array,
  disabled: Boolean
})

defineEmits(['select'])
</script>

<style scoped>
.quick-service-bar {
  background: #fff;
  border-bottom: 1px solid #f0f0f0;
  padding: 8px 0;
}
.quick-service-scroll {
  display: flex;
  gap: 8px;
  padding: 0 12px;
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
  scrollbar-width: none;
}
.quick-service-scroll::-webkit-scrollbar {
  display: none;
}
.quick-service-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  min-width: 56px;
  padding: 6px 4px;
  background: none;
  border: none;
  cursor: pointer;
  flex-shrink: 0;
}
.quick-service-item:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.quick-service-item:active:not(:disabled) {
  opacity: 0.6;
}
.qs-icon {
  font-size: 24px;
  line-height: 1;
}
.qs-label {
  font-size: 11px;
  color: #666;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 60px;
}
</style>
