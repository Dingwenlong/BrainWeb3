import { ref } from 'vue'

export function toErrorMessage(error: unknown, fallbackMessage: string) {
  return error instanceof Error ? error.message : fallbackMessage
}

export function useAsyncView(options?: {
  initialLoading?: boolean
}) {
  const loading = ref(options?.initialLoading ?? false)
  const error = ref<string | null>(null)

  async function run<T>(task: () => Promise<T>, fallbackMessage: string) {
    loading.value = true
    error.value = null

    try {
      return await task()
    } catch (taskError) {
      error.value = toErrorMessage(taskError, fallbackMessage)
      return null
    } finally {
      loading.value = false
    }
  }

  function setErrorMessage(message: string | null) {
    error.value = message
  }

  function clearError() {
    error.value = null
  }

  return {
    loading,
    error,
    run,
    setErrorMessage,
    clearError,
  }
}
