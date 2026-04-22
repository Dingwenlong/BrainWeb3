<script setup lang="ts">
defineProps<{
  kicker: string
  title: string
  lede?: string
}>()
</script>

<template>
  <article class="surface-card glass-panel">
    <div class="surface-card__header">
      <div class="surface-card__heading">
        <p class="section-kicker">{{ kicker }}</p>
        <h2 class="section-title">{{ title }}</h2>
        <p v-if="lede" class="surface-card__lede">{{ lede }}</p>
      </div>

      <div v-if="$slots.meta" class="surface-card__meta">
        <slot name="meta" />
      </div>
    </div>

    <slot />

    <div v-if="$slots.note" class="surface-card__note">
      <slot name="note" />
    </div>
  </article>
</template>

<style scoped>
.surface-card {
  padding: 20px;
  border-radius: var(--radius-panel);
  background: var(--panel-gradient);
}

.surface-card__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.surface-card__heading {
  min-width: 0;
}

.surface-card__lede {
  margin: 10px 0 0;
  max-width: 52ch;
  color: var(--text-muted);
  font-size: 0.9rem;
  line-height: 1.7;
}

.surface-card__meta {
  display: inline-flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10px;
}

.surface-card__note {
  margin-top: 14px;
  color: var(--text-muted);
  font-size: 0.9rem;
  line-height: 1.7;
}

@media (max-width: 760px) {
  .surface-card__header {
    flex-direction: column;
  }

  .surface-card__meta {
    justify-content: flex-start;
  }
}
</style>
