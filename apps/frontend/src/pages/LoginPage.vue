<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { confirmPasswordReset, requestPasswordResetTicket } from '../api/client'
import { useActorProfile } from '../composables/useActorProfile'
import { useToast } from '../composables/useToast'
import type { PasswordResetTicket } from '../types/api'
import { formatOrganizationLabel, formatRoleLabel } from '../utils/labels'

const demoAccounts = [
  {
    actorId: 'researcher-01',
    actorRole: 'researcher',
    actorOrg: 'Sichuan Neuro Lab',
    description: '提交访问申请，并在获批后查看脑区活跃度与数据详情。',
  },
  {
    actorId: 'owner-01',
    actorRole: 'owner',
    actorOrg: 'Huaxi Medical Union',
    description: '代表归属机构审批请求、巡检审计事件并直接查看资产。',
  },
  {
    actorId: 'approver-01',
    actorRole: 'approver',
    actorOrg: 'Huaxi Medical Union',
    description: '专注处理审批流与权限决策，验证门禁是否生效。',
  },
]

const router = useRouter()
const route = useRoute()
const { login, register } = useActorProfile()
const { pushToast } = useToast()

const form = reactive({
  actorId: demoAccounts[0].actorId,
  password: 'brainweb3-demo',
})

const registerForm = reactive({
  actorId: '',
  displayName: '',
  actorOrg: 'Sichuan Neuro Lab',
  password: 'brainweb3-demo',
})

const forgotForm = reactive({
  actorId: '',
  nextPassword: 'brainweb3-reset',
})

const submitting = ref(false)
const registerSubmitting = ref(false)
const forgotSubmitting = ref(false)
const resetSubmitting = ref(false)
const error = ref<string | null>(null)
const registerError = ref<string | null>(null)
const forgotError = ref<string | null>(null)
const forgotTicket = ref<PasswordResetTicket | null>(null)

function applyAccount(actorId: string) {
  form.actorId = actorId
}

async function handleLogin() {
  submitting.value = true
  error.value = null

  try {
    await login(form.actorId.trim(), form.password)
    pushToast({
      title: '登录成功',
      message: '当前会话已切换到正式 JWT 身份态。',
      tone: 'success',
    })
    await router.replace(String(route.query.redirect || '/'))
  } catch (loginError) {
    error.value = loginError instanceof Error ? loginError.message : '登录失败。'
  } finally {
    submitting.value = false
  }
}

async function handleRegister() {
  registerSubmitting.value = true
  registerError.value = null

  try {
    await register({
      actorId: registerForm.actorId.trim(),
      displayName: registerForm.displayName.trim(),
      actorOrg: registerForm.actorOrg.trim(),
      password: registerForm.password,
    })
    pushToast({
      title: '注册成功',
      message: '新账户已创建，并已自动进入正式会话。',
      tone: 'success',
    })
    await router.replace(String(route.query.redirect || '/'))
  } catch (registerFailure) {
    registerError.value = registerFailure instanceof Error ? registerFailure.message : '注册失败。'
  } finally {
    registerSubmitting.value = false
  }
}

async function handlePasswordResetRequest() {
  forgotSubmitting.value = true
  forgotError.value = null

  try {
    forgotTicket.value = await requestPasswordResetTicket({
      actorId: forgotForm.actorId.trim(),
    })
    pushToast({
      title: '恢复票据已生成',
      message: forgotTicket.value.tokenVisible
        ? '演示模式下，重置票据会直接显示在登录页，用于模拟站外投递链路。'
        : '当前环境已切到站外投递模式，登录页不会直接显示恢复票据。',
      tone: forgotTicket.value.tokenVisible ? 'warning' : 'success',
    })
  } catch (requestError) {
    forgotTicket.value = null
    forgotError.value = requestError instanceof Error ? requestError.message : '申请恢复票据失败。'
  } finally {
    forgotSubmitting.value = false
  }
}

