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
      message: '演示模式下，重置票据会直接显示在登录页，用于模拟站外投递链路。',
      tone: 'warning',
    })
  } catch (requestError) {
    forgotTicket.value = null
    forgotError.value = requestError instanceof Error ? requestError.message : '申请恢复票据失败。'
  } finally {
    forgotSubmitting.value = false
  }
}

async function handlePasswordResetConfirm() {
  if (!forgotTicket.value) {
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
        <p class="section-kicker">Access Gateway</p>
        <h1>先进入正式会话，再开始上传、审批和回放。</h1>
        <p class="login-copy__lede">
          这套工作台已经从“手工伪造请求头”切到“后端签发 JWT”的正式会话模式。下面这些演示账号都使用同一默认密码：
          <code>brainweb3-demo</code>
        </p>

        <div class="login-accounts">
          <button
            v-for="account in demoAccounts"
            :key="account.actorId"
            type="button"
            class="login-accounts__item"
            :class="{ 'login-accounts__item--active': account.actorId === form.actorId }"
            @click="applyAccount(account.actorId)"
          >
            <strong>{{ formatRoleLabel(account.actorRole) }}</strong>
            <span>{{ account.actorId }}</span>
            <small>{{ formatOrganizationLabel(account.actorOrg) }}</small>
            <p>{{ account.description }}</p>
          </button>
        </div>
      </div>

      <div class="login-column">
        <form class="login-form" @submit.prevent="handleLogin">
          <div>
            <p class="section-kicker">Sign In</p>
            <h2 class="section-title">登录闸门</h2>
            <p class="login-form__note">演示账号继续可用，同时这轮已经补上研究员自注册入口。</p>
          </div>

          <label>
            <span>Actor ID</span>
            <input v-model="form.actorId" type="text" autocomplete="username" />
          </label>

          <label>
            <span>密码</span>
            <input v-model="form.password" type="password" autocomplete="current-password" />
          </label>

          <div v-if="error" class="error-state">{{ error }}</div>

          <button type="submit" class="login-form__submit" :disabled="submitting">
            {{ submitting ? '登录中...' : '进入工作台' }}
          </button>
        </form>

        <form class="login-form login-form--register" @submit.prevent="handleRegister">
          <div>
            <p class="section-kicker">Register</p>
            <h2 class="section-title">研究员注册</h2>
            <p class="login-form__note">首版自注册默认创建 `researcher` 账户，`owner / approver / admin` 由管理员在账户页维护。</p>
          </div>

          <label>
            <span>Actor ID</span>
            <input v-model="registerForm.actorId" type="text" autocomplete="username" />
          </label>

          <label>
            <span>显示名</span>
            <input v-model="registerForm.displayName" type="text" autocomplete="name" />
          </label>

          <label>
            <span>机构</span>
            <input v-model="registerForm.actorOrg" type="text" />
          </label>

          <label>
            <span>密码</span>
            <input v-model="registerForm.password" type="password" autocomplete="new-password" />
          </label>

          <div v-if="registerError" class="error-state">{{ registerError }}</div>

          <button type="submit" class="login-form__submit" :disabled="registerSubmitting">
            {{ registerSubmitting ? '注册中...' : '创建研究员账户' }}
          </button>
        </form>

        <form class="login-form login-form--recovery" @submit.prevent="handlePasswordResetRequest">
          <div>
            <p class="section-kicker">Recover</p>
            <h2 class="section-title">忘记密码恢复</h2>
            <p class="login-form__note">当前是演示版票据流：先申请恢复票据，再确认消费，后续可以无缝换成短信或邮箱投递。</p>
          </div>

          <label>
            <span>Actor ID</span>
            <input v-model="forgotForm.actorId" type="text" autocomplete="username" />
          </label>

          <label>
            <span>新密码</span>
            <input v-model="forgotForm.nextPassword" type="password" autocomplete="new-password" />
          </label>

          <div v-if="forgotError" class="error-state">{{ forgotError }}</div>

          <button type="submit" class="login-form__submit login-form__submit--secondary" :disabled="forgotSubmitting">
            {{ forgotSubmitting ? '生成中...' : '生成恢复票据' }}
          </button>

          <div v-if="forgotTicket" class="ticket-panel">
            <span>演示票据</span>
            <code>{{ forgotTicket.resetToken }}</code>
            <small>有效期至 {{ new Date(forgotTicket.expiresAt).toLocaleString() }}</small>
            <button type="button" class="login-form__submit" :disabled="resetSubmitting" @click="handlePasswordResetConfirm">
              {{ resetSubmitting ? '确认中...' : '确认消费票据并重置' }}
            </button>
          </div>
        </form>
      </div>
    </section>
  </div>
</template>

<style scoped>
.login-page {
  display: grid;
  min-height: calc(100vh - 80px);
  align-items: center;
}

.login-shell {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(320px, 0.8fr);
  gap: 22px;
  padding: 26px;
  border-radius: 30px;
  background:
    linear-gradient(135deg, rgba(7, 19, 26, 0.96), rgba(6, 13, 18, 0.84)),
    radial-gradient(circle at top left, rgba(116, 210, 220, 0.14), transparent 36%);
}

.login-copy,
.login-column,
.login-form,
.login-accounts {
  display: grid;
  gap: 16px;
}

.login-copy h1 {
  margin: 0;
  font-family: var(--display);
  font-size: clamp(2.5rem, 4vw, 4rem);
  line-height: 0.96;
}

.login-copy__lede,
.login-form__note {
  margin: 0;
  color: var(--text-muted);
  line-height: 1.8;
}

.login-accounts {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.login-accounts__item {
  display: grid;
  gap: 8px;
  padding: 18px;
  border-radius: 22px;
  border: 1px solid rgba(108, 166, 186, 0.14);
  background: rgba(6, 18, 24, 0.78);
  color: var(--text-main);
  text-align: left;
}

.login-accounts__item--active {
  border-color: rgba(235, 178, 102, 0.3);
  box-shadow: inset 0 0 0 1px rgba(235, 178, 102, 0.16);
}

.login-accounts__item strong,
.login-form__submit {
  font-family: var(--display);
  letter-spacing: 0.08em;
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

.login-form {
  align-content: start;
  padding: 22px;
  border-radius: 26px;
  border: 1px solid rgba(108, 166, 186, 0.14);
  background:
    linear-gradient(180deg, rgba(4, 15, 21, 0.94), rgba(8, 17, 23, 0.78)),
    radial-gradient(circle at top right, rgba(116, 210, 220, 0.08), transparent 30%);
}

.login-column {
  align-content: start;
}

.login-form label {
  display: grid;
  gap: 8px;
}

.login-form input {
  width: 100%;
  min-height: 46px;
  padding: 10px 14px;
  border: 1px solid var(--line);
  border-radius: 14px;
  background: rgba(5, 16, 22, 0.92);
  color: var(--text-main);
}

.login-form__submit {
  min-height: 46px;
  border: 1px solid var(--line-warm);
  border-radius: 999px;
  background: linear-gradient(180deg, rgba(235, 178, 102, 0.24), rgba(235, 178, 102, 0.12));
  color: var(--text-main);
}

.login-form__submit--secondary {
  border-color: var(--line);
  background: rgba(12, 24, 32, 0.92);
}

.ticket-panel {
  display: grid;
  gap: 8px;
  padding: 14px;
  border-radius: 18px;
  border: 1px solid rgba(235, 178, 102, 0.22);
  background: rgba(18, 26, 32, 0.76);
}

.ticket-panel span,
.ticket-panel small {
  color: var(--text-faint);
  font-size: 0.72rem;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.ticket-panel code {
  overflow-wrap: anywhere;
  color: var(--text-main);
}

@media (max-width: 1040px) {
  .login-shell,
  .login-accounts {
    grid-template-columns: 1fr;
  }
}
</style>
