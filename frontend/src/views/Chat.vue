<template>
  <div class="chat-page">
    <ChatNavbar @transfer="handleTransfer" />
    <QuickServiceBar
      :items="quickServiceItems"
      :disabled="store.isAiResponding || store.humanMode"
      @select="handleQuickService"
    />
    <ChatMessageList
      :messages="store.messages"
      :is-typing="store.isTyping"
      :is-progress="!!store.currentProgress"
      :progress-text="store.currentProgress"
      :show-satisfaction="store.showSatisfaction"
      @link-click="handleLinkClick"
      @button-click="handleButtonClick"
      @rate="handleRate"
    />
    <ChatInput
      ref="inputRef"
      :disabled="store.isAiResponding && !store.humanMode"
      @send="handleSend"
    />
  </div>
</template>

<script setup>
import { onMounted, onBeforeUnmount, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useChatStore } from '@/stores/chat'
import { chatApi, streamChat, streamHumanChat } from '@/api/chat'

import ChatNavbar from '@/components/chat/ChatNavbar.vue'
import QuickServiceBar from '@/components/chat/QuickServiceBar.vue'
import ChatMessageList from '@/components/chat/ChatMessageList.vue'
import ChatInput from '@/components/chat/ChatInput.vue'
const route = useRoute()
const store = useChatStore()
const inputRef = ref(null)

const quickServiceItems = ref([])

let abortController = null
let humanStreamController = null
let sessionPollTimer = null

onMounted(async () => {
  try {
    const entryBundle = await chatApi.listQuickEntries()
    const entries = entryBundle.quickEntries || entryBundle
    quickServiceItems.value = entries.map(e => ({
      code: e.intentCode,
      label: e.label,
      icon: e.icon || '💬',
      message: e.entryMessage || e.label
    }))
  } catch {
    quickServiceItems.value = [
      { code: '实名认证问题', label: '实名后无法使用', icon: '🆔', message: '实名后无法使用' },
      { code: '设备问题', label: '新激活用户', icon: '🆕', message: '我刚激活设备，无法正常使用' },
      { code: '网络问题', label: '设备离线', icon: '📡', message: '我的设备显示离线' },
      { code: '网络问题', label: '网慢/网卡/无网络', icon: '🐢', message: '网络很慢，上网卡顿' },
      { code: '充值问题', label: '套餐充值', icon: '💳', message: '怎么充值套餐' },
      { code: '其他问题', label: '其他服务', icon: '💬', message: '还有其他问题想咨询' }
    ]
  }

  const problem = route.query.problem || null
  try {
    const data = await chatApi.createSession(problem || undefined)
    store.conversationId = data.conversationId

    if (data.greeting) {
      store.addMessage({
        id: 'greeting_' + Date.now(),
        role: 'assistant',
        messageType: 'TEXT',
        content: data.greeting,
        timestamp: Date.now()
      })
    }

    if (data.userMessage) {
      store.entryProblem = data.userMessage.content
      store.addMessage({
        id: 'user_' + Date.now() + '_' + Math.random().toString(36).slice(2, 8),
        role: 'user',
        messageType: 'TEXT',
        content: data.userMessage.content,
        timestamp: Date.now()
      })
      await sendToAi(data.userMessage.content)
    }
  } catch (err) {
    console.error('createSession failed:', err)
  }
  startSessionPolling()
})

function startSessionPolling() {
  stopSessionPolling()
  sessionPollTimer = setInterval(async () => {
    if (!store.conversationId) return
    try {
      const status = await chatApi.sessionStatus(store.conversationId)
      if (status.expired) {
        stopSessionPolling()
        store.addSystemMessage('由于您长时间未回复，本次会话已自动结束。如需帮助，欢迎随时联系我~')
        store.lockInput()
      } else if (status.remainingSeconds != null && status.remainingSeconds < 60) {
        stopSessionPolling()
        sessionPollTimer = setInterval(async () => {
          const s = await chatApi.sessionStatus(store.conversationId)
          if (s.expired) {
            stopSessionPolling()
            store.addSystemMessage('由于您长时间未回复，本次会话已自动结束。如需帮助，欢迎随时联系我~')
            store.lockInput()
          }
        }, 10000)
      }
    } catch {
      // ignore
    }
  }, 30000)
}

function stopSessionPolling() {
  if (sessionPollTimer) {
    clearInterval(sessionPollTimer)
    sessionPollTimer = null
  }
}

async function handleSend(content) {
  stopSessionPolling()
  if (store.humanMode) {
    store.addUserMessage(content)
    try {
      await chatApi.sendHumanMessage(store.conversationId, content)
    } catch {
      store.addSystemMessage('消息发送失败，请重试')
    }
    return
  }
  await sendToAi(content)
}

