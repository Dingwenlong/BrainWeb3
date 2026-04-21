<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'
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

const loading = ref(true)
const error = ref<string | null>(null)
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
      note: '管理员可以在这里完成账户创建、状态切换和密码重置，同时保留自保护边界，避免误伤当前治理入口。',
      credentialHint: 'VC 生命周期也集中在这里治理。签发会恢复可用状态，挂起适合临时冻结，吊销则代表明确撤销凭证。',
      passwordHint: '修改当前管理员密码会影响下次登录，代重置其他账户密码则会同步作废对方的刷新令牌。',
      directoryHint: '这里展示的是全局账户目录，适合从治理侧巡检角色分布、最近登录和状态切换。',
      emptyDirectory: '当前还没有其他账户记录，先创建一个研究员或审批人账户，补齐多角色演示面。',
    }
  }
  return {
    note: '非管理员只会看到自己的账户记录，重点是自助改密、确认最近登录以及回看个人身份信息。',
    credentialHint: '你的 VC 状态会跟随账户和机构治理结果变化，这里主要用于查看，不提供自助变更。',
    passwordHint: '修改密码后，下次登录会使用新密码；如忘记密码，可回到登录页走恢复票据流程。',
    directoryHint: '这里保留的是你的个人账户记录，不会暴露其他人的身份信息或治理动作。',
    emptyDirectory: '当前还没有账户记录，请稍后刷新；系统至少会保留你当前登录的账户信息。',
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
  loading.value = true
  error.value = null

  try {
    const [currentAccount, currentIdentity] = await Promise.all([
      getCurrentAccount(),
      getCurrentIdentity(),
    ])
    account.value = currentAccount
    identity.value = currentIdentity
    credentialVerification.value = await verifyCredential({
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
      accountRows.value = await getAccounts()
      const organizationNames = [...new Set(accountRows.value.map((row) => row.actorOrg))]
      organizationRows.value = await Promise.all(
        organizationNames.map((name) => getOrganizationIdentity(name)),
      )
    } else {
      accountRows.value = account.value ? [account.value] : []
      organizationRows.value = identity.value
        ? [await getOrganizationIdentity(identity.value.actorOrg)]
        : []
    }
    syncCredentialForms(accountRows.value)
    syncOrganizationCredentialForms(organizationRows.value)
  } catch (loadError) {
    error.value = loadError instanceof Error ? loadError.message : '加载账户页失败。'
  } finally {
    loading.value = false
  }
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
    error.value = changeError instanceof Error ? changeError.message : '修改密码失败。'
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
    error.value = createError instanceof Error ? createError.message : '创建账户失败。'
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
    error.value = updateError instanceof Error ? updateError.message : '更新账户状态失败。'
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
    error.value = updateError instanceof Error ? updateError.message : '更新账户角色失败。'
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
    error.value = resetError instanceof Error ? resetError.message : '重置密码失败。'
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
      title: 'VC 状态已更新',
      message: `${row.actorId} 的 VC 状态已切换为 ${formatIdentityStatusLabel(draft.status)}。`,
      tone: draft.status === 'revoked' ? 'warning' : 'success',
    })
    await loadPage()
  } catch (updateError) {
    error.value = updateError instanceof Error ? updateError.message : '更新 VC 状态失败。'
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
      title: '机构 VC 状态已更新',
      message: `${formatOrganizationLabel(row.organizationName)} 的 VC 状态已切换为 ${formatIdentityStatusLabel(draft.status)}。`,
      tone: draft.status === 'revoked' ? 'warning' : 'success',
    })
    await loadPage()
  } catch (updateError) {
    error.value = updateError instanceof Error ? updateError.message : '更新机构 VC 状态失败。'
  } finally {
    actionLoadingId.value = null
  }
}

onMounted(loadPage)
</script>

