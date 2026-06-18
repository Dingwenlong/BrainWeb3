<script setup lang="ts">
import { computed, ref } from 'vue'

const props = withDefaults(
  defineProps<{
    value: string | null | undefined
    head?: number
    tail?: number
  }>(),
  { head: 10, tail: 0 },
)

const open = ref(false)
const val = computed(() => props.value ?? '')
const truncatable = computed(() => val.value.length > props.head + props.tail + 3)
const preview = computed(() => {
  if (!truncatable.value) {
    return val.value
  }
  const h = val.value.slice(0, props.head)
  const t = props.tail ? val.value.slice(-props.tail) : ''
  return props.tail ? `${h}…${t}` : `${h}…`
})
</script>

<template>
  <span class="hashval" :class="{ 'hashval--open': open }">
    <span class="hashval__text">{{ open ? val : preview }}</span>
    <button
      v-if="truncatable"
      type="button"
      class="hashval__toggle"
      :aria-expanded="open"
      :title="open ? '收起' : '展开完整值'"
      @click.stop="open = !open"
    >▾</button>
  </span>
</template>

<style scoped>
.hashval {
  display: inline-flex;
  align-items: baseline;
  gap: 5px;
  max-width: 100%;
  font-family: var(--mono);
}

.hashval__text {
  word-break: break-all;
  overflow-wrap: anywhere;
}

.hashval__toggle {
  flex: none;
  align-self: center;
  width: 16px;
  height: 16px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: 1px solid var(--line-strong);
  border-radius: 2px;
  background: var(--bg-panel-muted);
  color: var(--text-muted);
  font-size: 0.66rem;
  line-height: 1;
  transition:
    transform 0.14s ease,
    color 0.14s ease,
    border-color 0.14s ease;
}

.hashval__toggle:hover {
  color: var(--accent);
  border-color: var(--line-warm);
}

.hashval--open .hashval__toggle {
  transform: rotate(180deg);
}
</style>
