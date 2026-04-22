import { computed, reactive, ref, type ComputedRef, type Ref } from 'vue'
import { ApiError, getBrainActivity } from '../api/client'
import type { AccessRequest, BrainActivityResponse, BrainBand } from '../types/api'
import type { ActorProfile } from './useActorProfile'
import { formatSecondsLabel } from '../utils/labels'
import { toErrorMessage } from './useAsyncView'
import { usePlaybackController } from './usePlaybackController'
import type { DatasetAccessState } from './useDatasetAccessWorkflow'

export function useDatasetActivity(options: {
  datasetId: string
  actorProfile: Ref<ActorProfile>
  latestAccessRequest: ComputedRef<AccessRequest | null>
  getDurationSeconds: () => number
  onAccessStateChange: (state: DatasetAccessState) => void
}) {
  const activity = ref<BrainActivityResponse | null>(null)
  const selectedBand = ref<BrainBand>('alpha')
  const activityLoading = ref(true)
  const activityError = ref<string | null>(null)

  const activityRequest = reactive({
    windowSize: 2,
    stepSize: 0.5,
    timeStart: 0,
    timeEnd: 30,
  })

  const frameCount = computed(() => activity.value?.frames.length ?? 0)
  const { frameIndex, playing, seekFrame, stopPlayback, resetPlayback, togglePlayback } =
    usePlaybackController({
      getFrameCount: () => frameCount.value,
      intervalMs: 900,
    })

  const currentFrame = computed(() => activity.value?.frames[frameIndex.value] ?? null)
  const currentTimestamp = computed(() => currentFrame.value?.timestamp ?? activityRequest.timeStart)
  const loadedRangeLabel = computed(
    () => `已载入 ${formatSecondsLabel(activityRequest.timeStart)} - ${formatSecondsLabel(activityRequest.timeEnd)}`,
  )

  function normalizeRequestedRange() {
    const duration = options.getDurationSeconds()
    if (!duration) {
      activityRequest.timeStart = 0
      activityRequest.timeEnd = 30
      return
    }

    const minSpan = Math.max(activityRequest.stepSize, 0.5)
    let start = Math.max(0, Math.min(activityRequest.timeStart, duration))
    let end = Math.max(0, Math.min(activityRequest.timeEnd, duration))

    if (end <= start) {
      end = Math.min(duration, start + minSpan)
    }
    if (end - start < minSpan) {
      end = Math.min(duration, start + minSpan)
    }
    if (end > duration) {
      end = duration
      start = Math.max(0, end - minSpan)
    }

    activityRequest.timeStart = Number(start.toFixed(1))
    activityRequest.timeEnd = Number(end.toFixed(1))
  }

  function resetDefaultRange() {
    const duration = options.getDurationSeconds() || 30
    activityRequest.timeStart = 0
    activityRequest.timeEnd = Number(Math.min(duration, 30).toFixed(1))
    normalizeRequestedRange()
  }

  function handleBandUpdate(band: BrainBand) {
    selectedBand.value = band
  }

  async function loadActivity() {
    activityLoading.value = true
    activityError.value = null
    stopPlayback()
    normalizeRequestedRange()

    try {
      activity.value = await getBrainActivity(
        options.datasetId,
        selectedBand.value,
        options.actorProfile.value,
        {
          windowSize: activityRequest.windowSize,
          stepSize: activityRequest.stepSize,
          timeStart: activityRequest.timeStart,
          timeEnd: activityRequest.timeEnd,
        },
      )
      resetPlayback()
      options.onAccessStateChange('granted')
    } catch (loadError) {
      activity.value = null
      if (loadError instanceof ApiError && loadError.status === 403) {
        options.onAccessStateChange(
          options.latestAccessRequest.value?.status === 'pending' ? 'pending' : 'denied',
        )
        activityError.value = '当前操作者尚未获批，神经活跃度数据已被门禁拦截。'
      } else {
        activityError.value = toErrorMessage(loadError, '加载脑区活跃度失败。')
      }
    } finally {
      activityLoading.value = false
    }
  }

  async function applyActivityRange() {
    await loadActivity()
  }

  return {
    activity,
    activityError,
    activityLoading,
    activityRequest,
    applyActivityRange,
    currentFrame,
    currentTimestamp,
    frameCount,
    frameIndex,
    handleBandUpdate,
    loadedRangeLabel,
    loadActivity,
    playing,
    resetDefaultRange,
    seekFrame,
    selectedBand,
    stopPlayback,
    togglePlayback,
  }
}
