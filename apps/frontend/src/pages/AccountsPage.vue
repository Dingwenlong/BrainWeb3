<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'
import PageHero from '../components/PageHero.vue'
import SurfaceCard from '../components/SurfaceCard.vue'
import {
  changePassword,
  createAccount,
  getAccounts,
  getCurrentAccount,
  getCurrentIdentity,
  getOrganizationIdentity,
  resetAccountPassword,
  updateAccountCredentialStatus,
  updateAccount,
  updateOrganizationCredentialStatus,
  verifyCredential,
} from '../api/client'
import { toErrorMessage, useAsyncView } from '../composables/useAsyncView'
import { useActorProfile } from '../composables/useActorProfile'
import { useToast } from '../composables/useToast'
import type { AccountUser, ActorIdentity, CredentialVerificationResult, OrganizationIdentity } from '../types/api'
import {
  formatIdentityStatusLabel,
  formatIdentityStatusSourceLabel,
  formatOrganizationLabel,
  formatRequestStatusLabel,
  formatRoleLabel,
} from '../utils/labels'

const { actorProfile, isAdmin } = useActorProfile()
const { pushToast } = useToast()

const { loading, error, run: runPageLoad, setErrorMessage } = useAsyncView({
  initialLoading: true,
})
const account = ref<AccountUser | null>(null)
const identity = ref<ActorIdentity | null>(null)
const credentialVerification = ref<CredentialVerificationResult | null>(null)
const accountRows = ref<AccountUser[]>([])
const organizationRows = ref<OrganizationIdentity[]>([])
const actionLoadingId = ref<string | null>(null)
const credentialForms = reactive<Record<string, { status: string; reason: string }>>({})
const organizationCredentialForms = reactive<Record<string, { status: string; reason: string }>>({})

const passwordForm = reactive({
  currentPassword: 'brainweb3-demo',
  nextPassword: 'brainweb3-next',
})

const createForm = reactive({
  actorId: '',
  displayName: '',
  actorRole: 'researcher',
  actorOrg: 'Sichuan Neuro Lab',
  status: 'active',
  password: 'brainweb3-demo',
})

const accountStats = computed(() => [
  { label: '当前角色', value: formatRoleLabel(actorProfile.value.actorRole) },
  { label: '账户总数', value: String(accountRows.value.length) },
  { label: '激活账户', value: String(accountRows.value.filter((row) => row.status === 'active').length) },
])
const organizationStats = computed(() => [
  { label: '机构数', value: String(organizationRows.value.length) },
  { label: '机构已签发', value: String(organizationRows.value.filter((row) => row.statusSnapshot.effectiveStatus === 'issued').length) },
  { label: '机构待治理', value: String(organizationRows.value.filter((row) => row.statusSnapshot.effectiveStatus !== 'issued').length) },
])
const roleGuide = computed(() => {
  if (isAdmin.value) {
    return {
      emptyDirectory: '暂无账户记录',
    }
  }
  return {
    emptyDirectory: '暂无账户记录',
  }
})

function formatTime(value: string | null) {
  if (!value) {
    return '暂无'
  }
  return new Date(value).toLocaleString()
}

function formatHistoryTransition(previousStatus: string | null, nextStatus: string) {
  if (!previousStatus) {
    return `${formatIdentityStatusLabel(nextStatus)}`
  }
  return `${formatIdentityStatusLabel(previousStatus)} -> ${formatIdentityStatusLabel(nextStatus)}`
}

function canManageRow(row: AccountUser) {
  return isAdmin.value && row.actorId !== actorProfile.value.actorId
}

function syncCredentialForms(rows: AccountUser[]) {
  for (const key of Object.keys(credentialForms)) {
    delete credentialForms[key]
  }
  for (const row of rows) {
    credentialForms[row.actorId] = {
      status: row.credentialStatus.effectiveStatus,
      reason: row.credentialStatus.reason ?? '',
    }
  }
}

function syncOrganizationCredentialForms(rows: OrganizationIdentity[]) {
  for (const key of Object.keys(organizationCredentialForms)) {
    delete organizationCredentialForms[key]
  }
  for (const row of rows) {
    organizationCredentialForms[row.organizationName] = {
      status: row.statusSnapshot.effectiveStatus,
      reason: row.statusSnapshot.reason ?? '',
    }
  }
}

