<script setup lang="ts">
import AppToastStack from './components/AppToastStack.vue'
import ActorProfileSwitcher from './components/ActorProfileSwitcher.vue'
</script>

<template>
  <div class="app-shell">
    <header class="topbar glass-panel">
      <div class="topbar__brand">
        <RouterLink class="brandmark" to="/">
          <span class="brandmark__pulse"></span>
          <div>
            <strong>BrainWeb3</strong>
            <small>神经数据可信工作台</small>
          </div>
        </RouterLink>
        <p class="topbar__mission">上传、存证、审批、回放与审计统一在一套前端工作流中完成。</p>
      </div>

      <nav class="topnav">
        <RouterLink to="/">总览</RouterLink>
        <RouterLink to="/access-requests">审批台</RouterLink>
        <a href="https://physionet.org/content/eegmmidb/1.0.0/" target="_blank" rel="noreferrer">
          数据源
        </a>
      </nav>
    </header>

    <section class="actor-surface glass-panel">
      <div class="actor-surface__copy">
        <p class="section-kicker">当前身份</p>
        <h2>操作者与机构上下文</h2>
        <p>这里控制当前视角是谁，以及页面是否以研究员、归属方或审批人身份访问系统。</p>
      </div>

      <ActorProfileSwitcher />
    </section>

    <main class="app-main">
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
}

.topbar {
  width: min(1280px, 100%);
  margin: 0 auto 18px;
  display: grid;
  grid-template-columns: minmax(320px, 1fr) auto;
  gap: 18px;
  align-items: center;
  padding: 18px 20px;
  border-radius: 24px;
}

.topbar__brand {
  display: grid;
  gap: 8px;
}

.brandmark {
  display: inline-flex;
  align-items: center;
  gap: 14px;
  color: inherit;
  text-decoration: none;
}

.brandmark__pulse {
  width: 14px;
  height: 14px;
  border-radius: 999px;
  background: radial-gradient(circle, var(--amber) 0%, var(--accent) 72%);
  box-shadow: 0 0 18px rgba(110, 222, 227, 0.55);
}

.brandmark strong,
.brandmark small {
  display: block;
}

.brandmark strong {
  font-family: var(--display);
  font-size: 1rem;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.brandmark small,
.topbar__mission {
  color: var(--text-muted);
}

.topbar__mission {
  margin: 0;
  max-width: 54ch;
  font-size: 0.92rem;
}

.topnav {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
}

.topnav a {
  display: inline-flex;
  align-items: center;
  min-height: 38px;
  padding: 0 14px;
  border-radius: 999px;
  border: 1px solid transparent;
  color: var(--text-muted);
  text-decoration: none;
  font-family: var(--display);
  font-size: 0.78rem;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.topnav a.router-link-active {
  color: var(--text-main);
  border-color: var(--line);
  background: rgba(10, 32, 40, 0.78);
}

.actor-surface {
  width: min(1280px, 100%);
  margin: 0 auto 18px;
  display: grid;
  grid-template-columns: minmax(240px, 320px) minmax(0, 1fr);
  gap: 18px;
  padding: 18px 20px;
  border-radius: 24px;
}

.actor-surface__copy {
  display: grid;
  align-content: start;
  gap: 10px;
}

.actor-surface__copy h2 {
  margin: 0;
  font-family: var(--display);
  font-size: clamp(1.3rem, 2vw, 1.9rem);
  line-height: 1;
}

.actor-surface__copy p:last-child {
  margin: 0;
  color: var(--text-muted);
  line-height: 1.6;
}

.app-main {
  width: min(1280px, 100%);
  margin: 0 auto;
}

@media (max-width: 980px) {
  .topbar,
  .actor-surface {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .app-shell {
    padding-inline: 14px;
  }

  .topbar,
  .actor-surface {
    padding: 16px;
  }
}
</style>