async function handlePasswordResetConfirm() {
  if (!forgotTicket.value?.resetToken) {
    return
  }

  resetSubmitting.value = true
  forgotError.value = null

  try {
    await confirmPasswordReset({
      resetToken: forgotTicket.value.resetToken,
      nextPassword: forgotForm.nextPassword,
    })
    form.actorId = forgotTicket.value.actorId
    form.password = forgotForm.nextPassword
    forgotTicket.value = null
    pushToast({
      title: '密码已恢复',
      message: '密码已通过重置票据更新，登录表单也已自动填入新密码。',
      tone: 'success',
    })
  } catch (confirmError) {
    forgotError.value = confirmError instanceof Error ? confirmError.message : '确认重置失败。'
  } finally {
    resetSubmitting.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <section class="login-shell glass-panel">
      <div class="login-copy">
        <div class="login-copy__masthead">
          <p class="section-kicker">登录入口</p>
          <span class="login-copy__stamp">JWT 会话</span>
        </div>
        <h1 class="page-main-heading">BrainWeb3 脑数据治理平台</h1>
        <p class="page-main-lede login-copy__lede">演示身份默认密码：<code>brainweb3-demo</code></p>

        <div class="login-accounts__header">
          <div>
            <p class="section-kicker">演示身份集</p>
            <h2 class="section-title">演示身份档案</h2>
          </div>
        </div>
        <div class="login-accounts">
          <button
            v-for="account in demoAccounts"
            :key="account.actorId"
            type="button"
            class="login-accounts__item"
            :class="{ 'login-accounts__item--active': account.actorId === form.actorId }"
            @click="applyAccount(account.actorId)"
          >
            <div class="login-accounts__meta">
              <strong>{{ formatRoleLabel(account.actorRole) }}</strong>
              <span>{{ account.actorId }}</span>
            </div>
            <small class="login-accounts__org">{{ formatOrganizationLabel(account.actorOrg) }}</small>
            <p class="login-accounts__description">{{ account.description }}</p>
            <span class="login-accounts__state">{{ account.actorId === form.actorId ? '当前已选' : '点击套用' }}</span>
          </button>
        </div>
      </div>

      <div class="login-column">
        <form class="login-form login-form--primary" @submit.prevent="handleLogin">
          <div class="login-form__header">
            <div class="login-form__header-top">
              <p class="section-kicker">登录</p>
              <span class="login-form__tag">主入口</span>
            </div>
            <h2 class="section-title">进入工作台</h2>
          </div>

          <label>
            <span>账户 ID</span>
            <input v-model="form.actorId" type="text" autocomplete="username" placeholder="researcher-01" />
          </label>

          <label>
            <span>密码</span>
            <input v-model="form.password" type="password" autocomplete="current-password" placeholder="brainweb3-demo" />
          </label>

          <div v-if="error" class="error-state">{{ error }}</div>

          <button type="submit" class="login-form__submit" :disabled="submitting">
            {{ submitting ? '登录中...' : '进入工作台' }}
          </button>
        </form>

        <section class="login-utilities">
          <div class="login-utilities__header">
            <p class="section-kicker">辅助功能</p>
            <h2 class="section-title">辅助入口</h2>
          </div>

          <details class="login-utility">
            <summary class="login-utility__summary">
              <span>研究员注册</span>
              <small>创建研究员账户</small>
            </summary>
            <form class="login-form login-form--secondary" @submit.prevent="handleRegister">
              <label>
                <span>账户 ID</span>
                <input v-model="registerForm.actorId" type="text" autocomplete="username" placeholder="new-researcher" />
              </label>

              <label>
                <span>显示名</span>
                <input v-model="registerForm.displayName" type="text" autocomplete="name" placeholder="王小明" />
              </label>

              <label>
                <span>机构</span>
                <input v-model="registerForm.actorOrg" type="text" placeholder="Sichuan Neuro Lab" />
              </label>

              <label>
                <span>密码</span>
                <input v-model="registerForm.password" type="password" autocomplete="new-password" placeholder="brainweb3-demo" />
              </label>

              <div v-if="registerError" class="error-state">{{ registerError }}</div>

              <button type="submit" class="login-form__submit login-form__submit--secondary" :disabled="registerSubmitting">
                {{ registerSubmitting ? '注册中...' : '创建研究员账户' }}
              </button>
            </form>
          </details>

          <details class="login-utility" :open="Boolean(forgotTicket || forgotError)">
            <summary class="login-utility__summary">
              <span>忘记密码恢复</span>
              <small>申请并消费恢复票据</small>
            </summary>
            <form class="login-form login-form--secondary" @submit.prevent="handlePasswordResetRequest">
              <label>
                <span>账户 ID</span>
                <input v-model="forgotForm.actorId" type="text" autocomplete="username" placeholder="researcher-01" />
              </label>

              <label>
                <span>新密码</span>
                <input v-model="forgotForm.nextPassword" type="password" autocomplete="new-password" placeholder="brainweb3-reset" />
              </label>

              <div v-if="forgotError" class="error-state">{{ forgotError }}</div>

              <button type="submit" class="login-form__submit login-form__submit--secondary" :disabled="forgotSubmitting">
                {{ forgotSubmitting ? '生成中...' : '生成恢复票据' }}
              </button>

              <div v-if="forgotTicket" class="ticket-panel">
                <span>{{ forgotTicket.tokenVisible ? '演示票据' : '恢复投递' }}</span>
                <code>{{ forgotTicket.resetToken || '当前环境不显示票据明文' }}</code>
                <small>有效期至 {{ new Date(forgotTicket.expiresAt).toLocaleString() }}</small>
                <small v-if="!forgotTicket.tokenVisible">当前环境不展示票据明文，请通过站外投递链路完成重置。</small>
                <button
                  v-if="forgotTicket.tokenVisible && forgotTicket.resetToken"
                  type="button"
                  class="login-form__submit"
                  :disabled="resetSubmitting"
                  @click="handlePasswordResetConfirm"
                >
                  {{ resetSubmitting ? '确认中...' : '确认消费票据并重置' }}
                </button>
              </div>
            </form>
          </details>
        </section>
      </div>
    </section>
  </div>
</template>

<style scoped>
.login-page {
  position: relative;
  display: grid;
  min-height: 100%;
  place-items: center;
  overflow-x: clip;
}

.login-page::before,
.login-page::after {
  content: '';
  position: absolute;
  inset: auto;
  pointer-events: none;
}

.login-page::before {
  top: 4%;
  left: -3%;
  width: 320px;
  height: 320px;
  border-radius: 999px;
  background: radial-gradient(circle, rgba(49, 87, 102, 0.18), rgba(49, 87, 102, 0));
  filter: blur(12px);
}

.login-page::after {
  right: -5%;
  bottom: 2%;
  width: 360px;
  height: 360px;
  border-radius: 36px;
  background:
    linear-gradient(135deg, rgba(156, 107, 54, 0.14), rgba(156, 107, 54, 0)),
    linear-gradient(180deg, rgba(49, 87, 102, 0.1), rgba(49, 87, 102, 0));
  filter: blur(14px);
  transform: rotate(14deg);
}

.login-shell {
  position: relative;
  overflow: hidden;
  display: grid;
  width: min(100%, 1280px);
  grid-template-columns: minmax(0, 1.08fr) minmax(360px, 0.92fr);
  gap: 28px;
  padding: clamp(22px, 3vw, 34px);
  border-radius: 34px;
  background:
    linear-gradient(180deg, rgba(244, 238, 230, 0.96), rgba(235, 227, 216, 0.92)),
    var(--panel-gradient);
}

.login-shell::after {
  content: '';
  position: absolute;
  inset: 18px;
  border-radius: 28px;
  border: 1px solid rgba(49, 87, 102, 0.06);
  background:
    linear-gradient(90deg, rgba(255, 255, 255, 0.1) 1px, transparent 1px),
    linear-gradient(rgba(255, 255, 255, 0.06) 1px, transparent 1px);
  background-size: 36px 36px;
  mask-image: linear-gradient(135deg, rgba(0, 0, 0, 0.48), transparent 72%);
  pointer-events: none;
  opacity: 0.52;
}

.login-copy,
.login-column {
  display: grid;
  gap: 16px;
  position: relative;
  z-index: 1;
}

.login-copy {
  align-content: start;
}

.login-copy__masthead,
.login-form__header-top,
.login-accounts__meta,
.login-accounts__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.login-copy__stamp,
.login-form__tag {
  display: inline-flex;
  align-items: center;
  min-height: 32px;
  padding: 0 12px;
  border-radius: 999px;
  border: 1px solid rgba(49, 87, 102, 0.14);
  background: rgba(248, 243, 236, 0.7);
  color: var(--accent-strong);
  font-size: 0.74rem;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.login-copy h1 {
  margin: 0;
  color: var(--text-strong);
  font-family: var(--display);
  font-size: var(--page-heading-size);
  font-weight: 600;
  line-height: var(--page-heading-line-height);
  letter-spacing: var(--page-heading-letter-spacing);
  max-width: 10ch;
  text-wrap: balance;
}

.login-copy__lede,
.login-form__note {
  margin: 0;
  color: var(--text-muted);
  font-size: var(--supporting-text-size);
  line-height: var(--supporting-text-line-height);
}

.login-copy__lede {
  max-width: 46ch;
}

.ticket-panel span,
.ticket-panel small,
.login-utilities__header p,
.login-accounts__state {
  color: var(--text-faint);
  font-size: 0.72rem;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.login-accounts__header p,
.login-utilities__header p {
  margin: 0;
  color: var(--text-muted);
  line-height: 1.7;
}

.login-accounts__header {
  align-items: end;
}

.login-accounts__header h2 {
  margin-bottom: 6px;
}

.login-accounts__header > p {
  max-width: 30ch;
}

.login-accounts {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(172px, 1fr));
  gap: 12px;
}

.login-accounts__item {
  display: grid;
  align-content: start;
  gap: 8px;
  min-height: 0;
  padding: 15px 15px 13px;
  border-radius: 20px;
  border: 1px solid rgba(49, 87, 102, 0.11);
  background:
    linear-gradient(180deg, rgba(247, 242, 235, 0.94), rgba(233, 225, 214, 0.88)),
    var(--panel-soft-gradient);
  color: var(--text-main);
  text-align: left;
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.84),
    0 18px 32px rgba(93, 76, 54, 0.08);
  transition:
    transform 0.22s ease,
    box-shadow 0.22s ease,
    border-color 0.22s ease,
    background 0.22s ease;
}

.login-accounts__item:hover {
  transform: translateY(-3px);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.84),
    0 24px 38px rgba(93, 76, 54, 0.12);
}

.login-accounts__item--active {
  border-color: rgba(156, 107, 54, 0.3);
  background:
    linear-gradient(180deg, rgba(250, 245, 238, 0.98), rgba(238, 229, 216, 0.92)),
    var(--panel-soft-gradient);
  box-shadow:
    inset 0 0 0 1px rgba(156, 107, 54, 0.12),
    0 26px 42px rgba(101, 79, 53, 0.14);
}

.login-accounts__item strong,
.login-form__submit {
  font-family: var(--body);
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.login-accounts__meta {
  align-items: flex-start;
  gap: 8px;
}

.login-accounts__item span,
.login-accounts__item small,
.login-form label span {
  color: var(--text-faint);
}

.login-accounts__item span,
.login-accounts__item small,
.login-form label span {
  font-size: var(--field-label-size);
  letter-spacing: var(--field-label-letter-spacing);
  text-transform: uppercase;
}

.login-accounts__item p {
  margin: 0;
  color: var(--text-muted);
  line-height: 1.55;
}

.login-accounts__org,
.login-accounts__description {
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
}

.login-accounts__org {
  -webkit-line-clamp: 1;
}

.login-accounts__description {
  min-height: calc(1.55em * 2);
  -webkit-line-clamp: 2;
}

.login-accounts__state {
  margin-top: 2px;
  padding-top: 8px;
  border-top: 1px solid rgba(49, 87, 102, 0.08);
}

.login-column {
  align-content: start;
}

.login-utilities {
  display: grid;
  gap: 12px;
  padding: 18px;
  border-radius: 24px;
  border: 1px solid rgba(49, 87, 102, 0.12);
  background:
    linear-gradient(180deg, rgba(250, 246, 240, 0.8), rgba(240, 232, 221, 0.74)),
    var(--surface-gradient);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.76),
    0 18px 32px rgba(93, 76, 54, 0.08);
}

.login-utilities__header {
  display: grid;
  gap: 4px;
}

.login-utility {
  border: 1px solid rgba(49, 87, 102, 0.12);
  border-radius: 20px;
  background: rgba(250, 246, 240, 0.72);
  overflow: hidden;
}

.login-utility[open] {
  background: rgba(248, 244, 238, 0.92);
}

.login-utility__summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 15px 18px;
  cursor: pointer;
  list-style: none;
}

