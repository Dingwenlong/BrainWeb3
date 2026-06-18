<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import {
  getAccounts,
  getAudits,
  getCurrentAccount,
  getCurrentIdentity,
  getOrganizationIdentity,
  verifyCredential,
} from '../api/client'
import { useAsyncView } from '../composables/useAsyncView'
import { useActorProfile } from '../composables/useActorProfile'
import type {
  AccountUser,
  ActorIdentity,
  AuditEvent,
  CredentialVerificationResult,
  OrganizationIdentity,
} from '../types/api'
import {
  formatActorName,
  formatAuditActionLabel,
  formatCredentialReason,
  formatCredentialType,
  formatIdentityStatusLabel,
  formatIdentityStatusSourceLabel,
  formatOrganizationLabel,
  formatRequestStatusLabel,
  formatRoleLabel,
} from '../utils/labels'

const { actorProfile, isAdmin } = useActorProfile()

const { loading, error, run: runPageLoad } = useAsyncView({
  initialLoading: true,
})
const account = ref<AccountUser | null>(null)
const identity = ref<ActorIdentity | null>(null)
const verification = ref<CredentialVerificationResult | null>(null)
const accountRows = ref<AccountUser[]>([])
const organizationRows = ref<OrganizationIdentity[]>([])
const identityAuditRows = ref<AuditEvent[]>([])

const scopeLabel = computed(() => {
  if (isAdmin.value) {
    return '全局身份治理视图'
  }
  return `个人身份视图 · ${actorProfile.value.actorId}`
})

const roleGuide = computed(() => {
  if (isAdmin.value) {
    return {
      accountEmpty: '暂无账户身份记录',
      orgEmpty: '暂无机构身份记录',
      auditEmpty: '暂无身份审计事件',
    }
  }

  return {
    accountEmpty: '暂无账户身份记录',
    orgEmpty: '暂无机构身份记录',
    auditEmpty: '暂无身份审计事件',
  }
})

const currentOrganization = computed(
  () =>
    organizationRows.value.find((row) => row.organizationName === actorProfile.value.actorOrg) ??
    organizationRows.value[0] ??
    null,
)

const identityStats = computed(() => [
  { label: '账户身份', value: String(accountRows.value.length) },
  {
    label: '待治理账户凭证',
    value: String(accountRows.value.filter((row) => row.credentialStatus.effectiveStatus !== 'issued').length),
  },
  {
    label: '待治理机构凭证',
    value: String(organizationRows.value.filter((row) => row.statusSnapshot.effectiveStatus !== 'issued').length),
  },
  { label: '身份审计事件', value: String(identityAuditRows.value.length) },
])

const spotlightAudit = computed(() => identityAuditRows.value[0] ?? null)

function formatTime(value: string | null | undefined) {
  if (!value) {
    return '暂无'
  }
  return new Date(value).toLocaleString()
}

function formatHistoryTransition(previousStatus: string | null, nextStatus: string) {
  if (!previousStatus) {
    return formatIdentityStatusLabel(nextStatus)
  }
  return `${formatIdentityStatusLabel(previousStatus)} -> ${formatIdentityStatusLabel(nextStatus)}`
}

function isIdentityAudit(action: string) {
  return (
    action.startsWith('AUTH_') ||
    action.startsWith('ACCOUNT_') ||
    action === 'ORGANIZATION_CREDENTIAL_STATUS_UPDATED'
  )
}

