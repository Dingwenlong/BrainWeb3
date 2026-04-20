import { ref } from 'vue'

export interface ToastItem {
  id: number
  title: string
  message: string
  tone: 'success' | 'info' | 'warning'
}

const toasts = ref<ToastItem[]>([])
let nextToastId = 1

function removeToast(id: number) {
  toasts.value = toasts.value.filter((toast) => toast.id !== id)
}

export function useToast() {
  function pushToast(input: Omit<ToastItem, 'id'>) {
    const id = nextToastId++
    toasts.value = [...toasts.value, { id, ...input }]
    window.setTimeout(() => removeToast(id), 3200)
  }

  return {
    toasts,
    pushToast,
    removeToast,
  }
}
