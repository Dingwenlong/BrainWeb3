<script setup lang="ts">
import { reactive, watch } from 'vue'
import type { ActivityFrame, BrainBand } from '../types/api'
import { formatBandLabel, formatQualityFlags, formatSecondsLabel } from '../utils/labels'

const props = defineProps<{
  bands: BrainBand[]
  selectedBand: BrainBand
  windowSize: number
  stepSize: number
  timeStart: number
  timeEnd: number
  playing: boolean
  loading: boolean
  error: string | null
  frameIndex: number
  frameCount: number
  currentTimestamp: number
  qualityFlags: string[]
  frames: ActivityFrame[]
}>()

const emit = defineEmits<{
  'update:selectedBand': [band: BrainBand]
  'update:windowSize': [value: number]
  'update:stepSize': [value: number]
  'update:timeStart': [value: number]
  'update:timeEnd': [value: number]
  'toggle-play': []
  'seek-frame': [index: number]
  'apply-range': []
}>()

const draft = reactive({
  windowSize: props.windowSize,
  stepSize: props.stepSize,
  timeStart: props.timeStart,
  timeEnd: props.timeEnd,
})

watch(
  () => [props.windowSize, props.stepSize, props.timeStart, props.timeEnd],
  ([windowSize, stepSize, timeStart, timeEnd]) => {
    draft.windowSize = windowSize
    draft.stepSize = stepSize
    draft.timeStart = timeStart
    draft.timeEnd = timeEnd
  },
)

function commitWindowSize() {
  emit('update:windowSize', Math.max(0.5, Number(draft.windowSize) || 0.5))
}

function commitStepSize() {
  emit('update:stepSize', Math.max(0.1, Number(draft.stepSize) || 0.1))
}

function applyRange() {
  emit('update:timeStart', Math.max(0, Number(draft.timeStart) || 0))
  emit('update:timeEnd', Math.max(0, Number(draft.timeEnd) || 0))
  emit('apply-range')
}
</script>

<template>
  <section class="timeline-panel">
    <div class="timeline-panel__heading">
      <div>
        <p class="section-kicker">回放控制</p>
        <h2 class="section-title">信号时间轴</h2>
      </div>
      <button class="timeline-panel__button" type="button" @click="emit('toggle-play')">
        {{ playing ? '暂停' : '播放' }}
      </button>
    </div>

    <div class="band-switch">
      <button
        v-for="band in bands"
        :key="band"
        type="button"
        class="band-switch__item"
        :class="{ 'band-switch__item--active': band === selectedBand }"
        @click="emit('update:selectedBand', band)"
      >
        {{ formatBandLabel(band) }}
      </button>
    </div>

    <div class="parameter-grid">
      <label>
        <span>时间窗</span>
        <input v-model.number="draft.windowSize" type="number" min="0.5" step="0.5" @change="commitWindowSize" />
      </label>
      <label>
        <span>滑窗步长</span>
        <input v-model.number="draft.stepSize" type="number" min="0.1" step="0.1" @change="commitStepSize" />
      </label>
    </div>

    <div class="range-grid">
      <label>
        <span>起始时间</span>
        <input v-model.number="draft.timeStart" type="number" min="0" step="0.5" />
      </label>
      <label>
        <span>结束时间</span>
        <input v-model.number="draft.timeEnd" type="number" min="0.5" step="0.5" />
      </label>
      <button class="range-grid__button" type="button" @click="applyRange">加载区间</button>
    </div>

    <div v-if="loading" class="loading-state">正在加载频段活跃度...</div>
    <div v-else-if="error" class="error-state timeline-panel__message">{{ error }}</div>
    <template v-else>
      <div class="timeline-panel__stats">
        <div class="metric-card">
          <span>当前帧</span>
          <strong>{{ frameCount ? `${frameIndex + 1}/${frameCount}` : '0/0' }}</strong>
        </div>
        <div class="metric-card">
          <span>时间点</span>
          <strong>{{ formatSecondsLabel(currentTimestamp) }}</strong>
        </div>
        <div class="metric-card">
          <span>质量标记</span>
          <strong>{{ formatQualityFlags(qualityFlags) }}</strong>
        </div>
      </div>

      <label class="timeline">
        <span>帧游标</span>
        <input
          :value="frameIndex"
          type="range"
          min="0"
          :max="Math.max(frameCount - 1, 0)"
          step="1"
          @input="emit('seek-frame', Number(($event.target as HTMLInputElement).value))"
        />
      </label>

      <div class="frame-ribbon">
        <button
          v-for="(frame, index) in frames"
          :key="`${frame.timestamp}-${index}`"
          type="button"
          class="frame-ribbon__item"
          :class="{ 'frame-ribbon__item--active': index === frameIndex }"
          @click="emit('seek-frame', index)"
        >
          {{ frame.timestamp.toFixed(1) }}
        </button>
      </div>
    </template>
  </section>
</template>

<style scoped>
.timeline-panel {
  display: grid;
  gap: 16px;
}

.timeline-panel__heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.timeline-panel__button,
.range-grid__button {
  min-height: var(--control-height);
  border: 1px solid var(--line-warm);
  border-radius: var(--radius-pill);
  padding: var(--space-button);
  background: var(--button-warm-gradient);
  color: var(--text-main);
  font-family: var(--body);
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.band-switch {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.band-switch__item {
  min-height: 38px;
  padding: 0 14px;
  border-radius: var(--radius-pill);
  border: 1px solid rgba(49, 87, 102, 0.16);
  background: var(--button-soft-gradient);
  color: var(--text-muted);
  text-transform: capitalize;
}

.band-switch__item--active {
  color: var(--text-main);
  border-color: rgba(49, 87, 102, 0.24);
  box-shadow: inset 0 0 18px rgba(49, 87, 102, 0.08);
}

.parameter-grid,
.range-grid,
.timeline-panel__stats {
  display: grid;
  gap: 12px;
}

.parameter-grid,
.range-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.range-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr)) auto;
  align-items: end;
}

.parameter-grid label,
.range-grid label {
  display: grid;
  gap: 8px;
}

.parameter-grid span,
.range-grid span,
.timeline span {
  color: var(--text-muted);
  font-size: var(--field-label-size);
  text-transform: uppercase;
  letter-spacing: var(--field-label-letter-spacing);
}

.parameter-grid input,
.range-grid input {
  width: 100%;
  border: 1px solid var(--line);
  border-radius: var(--radius-control);
  min-height: var(--field-height);
  padding: var(--space-field-x);
  background: var(--bg-panel);
  color: var(--text-main);
}

.timeline {
  display: grid;
  gap: 10px;
}

.timeline input {
  width: 100%;
  accent-color: var(--accent);
}

.frame-ribbon {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.frame-ribbon__item {
  min-height: 36px;
  padding: 0 12px;
  border-radius: var(--radius-control);
  border: 1px solid rgba(49, 87, 102, 0.12);
  background: var(--panel-soft-gradient);
  color: var(--text-muted);
}

.frame-ribbon__item--active {
  border-color: rgba(255, 187, 112, 0.26);
  color: var(--amber);
}

.timeline-panel__message {
  margin-top: 18px;
}

@media (max-width: 1040px) {
  .parameter-grid,
  .range-grid {
    grid-template-columns: 1fr;
  }
}
</style>
