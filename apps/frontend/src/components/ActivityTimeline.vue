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
      <div class="timeline-panel__heading-actions">
        <span class="status-chip" :class="{ 'status-chip--warn': !playing }">
          <span class="status-chip__dot" aria-hidden="true"></span>
          {{ playing ? '回放中' : '已暂停' }}
        </span>
        <button class="timeline-panel__button" type="button" @click="emit('toggle-play')">
          {{ playing ? '暂停' : '播放' }}
        </button>
      </div>
    </div>

    <div class="band-switch">
      <p class="band-switch__label">频段</p>
      <div class="band-switch__group">
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
          :style="{ animationDelay: `${Math.min(index, 24) * 0.03}s` }"
          @click="emit('seek-frame', index)"
        >
          <span class="frame-ribbon__node" aria-hidden="true"></span>
          <span class="frame-ribbon__time">{{ frame.timestamp.toFixed(1) }}</span>
        </button>
      </div>
    </template>
  </section>
</template>

<style scoped>
.timeline-panel {
  display: grid;
  gap: 18px;
}

.timeline-panel__heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.timeline-panel__heading-actions {
  display: inline-flex;
  align-items: center;
  gap: 12px;
}

.timeline-panel__button,
.range-grid__button {
  min-height: var(--control-height);
  border: 1px solid var(--line-warm);
  border-radius: var(--radius-control);
  padding: var(--space-button);
  background: var(--bg-panel-soft);
  color: var(--text-strong);
  font-family: var(--body);
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  transition:
    border-color 0.2s ease,
    background 0.2s ease;
}

.timeline-panel__button:hover,
.range-grid__button:hover {
  border-color: var(--accent);
  background: var(--bg-panel-muted);
}

.band-switch {
  display: grid;
  gap: 10px;
}

.band-switch__label {
  margin: 0;
  font-family: var(--mono);
  font-size: 0.7rem;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--text-faint);
}

.band-switch__group {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.band-switch__item {
  min-height: 38px;
  padding: 0 14px;
  border-radius: var(--radius-control);
  border: 1px solid var(--line);
  background: var(--bg-panel-soft);
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: 0.82rem;
  letter-spacing: 0.04em;
  text-transform: capitalize;
  transition:
    color 0.2s ease,
    border-color 0.2s ease,
    background 0.2s ease;
}

.band-switch__item:hover {
  color: var(--text-main);
  border-color: var(--line-strong);
}

.band-switch__item--active {
  color: var(--text-strong);
  border-color: var(--accent);
  background: var(--bg-panel-muted);
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
  color: var(--text-faint);
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
  background: #0e1013;
  color: var(--text-main);
  font-family: var(--mono);
}

.timeline {
  display: grid;
  gap: 10px;
}

.timeline input {
  width: 100%;
  accent-color: var(--accent);
}

/* metric cards — mono readouts */
.timeline-panel__stats {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.timeline-panel__stats .metric-card {
  animation: consoleRise 0.5s ease both;
}

.timeline-panel__stats .metric-card:nth-child(2) {
  animation-delay: 0.06s;
}

.timeline-panel__stats .metric-card:nth-child(3) {
  animation-delay: 0.12s;
}

.timeline-panel__stats .metric-card span {
  font-family: var(--mono);
  letter-spacing: 0.12em;
}

.timeline-panel__stats .metric-card strong {
  font-family: var(--mono);
  color: var(--text-strong);
}

/* event stream — cyan nodes on a connecting spine */
.frame-ribbon {
  position: relative;
  display: flex;
  flex-wrap: wrap;
  gap: 10px 14px;
  padding: 14px 4px 4px;
}

.frame-ribbon::before {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  top: 21px;
  height: 1px;
  background: linear-gradient(
    90deg,
    transparent,
    var(--line-strong) 8%,
    var(--line-strong) 92%,
    transparent
  );
  pointer-events: none;
}

.frame-ribbon__item {
  position: relative;
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  min-height: 36px;
  padding: 0 6px;
  border: 0;
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
  animation: consoleRise 0.42s ease both;
}

.frame-ribbon__node {
  width: 8px;
  height: 8px;
  border-radius: 2px;
  background: var(--bg-panel);
  border: 1px solid var(--line-strong);
  box-shadow: 0 0 0 3px var(--bg-page);
  transition:
    background 0.2s ease,
    border-color 0.2s ease,
    transform 0.2s ease;
}

.frame-ribbon__time {
  font-family: var(--mono);
  font-size: 0.74rem;
  letter-spacing: 0.04em;
  color: var(--text-faint);
  transition: color 0.2s ease;
}

.frame-ribbon__item:hover .frame-ribbon__node {
  border-color: var(--accent);
  box-shadow: 0 0 0 3px var(--bg-page);
}

.frame-ribbon__item:hover .frame-ribbon__time {
  color: var(--text-main);
}

.frame-ribbon__item--active .frame-ribbon__node {
  background: var(--accent);
  border-color: var(--accent);
  transform: none;
  box-shadow: 0 0 0 3px var(--bg-page);
}

.frame-ribbon__item--active .frame-ribbon__time {
  color: var(--accent);
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

@media (max-width: 760px) {
  .timeline-panel__stats {
    grid-template-columns: 1fr;
  }
}
</style>
