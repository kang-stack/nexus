<template>
  <div ref="listRef" class="message-list">
    <MessageBubble
      v-for="msg in messages"
      :key="msg.id"
      :msg="msg"
      @link-click="(link) => emit('linkClick', link)"
      @button-click="(btn) => emit('buttonClick', btn)"
      @rate="(r) => emit('rate', r)"
    />
    <TypingIndicator v-if="isTyping" />
    <TypingIndicator v-if="isProgress" :text="progressText" />
    <div v-if="showSatisfaction" class="satisfaction-row">
      <button class="sat-btn" @click="emit('rate', 'USEFUL')">有用</button>
      <button class="sat-btn" @click="emit('rate', 'NOT_USEFUL')">无用</button>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, nextTick } from 'vue'
import MessageBubble from './MessageBubble.vue'
import TypingIndicator from './TypingIndicator.vue'

const props = defineProps({
  messages: Array,
  isTyping: Boolean,
  isProgress: Boolean,
  progressText: String,
  showSatisfaction: Boolean
})

const emit = defineEmits(['linkClick', 'buttonClick', 'rate'])

const listRef = ref(null)

function scrollToBottom() {
  nextTick(() => {
    if (listRef.value) {
      listRef.value.scrollTop = listRef.value.scrollHeight
    }
  })
}

watch(() => props.messages.length, scrollToBottom)
watch(() => props.isTyping, scrollToBottom)
watch(() => props.isProgress, scrollToBottom)
</script>

<style scoped>
.message-list {
  flex: 1;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  padding: 8px 0;
  background: #f5f5f5;
}
.satisfaction-row {
  display: flex;
  justify-content: center;
  gap: 16px;
  padding: 12px;
}
.sat-btn {
  padding: 8px 24px;
  border: 1px solid #20c0b0;
  border-radius: 20px;
  background: #fff;
  color: #20c0b0;
  font-size: 14px;
  cursor: pointer;
}
.sat-btn:active {
  background: #e8faf8;
}
</style>
