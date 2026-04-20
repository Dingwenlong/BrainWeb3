<script setup lang="ts">
import { computed } from 'vue'
import type { ActivityFrame, BrainRegion } from '../types/api'
import { formatBandLabel, formatQualityFlags, formatRegionLabel, formatSecondsLabel } from '../utils/labels'

const props = defineProps<{
  regions: BrainRegion[]
  frame: ActivityFrame | null
  hoveredRegionCode: string | null
  qualityFlags: string[]
  band: string
  timestamp: number
}>()

const signalRegions = computed(() =>
  props.regions.map((region) => {
    const intensity = props.frame?.intensities[region.code] ?? 0
    const normalized = Math.max(0.04, Math.min(1, intensity))
    const hue = 194 - normalized * 148

    return {
      ...region,
      intensity,
      normalized,
      toneCss: `hsl(${hue}, 92%, ${44 + normalized * 15}%)`,
    }
  }),
)

const focusedRegion = computed(() => {
  const direct = signalRegions.value.find((region) => region.code === props.hoveredRegionCode)
  if (direct) {
    return direct
  }

  const [first, ...rest] = signalRegions.value
  if (!first) {
    return null
  }

  return rest.reduce((strongest, region) => {
    if (region.intensity > strongest.intensity) {
      return region
    }
    return strongest
  }, first)
})

const intensityBars = computed(() =>
  signalRegions.value
    .slice()
    .sort((left, right) => right.intensity - left.intensity)
    .map((region) => ({
      ...region,
      width: `${Math.max(8, region.normalized * 100)}%`,
    })),
)
</script>

<template>
  <section class="metrics">
    <div class="metrics__heading">
      <div>
        <p class="section-kicker">神经读数</p>
        <h2 class="section-title">脑区指标</h2>
      </div>
      <div class="metrics__chips">
        <span class="status-chip">{{ formatBandLabel(band) }}</span>
        <span class="status-chip">{{ formatSecondsLabel(timestamp) }}</span>
      </div>
    </div>

    <div class="metrics__focus" v-if="focusedRegion">
      <p class="metrics__focus-kicker">{{ hoveredRegionCode ? '悬停脑区' : '主导脑区' }}</p>
      <h3>{{ formatRegionLabel(focusedRegion.code, focusedRegion.label) }}</h3>
      <strong>{{ focusedRegion.intensity.toFixed(2) }}</strong>
      <p>{{ focusedRegion.electrodes.join(', ') }}</p>
    </div>

    <div class="metrics__quality">
      <span>数据质量标记</span>
      <strong>{{ formatQualityFlags(qualityFlags) }}</strong>
    </div>

    <div class="metrics__ladder">
      <div v-for="bar in intensityBars" :key="bar.code" class="metrics__row">
        <div class="metrics__copy">
          <span>{{ formatRegionLabel(bar.code, bar.label) }}</span>
          <strong>{{ bar.intensity.toFixed(2) }}</strong>
        </div>
        <div class="metrics__track">
          <div class="metrics__fill" :style="{ width: bar.width, background: bar.toneCss }"></div>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.metrics {
  display: grid;
  gap: 16px;
}

.metrics__heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.metrics__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.metrics__focus,
.metrics__quality,
.metrics__ladder {
  border-radius: 22px;
  padding: 16px;
  background: rgba(3, 14, 20, 0.82);
  border: 1px solid rgba(119, 235, 237, 0.08);
}

.metrics__focus-kicker {
  margin: 0;
  color: var(--amber);
  font-family: var(--display);
  font-size: 0.72rem;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.metrics__focus h3 {
  margin: 10px 0 0;
  font-family: var(--display);
  font-size: 1.4rem;
}

.metrics__focus strong {
  display: block;
  margin-top: 6px;
  font-family: var(--display);
  font-size: 2.2rem;
  line-height: 1;
}

.metrics__focus p,
.metrics__quality span {
  margin: 10px 0 0;
  color: var(--text-muted);
}

.metrics__quality strong {
  display: block;
  margin-top: 10px;
  color: var(--text-main);
  font-family: var(--display);
  font-size: 0.92rem;
  line-height: 1.5;
}

.metrics__ladder {
  display: grid;
  gap: 10px;
}

.metrics__row {
  display: grid;
  gap: 8px;
}

.metrics__copy {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.metrics__copy span {
  color: var(--text-muted);
  font-size: 0.78rem;
}

.metrics__copy strong {
  font-family: var(--display);
  font-size: 0.88rem;
}

.metrics__track {
  height: 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.06);
  overflow: hidden;
}

.metrics__fill {
  height: 100%;
  border-radius: inherit;
  box-shadow: 0 0 20px currentColor;
}

@media (max-width: 760px) {
  .metrics__heading {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
