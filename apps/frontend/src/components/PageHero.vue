<script setup lang="ts">
withDefaults(
  defineProps<{
    kicker: string
    title: string
    lede?: string
    layout?: 'default' | 'balanced'
  }>(),
  {
    layout: 'default',
  },
)
</script>

<template>
  <section class="page-hero glass-panel" :class="`page-hero--${layout}`">
    <svg class="page-hero__trace" viewBox="0 0 800 140" preserveAspectRatio="none" aria-hidden="true" focusable="false">
      <defs>
        <linearGradient id="pageHeroTrace" x1="0" y1="0" x2="1" y2="0">
          <stop offset="0" stop-color="#34e1d6" stop-opacity="0" />
          <stop offset="0.5" stop-color="#34e1d6" stop-opacity="0.9" />
          <stop offset="1" stop-color="#a07bff" stop-opacity="0.5" />
        </linearGradient>
      </defs>
      <path
        d="M0 70 H120 L150 36 L168 104 L196 70 H320 L348 50 L366 88 L388 30 L410 96 L432 70 H560 L588 54 L606 84 L628 40 L650 92 L672 70 H800"
      />
    </svg>

    <div class="page-hero__copy">
      <p class="section-kicker">{{ kicker }}</p>
      <h1 class="page-hero__title page-main-heading">{{ title }}</h1>
      <p v-if="lede" class="page-main-lede page-hero__lede">{{ lede }}</p>

      <div v-if="$slots.actions" class="page-hero__actions">
        <slot name="actions" />
      </div>

      <slot />
    </div>

    <div v-if="$slots.rail" class="page-hero__rail">
      <slot name="rail" />
    </div>
  </section>
</template>

<style scoped>
.page-hero {
  position: relative;
  overflow: hidden;
  display: grid;
  gap: 20px;
  padding: var(--space-hero);
  border-radius: var(--radius-hero);
  background:
    radial-gradient(120% 150% at 0% 0%, rgba(52, 225, 214, 0.13), transparent 46%),
    radial-gradient(120% 160% at 100% 0%, rgba(160, 123, 255, 0.13), transparent 50%),
    linear-gradient(180deg, rgba(18, 28, 47, 0.9), rgba(11, 17, 31, 0.92));
  animation: consoleRise 0.55s ease both;
}

.page-hero::after {
  content: '';
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(124, 200, 232, 0.05) 1px, transparent 1px),
    linear-gradient(90deg, rgba(124, 200, 232, 0.05) 1px, transparent 1px);
  background-size: 38px 38px;
  mask-image: radial-gradient(120% 100% at 100% 0%, black, transparent 62%);
  pointer-events: none;
}

.page-hero > * {
  position: relative;
  z-index: 1;
}

.page-hero__trace {
  position: absolute;
  top: 0;
  right: 0;
  width: min(70%, 560px);
  height: 140px;
  z-index: 0;
  opacity: 0.34;
  pointer-events: none;
  mask-image: linear-gradient(90deg, transparent, black 55%);
}

.page-hero__trace path {
  fill: none;
  stroke: url(#pageHeroTrace);
  stroke-width: 1.5;
  stroke-linejoin: round;
  stroke-linecap: round;
  stroke-dasharray: 1800;
  stroke-dashoffset: 1800;
  animation: traceFlow 3s ease-out 0.3s forwards;
  filter: drop-shadow(0 0 5px rgba(52, 225, 214, 0.4));
}

.page-hero__lede {
  max-width: 60ch;
}

.page-hero--default {
  grid-template-columns: minmax(0, 1.2fr) minmax(360px, 0.92fr);
}

.page-hero--balanced {
  grid-template-columns: minmax(0, 1.15fr) minmax(360px, 0.95fr);
}

.page-hero__copy,
.page-hero__rail {
  display: grid;
  gap: 18px;
}

.page-hero__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

@media (max-width: 1040px) {
  .page-hero {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .page-hero {
    padding: 20px;
  }

}
</style>
