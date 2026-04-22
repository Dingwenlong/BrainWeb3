import { computed, reactive, ref, type ComputedRef, type Ref } from 'vue'
import { createAccessRequest } from '../api/client'
import type { AccessRequest } from '../types/api'
import type { ActorProfile } from './useActorProfile'
import { toErrorMessage } from './useAsyncView'

export type DatasetAccessState = 'granted' | 'pending' | 'denied' | 'idle'

export function useDatasetAccessWorkflow(options: {
  datasetId: string
  actorProfile: Ref<ActorProfile>
  isPrivilegedActor: ComputedRef<boolean>
  accessRequests: Ref<AccessRequest[]>
  reloadSidePanels: () => Promise<void>
  pushToast: (input: { title: string; message: string; tone: 'success' | 'info' | 'warning' }) => void
}) {
  const accessState = ref<DatasetAccessState>('idle')
  const requestSubmitting = ref(false)
  const requestError = ref<string | null>(null)

  const accessForm = reactive({
    purpose: '脑区分析',
    requestedDurationHours: 24,
    reason: '需要先查看阿尔法和贝塔频段的脑区活跃情况，再决定是否进入训练预览。',
  })

  const latestAccessRequest = computed(() => {
    const rows = options.accessRequests.value.filter(
      (row) =>
        row.actorId === options.actorProfile.value.actorId || options.isPrivilegedActor.value,
    )
    return rows[0] ?? null
  })

  const recentAccessRequests = computed(() => options.accessRequests.value.slice(0, 3))

  function refreshAccessStateFromRequests() {
    const latest = latestAccessRequest.value
    if (!latest) {
      accessState.value = 'idle'
      return
    }

    if (latest.status === 'approved') {
      accessState.value = 'granted'
      return
    }

    if (latest.status === 'pending') {
      accessState.value = 'pending'
      return
    }

    accessState.value = 'denied'
  }

  async function submitAccessRequest() {
    requestSubmitting.value = true
    requestError.value = null

    try {
      await createAccessRequest(options.actorProfile.value, {
        datasetId: options.datasetId,
        purpose: accessForm.purpose,
        requestedDurationHours: accessForm.requestedDurationHours,
        reason: accessForm.reason,
      })
      await options.reloadSidePanels()
      refreshAccessStateFromRequests()
      options.pushToast({
        title: '申请已提交',
        message: '访问申请已进入审批队列，可切到审批台或归属方视角继续处理。',
        tone: 'success',
      })
    } catch (submitError) {
      requestError.value = toErrorMessage(submitError, '创建访问申请失败。')
    } finally {
      requestSubmitting.value = false
    }
  }

  return {
    accessForm,
    accessState,
    latestAccessRequest,
    recentAccessRequests,
    requestSubmitting,
    requestError,
    refreshAccessStateFromRequests,
    submitAccessRequest,
  }
}
