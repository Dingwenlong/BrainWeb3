<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppToastStack from './components/AppToastStack.vue'
import { useActorProfile } from './composables/useActorProfile'

const router = useRouter()
const route = useRoute()
const { actorProfile, displayName, logout } = useActorProfile()

const routeLabels: Record<string, string> = {
  login: '登录',
  overview: '总览',
  'dataset-detail': '数据详情',
  'access-requests': '访问申请',
  'destruction-requests': '销毁流程',
  accounts: '账户管理',
  'identity-center': '身份中心',
  audits: '审计中心',
  'chain-records': '链记录',
  'training-jobs': '训练任务',
  'model-records': '模型记录',
}

const currentSection = computed(() => routeLabels[String(route.name ?? 'overview')] ?? '工作区')
const isAuthRoute = computed(() => route.name === 'login')
const isPrivilegedActor = computed(() =>
  ['owner', 'approver', 'admin'].includes(actorProfile.value.actorRole.toLowerCase()),
)
const navigationGroups = computed(() => [
  {
    label: '工作台',
    items: [
      { to: '/', label: '总览', note: '首页与全局动态' },
      { to: '/training-jobs', label: '训练任务', note: '任务发起与进度' },
      { to: '/model-records', label: isPrivilegedActor.value ? '模型治理' : '模型记录', note: '模型版本与状态' },
    ],
  },
  {
    label: '治理',
    items: [
      { to: '/access-requests', label: isPrivilegedActor.value ? '访问申请' : '我的申请', note: '授权与审批流转' },
      { to: '/destruction-requests', label: isPrivilegedActor.value ? '销毁流程' : '销毁申请', note: '销毁申请与执行' },
      { to: '/audits', label: actorProfile.value.actorRole.toLowerCase() === 'admin' ? '监管审计' : '审计中心', note: '关键操作追踪' },
      ...(isPrivilegedActor.value
        ? [{ to: '/chain-records', label: actorProfile.value.actorRole.toLowerCase() === 'admin' ? '链记录监管' : '链记录', note: '上链状态与证明' }]
        : []),
    ],
  },
  {
    label: '系统',
    items: [
      { to: '/identity-center', label: actorProfile.value.actorRole.toLowerCase() === 'admin' ? '身份中心' : '我的身份', note: 'DID 与 VC 状态' },
      { to: '/accounts', label: actorProfile.value.actorRole.toLowerCase() === 'admin' ? '账户管理' : '我的账户', note: '账户与密码设置' },
    ],
  },
])

async function handleLogout() {
  logout()
  await router.replace({ name: 'login' })
}
</script>

<template>
  <div class="app-shell" :class="{ 'app-shell--auth': isAuthRoute, 'app-shell--workspace': !isAuthRoute }">
    <template v-if="!isAuthRoute">
      <header class="app-header">
        <RouterLink class="app-brand" to="/">
          <span class="app-brand__mark">BW</span>
          <span class="app-brand__copy">
            <strong>BrainWeb3</strong>
            <small>脑数据治理平台</small>
          </span>
        </RouterLink>

        <div class="app-header__title">
          <span>当前页面</span>
          <strong>{{ currentSection }}</strong>
        </div>

        <div class="app-header__actions">
          <a class="app-header__link" href="https://physionet.org/content/eegmmidb/1.0.0/" target="_blank" rel="noreferrer">
            公开数据源
          </a>
          <div class="app-header__account">
            <strong>{{ displayName || actorProfile.actorId }}</strong>
            <span>{{ actorProfile.actorRole }} · {{ actorProfile.actorOrg }}</span>
          </div>
          <RouterLink class="app-header__button" to="/accounts">账户</RouterLink>
          <button type="button" class="app-header__button app-header__button--primary" @click="handleLogout">
            退出
          </button>
        </div>
      </header>

      <aside class="app-sidebar">
        <div v-for="group in navigationGroups" :key="group.label" class="app-sidebar__group">
          <p class="app-sidebar__label">{{ group.label }}</p>
          <nav class="app-menu">
            <RouterLink v-for="item in group.items" :key="item.to" :to="item.to" class="app-menu__item">
              <strong>{{ item.label }}</strong>
            </RouterLink>
          </nav>
        </div>

        <div class="app-sidebar__footer">
          <p class="app-sidebar__label">当前会话</p>
          <dl class="session-card">
            <div>
              <dt>用户</dt>
              <dd>{{ displayName || actorProfile.actorId }}</dd>
            </div>
            <div>
              <dt>角色</dt>
              <dd>{{ actorProfile.actorRole }}</dd>
            </div>
            <div>
              <dt>机构</dt>
              <dd>{{ actorProfile.actorOrg }}</dd>
            </div>
          </dl>
        </div>
      </aside>
    </template>

    <main class="app-main">
      <div v-if="!isAuthRoute" class="app-main__intro">
        <p class="section-kicker">当前工作区</p>
        <h1>{{ currentSection }}</h1>
      </div>

      <RouterView />
    </main>

    <AppToastStack />
  </div>
</template>

<style scoped>
.app-shell {
  min-height: 100dvh;
}

.app-shell--auth {
  display: block;
  padding: 20px;
}

