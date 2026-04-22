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

const gatewaySignals = [
  {
    label: '身份入口',
    value: '3 类演示角色',
    note: '研究员、归属机构、审批岗都能一键代入',
  },
  {
    label: '会话模式',
    value: 'JWT 正式态',
    note: '已切离手工请求头，所有页面通过后端签发令牌维持身份',
  },
  {
    label: '恢复链路',
    value: '票据可回放',
    note: '支持演示态明文票据，也能平滑切到站外投递',
  },
]

const gatewayChecklist = [
  '先选择一个可用身份，确认自己接下来要走的是上传、审批还是审计链路。',
  '登录成功后会自动回跳到原目标页面，避免在跨页流程里重复认证。',
  '忘记密码时先申领恢复票据，再消费票据完成密码切换，整条链路和正式环境保持一致。',
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
          <p class="section-kicker">Access Gateway</p>
          <span class="login-copy__stamp">Protocol 03 · Secure Session Relay</span>
        </div>
        <h1>先建立可信会话，再进入上传、审批与回放链路。</h1>
        <p class="login-copy__lede">
          这套工作台已经从“手工伪造请求头”切到“后端签发 JWT”的正式会话模式。登录页不再只是一个表单入口，而是整个脑数据治理流程的会话闸门。下面这些演示账号都使用同一默认密码：
          <code>brainweb3-demo</code>
        </p>

        <div class="login-signals">
          <article v-for="signal in gatewaySignals" :key="signal.label" class="login-signal">
            <span>{{ signal.label }}</span>
            <strong>{{ signal.value }}</strong>
            <p>{{ signal.note }}</p>
          </article>
        </div>

        <div class="login-brief">
          <div class="login-brief__header">
            <span>Session Checklist</span>
            <strong>登录前先确认这三件事，整条演示链路会更顺。</strong>
          </div>
          <ol class="login-brief__steps">
            <li v-for="item in gatewayChecklist" :key="item">{{ item }}</li>
          </ol>
        </div>

        <div class="login-accounts__header">
          <div>
            <p class="section-kicker">Demo Identity Set</p>
            <h2 class="section-title">演示身份档案</h2>
          </div>
          <p>每张卡片都代表一条不同的业务视角，点一下就能把登录账号代入表单。</p>
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
            <small>{{ formatOrganizationLabel(account.actorOrg) }}</small>
            <p>{{ account.description }}</p>
            <div class="login-accounts__action">
              <span>快速代入</span>
              <span>{{ account.actorId === form.actorId ? '当前已选' : '点击套用账号' }}</span>
            </div>
          </button>
        </div>
      </div>

      <div class="login-column">
        <section class="login-column__summary">
          <div>
            <p class="section-kicker">Session Relay</p>
            <h2 class="section-title">一页完成进入、注册与恢复</h2>
          </div>
          <p>
            我把入口重新整理成一块更像“会话中控”的面板：主登录保持最强视觉优先级，注册和恢复则作为并列辅助链路，减少过去那种均匀堆叠卡片的模板感。
          </p>
        </section>

        <form class="login-form login-form--primary" @submit.prevent="handleLogin">
          <div class="login-form__header">
            <div class="login-form__header-top">
              <p class="section-kicker">Sign In</p>
              <span class="login-form__tag">Primary Lane</span>
            </div>
            <h2 class="section-title">登录闸门</h2>
            <p class="login-form__note">演示账号继续可用，同时这轮已经补上研究员自注册入口。</p>
          </div>

          <label>
            <span>Actor ID</span>
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

        <div class="login-column__grid">
          <form class="login-form login-form--register" @submit.prevent="handleRegister">
            <div class="login-form__header">
              <div class="login-form__header-top">
                <p class="section-kicker">Register</p>
                <span class="login-form__tag">Self-Service</span>
              </div>
              <h2 class="section-title">研究员注册</h2>
              <p class="login-form__note">首版自注册默认创建 `researcher` 账户，`owner / approver / admin` 由管理员在账户页维护。</p>
            </div>

            <label>
              <span>Actor ID</span>
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

            <button type="submit" class="login-form__submit" :disabled="registerSubmitting">
              {{ registerSubmitting ? '注册中...' : '创建研究员账户' }}
            </button>
          </form>

          <form class="login-form login-form--recovery" @submit.prevent="handlePasswordResetRequest">
            <div class="login-form__header">
              <div class="login-form__header-top">
                <p class="section-kicker">Recover</p>
                <span class="login-form__tag">Fallback Lane</span>
              </div>
              <h2 class="section-title">忘记密码恢复</h2>
              <p class="login-form__note">先申请恢复票据，再确认消费，后续可以无缝换成短信或邮箱投递。</p>
            </div>

            <label>
              <span>Actor ID</span>
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
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.login-page {
  position: relative;
  display: grid;
  min-height: calc(100vh - 80px);
  align-items: center;
  padding-block: clamp(16px, 3vw, 32px);
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
  gap: 18px;
  position: relative;
  z-index: 1;
}

.login-copy {
  align-content: start;
}

.login-copy__masthead,
.login-form__header-top,
.login-accounts__meta,
.login-accounts__action,
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
  font-family: var(--display);
  font-size: clamp(2.9rem, 4.7vw, 4.7rem);
  line-height: 0.92;
  max-width: 11ch;
  text-wrap: balance;
}