.login-utility__summary::-webkit-details-marker {
  display: none;
}

.login-utility__summary span {
  color: var(--text-strong);
  font-weight: 600;
}

.login-utility__summary small {
  color: var(--text-muted);
}

.login-form--secondary {
  gap: 14px;
  padding: 0 18px 18px;
  border: 0;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
}

.login-form--secondary::before {
  display: none;
}

.login-form {
  position: relative;
  overflow: hidden;
  display: grid;
  align-content: start;
  gap: 16px;
  padding: 22px 24px 24px;
  border-radius: 26px;
  border: 1px solid rgba(49, 87, 102, 0.12);
  background:
    linear-gradient(180deg, rgba(248, 244, 238, 0.94), rgba(235, 226, 214, 0.9)),
    var(--panel-soft-gradient);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.84),
    0 20px 36px rgba(93, 76, 54, 0.09);
}

.login-form::before {
  content: '';
  position: absolute;
  inset: 0 0 auto;
  height: 4px;
  background: linear-gradient(90deg, rgba(49, 87, 102, 0.86), rgba(156, 107, 54, 0.64));
}

.login-form--primary {
  padding-top: 24px;
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.84),
    0 28px 46px rgba(82, 64, 45, 0.14);
}

.login-form__header {
  display: grid;
  gap: 6px;
}

