<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import AppToastStack from './components/AppToastStack.vue'
import ActorProfileSwitcher from './components/ActorProfileSwitcher.vue'
import { useActorProfile } from './composables/useActorProfile'

const route = useRoute()
const { actorProfile } = useActorProfile()

const routeLabels: Record<string, string> = {
  login: '登录闸门',
  overview: '总览扇区',
  'dataset-detail': '数据舱',
  'access-requests': '审批台',
  'destruction-requests': '销毁台',
  accounts: '账户域',
  'identity-center': '身份中枢',
  audits: '审计中心',
  'chain-records': '链轨迹',
  'training-jobs': '训练编排',
  'model-records': '模型库',
}

const currentSection = computed(() => routeLabels[String(route.name ?? 'overview')] ?? '控制台')
const isAuthRoute = computed(() => route.name === 'login')
const isPrivilegedActor = computed(() =>
  ['owner', 'approver', 'admin'].includes(actorProfile.value.actorRole.toLowerCase()),
)
const navigationLinks = computed(() => [
  { to: '/', label: '总览' },
  { to: '/training-jobs', label: '训练' },
  { to: '/model-records', label: isPrivilegedActor.value ? '模型治理' : '模型库' },
  { to: '/access-requests', label: isPrivilegedActor.value ? '审批台' : '我的申请' },
  { to: '/destruction-requests', label: isPrivilegedActor.value ? '销毁台' : '销毁申请' },
  { to: '/identity-center', label: actorProfile.value.actorRole.toLowerCase() === 'admin' ? '身份中心' : '我的身份' },
  { to: '/audits', label: actorProfile.value.actorRole.toLowerCase() === 'admin' ? '监管审计' : '审计' },
  ...(isPrivilegedActor.value
    ? [{ to: '/chain-records', label: actorProfile.value.actorRole.toLowerCase() === 'admin' ? '链监管' : '链轨迹' }]
    : []),
  { to: '/accounts', label: actorProfile.value.actorRole.toLowerCase() === 'admin' ? '账户治理' : '我的账户' },
])

const workflowNodes = [
  { label: 'Upload', note: '数据接入' },
  { label: 'Proof', note: '链上存证' },
  { label: 'Govern', note: '权限审批' },
  { label: 'Train', note: '联邦训练' },
  { label: 'Audit', note: '审计追踪' },
]
</script>

<template>
  <div class="app-shell">
    <div class="app-shell__glow app-shell__glow--left"></div>
    <div class="app-shell__glow app-shell__glow--right"></div>

    <header v-if="!isAuthRoute" class="command-deck glass-panel">
      <div class="command-deck__brand">
        <p class="command-deck__eyebrow">
          <span class="command-deck__pulse"></span>
          Neural Data Trust Console
        </p>

        <RouterLink class="brandmark" to="/">
          <div class="brandmark__core">
            <span class="brandmark__ring"></span>
            <span class="brandmark__beam"></span>
          </div>
          <div>
            <strong>BrainWeb3</strong>
            <small>神经数据可信工作台</small>
          </div>
        </RouterLink>

        <p class="command-deck__mission">上传、存证、审批、回放与审计统一在一套控制台叙事中完成。</p>
      </div>

      <div class="command-deck__nav">
        <nav class="topnav">
          <RouterLink v-for="item in navigationLinks" :key="item.to" :to="item.to">{{ item.label }}</RouterLink>
          <a href="https://physionet.org/content/eegmmidb/1.0.0/" target="_blank" rel="noreferrer">
            数据源
          </a>
        </nav>

        <div class="workflow-strip">
          <div class="workflow-strip__intro">
            <span>当前扇区</span>
            <strong>{{ currentSection }}</strong>
          </div>
          <div class="workflow-strip__nodes">
            <div v-for="node in workflowNodes" :key="node.label" class="workflow-strip__node">
              <strong>{{ node.label }}</strong>
              <span>{{ node.note }}</span>
            </div>
          </div>
        </div>
      </div>
    </header>

    <section v-if="!isAuthRoute" class="actor-surface glass-panel">
      <div class="actor-surface__copy">
        <p class="section-kicker">Identity Mesh</p>
        <h2>操作者与机构上下文</h2>
        <p>当前工作台已经切到正式登录态，操作者身份来自会话本身，不再依赖手工伪造请求头。</p>
        <div class="actor-surface__rail">
          <div class="actor-surface__rail-item">
            <span>Workspace</span>
            <strong>{{ currentSection }}</strong>
          </div>
          <div class="actor-surface__rail-item">
            <span>Flow Bias</span>
            <strong>JWT Session</strong>
          </div>
        </div>
      </div>

      <ActorProfileSwitcher />
    </section>

    <main class="app-main" :class="{ 'app-main--auth': isAuthRoute }">
      <RouterView />
    </main>

    <AppToastStack />
  </div>