async function loadPage() {
  const payload = await runPageLoad(async () => {
    const [currentAccount, currentIdentity] = await Promise.all([
      getCurrentAccount(),
      getCurrentIdentity(),
    ])
    const verification = await verifyCredential({
      id: currentIdentity.credential.id,
      type: currentIdentity.credential.type,
      issuerDid: currentIdentity.credential.issuerDid,
      holderDid: currentIdentity.credential.holderDid,
      subjectDid: currentIdentity.credential.subjectDid,
      subjectType: currentIdentity.credential.subjectType,
      issuedAt: currentIdentity.credential.issuedAt,
      expiresAt: currentIdentity.credential.expiresAt,
      proof: currentIdentity.credential.proof,
      credentialStatus: currentIdentity.credential.credentialStatus,
      claims: currentIdentity.credential.claims,
    })

    if (isAdmin.value) {
      const accounts = await getAccounts()
      const organizationNames = [...new Set(accounts.map((row) => row.actorOrg))]
      const organizations = await Promise.all(
        organizationNames.map((name) => getOrganizationIdentity(name)),
      )
      return {
        currentAccount,
        currentIdentity,
        verification,
        accounts,
        organizations,
      }
    } else {
      const organizations = [await getOrganizationIdentity(currentIdentity.actorOrg)]
      return {
        currentAccount,
        currentIdentity,
        verification,
        accounts: [currentAccount],
        organizations,
      }
    }
  }, '加载账户页失败。')

  if (!payload) {
    return
  }

  account.value = payload.currentAccount
  identity.value = payload.currentIdentity
  credentialVerification.value = payload.verification
  accountRows.value = payload.accounts
  organizationRows.value = payload.organizations
  syncCredentialForms(accountRows.value)
  syncOrganizationCredentialForms(organizationRows.value)
}

async function submitPasswordChange() {
  try {
    await changePassword(passwordForm)
    pushToast({
      title: '密码已更新',
      message: '当前账户密码已更新，下次登录会使用新密码。',
      tone: 'success',
    })
    await loadPage()
  } catch (changeError) {
    setErrorMessage(toErrorMessage(changeError, '修改密码失败。'))
  }
}

async function submitCreateAccount() {
  try {
    await createAccount(createForm)
    pushToast({
      title: '账户已创建',
      message: `${createForm.actorId} 已加入账户目录。`,
      tone: 'success',
    })
    createForm.actorId = ''
    createForm.displayName = ''
    createForm.actorRole = 'researcher'
    createForm.actorOrg = 'Sichuan Neuro Lab'
    createForm.status = 'active'
    createForm.password = 'brainweb3-demo'
    await loadPage()
  } catch (createError) {
    setErrorMessage(toErrorMessage(createError, '创建账户失败。'))
  }
}

async function toggleStatus(row: AccountUser) {
  actionLoadingId.value = row.actorId
  try {
    await updateAccount(row.actorId, {
      status: row.status === 'active' ? 'disabled' : 'active',
    })
    await loadPage()
  } catch (updateError) {
    setErrorMessage(toErrorMessage(updateError, '更新账户状态失败。'))
  } finally {
    actionLoadingId.value = null
  }
}

async function promoteToApprover(row: AccountUser) {
  actionLoadingId.value = row.actorId
  try {
    await updateAccount(row.actorId, {
      actorRole: 'approver',
    })
    await loadPage()
  } catch (updateError) {
    setErrorMessage(toErrorMessage(updateError, '更新账户角色失败。'))
  } finally {
    actionLoadingId.value = null
  }
}

async function resetPassword(row: AccountUser) {
  actionLoadingId.value = row.actorId
  try {
    await resetAccountPassword(row.actorId, {
      nextPassword: 'brainweb3-demo',
    })
    pushToast({
      title: '密码已重置',
      message: `${row.actorId} 的密码已重置为 brainweb3-demo。`,
      tone: 'warning',
    })
    await loadPage()
  } catch (resetError) {
    setErrorMessage(toErrorMessage(resetError, '重置密码失败。'))
  } finally {
    actionLoadingId.value = null
  }
}