.app-shell--auth .app-main {
  min-height: calc(100dvh - 40px);
  padding: 0;
  background: transparent;
}

.app-shell--workspace {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  grid-template-rows: auto 1fr;
}

.app-header {
  grid-column: 1 / -1;
  position: sticky;
  top: 0;
  z-index: 20;
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 24px;
  padding: 16px 24px;
  border-bottom: 1px solid var(--line);
  background: rgba(242, 235, 226, 0.9);
  backdrop-filter: blur(14px);
}

.app-brand {
  display: inline-flex;
  align-items: center;
  gap: 14px;
  text-decoration: none;
}

.app-brand__mark {
  display: inline-grid;
  place-items: center;
  width: 42px;
  height: 42px;
  border-radius: 12px;
  background: linear-gradient(135deg, #1d4053, #305f63);
  color: #fffdf9;
  font-family: var(--body);
  font-size: 0.86rem;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.app-brand__copy {
  display: grid;
}

.app-brand__copy strong {
  font-size: 1rem;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.app-brand__copy small,
.app-header__title span,
.app-header__account span,
.app-sidebar__label,
.session-card dt {
  color: var(--text-muted);
  font-size: 0.78rem;
}

.app-header__title {
  display: grid;
  justify-items: start;
  gap: 2px;
}

.app-header__title strong {
  font-family: var(--display);
  font-size: 1.18rem;
  font-weight: 600;
}

.app-header__actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.app-header__link,
.app-header__button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 38px;
  padding: 0 14px;
  border-radius: 999px;
  border: 1px solid var(--line);
  background: rgba(238, 231, 221, 0.86);
  color: var(--text-main);
  text-decoration: none;
  font-size: 0.82rem;
}

.app-header__button--primary {
  border-color: rgba(29, 64, 83, 0.18);
  background: #1d4053;
  color: #fffdf9;
}

.app-header__account {
  display: grid;
  gap: 2px;
  padding: 0 4px;
}

.app-header__account strong,
.session-card dd {
  font-size: 0.88rem;
  font-weight: 600;
}

.app-sidebar {
  padding: 24px 18px 24px 24px;
  border-right: 1px solid var(--line);
  background: linear-gradient(180deg, rgba(242, 235, 226, 0.94), rgba(235, 227, 216, 0.9));
}

.app-sidebar__group + .app-sidebar__group {
  margin-top: 20px;
}

.app-sidebar__label {
  margin: 0 0 10px;
  font-family: var(--body);
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.app-menu {
  display: grid;
  gap: 6px;
}

.app-menu__item {
  display: grid;
  gap: 4px;
  padding: 12px 14px;
  border-radius: 16px;
  border: 1px solid transparent;
  text-decoration: none;
  color: var(--text-main);
}

.app-menu__item strong {
  font-size: 0.94rem;
  font-weight: 600;
}

.app-menu__item small {
  color: var(--text-muted);
  font-size: 0.8rem;
  line-height: 1.45;
}

.app-menu__item:hover {
  background: rgba(236, 228, 217, 0.88);
  border-color: rgba(29, 64, 83, 0.08);
}

.app-menu__item.router-link-active {
  background: rgba(241, 234, 224, 0.92);
  border-color: rgba(29, 64, 83, 0.12);
  box-shadow: 0 12px 24px rgba(84, 71, 50, 0.08);
}

.app-sidebar__footer {
  margin-top: 28px;
  padding-top: 20px;
  border-top: 1px solid var(--line);
}

.session-card {
  display: grid;
  gap: 10px;
  margin: 0;
}

.session-card div {
  padding: 12px 14px;
  border-radius: 14px;
  background: rgba(238, 231, 220, 0.88);
  border: 1px solid rgba(29, 64, 83, 0.08);
}

.session-card dt,
.session-card dd {
  margin: 0;
}

.session-card dd {
  margin-top: 6px;
  color: var(--text-strong);
  line-height: 1.5;
}

.app-main {
  min-width: 0;
  padding: 24px;
  background: linear-gradient(180deg, rgba(244, 238, 230, 0.44), rgba(239, 231, 220, 0.18));
}

.app-main__intro {
  display: grid;
  gap: 8px;
  margin-bottom: 20px;
}

.app-main__intro h1 {
  margin: 0;
  font-family: var(--display);
  font-size: clamp(1.8rem, 2.6vw, 2.4rem);
  font-weight: 600;
  line-height: 1.1;
}

.app-main__intro p:last-child {
  margin: 0;
  color: var(--text-muted);
  line-height: 1.7;
}

@media (max-width: 1080px) {
  .app-shell--workspace {
    grid-template-columns: 1fr;
    grid-template-rows: auto auto 1fr;
  }

  .app-header {
    grid-template-columns: 1fr;
  }

  .app-sidebar {
    padding-top: 18px;
    border-right: 0;
    border-bottom: 1px solid var(--line);
  }
}

@media (max-width: 760px) {
  .app-shell--auth {
    padding: 14px;
  }

  .app-shell--auth .app-main {
    min-height: calc(100dvh - 28px);
  }

  .app-header,
  .app-main {
    padding: 16px;
  }

  .app-header__actions {
    flex-wrap: wrap;
    justify-content: flex-start;
  }

  .app-header__account {
    width: 100%;
  }
}
</style>