</template>

<style scoped>
.app-shell {
  position: relative;
  min-height: 100vh;
  padding: 20px 20px 40px;
  overflow: hidden;
}

.app-shell__glow {
  position: fixed;
  width: 34vw;
  height: 34vw;
  border-radius: 999px;
  filter: blur(90px);
  pointer-events: none;
  opacity: 0.22;
}

.app-shell__glow--left {
  top: -10vw;
  left: -8vw;
  background: radial-gradient(circle, rgba(116, 210, 220, 0.4), transparent 70%);
}

.app-shell__glow--right {
  top: 10vh;
  right: -12vw;
  background: radial-gradient(circle, rgba(235, 178, 102, 0.28), transparent 74%);
}

.command-deck {
  width: min(1280px, 100%);
  margin: 0 auto 18px;
  display: grid;
  grid-template-columns: minmax(320px, 1.1fr) minmax(360px, 1fr);
  gap: 22px;
  padding: 22px 24px;
  border-radius: 30px;
  background:
    linear-gradient(140deg, rgba(8, 22, 30, 0.96), rgba(5, 15, 21, 0.86)),
    radial-gradient(circle at top right, rgba(116, 210, 220, 0.08), transparent 32%);
}

.command-deck__brand,
.command-deck__nav {
  display: grid;
  gap: 14px;
}

.command-deck__eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  margin: 0;
  color: var(--text-faint);
  font-family: var(--display);
  font-size: 0.72rem;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

.command-deck__pulse {
  width: 9px;
  height: 9px;
  border-radius: 999px;
  background: radial-gradient(circle, var(--amber), var(--accent));
  box-shadow: 0 0 16px rgba(116, 210, 220, 0.58);
}

.brandmark {
  display: inline-flex;
  align-items: center;
  gap: 18px;
  color: inherit;
  text-decoration: none;
}

.brandmark__core {
  position: relative;
  width: 62px;
  height: 62px;
  border-radius: 18px;
  background:
    radial-gradient(circle at 50% 50%, rgba(116, 210, 220, 0.22), rgba(7, 17, 24, 0.1) 60%),
    rgba(5, 15, 21, 0.88);
  border: 1px solid rgba(108, 166, 186, 0.18);
  overflow: hidden;
}

.brandmark__ring,
.brandmark__beam {
  position: absolute;
  inset: 0;
  margin: auto;
  border-radius: 999px;
}

.brandmark__ring {
  width: 34px;
  height: 34px;
  border: 1px solid rgba(116, 210, 220, 0.38);
  box-shadow: inset 0 0 20px rgba(116, 210, 220, 0.08);
}

.brandmark__beam {
  width: 10px;
  height: 42px;
  background: linear-gradient(180deg, rgba(235, 178, 102, 0.9), rgba(116, 210, 220, 0.18));
  filter: blur(0.4px);
  transform: rotate(34deg);
}

.brandmark strong,
.brandmark small {
  display: block;
}

