<script setup lang="ts">
import { useToast } from '../composables/useToast'

const { toasts, removeToast } = useToast()
</script>

<template>
  <div class="toast-stack" v-if="toasts.length">
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
  </div>
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
  padding: 16px 18px;
  border-radius: 20px;
  background: rgba(6, 22, 29, 0.92);
}

.toast-card--success {
  border-color: rgba(119, 235, 237, 0.26);
}

.toast-card--warning {
  border-color: rgba(255, 187, 112, 0.3);
}

.toast-card__title {
  margin: 0;
  font-family: var(--display);
  font-size: 0.92rem;
  letter-spacing: 0.06em;
  text-transform: uppercase;
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
  font-family: var(--display);
  font-size: 0.74rem;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}
</style>
