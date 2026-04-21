<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useActorProfile } from '../composables/useActorProfile'
import { formatOrganizationLabel, formatRoleLabel } from '../utils/labels'

const router = useRouter()
const { actorProfile, displayName, isAdmin, logout } = useActorProfile()

async function handleLogout() {
  logout()
  await router.replace({ name: 'login' })
}

async function openAccounts() {
  await router.push({ name: 'accounts' })
}
</script>

<template>
  <div class="switcher">
    <div class="switcher__summary">
      <div class="switcher__summary-card">
        <span>当前会话</span>
        <strong>{{ displayName || actorProfile.actorId }}</strong>
      </div>
      <div class="switcher__summary-card">
        <span>角色</span>
        <strong>{{ formatRoleLabel(actorProfile.actorRole) }}</strong>
      </div>
      <div class="switcher__summary-card">
        <span>机构</span>
        <strong>{{ formatOrganizationLabel(actorProfile.actorOrg) }}</strong>
      </div>
    </div>

    <div class="switcher__presets">
      <article class="switcher__preset switcher__preset--active">
        <strong>正式登录态</strong>
        <p>当前操作者上下文来自后端签发的 JWT，会随所有受保护接口一起发送。</p>
      </article>
      <article class="switcher__preset">
        <strong>Actor ID</strong>
        <p>{{ actorProfile.actorId }}</p>
      </article>
      <article class="switcher__preset">
        <strong>组织边界</strong>
        <p>{{ formatOrganizationLabel(actorProfile.actorOrg) }}</p>
      </article>
    </div>

    <div class="switcher__actions">
      <button type="button" class="switcher__secondary" @click="openAccounts">
        {{ isAdmin ? '管理账户' : '查看账户' }}
      </button>
      <button type="button" class="switcher__apply" @click="handleLogout">退出登录</button>
    </div>
  </div>
</template>

<style scoped>
.switcher {
  display: grid;
  gap: 14px;
}

.switcher__summary,
.switcher__presets {
  display: grid;
  gap: 12px;
}

.switcher__summary {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.switcher__summary-card,
.switcher__preset {
  padding: 14px 16px;
  border-radius: 16px;
  border: 1px solid rgba(110, 222, 227, 0.1);
  background: var(--bg-panel-soft);
}

.switcher__summary-card span {
  display: block;
  color: var(--text-faint);
  font-size: 0.72rem;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.switcher__summary-card strong,
.switcher__preset strong {
  display: block;
  margin-top: 10px;
  font-family: var(--display);
}

.switcher__presets {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.switcher__preset {
  text-align: left;
}

.switcher__preset--active {
  border-color: var(--line-strong);
  box-shadow: inset 0 0 0 1px rgba(110, 222, 227, 0.12);
}

.switcher__preset p {
  margin: 8px 0 0;
  color: var(--text-muted);
  font-size: 0.84rem;
  line-height: 1.45;
}

.switcher__actions {
  display: flex;
  justify-content: flex-end;
}

.switcher__apply {
  min-height: 42px;
  padding: 0 16px;
  border: 1px solid var(--line-warm);
  border-radius: 999px;
  background: linear-gradient(180deg, rgba(255, 180, 108, 0.22), rgba(255, 180, 108, 0.12));
  color: var(--text-main);
  font-family: var(--display);
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.switcher__secondary {
  min-height: 42px;
  padding: 0 16px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: rgba(12, 24, 32, 0.92);
  color: var(--text-main);
  font-family: var(--display);
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

@media (max-width: 900px) {
  .switcher__summary,
  .switcher__presets {
    grid-template-columns: 1fr;
  }

  .switcher__actions {
    justify-content: stretch;
  }

  .switcher__apply {
    width: 100%;
  }
}
</style>