async function loadPage() {
  const payload = await runPageLoad(async () => {
    const [currentAccount, currentIdentity, auditRows] = await Promise.all([
      getCurrentAccount(),
      getCurrentIdentity(),
      getAudits(actorProfile.value),
    ])

    const credentialVerification = await verifyCredential({
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
    const identityAudits = auditRows.filter((row) => isIdentityAudit(row.action))

    if (isAdmin.value) {
      const accounts = await getAccounts()
      const organizationNames = [...new Set(accounts.map((row) => row.actorOrg))]
      const organizations = await Promise.all(organizationNames.map((name) => getOrganizationIdentity(name)))
      return {
        currentAccount,
        currentIdentity,
        credentialVerification,
        identityAudits,
        accounts,
        organizations,
      }
    } else {
      const organizations = [await getOrganizationIdentity(currentIdentity.actorOrg)]
      return {
        currentAccount,
        currentIdentity,
        credentialVerification,
        identityAudits,
        accounts: currentAccount ? [currentAccount] : [],
        organizations,
      }
    }
  }, '加载身份中心失败。')

  if (!payload) {
    return
  }

  account.value = payload.currentAccount
  identity.value = payload.currentIdentity
  verification.value = payload.credentialVerification
  identityAuditRows.value = payload.identityAudits
  accountRows.value = payload.accounts
  organizationRows.value = payload.organizations
}

onMounted(loadPage)
</script>

<template>
  <div class="identity-page">
    <section class="hero-panel glass-panel">
      <div class="hero-panel__copy">
        <p class="section-kicker">身份中心</p>
        <h1 class="page-main-heading">DID / VC 治理</h1>
        <p class="page-main-lede">
          查看你与所属机构的去中心化身份（DID）和可验证凭证（VC）状态。简单说：DID 是你在平台的唯一身份标识，VC 是一张可被验证的「数字证件」；管理员可在此挂起 / 吊销凭证。
        </p>

        <div class="hero-panel__actions">
          <span class="status-chip status-chip--ghost hero-panel__scope">{{ scopeLabel }}</span>
          <RouterLink class="hero-panel__secondary" to="/accounts">打开账户页</RouterLink>
          <RouterLink class="hero-panel__secondary" to="/audits?action=ACCOUNT_CREDENTIAL_STATUS_UPDATED">打开身份审计</RouterLink>
        </div>

        <div class="summary-strip">
          <article v-for="stat in identityStats" :key="stat.label" class="summary-strip__card">
            <span>{{ stat.label }}</span>
            <strong>{{ stat.value }}</strong>
          </article>
        </div>
      </div>

      <div class="hero-panel__rail">
        <article class="hero-spotlight hero-spotlight--identity">
          <p class="hero-spotlight__kicker">我的身份</p>
          <template v-if="identity">
            <div class="hero-spotlight__headline">
              <strong>{{ identity.displayName }}</strong>
              <span
                class="status-chip"
                :class="[
                  (verification?.status ?? identity.credential.verificationStatus) === 'verified'
                    ? ''
                    : (verification?.status ?? identity.credential.verificationStatus) === 'suspended'
                      ? 'status-chip--warn'
                      : 'status-chip--danger',
                ]"
              >
                {{ formatIdentityStatusLabel(verification?.status ?? identity.credential.verificationStatus) }}
              </span>
            </div>
            <p class="hero-spotlight__context hero-spotlight__did">{{ identity.actorDid }}</p>
            <p v-if="formatCredentialReason(verification?.reason)" class="hero-spotlight__reason">{{ formatCredentialReason(verification?.reason) }}</p>
            <div class="hero-spotlight__meta">
              <div>
                <span>凭证状态</span>
                <strong class="mono">{{ formatIdentityStatusLabel(identity.credential.credentialStatus) }}</strong>
              </div>
              <div>
                <span>机构 DID</span>
                <strong class="mono">{{ identity.organizationDid }}</strong>
              </div>
              <div>
                <span>到期时间</span>
                <strong class="mono">{{ formatTime(identity.credential.expiresAt) }}</strong>
              </div>
            </div>
          </template>
        </article>

        <article class="hero-spotlight hero-spotlight--audit">
          <p class="hero-spotlight__kicker">最新身份审计</p>
          <template v-if="spotlightAudit">
            <div class="hero-spotlight__headline">
              <strong>{{ formatAuditActionLabel(spotlightAudit.action) }}</strong>
              <span class="status-chip" :class="{ 'status-chip--danger': spotlightAudit.status !== 'success' }">
                {{ formatRequestStatusLabel(spotlightAudit.status) }}
              </span>
            </div>
            <p class="hero-spotlight__context">
              <span class="mono">{{ spotlightAudit.actorId }}</span> · {{ formatRoleLabel(spotlightAudit.actorRole) }} ·
              {{ formatOrganizationLabel(spotlightAudit.actorOrg) }}
            </p>
            <p v-if="spotlightAudit.detail" class="hero-spotlight__reason">{{ spotlightAudit.detail }}</p>
            <div class="hero-spotlight__meta">
              <div>
                <span>原始动作</span>
                <strong class="mono">{{ spotlightAudit.action }}</strong>
              </div>
              <div>
                <span>对象</span>
                <strong class="mono">{{ spotlightAudit.datasetId || '平台级事件' }}</strong>
              </div>
              <div>
                <span>发生时间</span>
                <strong class="mono">{{ formatTime(spotlightAudit.createdAt) }}</strong>
              </div>
            </div>
          </template>
          <p v-else class="hero-spotlight__reason">{{ roleGuide.auditEmpty }}</p>
        </article>
      </div>
    </section>

    <div v-if="loading" class="loading-state">正在加载身份中心...</div>
    <div v-else-if="error" class="error-state">{{ error }}</div>

    <template v-else>
      <section class="identity-layout">
        <aside class="identity-layout__side">
          <article v-if="identity" class="workspace-card workspace-card--identity glass-panel">
            <div class="workspace-card__header">
              <div>
                <p class="section-kicker">当前身份凭证</p>
                <h2 class="section-title">操作者身份</h2>
              </div>
              <RouterLink class="workspace-card__link" to="/accounts">去账户治理</RouterLink>
            </div>

            <dl class="identity-details">
              <div>
                <dt>个人 DID</dt>
                <dd class="mono">{{ identity.actorDid }}</dd>
              </div>
              <div>
                <dt>机构 DID</dt>
                <dd class="mono">{{ identity.organizationDid }}</dd>
              </div>
              <div>
                <dt>凭证类型</dt>
                <dd class="mono">{{ formatCredentialType(identity.credential.type) }}</dd>
              </div>
              <div>
                <dt>签发方</dt>
                <dd class="mono">{{ identity.credential.issuerDid }}</dd>
              </div>
            </dl>

            <p v-if="formatCredentialReason(verification?.reason)" class="workspace-card__note">{{ formatCredentialReason(verification?.reason) }}</p>
          </article>

          <article v-if="currentOrganization" class="workspace-card workspace-card--identity glass-panel">
            <div class="workspace-card__header">
              <div>
                <p class="section-kicker">当前机构凭证</p>
                <h2 class="section-title">机构身份</h2>
              </div>
              <span
                class="status-chip"
                :class="[
                  currentOrganization.statusSnapshot.effectiveStatus === 'issued'
                    ? ''
                    : currentOrganization.statusSnapshot.effectiveStatus === 'suspended'
                      ? 'status-chip--warn'
                      : 'status-chip--danger',
                ]"
              >
                {{ formatIdentityStatusLabel(currentOrganization.statusSnapshot.effectiveStatus) }}
              </span>
            </div>

            <dl class="identity-details">
              <div>
                <dt>机构</dt>
                <dd>{{ formatOrganizationLabel(currentOrganization.organizationName) }}</dd>
              </div>
              <div>
                <dt>机构 DID</dt>
                <dd class="mono">{{ currentOrganization.organizationDid }}</dd>
              </div>
              <div>
                <dt>凭证状态</dt>
                <dd class="mono">{{ formatIdentityStatusLabel(currentOrganization.statusSnapshot.effectiveStatus) }}</dd>
              </div>
              <div>
                <dt>状态来源</dt>
                <dd>{{ formatIdentityStatusSourceLabel(currentOrganization.statusSnapshot.source) }}</dd>
              </div>
            </dl>

            <div v-if="currentOrganization.credentialHistory.length" class="history-timeline">
              <div
                v-for="entry in currentOrganization.credentialHistory.slice(0, 3)"
                :key="`${currentOrganization.organizationName}-${entry.id ?? entry.createdAt ?? entry.nextStatus}`"
                class="history-timeline__item"
              >
                <strong class="mono">{{ formatHistoryTransition(entry.previousStatus, entry.nextStatus) }}</strong>
                <p v-if="formatCredentialReason(entry.reason)">{{ formatCredentialReason(entry.reason) }}</p>
                <span class="mono">{{ formatIdentityStatusSourceLabel(entry.source) }} · {{ formatActorName(entry.updatedBy) }} · {{ formatTime(entry.createdAt) }}</span>
              </div>
            </div>
          </article>
        </aside>

        <div class="identity-layout__main">
          <article class="workspace-card glass-panel">
            <div class="workspace-card__header">
              <div>
                <p class="section-kicker">账户身份</p>
                <h2 class="section-title">{{ isAdmin ? '账户身份目录' : '我的账户身份' }}</h2>
              </div>
              <span class="status-chip">{{ accountRows.length }} 条记录</span>
            </div>

            <div v-if="accountRows.length" class="identity-grid">
              <article v-for="row in accountRows" :key="row.actorId" class="identity-card">
                <div class="identity-card__header">
                  <div>
                    <strong>{{ row.displayName }}</strong>
                    <p><span class="mono">{{ row.actorId }}</span> · {{ formatRoleLabel(row.actorRole) }}</p>
                  </div>
                  <span
                    class="status-chip"
                    :class="[
                      row.credentialStatus.effectiveStatus === 'issued'
                        ? ''
                        : row.credentialStatus.effectiveStatus === 'suspended'
                          ? 'status-chip--warn'
                          : 'status-chip--danger',
                    ]"
                  >
                    {{ formatIdentityStatusLabel(row.credentialStatus.effectiveStatus) }}
                  </span>
                </div>

                <dl class="identity-card__details">
                  <div>
                    <dt>机构</dt>
                    <dd>{{ formatOrganizationLabel(row.actorOrg) }}</dd>
                  </div>
                  <div>
                    <dt>账户状态</dt>
                    <dd class="mono">{{ formatRequestStatusLabel(row.status) }}</dd>
                  </div>
                  <div>
                    <dt>状态来源</dt>
                    <dd>{{ formatIdentityStatusSourceLabel(row.credentialStatus.source) }}</dd>
                  </div>
                  <div>
                    <dt>最近治理</dt>
                    <dd class="mono">{{ formatTime(row.credentialStatus.updatedAt) }}</dd>
                  </div>
                </dl>

                <div class="history-timeline">
                  <div
                    v-for="entry in row.credentialHistory.slice(0, 3)"
                    :key="`${row.actorId}-${entry.id ?? entry.createdAt ?? entry.nextStatus}`"
                    class="history-timeline__item"
                  >
                    <strong class="mono">{{ formatHistoryTransition(entry.previousStatus, entry.nextStatus) }}</strong>
                    <p v-if="formatCredentialReason(entry.reason)">{{ formatCredentialReason(entry.reason) }}</p>
                    <span class="mono">{{ formatIdentityStatusSourceLabel(entry.source) }} · {{ formatActorName(entry.updatedBy) }} · {{ formatTime(entry.createdAt) }}</span>
                  </div>
                </div>
              </article>
            </div>
            <div v-else class="empty-state">{{ roleGuide.accountEmpty }}</div>
          </article>

          <article class="workspace-card glass-panel">
            <div class="workspace-card__header">
              <div>
                <p class="section-kicker">机构身份</p>
                <h2 class="section-title">{{ isAdmin ? '机构身份目录' : '我的机构身份' }}</h2>
              </div>
              <span class="status-chip">{{ organizationRows.length }} 个机构</span>
            </div>

            <div v-if="organizationRows.length" class="identity-grid">
              <article v-for="row in organizationRows" :key="row.organizationName" class="identity-card">
                <div class="identity-card__header">
                  <div>
                    <strong>{{ formatOrganizationLabel(row.organizationName) }}</strong>
                    <p class="mono">{{ row.organizationDid }}</p>
                  </div>
                  <span
                    class="status-chip"
                    :class="[
                      row.statusSnapshot.effectiveStatus === 'issued'
                        ? ''
                        : row.statusSnapshot.effectiveStatus === 'suspended'
                          ? 'status-chip--warn'
                          : 'status-chip--danger',
                    ]"
                  >
                    {{ formatIdentityStatusLabel(row.statusSnapshot.effectiveStatus) }}
                  </span>
                </div>

                <dl class="identity-card__details">
                  <div>
                    <dt>凭证类型</dt>
                    <dd class="mono">{{ formatCredentialType(row.credential.type) }}</dd>
                  </div>
                  <div>
                    <dt>状态来源</dt>
                    <dd>{{ formatIdentityStatusSourceLabel(row.statusSnapshot.source) }}</dd>
                  </div>
                  <div>
                    <dt>签发方</dt>
                    <dd class="mono">{{ row.credential.issuerDid }}</dd>
                  </div>
                  <div>
                    <dt>最近治理</dt>
                    <dd class="mono">{{ formatTime(row.statusSnapshot.updatedAt) }}</dd>
                  </div>
                </dl>

                <div class="history-timeline">
                  <div
                    v-for="entry in row.credentialHistory.slice(0, 3)"
                    :key="`${row.organizationName}-${entry.id ?? entry.createdAt ?? entry.nextStatus}`"
                    class="history-timeline__item"
                  >
                    <strong class="mono">{{ formatHistoryTransition(entry.previousStatus, entry.nextStatus) }}</strong>
                    <p v-if="formatCredentialReason(entry.reason)">{{ formatCredentialReason(entry.reason) }}</p>
                    <span class="mono">{{ formatIdentityStatusSourceLabel(entry.source) }} · {{ formatActorName(entry.updatedBy) }} · {{ formatTime(entry.createdAt) }}</span>
                  </div>
                </div>
              </article>
            </div>
            <div v-else class="empty-state">{{ roleGuide.orgEmpty }}</div>
          </article>

          <article class="workspace-card workspace-card--audit glass-panel">
            <div class="workspace-card__header">
              <div>
                <p class="section-kicker">身份审计</p>
                <h2 class="section-title">身份审计流</h2>
              </div>
              <RouterLink class="workspace-card__link" to="/audits">打开审计中心</RouterLink>
            </div>

            <div v-if="identityAuditRows.length" class="audit-list">
              <article v-for="event in identityAuditRows.slice(0, 8)" :key="event.id" class="audit-card">
                <div class="audit-card__header">
                  <div>
                    <strong>{{ formatAuditActionLabel(event.action) }}</strong>
                    <p><span class="mono">{{ event.actorId }}</span> · {{ formatRoleLabel(event.actorRole) }}</p>
                  </div>
                  <span class="status-chip" :class="{ 'status-chip--danger': event.status !== 'success' }">
                    {{ formatRequestStatusLabel(event.status) }}
                  </span>
                </div>

                <dl class="identity-card__details">
                  <div>
                    <dt>机构</dt>
                    <dd>{{ formatOrganizationLabel(event.actorOrg) }}</dd>
                  </div>
                  <div>
                    <dt>原始动作</dt>
                    <dd class="mono">{{ event.action }}</dd>
                  </div>
                  <div>
                    <dt>对象</dt>
                    <dd class="mono">{{ event.datasetId || '平台级事件' }}</dd>
                  </div>
                  <div>
                    <dt>发生时间</dt>
                    <dd class="mono">{{ formatTime(event.createdAt) }}</dd>
                  </div>
                </dl>

                <p v-if="event.detail" class="identity-card__hint">{{ event.detail }}</p>
              </article>
            </div>
            <div v-else class="empty-state">{{ roleGuide.auditEmpty }}</div>
          </article>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.identity-page {
  display: grid;
  gap: 18px;
}