async function submitCredentialStatus(row: AccountUser) {
  actionLoadingId.value = row.actorId
  try {
    const draft = credentialForms[row.actorId]
    await updateAccountCredentialStatus(row.actorId, {
      status: draft.status,
      reason: draft.reason,
    })
    pushToast({
      title: '凭证状态已更新',
      message: `${row.actorId} 的凭证状态已切换为 ${formatIdentityStatusLabel(draft.status)}。`,
      tone: draft.status === 'revoked' ? 'warning' : 'success',
    })
    await loadPage()
  } catch (updateError) {
    setErrorMessage(toErrorMessage(updateError, '更新凭证状态失败。'))
  } finally {
    actionLoadingId.value = null
  }
}

async function submitOrganizationCredentialStatus(row: OrganizationIdentity) {
  actionLoadingId.value = `org:${row.organizationName}`
  try {
    const draft = organizationCredentialForms[row.organizationName]
    await updateOrganizationCredentialStatus({
      organizationName: row.organizationName,
      status: draft.status,
      reason: draft.reason,
    })
    pushToast({
      title: '机构凭证状态已更新',
      message: `${formatOrganizationLabel(row.organizationName)} 的凭证状态已切换为 ${formatIdentityStatusLabel(draft.status)}。`,
      tone: draft.status === 'revoked' ? 'warning' : 'success',
    })
    await loadPage()
  } catch (updateError) {
    setErrorMessage(toErrorMessage(updateError, '更新机构凭证状态失败。'))
  } finally {
    actionLoadingId.value = null
  }
}

onMounted(loadPage)
</script>