<template>
  <div class="accounts-page">
    <section class="hero-panel glass-panel">
      <div class="hero-panel__copy">
        <p class="section-kicker">Account Mesh</p>
        <h1>把演示账号升级成真正可管理的账户域。</h1>
        <p class="hero-panel__lede">
          当前身份是 {{ actorProfile.actorId }} / {{ formatRoleLabel(actorProfile.actorRole) }} /
          {{ formatOrganizationLabel(actorProfile.actorOrg) }}。这一页负责把注册、自助改密和管理员账户维护集中到一处。
        </p>
        <div class="hero-panel__guide">
          <span>Role Guidance</span>
          <strong>{{ roleGuide.note }}</strong>
        </div>

        <div class="summary-strip">
          <article v-for="stat in accountStats" :key="stat.label" class="summary-strip__card">
            <span>{{ stat.label }}</span>
            <strong>{{ stat.value }}</strong>
          </article>
          <article v-for="stat in organizationStats" :key="stat.label" class="summary-strip__card summary-strip__card--secondary">
            <span>{{ stat.label }}</span>
            <strong>{{ stat.value }}</strong>
          </article>
        </div>
      </div>

      <div class="hero-panel__rail">
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
      </div>
    </section>

    <div v-if="loading" class="loading-state">正在加载账户域...</div>
    <div v-else-if="error" class="error-state">{{ error }}</div>

    <template v-else>
      <section class="accounts-layout">
        <aside class="accounts-layout__side">
          <article class="workspace-card glass-panel">
            <div class="workspace-card__header">
              <div>
                <p class="section-kicker">安全设置</p>
                <h2 class="section-title">修改密码</h2>
              </div>
              <RouterLink class="workspace-card__link" to="/">返回总览</RouterLink>
            </div>

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

            <p class="workspace-card__note">{{ roleGuide.passwordHint }}</p>
          </article>

          <article v-if="identity" class="workspace-card glass-panel">
            <div class="workspace-card__header">
              <div>
                <p class="section-kicker">Identity Card</p>
                <h2 class="section-title">我的 DID / VC</h2>
              </div>
              <RouterLink class="workspace-card__link" to="/identity-center">打开身份中心</RouterLink>
            </div>

            <dl class="account-card__details">
              <div>
                <dt>Actor DID</dt>
                <dd>{{ identity.actorDid }}</dd>
              </div>
              <div>
                <dt>Org DID</dt>
                <dd>{{ identity.organizationDid }}</dd>
              </div>
              <div>
                <dt>VC 类型</dt>
                <dd>{{ identity.credential.type }}</dd>
              </div>
              <div>
                <dt>VC 状态</dt>
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

            <p class="workspace-card__note">
              {{ credentialVerification?.reason ?? '当前身份 VC 已通过平台内置校验。' }}
            </p>
            <p class="workspace-card__note">
              {{ roleGuide.credentialHint }}
            </p>
          </article>

          <article v-if="isAdmin" class="workspace-card glass-panel">
            <div class="workspace-card__header">
              <div>
                <p class="section-kicker">Admin Create</p>
                <h2 class="section-title">创建账户</h2>
              </div>
            </div>

            <form class="form-grid" @submit.prevent="submitCreateAccount">
              <label>
                <span>Actor ID</span>
                <input v-model="createForm.actorId" type="text" />
              </label>
              <label>
                <span>显示名</span>
                <input v-model="createForm.displayName" type="text" />
              </label>
              <label>
                <span>角色</span>
                <select v-model="createForm.actorRole">
                  <option value="researcher">researcher</option>
                  <option value="owner">owner</option>
                  <option value="approver">approver</option>
                  <option value="admin">admin</option>
                </select>
              </label>
              <label>
                <span>机构</span>
                <input v-model="createForm.actorOrg" type="text" />
              </label>
              <label>
                <span>状态</span>
                <select v-model="createForm.status">
                  <option value="active">active</option>
                  <option value="disabled">disabled</option>
                </select>
              </label>
              <label>
                <span>初始密码</span>
                <input v-model="createForm.password" type="password" />
              </label>
              <button type="submit" class="form-grid__submit">创建账户</button>
            </form>
          </article>
        </aside>

        <div class="accounts-layout__main">
          <article class="workspace-card glass-panel">
            <div class="workspace-card__header">
              <div>
                <p class="section-kicker">Account Directory</p>
                <h2 class="section-title">{{ isAdmin ? '账户管理台' : '我的账户记录' }}</h2>
                <p class="workspace-card__note workspace-card__note--inline">{{ roleGuide.directoryHint }}</p>
              </div>
              <span class="status-chip">{{ accountRows.length }} 条记录</span>
            </div>

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
                    <dt>VC 状态</dt>
                    <dd>{{ formatIdentityStatusLabel(row.credentialStatus.effectiveStatus) }}</dd>
                  </div>
                  <div>
                    <dt>状态来源</dt>
                    <dd>{{ formatIdentityStatusSourceLabel(row.credentialStatus.source) }}</dd>
                  </div>
                </dl>

                <p class="account-card__hint">
                  {{ row.credentialStatus.reason || '当前 VC 状态暂无额外说明。' }}
                </p>

                <div class="history-timeline">
                  <div v-for="entry in row.credentialHistory.slice(0, 3)" :key="`${row.actorId}-${entry.id ?? entry.createdAt ?? entry.nextStatus}`" class="history-timeline__item">
                    <strong>{{ formatHistoryTransition(entry.previousStatus, entry.nextStatus) }}</strong>
                    <p>{{ entry.reason || '该次治理未附带额外说明。' }}</p>
                    <span>{{ formatIdentityStatusSourceLabel(entry.source) }} · {{ entry.updatedBy || 'system' }} · {{ formatTime(entry.createdAt) }}</span>
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
                    <span>VC 状态</span>
                    <select v-model="credentialForms[row.actorId].status" :disabled="!canManageRow(row) || actionLoadingId === row.actorId">
                      <option value="issued">issued</option>
                      <option value="suspended">suspended</option>
                      <option value="revoked">revoked</option>
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
                    更新 VC 状态
                  </button>
                </div>
                <p v-if="isAdmin && !canManageRow(row)" class="account-card__hint">当前登录的管理员账户受保护，停用、降权和管理员代重置都会被拦截。</p>
              </article>
            </div>
            <div v-else class="empty-state">{{ roleGuide.emptyDirectory }}</div>
          </article>

          <article v-if="organizationRows.length" class="workspace-card glass-panel">
            <div class="workspace-card__header">
              <div>
                <p class="section-kicker">Organization VC</p>
                <h2 class="section-title">机构凭证治理</h2>
                <p class="workspace-card__note workspace-card__note--inline">机构级 DID / VC 会直接影响机构可信状态和数据详情页的身份说明。</p>
              </div>
              <span class="status-chip">{{ organizationRows.length }} 个机构</span>
            </div>

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
                    <dt>VC 类型</dt>
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

                <p class="account-card__hint">
                  {{ row.statusSnapshot.reason || '当前机构 VC 状态暂无额外说明。' }}
                </p>

                <div class="history-timeline">
                  <div v-for="entry in row.credentialHistory.slice(0, 3)" :key="`${row.organizationName}-${entry.id ?? entry.createdAt ?? entry.nextStatus}`" class="history-timeline__item">
                    <strong>{{ formatHistoryTransition(entry.previousStatus, entry.nextStatus) }}</strong>
                    <p>{{ entry.reason || '该次治理未附带额外说明。' }}</p>
                    <span>{{ formatIdentityStatusSourceLabel(entry.source) }} · {{ entry.updatedBy || 'system' }} · {{ formatTime(entry.createdAt) }}</span>
                  </div>
                </div>

                <div v-if="isAdmin" class="credential-form">
                  <label>
                    <span>机构 VC 状态</span>
                    <select
                      v-model="organizationCredentialForms[row.organizationName].status"
                      :disabled="actionLoadingId === `org:${row.organizationName}`"
                    >
                      <option value="issued">issued</option>
                      <option value="suspended">suspended</option>
                      <option value="revoked">revoked</option>
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
                    更新机构 VC 状态
                  </button>
                </div>
              </article>
            </div>
          </article>
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