/* ---- Mono readouts: DID / VC ids, statuses, timestamps ---- */
.mono {
  font-family: var(--mono);
  color: var(--text-strong);
  letter-spacing: 0.01em;
}

/* ---- Hero: identity command banner ---- */
.hero-panel {
  position: relative;
  overflow: hidden;
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(320px, 0.85fr);
  gap: 20px;
  padding: var(--space-hero);
  border-radius: var(--radius-hero);
  background: var(--bg-panel);
  animation: consoleRise 0.55s ease both;
}

.hero-panel::before {
  content: '';
  position: absolute;
  inset: 0 0 auto;
  height: 2px;
  background: var(--line-strong);
}

.hero-panel__copy,
.hero-panel__rail,
.summary-strip,
.identity-layout,
.identity-layout__side,
.identity-layout__main,
.identity-grid,
.audit-list,
.history-timeline {
  display: grid;
  gap: var(--space-list);
}

.hero-panel__copy {
  align-content: start;
  position: relative;
  z-index: 1;
}

.hero-panel h1,
.section-title {
  margin: 0;
  font-family: var(--display);
}

.hero-panel h1 {
  color: var(--text-strong);
  font-size: var(--page-heading-size);
  font-weight: 600;
  line-height: var(--page-heading-line-height);
  letter-spacing: var(--page-heading-letter-spacing);
  text-wrap: balance;
}

