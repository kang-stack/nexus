<template>
  <div class="chat-input-area">
    <div class="input-row">
      <input
        ref="inputRef"
        v-model="text"
        class="input-field"
        type="text"
        :placeholder="placeholder"
        :maxlength="maxLength"
        :disabled="disabled"
        @keyup.enter="send"
      />
      <button
        class="send-btn"
        :class="{ disabled: !canSend }"
        :disabled="!canSend"
        @click="send"
      >发送</button>
    </div>
    <div v-if="overLimit" class="hint-error">最多输入 {{ maxLength }} 字</div>
    <div v-else-if="text.length > 0" class="hint-count">{{ text.length }}/{{ maxLength }}</div>
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick } from 'vue'

const props = defineProps({
  placeholder: {
    type: String,
    default: '请告诉我您遇到了什么问题'
  },
  maxLength: {
    type: Number,
    default: 200
  },
  disabled: Boolean
})

const emit = defineEmits(['send'])

const text = ref('')
const inputRef = ref(null)

const canSend = computed(() =>
  text.value.trim().length > 0 && !props.disabled && text.value.length <= props.maxLength
)

const overLimit = computed(() => text.value.length > props.maxLength)

function send() {
  if (!canSend.value) return
  const content = text.value.trim()
  text.value = ''
  emit('send', content)
}

function focus() {
  nextTick(() => inputRef.value?.focus())
}

watch(() => props.disabled, (val) => {
  if (!val) focus()
})

defineExpose({ focus })
</script>

<style scoped>
.chat-input-area {
  background: #fff;
  border-top: 1px solid #eee;
  padding: 8px 12px;
  padding-bottom: max(8px, env(safe-area-inset-bottom));
}
.input-row {
  display: flex;
  gap: 8px;
  align-items: center;
}
.input-field {
  flex: 1;
  height: 36px;
  padding: 0 12px;
  border: 1px solid #ddd;
  border-radius: 18px;
  font-size: 14px;
  outline: none;
  background: #f7f7f7;
}
.input-field:focus {
  border-color: #20c0b0;
  background: #fff;
}
.input-field:disabled {
  background: #f0f0f0;
  color: #999;
}
.send-btn {
  width: 56px;
  height: 36px;
  border: none;
  border-radius: 18px;
  background: #20c0b0;
  color: #fff;
  font-size: 14px;
  cursor: pointer;
  flex-shrink: 0;
}
.send-btn.disabled {
  background: #ccc;
  cursor: not-allowed;
}
.send-btn:active:not(.disabled) {
  opacity: 0.8;
}
.hint-error {
  font-size: 11px;
  color: #e74c3c;
  margin-top: 4px;
  padding-left: 12px;
}
.hint-count {
  font-size: 11px;
  color: #aaa;
  margin-top: 4px;
  padding-left: 12px;
}
</style>
