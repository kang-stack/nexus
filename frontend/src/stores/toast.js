import { ref } from 'vue'

const toasts = ref([])
let nextId = 0

export function useToast() {
  function show(message, type = 'info') {
    const id = nextId++
    toasts.value.push({ id, message, type })
    setTimeout(() => remove(id), type === 'error' ? 5000 : 3000)
  }

  function remove(id) {
    toasts.value = toasts.value.filter(t => t.id !== id)
  }

  function success(msg) { show(msg, 'success') }
  function error(msg) { show(msg, 'error') }
  function warning(msg) { show(msg, 'warning') }
  function info(msg) { show(msg, 'info') }

  return { toasts, show, remove, success, error, warning, info }
}

/** @deprecated 使用 useToast，保留别名避免旧页面 import 失败 */
export const useToastStore = useToast