.login-copy__lede,
.login-form__note {
  margin: 0;
  color: var(--text-muted);
  line-height: 1.8;
}

.login-copy__lede {
  max-width: 60ch;
}

.login-signals {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.login-signal {
  display: grid;
  gap: 8px;
  min-height: 132px;
  padding: 16px 18px;
  border-radius: 22px;
  border: 1px solid rgba(49, 87, 102, 0.11);
  background:
    linear-gradient(180deg, rgba(249, 246, 241, 0.92), rgba(237, 230, 220, 0.84)),
    var(--panel-soft-gradient);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.82),
    0 14px 28px rgba(93, 76, 54, 0.08);
}

.login-signal span,
.login-brief__header span,
.login-column__summary p,
.ticket-panel span,
.ticket-panel small {
  color: var(--text-faint);
  font-size: 0.72rem;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.login-signal strong {
  color: var(--text-strong);
  font-size: 1.05rem;
  line-height: 1.3;
}

.login-signal p,
.login-accounts__header p,
.login-column__summary > p {
  margin: 0;
  color: var(--text-muted);
  line-height: 1.7;
}

.login-brief {
  display: grid;
  gap: 18px;
  padding: 22px 22px 24px;
  border-radius: 28px;
  border: 1px solid rgba(49, 87, 102, 0.12);
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.34), rgba(255, 255, 255, 0)),
    linear-gradient(180deg, rgba(236, 228, 216, 0.95), rgba(229, 220, 208, 0.88));
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.76),
    0 22px 48px rgba(93, 76, 54, 0.1);
}

.login-brief__header {
  display: grid;
  gap: 8px;
}

.login-brief__header strong,
.login-column__summary .section-title {
  font-family: var(--display);
  font-size: clamp(1.3rem, 2vw, 1.7rem);
  font-weight: 600;
  line-height: 1.25;
}

.login-brief__steps {
  display: grid;
  gap: 12px;
  margin: 0;
  padding: 0;
  list-style: none;
  counter-reset: login-step;
}

.login-brief__steps li {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 12px;
  align-items: start;
  color: var(--text-main);
  line-height: 1.75;
}

.login-brief__steps li::before {
  counter-increment: login-step;
  content: counter(login-step, decimal-leading-zero);
  display: inline-grid;
  place-items: center;
  min-width: 34px;
  height: 34px;
  margin-top: 2px;
  border-radius: 10px;
  border: 1px solid rgba(156, 107, 54, 0.22);
  background: rgba(252, 248, 242, 0.72);
  color: var(--amber);
  font-size: 0.78rem;
  font-weight: 700;
  letter-spacing: 0.08em;
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
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.login-accounts__item {
  display: grid;
  gap: 10px;
  padding: 18px 18px 16px;
  border-radius: 22px;
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

.login-accounts__item span,
.login-accounts__item small,
.login-form label span {
  color: var(--text-faint);
}

.login-accounts__item span,
.login-accounts__item small,
.login-form label span {
  font-size: 0.74rem;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.login-accounts__item p {
  margin: 0;
  color: var(--text-muted);
  line-height: 1.6;
}

.login-accounts__action {
  padding-top: 8px;
  border-top: 1px solid rgba(49, 87, 102, 0.08);
}

.login-column {
  align-content: start;
}

.login-column__summary {
  display: grid;
  gap: 10px;
  padding: 18px 20px;
  border-radius: 24px;
  border: 1px solid rgba(49, 87, 102, 0.12);
  background:
    linear-gradient(180deg, rgba(250, 246, 240, 0.8), rgba(240, 232, 221, 0.74)),
    var(--surface-gradient);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.76),
    0 18px 32px rgba(93, 76, 54, 0.08);
}

.login-column__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
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
  .login-shell,
  .login-accounts,
  .login-signals,
  .login-column__grid {
    grid-template-columns: 1fr;
  }

  .login-copy h1 {
    max-width: none;
  }
}

@media (max-width: 820px) {
  .login-copy__masthead,
  .login-accounts__header,
  .login-form__header-top,
  .login-accounts__meta,
  .login-accounts__action {
    align-items: flex-start;
    flex-direction: column;
  }
}

@media (max-width: 640px) {
  .login-page {
    min-height: auto;
    padding-block: 4px 16px;
  }

  .login-shell {
    padding: 18px;
    border-radius: 24px;
  }

  .login-form,
  .login-brief,
  .login-column__summary,
  .login-accounts__item,
  .login-signal {
    border-radius: 20px;
  }

  .login-copy h1 {
    font-size: clamp(2.25rem, 10vw, 3.2rem);
  }
}
</style>