.login-form--primary .login-form__header {
  margin-bottom: 4px;
}

.login-form label {
  display: grid;
  gap: 8px;
}

.login-form input {
  width: 100%;
  min-height: 48px;
  padding: 0 15px;
  border: 1px solid rgba(53, 67, 75, 0.12);
  border-radius: 16px;
  background: rgba(251, 248, 243, 0.9);
  color: var(--text-main);
  box-shadow: inset 0 1px 2px rgba(93, 76, 54, 0.05);
}

.login-form input::placeholder {
  color: rgba(102, 112, 120, 0.76);
}

.login-form__submit {
  min-height: 48px;
  padding: 0 18px;
  border: 1px solid rgba(156, 107, 54, 0.24);
  border-radius: 16px;
  background:
    linear-gradient(180deg, rgba(187, 144, 97, 0.34), rgba(219, 196, 163, 0.48)),
    var(--button-warm-gradient);
  color: var(--text-strong);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.5),
    0 16px 28px rgba(114, 88, 58, 0.12);
}

.login-form__submit--secondary {
  border-color: rgba(49, 87, 102, 0.14);
  background:
    linear-gradient(180deg, rgba(244, 238, 229, 0.96), rgba(231, 223, 211, 0.9)),
    var(--button-soft-gradient);
  color: var(--text-main);
}