.hero-panel__lede {
  margin: 0;
  max-width: 58ch;
  color: var(--text-muted);
  font-size: var(--supporting-text-size);
  line-height: var(--supporting-text-line-height);
}

.workspace-card__note,
.workspace-card__link {
  color: var(--text-muted);
}

.hero-panel__actions,
.workspace-card__header,
.hero-spotlight__headline,
.identity-card__header,
.audit-card__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.hero-panel__actions {
  align-items: center;
  flex-wrap: wrap;
}

.hero-panel__scope {
  letter-spacing: 0.06em;
}

.hero-panel__secondary,
.workspace-card__link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: var(--control-height);
  padding: var(--space-button);
  border: 1px solid var(--line);
  border-radius: var(--radius-pill);
  background: var(--button-soft-gradient);
  color: var(--text-main);
  text-decoration: none;
  font-family: var(--body);
  font-size: 0.82rem;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  transition:
    border-color 0.2s ease,
    color 0.2s ease;
}

.hero-panel__secondary:hover,
.workspace-card__link:hover {
  border-color: var(--line-strong);
  color: var(--text-strong);
}

.summary-strip__card span,
.hero-spotlight__kicker,
.hero-spotlight__meta span,
.identity-card dt {
  display: block;
  color: var(--text-faint);
  font-family: var(--mono);
  font-size: var(--field-label-size);
  letter-spacing: var(--field-label-letter-spacing);
  text-transform: uppercase;
}

