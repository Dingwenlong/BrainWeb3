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
        <p class="section-kicker">活跃度读数</p>
        <h2 class="section-title">脑区指标</h2>
      </div>
      <div class="metrics__chips">
        <span class="status-chip">{{ formatBandLabel(band) }}</span>
        <span class="status-chip status-chip--ghost">{{ formatSecondsLabel(timestamp) }}</span>
      </div>
    </div>

    <div class="metrics__focus" v-if="focusedRegion" :style="{ '--region-tone': focusedRegion.toneCss }">
      <div class="metrics__focus-head">
        <p class="metrics__focus-kicker">{{ hoveredRegionCode ? '悬停脑区' : '主导脑区' }}</p>
        <span class="metrics__focus-code">{{ focusedRegion.code }}</span>
      </div>
      <h3>{{ formatRegionLabel(focusedRegion.code, focusedRegion.label) }}</h3>
      <strong class="metrics__focus-value">{{ focusedRegion.intensity.toFixed(2) }}</strong>
      <div class="metrics__electrodes">
        <span v-for="electrode in focusedRegion.electrodes" :key="electrode" class="metrics__electrode">
          {{ electrode }}
        </span>
      </div>
    </div>

    <div class="metrics__quality">
      <span>数据质量标记</span>
      <strong>{{ formatQualityFlags(qualityFlags) }}</strong>
    </div>

    <div class="metrics__ladder">
      <div
        v-for="bar in intensityBars"
        :key="bar.code"
        class="metrics__row"
        :class="{ 'metrics__row--active': bar.code === hoveredRegionCode }"
      >
        <div class="metrics__copy">
          <span class="metrics__region">
            <i class="metrics__dot" :style="{ background: bar.toneCss }"></i>
            {{ formatRegionLabel(bar.code, bar.label) }}
          </span>
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
  border-radius: var(--radius-block);
  padding: var(--space-card);
  background: var(--panel-soft-gradient);
  border: 1px solid var(--line);
  animation: consoleRise 0.5s ease both;
}

.metrics__focus {
  animation-delay: 0.06s;
}

.metrics__quality {
  animation-delay: 0.12s;
}

.metrics__ladder {
  animation-delay: 0.18s;
}

/* Focus readout — dominant / hovered region, signal-tinted instrument card */
.metrics__focus {
  position: relative;
  overflow: hidden;
  --region-tone: var(--accent);
  border-color: var(--line-warm);
  background:
    radial-gradient(120% 140% at 0% 0%, color-mix(in srgb, var(--region-tone) 14%, transparent), transparent 55%),
    var(--warm-panel-gradient);
  box-shadow:
    inset 0 0 0 1px rgba(52, 225, 214, 0.06),
    0 0 26px rgba(52, 225, 214, 0.05);
}

.metrics__focus::before {
  content: '';
  position: absolute;
  inset: 0 0 auto;
  height: 2px;
  background: linear-gradient(90deg, var(--region-tone), transparent 80%);
  box-shadow: 0 0 14px var(--region-tone);
  opacity: 0.85;
}

.metrics__focus-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.metrics__focus-kicker {
  margin: 0;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  color: var(--accent);
  font-family: var(--mono);
  font-size: 0.72rem;
  font-weight: 600;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.metrics__focus-kicker::before {
  content: '';
  width: 6px;
  height: 6px;
  border-radius: 999px;
  background: var(--region-tone);
  box-shadow: 0 0 10px var(--region-tone);
}

.metrics__focus-code {
  font-family: var(--mono);
  font-size: 0.72rem;
  letter-spacing: 0.1em;
  color: var(--text-faint);
}

.metrics__focus h3 {
  margin: 12px 0 0;
  color: var(--text-strong);
  font-family: var(--display);
  font-size: 1.4rem;
  font-weight: 600;
}

.metrics__focus-value {
  display: block;
  margin-top: 8px;
  font-family: var(--mono);
  font-size: 2.4rem;
  line-height: 1;
  color: var(--text-strong);
  text-shadow: 0 0 22px color-mix(in srgb, var(--region-tone) 45%, transparent);
}

.metrics__electrodes {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 14px;
}

.metrics__electrode {
  padding: 3px 9px;
  border-radius: var(--radius-pill);
  border: 1px solid var(--line-strong);
  background: rgba(8, 13, 26, 0.5);
  color: var(--text-main);
  font-family: var(--mono);
  font-size: 0.74rem;
  letter-spacing: 0.04em;
}

.metrics__quality span {
  color: var(--text-faint);
  font-family: var(--mono);
  font-size: 0.72rem;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.metrics__quality strong {
  display: block;
  margin-top: 10px;
  color: var(--text-main);
  font-family: var(--mono);
  font-size: 0.9rem;
  line-height: 1.6;
}

.metrics__ladder {
  display: grid;
  gap: 8px;
}

.metrics__row {
  display: grid;
  gap: 9px;
  padding: 11px 12px;
  border-radius: var(--radius-subpanel);
  border: 1px solid transparent;
  transition:
    border-color 0.2s ease,
    background 0.2s ease,
    box-shadow 0.2s ease;
}

.metrics__row:hover,
.metrics__row--active {
  border-color: rgba(52, 225, 214, 0.28);
  background: rgba(52, 225, 214, 0.04);
  box-shadow: 0 0 22px rgba(52, 225, 214, 0.08);
}

.metrics__copy {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.metrics__region {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  color: var(--text-main);
  font-size: 0.82rem;
}

.metrics__dot {
  width: 7px;
  height: 7px;
  border-radius: 999px;
  flex-shrink: 0;
  box-shadow: 0 0 8px currentColor;
}

.metrics__copy strong {
  font-family: var(--mono);
  font-size: 0.9rem;
  color: var(--text-strong);
}

.metrics__track {
  height: 8px;
  border-radius: 999px;
  background: var(--bg-panel-muted);
  border: 1px solid var(--line);
  overflow: hidden;
}

.metrics__fill {
  height: 100%;
  border-radius: inherit;
  box-shadow: 0 0 16px currentColor;
  transition: width 0.4s ease;
}

@media (max-width: 760px) {
  .metrics__heading {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
