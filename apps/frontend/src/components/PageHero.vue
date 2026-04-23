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
    <div class="page-hero__copy">
      <p class="section-kicker">{{ kicker }}</p>
      <h1 class="page-hero__title page-main-heading">{{ title }}</h1>

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
  display: grid;
  gap: 20px;
  padding: var(--space-hero);
  border-radius: var(--radius-hero);
  background:
    linear-gradient(180deg, rgba(246, 240, 232, 0.96), rgba(235, 227, 216, 0.92)),
    radial-gradient(circle at top left, rgba(49, 87, 102, 0.08), transparent 34%);
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
