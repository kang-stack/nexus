<template>
  <div class="message-bubble" :class="[msg.role]">
    <div class="bubble-wrapper">
      <div v-if="msg.role === 'system'" class="system-msg">{{ msg.content }}</div>

      <div v-else-if="msg.role === 'user'" class="user-bubble">
        {{ msg.content }}
      </div>

      <div v-else-if="msg.messageType === 'SATISFACTION'" class="assistant-bubble satisfaction-card">
        <p class="sat-prompt">本次服务对您有帮助吗？</p>
        <div class="sat-actions">
          <button class="sat-btn" @click="$emit('rate', 'USEFUL')">有用</button>
          <button class="sat-btn" @click="$emit('rate', 'NOT_USEFUL')">无用</button>
        </div>
      </div>

      <div v-else-if="msg.role === 'human'" class="assistant-bubble human-bubble">
        <p v-if="msg.agentName" class="human-agent">{{ msg.agentName }}</p>
        <div class="bubble-content">
          <MarkdownContent class="bubble-text" :content="msg.content" />
        </div>
      </div>

      <div v-else-if="hasAssistantContent(msg)" class="assistant-bubble">
        <div class="bubble-content">
          <MarkdownContent class="bubble-text" :content="msg.content" />

          <div v-if="msg.links && msg.links.length > 0" class="links-area">
            <button
              v-for="(link, i) in msg.links"
              :key="i"
              class="link-btn"
              @click="$emit('linkClick', link)"
            >
              {{ link.text }}
              <span class="link-arrow">→</span>
            </button>
          </div>

          <button
            v-if="msg.button && !msg.button.disabled"
            class="action-btn"
            :disabled="msg.button.disabled"
            @click="$emit('buttonClick', msg.button)"
          >
            {{ msg.button.text }}
          </button>
          <button
            v-else-if="msg.button && msg.button.disabled"
            class="action-btn disabled"
            disabled
          >
            {{ msg.button.text }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import MarkdownContent from '@/components/MarkdownContent.vue'

defineProps({
  msg: Object
})

defineEmits(['linkClick', 'buttonClick', 'rate'])

/** 无文本/链接/按钮时不渲染 assistant 气泡，避免空白占位 */
function hasAssistantContent(msg) {
  if (!msg) return false
  if (msg.content && msg.content.trim()) return true
  if (msg.links && msg.links.length > 0) return true
  if (msg.button && msg.button.text) return true
  return false
}
</script>

<style scoped>
.message-bubble {
  display: flex;
  padding: 6px 12px;
}
.message-bubble.user {
  justify-content: flex-end;
}
.message-bubble.assistant, .message-bubble.human {
  justify-content: flex-start;
}
.message-bubble.system {
  justify-content: center;
}
.system-msg {
  font-size: 12px;
  color: #999;
  text-align: center;
  padding: 6px 0;
}
.bubble-wrapper {
  max-width: 78%;
}
.user-bubble {
  background: #20c0b0;
  color: #fff;
  padding: 10px 14px;
  border-radius: 18px 18px 4px 18px;
  font-size: 15px;
  line-height: 1.5;
  word-break: break-word;
}
.human-bubble {
  border-left: 3px solid #2080d0;
}
.human-agent {
  font-size: 11px;
  color: #2080d0;
  margin: 0 0 4px;
  font-weight: 500;
}
.assistant-bubble {
  background: #fff;
  padding: 10px 14px;
  border-radius: 4px 18px 18px 18px;
  font-size: 15px;
  line-height: 1.6;
  color: #333;
  word-break: break-word;
  box-shadow: 0 1px 3px rgba(0,0,0,0.06);
}
.bubble-text {
  margin-bottom: 4px;
}
.links-area {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid #f0f0f0;
}
.link-btn {
  background: #f0f8ff;
  border: 1px solid #d0e8ff;
  border-radius: 14px;
  padding: 6px 12px;
  font-size: 13px;
  color: #2080d0;
  cursor: pointer;
}
.link-btn:active {
  background: #e0f0ff;
}
.link-arrow {
  margin-left: 2px;
  font-size: 11px;
}
.action-btn {
  display: block;
  width: 100%;
  margin-top: 8px;
  padding: 10px 0;
  background: #20c0b0;
  color: #fff;
  border: none;
  border-radius: 20px;
  font-size: 14px;
  cursor: pointer;
  text-align: center;
}
.action-btn:active {
  opacity: 0.8;
}
.action-btn.disabled {
  background: #ccc;
  cursor: not-allowed;
}
.satisfaction-card {
  padding: 12px 14px;
}
.sat-prompt {
  margin: 0 0 10px;
  font-size: 14px;
  color: #333;
}
.sat-actions {
  display: flex;
  gap: 12px;
}
.sat-btn {
  padding: 6px 18px;
  border: 1px solid #20c0b0;
  border-radius: 18px;
  background: #fff;
  color: #20c0b0;
  font-size: 13px;
  cursor: pointer;
}
</style>