<template>
  <div class="accounts-page">
    <PageHero
      kicker="账户管理"
      title="把账户、密码和凭证状态放进一个清晰的管理页。"
      layout="balanced"
    >
      <div class="summary-strip">
        <article v-for="stat in accountStats" :key="stat.label" class="summary-strip__card">
          <span>{{ stat.label }}</span>
          <strong>{{ stat.value }}</strong>
        </article>
        <article
          v-for="stat in organizationStats"
          :key="stat.label"
          class="summary-strip__card summary-strip__card--secondary"
        >
          <span>{{ stat.label }}</span>
          <strong>{{ stat.value }}</strong>
        </article>
      </div>

      <template #rail>
        <article class="hero-spotlight">
          <p class="hero-spotlight__kicker">当前账户</p>
          <template v-if="account">
            <div class="hero-spotlight__headline">
              <strong>{{ account.displayName }}</strong>
              <span class="status-chip" :class="{ 'status-chip--danger': account.status !== 'active' }">
                {{ formatRequestStatusLabel(account.status) }}
              </span>
            </div>
            <p class="hero-spotlight__context">{{ account.actorId }} · {{ formatRoleLabel(account.actorRole) }}</p>
            <div class="hero-spotlight__meta">
              <div>
                <span>机构</span>
                <strong>{{ formatOrganizationLabel(account.actorOrg) }}</strong>
              </div>
              <div>
                <span>最近登录</span>
                <strong>{{ formatTime(account.lastLoginAt) }}</strong>
              </div>
              <div>
                <span>改密时间</span>
                <strong>{{ formatTime(account.passwordChangedAt) }}</strong>
              </div>
            </div>
          </template>
        </article>
      </template>
    </PageHero>

    <div v-if="loading" class="loading-state">正在加载账户信息...</div>
    <div v-else-if="error" class="error-state">{{ error }}</div>

    <template v-else>
      <section class="accounts-layout">
        <aside class="accounts-layout__side">
          <SurfaceCard kicker="安全设置" title="修改密码">
            <template #meta>
              <RouterLink class="panel-link" to="/">返回总览</RouterLink>
            </template>

            <form class="form-grid" @submit.prevent="submitPasswordChange">
              <label>
                <span>当前密码</span>
                <input v-model="passwordForm.currentPassword" type="password" autocomplete="current-password" />
              </label>
              <label>
                <span>新密码</span>
                <input v-model="passwordForm.nextPassword" type="password" autocomplete="new-password" />
              </label>
              <button type="submit" class="form-grid__submit">更新当前密码</button>
            </form>
          </SurfaceCard>

          <SurfaceCard v-if="identity" kicker="身份凭证" title="我的 DID 与凭证">
            <template #meta>
              <RouterLink class="panel-link" to="/identity-center">打开身份中心</RouterLink>
            </template>

            <dl class="account-card__details">
              <div>
                <dt>个人 DID</dt>
                <dd>{{ identity.actorDid }}</dd>
              </div>
              <div>
                <dt>机构 DID</dt>
                <dd>{{ identity.organizationDid }}</dd>
              </div>
              <div>
                <dt>凭证类型</dt>
                <dd>{{ identity.credential.type }}</dd>
              </div>
              <div>
                <dt>凭证状态</dt>
                <dd>{{ formatIdentityStatusLabel(identity.credential.credentialStatus) }}</dd>
              </div>
              <div>
                <dt>校验状态</dt>
                <dd>{{ formatIdentityStatusLabel(credentialVerification?.status ?? identity.credential.verificationStatus) }}</dd>
              </div>
              <div>
                <dt>签发方</dt>
                <dd>{{ identity.credential.issuerDid }}</dd>
              </div>
              <div>
                <dt>到期时间</dt>
                <dd>{{ formatTime(identity.credential.expiresAt) }}</dd>
              </div>
            </dl>

            <template v-if="credentialVerification?.reason" #note>
              <p class="panel-note">{{ credentialVerification.reason }}</p>
            </template>
          </SurfaceCard>

          <SurfaceCard v-if="isAdmin" kicker="新建账户" title="创建账户">

            <form class="form-grid" @submit.prevent="submitCreateAccount">
              <label>
                <span>账户 ID</span>
                <input v-model="createForm.actorId" type="text" />
              </label>
              <label>
                <span>显示名</span>
                <input v-model="createForm.displayName" type="text" />
              </label>
              <label>
                <span>角色</span>
                <select v-model="createForm.actorRole">
                  <option value="researcher">研究员</option>
                  <option value="owner">归属方</option>
                  <option value="approver">审批人</option>
                  <option value="admin">管理员</option>
                </select>
              </label>
              <label>
                <span>机构</span>
                <input v-model="createForm.actorOrg" type="text" />
              </label>
              <label>
                <span>状态</span>
                <select v-model="createForm.status">
                  <option value="active">启用</option>
                  <option value="disabled">停用</option>
                </select>
              </label>
              <label>
                <span>初始密码</span>
                <input v-model="createForm.password" type="password" />
              </label>
              <button type="submit" class="form-grid__submit">创建账户</button>
            </form>
          </SurfaceCard>
        </aside>

        <div class="accounts-layout__main">
          <SurfaceCard kicker="账户目录" :title="isAdmin ? '账户管理台' : '我的账户记录'">
            <template #meta>
              <span class="status-chip">{{ accountRows.length }} 条记录</span>
            </template>

            <div v-if="accountRows.length" class="account-list">
              <article v-for="row in accountRows" :key="row.actorId" class="account-card">
                <div class="account-card__header">
                  <div>
                    <strong>{{ row.displayName }}</strong>
                    <p>{{ row.actorId }} · {{ formatRoleLabel(row.actorRole) }}</p>
                  </div>
                  <span class="status-chip" :class="{ 'status-chip--danger': row.status !== 'active' }">
                    {{ formatRequestStatusLabel(row.status) }}
                  </span>
                </div>

                <dl class="account-card__details">
                  <div>
                    <dt>机构</dt>
                    <dd>{{ formatOrganizationLabel(row.actorOrg) }}</dd>
                  </div>
                  <div>
                    <dt>创建时间</dt>
                    <dd>{{ formatTime(row.createdAt) }}</dd>
                  </div>
                  <div>
                    <dt>最近登录</dt>
                    <dd>{{ formatTime(row.lastLoginAt) }}</dd>
                  </div>
                  <div>
                    <dt>最近改密</dt>
                    <dd>{{ formatTime(row.passwordChangedAt) }}</dd>
                  </div>
                  <div>
                    <dt>凭证状态</dt>
                    <dd>{{ formatIdentityStatusLabel(row.credentialStatus.effectiveStatus) }}</dd>
                  </div>
                  <div>
                    <dt>状态来源</dt>
                    <dd>{{ formatIdentityStatusSourceLabel(row.credentialStatus.source) }}</dd>
                  </div>
                </dl>

                <p v-if="row.credentialStatus.reason" class="account-card__hint">{{ row.credentialStatus.reason }}</p>

                <div class="history-timeline">
                  <div v-for="entry in row.credentialHistory.slice(0, 3)" :key="`${row.actorId}-${entry.id ?? entry.createdAt ?? entry.nextStatus}`" class="history-timeline__item">
                    <strong>{{ formatHistoryTransition(entry.previousStatus, entry.nextStatus) }}</strong>
                    <p v-if="entry.reason">{{ entry.reason }}</p>
                    <span>{{ formatIdentityStatusSourceLabel(entry.source) }} · {{ entry.updatedBy || '系统' }} · {{ formatTime(entry.createdAt) }}</span>
                  </div>
                </div>

                <div v-if="isAdmin" class="account-card__actions">
                  <button type="button" @click="toggleStatus(row)" :disabled="!canManageRow(row) || actionLoadingId === row.actorId">
                    {{ row.status === 'active' ? '停用账户' : '启用账户' }}
                  </button>
                  <button
                    v-if="row.actorRole === 'researcher'"
                    type="button"
                    @click="promoteToApprover(row)"
                    :disabled="!canManageRow(row) || actionLoadingId === row.actorId"
                  >
                    提升为审批人
                  </button>
                  <button type="button" class="account-card__danger" @click="resetPassword(row)" :disabled="!canManageRow(row) || actionLoadingId === row.actorId">
                    重置为默认密码
                  </button>
                </div>
                <div v-if="isAdmin" class="credential-form">
                  <label>
                    <span>凭证状态</span>
                    <select v-model="credentialForms[row.actorId].status" :disabled="!canManageRow(row) || actionLoadingId === row.actorId">
                      <option value="issued">已签发</option>
                      <option value="suspended">已暂停</option>
                      <option value="revoked">已吊销</option>
                    </select>
                  </label>
                  <label>
                    <span>治理说明</span>
                    <input
                      v-model="credentialForms[row.actorId].reason"
                      type="text"
                      placeholder="例如：待补材料复核"
                      :disabled="!canManageRow(row) || actionLoadingId === row.actorId"
                    />
                  </label>
                  <button
                    type="button"
                    class="credential-form__submit"
                    @click="submitCredentialStatus(row)"
                    :disabled="!canManageRow(row) || actionLoadingId === row.actorId"
                  >
                    更新凭证状态
                  </button>
                </div>
                <p v-if="isAdmin && !canManageRow(row)" class="account-card__hint">当前登录的管理员账户受保护，停用、降权和管理员代重置都会被拦截。</p>
              </article>
            </div>
            <div v-else class="empty-state">{{ roleGuide.emptyDirectory }}</div>
          </SurfaceCard>

          <SurfaceCard v-if="organizationRows.length" kicker="机构凭证" title="机构凭证治理">
            <template #meta>
              <span class="status-chip">{{ organizationRows.length }} 个机构</span>
            </template>

            <div class="account-list">
              <article v-for="row in organizationRows" :key="row.organizationName" class="account-card">
                <div class="account-card__header">
                  <div>
                    <strong>{{ formatOrganizationLabel(row.organizationName) }}</strong>
                    <p>{{ row.organizationDid }}</p>
                  </div>
                  <span class="status-chip" :class="{ 'status-chip--danger': row.statusSnapshot.effectiveStatus === 'revoked' }">
                    {{ formatIdentityStatusLabel(row.statusSnapshot.effectiveStatus) }}
                  </span>
                </div>

                <dl class="account-card__details">
                  <div>
                    <dt>凭证类型</dt>
                    <dd>{{ row.credential.type }}</dd>
                  </div>
                  <div>
                    <dt>状态来源</dt>
                    <dd>{{ formatIdentityStatusSourceLabel(row.statusSnapshot.source) }}</dd>
                  </div>
                  <div>
                    <dt>签发方</dt>
                    <dd>{{ row.credential.issuerDid }}</dd>
                  </div>
                  <div>
                    <dt>到期时间</dt>
                    <dd>{{ formatTime(row.credential.expiresAt) }}</dd>
                  </div>
                </dl>

                <p v-if="row.statusSnapshot.reason" class="account-card__hint">{{ row.statusSnapshot.reason }}</p>

                <div class="history-timeline">
                  <div v-for="entry in row.credentialHistory.slice(0, 3)" :key="`${row.organizationName}-${entry.id ?? entry.createdAt ?? entry.nextStatus}`" class="history-timeline__item">
                    <strong>{{ formatHistoryTransition(entry.previousStatus, entry.nextStatus) }}</strong>
                    <p v-if="entry.reason">{{ entry.reason }}</p>
                    <span>{{ formatIdentityStatusSourceLabel(entry.source) }} · {{ entry.updatedBy || '系统' }} · {{ formatTime(entry.createdAt) }}</span>
                  </div>
                </div>

                <div v-if="isAdmin" class="credential-form">
                  <label>
                    <span>机构凭证状态</span>
                    <select
                      v-model="organizationCredentialForms[row.organizationName].status"
                      :disabled="actionLoadingId === `org:${row.organizationName}`"
                    >
                      <option value="issued">已签发</option>
                      <option value="suspended">已暂停</option>
                      <option value="revoked">已吊销</option>
                    </select>
                  </label>
                  <label>
                    <span>治理说明</span>
                    <input
                      v-model="organizationCredentialForms[row.organizationName].reason"
                      type="text"
                      placeholder="例如：机构合规审核中"
                      :disabled="actionLoadingId === `org:${row.organizationName}`"
                    />
                  </label>
                  <button
                    type="button"
                    class="credential-form__submit"
                    @click="submitOrganizationCredentialStatus(row)"
                    :disabled="actionLoadingId === `org:${row.organizationName}`"
                  >
                    更新机构凭证状态
                  </button>
                </div>
              </article>
            </div>
          </SurfaceCard>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.accounts-page {
  display: grid;
  gap: 18px;
}

