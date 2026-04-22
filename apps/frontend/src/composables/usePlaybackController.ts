import { onScopeDispose, ref } from 'vue'

export function usePlaybackController(options: {
  getFrameCount: () => number
  intervalMs?: number
}) {
  const frameIndex = ref(0)
  const playing = ref(false)
  let playbackTimer: number | null = null

  function stopPlayback() {
    if (playbackTimer !== null) {
      window.clearInterval(playbackTimer)
      playbackTimer = null
    }
    playing.value = false
  }

  function seekFrame(index: number) {
    const frameCount = options.getFrameCount()
    if (!frameCount) {
      frameIndex.value = 0
      return
    }

    frameIndex.value = Math.max(0, Math.min(index, frameCount - 1))
  }

  function resetPlayback() {
    stopPlayback()
    frameIndex.value = 0
  }

  function togglePlayback() {
    const frameCount = options.getFrameCount()
    if (!frameCount) {
      return
    }

    if (playing.value) {
      stopPlayback()
      return
    }

    playing.value = true
    playbackTimer = window.setInterval(() => {
      const count = options.getFrameCount()
      if (!count) {
        stopPlayback()
        return
      }

      frameIndex.value = (frameIndex.value + 1) % count
    }, options.intervalMs ?? 900)
  }

  onScopeDispose(() => {
    stopPlayback()
  })

  return {
    frameIndex,
    playing,
    seekFrame,
    stopPlayback,
    resetPlayback,
    togglePlayback,
  }
}
