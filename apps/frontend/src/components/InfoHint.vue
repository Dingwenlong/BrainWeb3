<script setup lang="ts">
defineProps<{ text: string }>()
</script>

<template>
  <span class="infohint" tabindex="0" role="note" :aria-label="text">
    <span class="infohint__icon" aria-hidden="true">i</span>
    <span class="infohint__pop" role="tooltip">{{ text }}</span>
  </span>
</template>

<style scoped>
.infohint {
  position: relative;
  display: inline-flex;
  align-items: center;
  vertical-align: middle;
  cursor: help;
  outline: none;
}

.infohint__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 15px;
  height: 15px;
  border: 1px solid var(--line-strong);
  border-radius: 999px;
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: 0.64rem;
  font-style: italic;
  line-height: 1;
  transition:
    color 0.14s ease,
    border-color 0.14s ease;
}

.infohint:hover .infohint__icon,
.infohint:focus-visible .infohint__icon {
  color: var(--accent);
  border-color: var(--line-warm);
}

.infohint__pop {
  position: absolute;
  bottom: calc(100% + 6px);
  left: 50%;
  transform: translateX(-50%);
  z-index: 40;
  width: max-content;
  max-width: 260px;
  padding: 8px 10px;
  border: 1px solid var(--line-strong);
  border-radius: var(--radius-subpanel);
  background: var(--bg-panel-muted);
  color: var(--text-main);
  font-family: var(--body);
  font-size: 0.78rem;
  font-weight: 400;
  line-height: 1.55;
  text-transform: none;
  letter-spacing: normal;
  white-space: normal;
  text-align: left;
  opacity: 0;
  visibility: hidden;
  transition: opacity 0.14s ease;
  pointer-events: none;
}

.infohint:hover .infohint__pop,
.infohint:focus-visible .infohint__pop {
  opacity: 1;
  visibility: visible;
}
</style>