.summary-strip__card strong,
.hero-spotlight__headline strong,
.hero-spotlight__meta strong,
.identity-card strong,
.audit-card strong,
.history-timeline__item strong {
  display: block;
  font-family: var(--body);
}

/* ---- Summary strip: signal metric tiles ---- */
.summary-strip {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.summary-strip__card {
  padding: var(--space-card);
  border-radius: var(--radius-panel);
  border: 1px solid var(--line-warm);
  background: var(--warm-panel-gradient);
  animation: consoleRise 0.5s ease both;
}

.summary-strip__card:nth-child(1) {
  animation-delay: 0.08s;
}

.summary-strip__card:nth-child(2) {
  animation-delay: 0.15s;
}

.summary-strip__card:nth-child(3) {
  animation-delay: 0.22s;
}

.summary-strip__card:nth-child(4) {
  animation-delay: 0.29s;
}

.summary-strip__card strong {
  margin-top: 10px;
  font-family: var(--mono);
  font-size: clamp(1.8rem, 3vw, 2.4rem);
  color: var(--text-strong);
}

/* ---- Hero spotlight + workspace panels ---- */
.hero-spotlight,
.workspace-card,
.identity-card,
.audit-card {
  padding: var(--space-card);
  border-radius: var(--radius-panel);
  border: 1px solid var(--line);
  background: var(--panel-gradient);
}

.hero-spotlight,
.workspace-card {
  display: grid;
  gap: 14px;
}

.hero-spotlight {
  position: relative;
  z-index: 1;
}

.hero-spotlight--identity {
  border-color: var(--line);
}

.hero-spotlight--audit {
  border-color: var(--line);
}

.hero-spotlight__kicker {
  margin: 0;
  font-weight: 600;
}

.hero-spotlight__headline strong {
  font-size: 1.28rem;
  line-height: 1.18;
}

.hero-spotlight__did {
  font-family: var(--mono);
  color: var(--text-muted);
  word-break: break-all;
}

.hero-spotlight__context,
.hero-spotlight__reason,
.identity-card__hint,
.history-timeline__item p,
.history-timeline__item span,
.audit-card p,
.audit-card dd {
  margin: 0;
  color: var(--text-muted);
  font-size: var(--supporting-text-size);
  line-height: var(--supporting-text-line-height);
  overflow-wrap: anywhere;
}

.hero-spotlight__reason {
  padding: 10px 12px;
  border-radius: var(--radius-control);
  border-left: 2px solid var(--line-strong);
  background: var(--bg-panel-soft);
  color: var(--text-main);
}

.hero-spotlight__meta,
.identity-details,
.identity-card__details {
  display: grid;
  gap: 12px;
}

.hero-spotlight__meta {
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
}

.hero-spotlight__meta strong {
  margin-top: 8px;
  font-size: 0.94rem;
  line-height: 1.5;
}

.hero-spotlight__meta div,
.identity-details div,
.identity-card__details div,
.history-timeline__item {
  min-width: 0;
  padding: var(--space-subpanel);
  border-radius: var(--radius-subpanel);
  border: 1px solid var(--line);
  background: var(--panel-soft-gradient);
}

/* ---- Layout columns ---- */
.identity-layout {
  grid-template-columns: minmax(320px, 360px) minmax(0, 1fr);
}

.identity-details,
.identity-card__details {
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
}

.identity-details dt,
.identity-card dt {
  margin: 0;
}

.identity-details dd,
.identity-card__details dd {
  margin: 6px 0 0;
  color: var(--text-main);
  font-size: 0.9rem;
  line-height: 1.5;
}

/* ---- Workspace cards: staggered console boot ---- */
.workspace-card {
  animation: consoleRise 0.5s ease both;
  transition:
    border-color 0.22s ease,
    box-shadow 0.22s ease;
}

.identity-layout__side .workspace-card:nth-child(1) {
  animation-delay: 0.1s;
}

.identity-layout__side .workspace-card:nth-child(2) {
  animation-delay: 0.18s;
}

.identity-layout__main .workspace-card:nth-child(1) {
  animation-delay: 0.16s;
}

.identity-layout__main .workspace-card:nth-child(2) {
  animation-delay: 0.24s;
}

.identity-layout__main .workspace-card:nth-child(3) {
  animation-delay: 0.32s;
}

.workspace-card--identity:hover {
  border-color: var(--line-strong);
}

.workspace-card--audit:hover {
  border-color: var(--line-strong);
}

.workspace-card__note {
  margin: 0;
  padding: 10px 12px;
  border-radius: var(--radius-control);
  border-left: 2px solid var(--line-strong);
  background: var(--bg-panel-soft);
  color: var(--text-main);
  font-size: var(--supporting-text-size);
  line-height: var(--supporting-text-line-height);
}

/* ---- Identity + audit cards: governed entities ---- */
.identity-grid {
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
}

.identity-card,
.audit-card {
  display: grid;
  gap: 14px;
  align-content: start;
  background: var(--panel-soft-gradient);
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease;
}

.identity-card:hover {
  border-color: var(--line-strong);
}

.audit-card:hover {
  border-color: var(--line-strong);
}

.identity-card__header strong,
.audit-card__header strong {
  font-size: 1.04rem;
  line-height: 1.3;
  color: var(--text-strong);
}

.identity-card__header p,
.audit-card__header p {
  margin: 6px 0 0;
  color: var(--text-muted);
  font-size: 0.84rem;
}

.identity-card__hint {
  padding: 10px 12px;
  border-radius: var(--radius-control);
  border-left: 2px solid var(--line-strong);
  background: var(--bg-panel-soft);
  color: var(--text-main);
}

/* ---- History timeline: status transition readouts ---- */
.history-timeline__item {
  position: relative;
  display: grid;
  gap: 4px;
}

.history-timeline__item strong {
  font-size: 0.86rem;
  line-height: 1.4;
  color: var(--text-strong);
}

.history-timeline__item span {
  font-size: 0.74rem;
  color: var(--text-faint);
}

/* ---- Wrapping guards for long ids / hashes ---- */
.hero-spotlight__headline > div,
.identity-card__header > div,
.audit-card__header > div,
.hero-spotlight__context,
.hero-spotlight__reason,
.hero-spotlight__did,
.hero-spotlight__meta strong,
.identity-details dd,
.identity-card__details dd,
.identity-card__header p,
.audit-card__header p,
.workspace-card__note,
.identity-card__hint,
.history-timeline__item p,
.history-timeline__item span,
.audit-card p,
.audit-card dd {
  min-width: 0;
  overflow-wrap: anywhere;
  word-break: break-word;
}

@media (max-width: 1040px) {
  .hero-panel,
  .identity-layout,
  .summary-strip,
  .hero-spotlight__meta,
  .identity-details,
  .identity-card__details {
    grid-template-columns: 1fr;
  }

  .workspace-card__header,
  .hero-spotlight__headline,
  .identity-card__header,
  .audit-card__header {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