async function sendToAi(content, quickServiceCode) {
  if (!store.conversationId) return
  store.lockInput()

  try {
    const pre = await chatApi.sendMessage(store.conversationId, content)
    if (pre.chitchat && pre.message) {
      store.addUserMessage(content)
      const m = pre.message
      store.addMessage({
        id: m.id,
        role: 'assistant',
        messageType: m.contentType || 'TEXT',
        content: m.content || '',
        links: m.links,
        timestamp: Date.now()
      })
      store.unlockInput()
      startSessionPolling()
      return
    }
  } catch (err) {
    console.warn('chitchat pre-check failed, fallback to stream', err)
  }

  store.addUserMessage(content)
  store.setProgress('正在为您查询...')

  if (abortController) {
    abortController.abort()
  }

  let activeAssistantId = null

  abortController = streamChat(store.conversationId, content, {
    onSession(id) {
      store.conversationId = id
    },
    onProgress(label) {
      store.setProgress(label)
    },
    onTyping() {
      store.setTyping(true)
    },
    onMessage(event) {
      store.setTyping(false)
      store.setProgress('')

      const d = event.data || event
      const messageType = d.messageType || d.contentType || 'TEXT'
      const msgId = d.messageId || d.id

      if (messageType === 'SATISFACTION') {
        store.addMessage({
          id: msgId || ('sat_' + Date.now()),
          role: 'assistant',
          messageType: 'SATISFACTION',
          content: d.content || '',
          timestamp: Date.now()
        })
        return
      }

      // newBubble=false 时复用当前 assistant 气泡（对应后端「首条不新建气泡」）
      if (d.newBubble || !activeAssistantId) {
        if (msgId) {
          activeAssistantId = store.messages.some(m => m.id === msgId)
              ? msgId
              : store.addAssistantWithId(msgId)
        } else {
          activeAssistantId = store.addAssistantPlaceholder()
        }
      } else if (msgId && msgId !== activeAssistantId) {
        activeAssistantId = store.reassignMessageId(activeAssistantId, msgId)
      }

      store.updateMessage(activeAssistantId, {
        messageType,
        content: d.content || '',
        links: d.links,
        button: d.button,
        sequenceNumber: d.sequenceNumber,
        totalSequence: d.totalSequence,
        isEnding: d.isEnding,
        intentLabel: d.intentLabel
      })
    },
    onError(content) {
      store.addSystemMessage(content || '抱歉，系统繁忙，请稍后再试')
      store.unlockInput()
    },
    onComplete(meta) {
      store.removeEmptyAssistantMessages()
      store.unlockInput()
      startSessionPolling()
    },
    onStopped(content) {
      store.unlockInput()
      if (content) {
        store.updateMessage(activeAssistantId, { content })
      }
    }
  }, quickServiceCode)
}

async function handleQuickService(item) {
  if (store.isAiResponding || store.humanMode) return
  const text = item.message || item.label
  await sendToAi(text, item.code)
}

function handleLinkClick(link) {
  if (link.type === 'jump' && link.url) {
    window.open(link.url, '_blank')
  } else if (link.type === 'intent') {
    handleSend(link.message || link.text, link.intentCode)
  }
}

async function handleButtonClick(button) {
  if (button.type === 'jump' && button.url) {
    window.open(button.url, '_blank')
    return
  }
  if (button.type === 'action' && button.action) {
    try {
      const res = await chatApi.executeAction(store.conversationId, button.action)
      store.addSystemMessage(res.message || `已为您执行${button.text}`)
    } catch {
      store.addSystemMessage('操作未成功，请稍后再试')
    }
    button.disabled = true
  }
}

function handleRate(rating) {
  if (store.conversationId) {
    chatApi.submitRating(store.conversationId, rating).catch(() => {})
  }
}

onBeforeUnmount(() => {
  stopSessionPolling()
  stopHumanStream()
  if (abortController) abortController.abort()
  store.reset()
})

function stopHumanStream() {
  if (humanStreamController) {
    humanStreamController.abort()
    humanStreamController = null
  }
}

function startHumanStream() {
  stopHumanStream()
  store.enterHumanMode()
  humanStreamController = streamHumanChat(store.conversationId, {
    onHumanQueued(position) {
      store.humanQueuePosition = position
    },
    onHumanConnected(agentName) {
      store.humanAgentName = agentName
      store.addSystemMessage(agentName ? `客服 ${agentName} 已接入，请描述您的问题` : '人工客服已接入')
    },
    onHumanText(text) {
      store.addMessage({
        id: 'human_' + Date.now() + '_' + Math.random().toString(36).slice(2, 6),
        role: 'human',
        messageType: 'TEXT',
        content: text,
        agentName: store.humanAgentName,
        timestamp: Date.now()
      })
    },
    onHumanClosed() {
      store.exitHumanMode()
      store.addSystemMessage('人工会话已结束，如需帮助可继续向智能客服提问')
      stopHumanStream()
    },
    onError(msg) {
      console.warn('human stream error:', msg)
    }
  })
}

async function handleTransfer() {
  if (!store.conversationId) return
  if (abortController) {
    abortController.abort()
    abortController = null
  }
  store.addSystemMessage('正在为您转接人工客服...')
  try {
    const data = await chatApi.transferHuman(store.conversationId)
    if (data.success) {
      store.addSystemMessage(data.message)
      if (data.queuePosition != null) {
        store.humanQueuePosition = data.queuePosition
      }
      startHumanStream()
    } else {
      store.addSystemMessage(data.message || '转接失败，请稍后再试')
    }
  } catch {
    store.addSystemMessage('转接失败，请稍后再试')
  }
}
</script>

<style scoped>
.chat-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  height: 100dvh;
  background: #f5f5f5;
  max-width: 480px;
  margin: 0 auto;
}
</style>