.ticket-panel {
  display: grid;
  gap: 10px;
  padding: 16px;
  border-radius: 18px;
  border: 1px solid rgba(156, 107, 54, 0.22);
  background:
    linear-gradient(180deg, rgba(247, 239, 229, 0.92), rgba(236, 226, 212, 0.9)),
    var(--warm-panel-gradient);
}

.ticket-panel code {
  overflow-wrap: anywhere;
  color: var(--text-main);
  padding: 10px 12px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.36);
  border: 1px dashed rgba(156, 107, 54, 0.18);
}

@media (max-width: 1040px) {
  .login-shell {
    grid-template-columns: 1fr;
  }

  .login-copy h1 {
    max-width: none;
  }

  .login-accounts {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 820px) {
  .login-copy__masthead,
  .login-accounts__header,
  .login-form__header-top,
  .login-accounts__meta,
  .login-utility__summary {
    align-items: flex-start;
    flex-direction: column;
  }
}

@media (max-width: 640px) {
  .login-page {
    min-height: auto;
  }

  .login-accounts {
    grid-template-columns: 1fr;
  }

  .login-shell {
    padding: 18px;
    border-radius: 24px;
  }

  .login-form,
  .login-utilities,
  .login-utility,
  .login-accounts__item,
  .ticket-panel {
    border-radius: 20px;
  }

}
</style>