.brandmark strong {
  font-family: var(--display);
  font-size: clamp(1.24rem, 2vw, 1.46rem);
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

.brandmark small,
.command-deck__mission {
  color: var(--text-muted);
}

.command-deck__mission {
  margin: 0;
  max-width: 58ch;
  font-size: 0.96rem;
  line-height: 1.7;
}

.topnav {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  justify-content: flex-end;
}

.topnav a {
  display: inline-flex;
  align-items: center;
  min-height: 40px;
  padding: 0 15px;
  border-radius: 999px;
  border: 1px solid rgba(108, 166, 186, 0.14);
  background: rgba(5, 18, 24, 0.56);
  color: var(--text-muted);
  text-decoration: none;
  font-family: var(--display);
  font-size: 0.76rem;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.topnav a.router-link-active {
  color: var(--text-main);
  border-color: rgba(235, 178, 102, 0.32);
  background: linear-gradient(180deg, rgba(235, 178, 102, 0.16), rgba(12, 24, 32, 0.88));
}

.workflow-strip {
  display: grid;
  gap: 14px;
  padding: 16px 18px;
  border-radius: 24px;
  border: 1px solid rgba(108, 166, 186, 0.14);
  background:
    linear-gradient(180deg, rgba(3, 13, 18, 0.92), rgba(5, 15, 22, 0.7)),
    radial-gradient(circle at top right, rgba(116, 210, 220, 0.12), transparent 34%);
}

.workflow-strip__intro {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.workflow-strip__intro span,
.workflow-strip__node span,
.actor-surface__rail-item span {
  color: var(--text-faint);
  font-size: 0.72rem;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.workflow-strip__intro strong {
  font-family: var(--display);
  font-size: 0.94rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.workflow-strip__nodes {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
}

.workflow-strip__node {
  display: grid;
  gap: 6px;
  padding: 12px 14px;
  border-radius: 18px;
  border: 1px solid rgba(108, 166, 186, 0.12);
  background: rgba(8, 21, 28, 0.82);
}

.workflow-strip__node strong,
.actor-surface__rail-item strong {
  font-family: var(--display);
  font-size: 0.88rem;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.actor-surface {
  width: min(1280px, 100%);
  margin: 0 auto 18px;
  display: grid;
  grid-template-columns: minmax(280px, 360px) minmax(0, 1fr);
  gap: 18px;
  padding: 22px 24px;
  border-radius: 28px;
  background:
    linear-gradient(180deg, rgba(9, 19, 26, 0.92), rgba(7, 16, 22, 0.82)),
    radial-gradient(circle at left center, rgba(116, 210, 220, 0.08), transparent 30%);
}

.actor-surface__copy {
  display: grid;
  align-content: start;
  gap: 12px;
}

.actor-surface__copy h2 {
  margin: 0;
  font-family: var(--display);
  font-size: clamp(1.42rem, 2vw, 2rem);
  line-height: 1;
}

.actor-surface__copy p:last-of-type {
  margin: 0;
  color: var(--text-muted);
  line-height: 1.7;
}

.actor-surface__rail {
  display: grid;
  gap: 10px;
  margin-top: 4px;
}

.actor-surface__rail-item {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  border-radius: 18px;
  border: 1px solid rgba(108, 166, 186, 0.12);
  background: rgba(6, 18, 24, 0.8);
}

.app-main {
  width: min(1280px, 100%);
  margin: 0 auto;
}

.app-main--auth {
  width: min(1100px, 100%);
}

@media (max-width: 1080px) {
  .command-deck,
  .actor-surface {
    grid-template-columns: 1fr;
  }

  .topnav {
    justify-content: flex-start;
  }
}

@media (max-width: 760px) {
  .app-shell {
    padding-inline: 14px;
  }

  .command-deck,
  .actor-surface {
    padding: 18px;
  }

  .workflow-strip__intro,
  .actor-surface__rail-item {
    flex-direction: column;
    align-items: flex-start;
  }

  .workflow-strip__nodes {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 560px) {
  .brandmark {
    align-items: flex-start;
  }

  .brandmark__core {
    width: 54px;
    height: 54px;
  }

  .workflow-strip__nodes {
    grid-template-columns: 1fr;
  }
}
</style>