.hero-panel__rail,
.summary-strip,
.accounts-layout,
.accounts-layout__side,
.accounts-layout__main,
.account-list {
  display: grid;
  gap: var(--space-list);
}

.hero-panel__guide {
  display: grid;
  gap: 8px;
  max-width: 720px;
  padding: var(--space-subpanel);
  border-radius: var(--radius-subpanel);
  border: 1px solid var(--line-warm);
  background: var(--panel-soft-gradient);
}

.hero-panel__guide span {
  color: var(--text-faint);
  font-size: 0.72rem;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.hero-panel__guide strong {
  font-family: var(--body);
  font-size: 0.94rem;
  line-height: 1.6;
}

.summary-strip {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.summary-strip__card,
.hero-spotlight,
.account-card {
  padding: var(--space-card);
  border-radius: var(--radius-panel);
  border: 1px solid var(--line);
  background: var(--panel-gradient);
}

.summary-strip__card span,
.hero-spotlight__kicker,
.hero-spotlight__meta span,
.account-card dt {
  display: block;
  color: var(--text-faint);
  font-size: var(--field-label-size);
  letter-spacing: var(--field-label-letter-spacing);
  text-transform: uppercase;
}

.summary-strip__card strong,
.hero-spotlight__headline strong,
.hero-spotlight__meta strong,
.account-card strong {
  display: block;
  font-family: var(--body);
}

.summary-strip__card strong {
  margin-top: 10px;
  font-size: clamp(1.8rem, 3vw, 2.4rem);
}

.hero-spotlight {
  display: grid;
  gap: 14px;
}

.hero-spotlight__headline,
.account-card__header,
.account-card__actions {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.hero-spotlight__meta,
.account-card__details,
.form-grid {
  display: grid;
  gap: 12px;
}

.hero-spotlight__meta {
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
}

.hero-spotlight__meta div,
.account-card__details div {
  min-width: 0;
  padding: var(--space-subpanel);
  border-radius: var(--radius-subpanel);
  border: 1px solid var(--line);
  background: var(--panel-soft-gradient);
}

.accounts-layout {
  grid-template-columns: minmax(320px, 360px) minmax(0, 1fr);
}

.panel-link {
  text-decoration: none;
  font-family: var(--body);
  font-size: 0.8rem;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--text-muted);
}

.panel-note {
  margin: 0;
  color: var(--text-muted);
  font-size: var(--supporting-text-size);
  line-height: var(--supporting-text-line-height);
  overflow-wrap: anywhere;
  word-break: break-word;
}

.hero-spotlight__context,
.history-timeline__item p,
.history-timeline__item span {
  color: var(--text-muted);
  font-size: var(--supporting-text-size);
  line-height: var(--supporting-text-line-height);
}

.form-grid label {
  display: grid;
  gap: 8px;
}

.form-grid span {
  color: var(--text-faint);
  font-size: var(--field-label-size);
  letter-spacing: var(--field-label-letter-spacing);
  text-transform: uppercase;
}

.form-grid input,
.form-grid select {
  width: 100%;
  min-height: var(--field-height);
  padding: var(--space-field-x);
  border: 1px solid var(--line);
  border-radius: var(--radius-control);
  background: var(--bg-panel);
  color: var(--text-main);
}

.form-grid__submit,
.account-card__actions button {
  min-height: var(--control-height);
  padding: var(--space-button);
  border: 1px solid var(--line);
  border-radius: var(--radius-pill);
  background: var(--button-soft-gradient);
  color: var(--text-main);
  font-family: var(--body);
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.form-grid__submit {
  border-color: var(--line-warm);
  background: var(--button-warm-gradient);
}

.account-card__header p,
.account-card dd,
.account-card__hint {
  margin: 6px 0 0;
  color: var(--text-muted);
  font-size: var(--supporting-text-size);
  line-height: var(--supporting-text-line-height);
}

.hero-spotlight__headline > div,
.account-card__header > div,
.hero-spotlight__context,
.hero-spotlight__meta strong,
.account-card__details dd,
.account-card__hint,
.history-timeline__item p,
.history-timeline__item span {
  min-width: 0;
  overflow-wrap: anywhere;
  word-break: break-word;
}

.account-card__details {
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  margin-top: 14px;
}

.account-card__actions {
  flex-wrap: wrap;
  margin-top: 16px;
}

.history-timeline {
  display: grid;
  gap: var(--space-list-tight);
  margin-top: 14px;
}

.history-timeline__item {
  padding: 12px 14px;
  border-radius: var(--radius-control);
  border: 1px solid var(--line);
  background: var(--panel-soft-gradient);
}

.history-timeline__item strong {
  font-family: var(--body);
  font-weight: 700;
}

.history-timeline__item p,
.history-timeline__item span {
  display: block;
  margin: 6px 0 0;
  color: var(--text-muted);
}

.credential-form {
  display: grid;
  gap: 10px;
  margin-top: 14px;
  padding: var(--space-subpanel);
  border-radius: var(--radius-subpanel);
  border: 1px solid var(--line);
  background: var(--panel-soft-gradient);
}

.credential-form label {
  display: grid;
  gap: 8px;
}

.credential-form span {
  color: var(--text-faint);
  font-size: 0.74rem;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.credential-form input,
.credential-form select,
.credential-form__submit {
  width: 100%;
  min-height: var(--field-height);
  padding: var(--space-field-x);
  border: 1px solid var(--line);
  border-radius: var(--radius-control);
  background: var(--bg-panel);
  color: var(--text-main);
}

.credential-form__submit {
  min-height: var(--control-height);
  border-color: var(--line-warm);
  background: var(--button-warm-gradient);
  font-family: var(--body);
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.account-card__danger {
  border-color: rgba(242, 126, 126, 0.28) !important;
  color: var(--danger) !important;
}

@media (max-width: 1040px) {
  .accounts-layout,
  .summary-strip,
  .hero-spotlight__meta,
  .account-card__details {
    grid-template-columns: 1fr;
  }
}
</style>