.hero-panel {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(320px, 0.85fr);
  gap: 20px;
  padding: 26px;
  border-radius: 30px;
  background:
    linear-gradient(135deg, rgba(7, 19, 26, 0.96), rgba(6, 13, 18, 0.84)),
    radial-gradient(circle at top left, rgba(116, 210, 220, 0.14), transparent 36%);
}

.hero-panel__copy,
.hero-panel__rail,
.summary-strip,
.accounts-layout,
.accounts-layout__side,
.accounts-layout__main,
.account-list {
  display: grid;
  gap: 18px;
}

.hero-panel h1 {
  margin: 0;
  font-family: var(--display);
  font-size: clamp(2.5rem, 5vw, 4rem);
  line-height: 0.96;
}

.hero-panel__lede,
.workspace-card__link {
  color: var(--text-muted);
}

.hero-panel__guide {
  display: grid;
  gap: 8px;
  max-width: 720px;
  padding: 14px 16px;
  border-radius: 18px;
  border: 1px solid rgba(235, 178, 102, 0.18);
  background: rgba(17, 24, 16, 0.42);
}

.hero-panel__guide span {
  color: var(--text-faint);
  font-size: 0.72rem;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.hero-panel__guide strong {
  font-family: var(--display);
  font-size: 0.94rem;
  line-height: 1.6;
}

.summary-strip {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.summary-strip__card,
.hero-spotlight,
.account-card {
  padding: 18px;
  border-radius: 24px;
  border: 1px solid rgba(108, 166, 186, 0.14);
  background:
    linear-gradient(180deg, rgba(4, 15, 21, 0.94), rgba(8, 17, 23, 0.78)),
    radial-gradient(circle at top right, rgba(116, 210, 220, 0.08), transparent 30%);
}

.summary-strip__card span,
.hero-spotlight__kicker,
.hero-spotlight__meta span,
.account-card dt {
  display: block;
  color: var(--text-faint);
  font-size: 0.72rem;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.summary-strip__card strong,
.hero-spotlight__headline strong,
.hero-spotlight__meta strong,
.account-card strong {
  display: block;
  font-family: var(--display);
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
.workspace-card__header,
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
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.hero-spotlight__meta div,
.account-card__details div {
  padding: 14px 16px;
  border-radius: 18px;
  border: 1px solid rgba(108, 166, 186, 0.1);
  background: rgba(8, 18, 25, 0.76);
}

.accounts-layout {
  grid-template-columns: minmax(320px, 360px) minmax(0, 1fr);
}

.workspace-card {
  padding: 20px;
  border-radius: 24px;
}

.workspace-card__link {
  text-decoration: none;
  font-family: var(--display);
  font-size: 0.8rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.workspace-card__note {
  margin: 14px 0 0;
  color: var(--text-muted);
  line-height: 1.7;
}

.workspace-card__note--inline {
  margin-top: 10px;
}

.form-grid label {
  display: grid;
  gap: 8px;
}

.form-grid span {
  color: var(--text-faint);
  font-size: 0.74rem;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.form-grid input,
.form-grid select {
  width: 100%;
  min-height: 44px;
  padding: 10px 12px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: rgba(8, 18, 25, 0.94);
  color: var(--text-main);
}

.form-grid__submit,
.account-card__actions button {
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

.form-grid__submit {
  border-color: var(--line-warm);
  background: linear-gradient(180deg, rgba(235, 178, 102, 0.24), rgba(235, 178, 102, 0.12));
}

.account-card__header p,
.account-card dd,
.account-card__hint {
  margin: 6px 0 0;
  color: var(--text-muted);
}

.account-card__details {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin-top: 14px;
}

.account-card__actions {
  flex-wrap: wrap;
  margin-top: 16px;
}

.history-timeline {
  display: grid;
  gap: 10px;
  margin-top: 14px;
}

.history-timeline__item {
  padding: 12px 14px;
  border-radius: 14px;
  border: 1px solid rgba(108, 166, 186, 0.1);
  background: rgba(8, 18, 25, 0.64);
}

.history-timeline__item strong {
  font-family: var(--display);
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
  padding: 14px 16px;
  border-radius: 18px;
  border: 1px solid rgba(108, 166, 186, 0.1);
  background: rgba(8, 18, 25, 0.76);
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
  min-height: 42px;
  padding: 10px 12px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: rgba(8, 18, 25, 0.94);
  color: var(--text-main);
}

.credential-form__submit {
  border-color: rgba(116, 210, 220, 0.22);
  font-family: var(--display);
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.account-card__danger {
  border-color: rgba(242, 126, 126, 0.28) !important;
  color: var(--danger) !important;
}

@media (max-width: 1040px) {
  .hero-panel,
  .accounts-layout,
  .summary-strip,
  .hero-spotlight__meta,
  .account-card__details {
    grid-template-columns: 1fr;
  }
}
</style>
