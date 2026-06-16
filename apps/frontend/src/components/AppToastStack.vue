<script setup lang="ts">
import { useToast } from '../composables/useToast'

const { toasts, removeToast } = useToast()
</script>

<template>
  <TransitionGroup tag="div" name="toast" class="toast-stack">
    <article
      v-for="toast in toasts"
      :key="toast.id"
      class="toast-card glass-panel"
      :class="`toast-card--${toast.tone}`"
    >
      <div>
        <p class="toast-card__title">{{ toast.title }}</p>
        <p class="toast-card__message">{{ toast.message }}</p>
      </div>
      <button type="button" class="toast-card__dismiss" @click="removeToast(toast.id)">关闭</button>
    </article>
  </TransitionGroup>
</template>

<style scoped>
.toast-stack {
  position: fixed;
  right: 22px;
  bottom: 22px;
  z-index: 50;
  display: grid;
  gap: 12px;
  width: min(360px, calc(100vw - 32px));
}

.toast-card {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 15px 18px;
  border-radius: var(--radius-panel);
  border-left: 2px solid var(--accent);
  background: linear-gradient(180deg, rgba(19, 29, 49, 0.97), rgba(12, 19, 34, 0.97));
  box-shadow: var(--shadow-soft), 0 0 24px rgba(52, 225, 214, 0.08);
  animation: consoleRise 0.32s ease both;
}

.toast-card--success {
  border-left-color: var(--accent);
}

.toast-card--warning {
  border-left-color: var(--amber);
  box-shadow: var(--shadow-soft), 0 0 24px rgba(242, 178, 89, 0.1);
}

.toast-card--error,
.toast-card--danger {
  border-left-color: var(--danger);
  box-shadow: var(--shadow-soft), 0 0 24px rgba(255, 97, 115, 0.12);
}

.toast-card__title {
  margin: 0;
  color: var(--text-strong);
  font-family: var(--body);
  font-size: 0.92rem;
  font-weight: 700;
}

.toast-card__message {
  margin: 8px 0 0;
  color: var(--text-muted);
  font-size: 0.9rem;
  line-height: 1.45;
}

.toast-card__dismiss {
  border: 0;
  background: transparent;
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: 0.72rem;
  font-weight: 600;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.toast-card__dismiss:hover {
  color: var(--accent);
}

/* Non-color cue per tone (so meaning never rides on hue alone) */
.toast-card__title::before {
  margin-right: 8px;
  font-weight: 700;
}

.toast-card--success .toast-card__title::before {
  content: '✓';
  color: var(--accent);
}

.toast-card--warning .toast-card__title::before {
  content: '!';
  color: var(--amber);
}

.toast-card--error .toast-card__title::before,
.toast-card--danger .toast-card__title::before {
  content: '✕';
  color: var(--danger);
}

/* Smooth stacked enter/leave/reorder */
.toast-leave-active {
  transition:
    opacity 0.28s ease,
    transform 0.28s ease;
}

.toast-leave-to {
  opacity: 0;
  transform: translateX(24px);
}

.toast-move {
  transition: transform 0.28s ease;
}
</style>
