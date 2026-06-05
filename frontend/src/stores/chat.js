import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useChatStore = defineStore('chat', () => {
  const conversationId = ref(null)
  const sessionTimeoutAt = ref(null)
  const messages = ref([])
  const entryProblem = ref(null)
  const isAiResponding = ref(false)
  const currentProgress = ref('')
  const isTyping = ref(false)
  const currentRound = ref(0)
  const currentTopic = ref(null)
  const showSatisfaction = ref(false)
  const satisfactionMessageId = ref(null)
  const humanQueuePosition = ref(null)
  const humanAgentName = ref(null)
  const humanMode = ref(false)

  const lastMessage = computed(() =>
    messages.value.length > 0 ? messages.value[messages.value.length - 1] : null
  )

  function addMessage(msg) {
    messages.value.push(msg)
  }

  function updateMessage(id, patch) {
    const idx = messages.value.findIndex(m => m.id === id)
    if (idx >= 0) {
      messages.value[idx] = { ...messages.value[idx], ...patch }
    }
  }

  function addUserMessage(content) {
    const id = 'user_' + Date.now() + '_' + Math.random().toString(36).slice(2, 8)
    addMessage({
      id,
      role: 'user',
      messageType: 'TEXT',
      content,
      timestamp: Date.now()
    })
    return id
  }

  function addAssistantPlaceholder() {
    const id = 'asst_' + Date.now() + '_' + Math.random().toString(36).slice(2, 8)
    addMessage({
      id,
      role: 'assistant',
      messageType: 'TEXT',
      content: '',
      timestamp: Date.now()
    })
    return id
  }

  function addAssistantWithId(messageId) {
    if (messages.value.some(m => m.id === messageId)) {
      return messageId
    }
    addMessage({
      id: messageId,
      role: 'assistant',
      messageType: 'TEXT',
      content: '',
      timestamp: Date.now()
    })
    return messageId
  }

  /** 将占位气泡 id 替换为后端 messageId，便于后续 updateMessage */
  function reassignMessageId(oldId, newId) {
    if (oldId === newId || messages.value.some(m => m.id === newId)) {
      return newId
    }
    const idx = messages.value.findIndex(m => m.id === oldId)
    if (idx >= 0) {
      messages.value[idx] = { ...messages.value[idx], id: newId }
      return newId
    }
    return addAssistantWithId(newId)
  }

  /** 清理无内容的 assistant 占位气泡（避免「空气泡」残留） */
  function removeEmptyAssistantMessages() {
    messages.value = messages.value.filter(m => {
      if (m.role !== 'assistant') return true
      if (m.messageType === 'SATISFACTION') return true
      const hasText = m.content && m.content.trim().length > 0
      const hasLinks = m.links && m.links.length > 0
      const hasButton = m.button && m.button.text
      return hasText || hasLinks || hasButton
    })
  }

  function addSystemMessage(content) {
    addMessage({
      id: 'sys_' + Date.now(),
      role: 'system',
      messageType: 'TEXT',
      content,
      timestamp: Date.now()
    })
  }

  function lockInput() {
    isAiResponding.value = true
  }

  function unlockInput() {
    isAiResponding.value = false
    currentProgress.value = ''
    isTyping.value = false
  }

  function setProgress(label) {
    currentProgress.value = label
  }

  function setTyping(v) {
    isTyping.value = v
  }

  function startSatisfaction(messageId) {
    showSatisfaction.value = true
    satisfactionMessageId.value = messageId
  }

  function dismissSatisfaction() {
    showSatisfaction.value = false
    satisfactionMessageId.value = null
  }

  function reset() {
    conversationId.value = null
    sessionTimeoutAt.value = null
    messages.value = []
    entryProblem.value = null
    isAiResponding.value = false
    currentProgress.value = ''
    isTyping.value = false
    currentRound.value = 0
    currentTopic.value = null
    showSatisfaction.value = false
    satisfactionMessageId.value = null
    humanQueuePosition.value = null
    humanAgentName.value = null
    humanMode.value = false
  }

  function enterHumanMode() {
    humanMode.value = true
  }

  function exitHumanMode() {
    humanMode.value = false
    humanQueuePosition.value = null
    humanAgentName.value = null
  }

  return {
    conversationId, sessionTimeoutAt, messages, entryProblem,
    isAiResponding, currentProgress, isTyping,
    currentRound, currentTopic,
    showSatisfaction, satisfactionMessageId,
    humanQueuePosition, humanAgentName, humanMode,
    lastMessage,
    addMessage, updateMessage, addUserMessage, addAssistantPlaceholder, addAssistantWithId,
    reassignMessageId, removeEmptyAssistantMessages,
    addSystemMessage,
    lockInput, unlockInput, setProgress, setTyping,
    startSatisfaction, dismissSatisfaction,
    enterHumanMode, exitHumanMode,
    reset
  }
})
