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
import { useActorProfile } from '../composables/useActorProfile'
import type {
  AccountUser,
  ActorIdentity,
  AuditEvent,
  CredentialVerificationResult,
  OrganizationIdentity,
} from '../types/api'
import {
  formatAuditActionLabel,
  formatIdentityStatusLabel,
  formatIdentityStatusSourceLabel,
  formatOrganizationLabel,
  formatRequestStatusLabel,
  formatRoleLabel,
} from '../utils/labels'

const { actorProfile, isAdmin } = useActorProfile()

const loading = ref(true)
const error = ref<string | null>(null)
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
      note: '这里用于集中查看账户 VC、机构 VC、治理时间线和身份相关审计，再决定是否回到账户治理台执行变更。',
      accountEmpty: '当前还没有账户身份记录。先在账户页创建一批研究员或审批人，再回来查看身份分布。',
      orgEmpty: '当前还没有机构身份记录。只要账户目录里出现机构归属，这里就会自动汇聚对应机构身份。',
      auditEmpty: '当前还没有身份治理审计。登录、建号、改密、VC 状态更新后，这里会逐步形成时间线。',
    }
  }

  return {
    note: '这里集中展示你自己的 DID / VC、机构身份状态和最近身份审计，重点是回看可信身份而不是做治理操作。',
    accountEmpty: '当前还没有可展示的个人账户身份记录，请稍后刷新。',
    orgEmpty: '当前机构身份暂未返回，请稍后重试。',
    auditEmpty: '当前还没有你的身份审计事件。登录、改密或恢复密码后，这里会逐步出现轨迹。',
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
    label: '待治理账户 VC',
    value: String(accountRows.value.filter((row) => row.credentialStatus.effectiveStatus !== 'issued').length),
  },
  {
    label: '待治理机构 VC',
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
  loading.value = true
  error.value = null

  try {
    const [currentAccount, currentIdentity, auditRows] = await Promise.all([
      getCurrentAccount(),
      getCurrentIdentity(),
      getAudits(actorProfile.value),
    ])

    account.value = currentAccount
    identity.value = currentIdentity
    verification.value = await verifyCredential({
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
    identityAuditRows.value = auditRows.filter((row) => isIdentityAudit(row.action))

    if (isAdmin.value) {
      accountRows.value = await getAccounts()
      const organizationNames = [...new Set(accountRows.value.map((row) => row.actorOrg))]
      organizationRows.value = await Promise.all(organizationNames.map((name) => getOrganizationIdentity(name)))
    } else {
      accountRows.value = currentAccount ? [currentAccount] : []
      organizationRows.value = [await getOrganizationIdentity(currentIdentity.actorOrg)]
    }
  } catch (loadError) {
    error.value = loadError instanceof Error ? loadError.message : '加载身份中心失败。'
  } finally {
    loading.value = false
  }
}

onMounted(loadPage)
</script>

<template>
  <div class="identity-page">
    <section class="hero-panel glass-panel">
      <div class="hero-panel__copy">
        <p class="section-kicker">Identity Control</p>
        <h1>把 DID、VC、治理快照和身份审计收进一个独立工作区。</h1>
        <p class="hero-panel__lede">
          当前身份是 {{ actorProfile.actorId }} / {{ formatRoleLabel(actorProfile.actorRole) }} /
          {{ formatOrganizationLabel(actorProfile.actorOrg) }}。这里负责汇聚可信身份，不再把所有内容都塞进账户页。
        </p>

        <div class="hero-panel__actions">
          <span class="status-chip">{{ scopeLabel }}</span>
          <RouterLink class="hero-panel__secondary" to="/accounts">打开账户页</RouterLink>
          <RouterLink class="hero-panel__secondary" to="/audits?action=ACCOUNT_CREDENTIAL_STATUS_UPDATED">打开身份审计</RouterLink>
        </div>

        <div class="hero-panel__guide">
          <span>Role Guidance</span>
          <strong>{{ roleGuide.note }}</strong>
        </div>

        <div class="summary-strip">
          <article v-for="stat in identityStats" :key="stat.label" class="summary-strip__card">
            <span>{{ stat.label }}</span>
            <strong>{{ stat.value }}</strong>
          </article>
        </div>
      </div>

      <div class="hero-panel__rail">
        <article class="hero-spotlight">
          <p class="hero-spotlight__kicker">我的身份</p>
          <template v-if="identity">
            <div class="hero-spotlight__headline">
              <strong>{{ identity.displayName }}</strong>
              <span
                class="status-chip"
                :class="{ 'status-chip--danger': (verification?.status ?? identity.credential.verificationStatus) !== 'verified' }"
              >
                {{ formatIdentityStatusLabel(verification?.status ?? identity.credential.verificationStatus) }}
              </span>
            </div>
            <p class="hero-spotlight__context">{{ identity.actorDid }}</p>
            <p class="hero-spotlight__reason">{{ verification?.reason ?? '当前身份 VC 已通过平台校验。' }}</p>
            <div class="hero-spotlight__meta">
              <div>
                <span>VC 状态</span>
                <strong>{{ formatIdentityStatusLabel(identity.credential.credentialStatus) }}</strong>
              </div>
              <div>
                <span>机构 DID</span>
                <strong>{{ identity.organizationDid }}</strong>
              </div>
              <div>
                <span>到期时间</span>
                <strong>{{ formatTime(identity.credential.expiresAt) }}</strong>
              </div>
            </div>
          </template>
        </article>

        <article class="hero-spotlight">
          <p class="hero-spotlight__kicker">最新身份审计</p>
          <template v-if="spotlightAudit">
            <div class="hero-spotlight__headline">
              <strong>{{ formatAuditActionLabel(spotlightAudit.action) }}</strong>
              <span class="status-chip" :class="{ 'status-chip--danger': spotlightAudit.status !== 'success' }">
                {{ formatRequestStatusLabel(spotlightAudit.status) }}
              </span>
            </div>
            <p class="hero-spotlight__context">
              {{ spotlightAudit.actorId }} · {{ formatRoleLabel(spotlightAudit.actorRole) }} ·
              {{ formatOrganizationLabel(spotlightAudit.actorOrg) }}
            </p>
            <p class="hero-spotlight__reason">{{ spotlightAudit.detail || '该事件未附带额外说明。' }}</p>
            <div class="hero-spotlight__meta">
              <div>
                <span>原始动作</span>
                <strong>{{ spotlightAudit.action }}</strong>
              </div>
              <div>
                <span>对象</span>
                <strong>{{ spotlightAudit.datasetId || '平台级事件' }}</strong>
              </div>
              <div>
                <span>发生时间</span>
                <strong>{{ formatTime(spotlightAudit.createdAt) }}</strong>
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
          <article v-if="identity" class="workspace-card glass-panel">
            <div class="workspace-card__header">
              <div>
                <p class="section-kicker">Actor DID / VC</p>
                <h2 class="section-title">当前操作者身份卡</h2>
              </div>
              <RouterLink class="workspace-card__link" to="/accounts">去账户治理</RouterLink>
            </div>

            <dl class="identity-details">
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
                <dt>签发方</dt>
                <dd>{{ identity.credential.issuerDid }}</dd>
              </div>
            </dl>

            <p class="workspace-card__note">{{ verification?.reason ?? '当前 VC 校验结果正常。' }}</p>
          </article>

          <article v-if="currentOrganization" class="workspace-card glass-panel">
            <div class="workspace-card__header">
              <div>
                <p class="section-kicker">Organization VC</p>
                <h2 class="section-title">当前机构身份</h2>
              </div>
            </div>

            <dl class="identity-details">
              <div>
                <dt>机构</dt>
                <dd>{{ formatOrganizationLabel(currentOrganization.organizationName) }}</dd>
              </div>
              <div>
                <dt>机构 DID</dt>
                <dd>{{ currentOrganization.organizationDid }}</dd>
              </div>
              <div>
                <dt>VC 状态</dt>
                <dd>{{ formatIdentityStatusLabel(currentOrganization.statusSnapshot.effectiveStatus) }}</dd>
              </div>
              <div>
                <dt>状态来源</dt>
                <dd>{{ formatIdentityStatusSourceLabel(currentOrganization.statusSnapshot.source) }}</dd>
              </div>
            </dl>

            <p class="workspace-card__note">
              {{ currentOrganization.statusSnapshot.reason || '当前机构 VC 状态暂无额外说明。' }}
            </p>

            <div v-if="currentOrganization.credentialHistory.length" class="history-timeline">
              <div
                v-for="entry in currentOrganization.credentialHistory.slice(0, 3)"
                :key="`${currentOrganization.organizationName}-${entry.id ?? entry.createdAt ?? entry.nextStatus}`"
                class="history-timeline__item"
              >
                <strong>{{ formatHistoryTransition(entry.previousStatus, entry.nextStatus) }}</strong>
                <p>{{ entry.reason || '该次治理未附带额外说明。' }}</p>
                <span>{{ formatIdentityStatusSourceLabel(entry.source) }} · {{ entry.updatedBy || 'system' }} · {{ formatTime(entry.createdAt) }}</span>
              </div>
            </div>
          </article>
        </aside>

        <div class="identity-layout__main">
          <article class="workspace-card glass-panel">
            <div class="workspace-card__header">
              <div>
                <p class="section-kicker">Account Identity</p>
                <h2 class="section-title">{{ isAdmin ? '账户身份目录' : '我的账户身份' }}</h2>
              </div>
              <span class="status-chip">{{ accountRows.length }} 条记录</span>
            </div>

            <div v-if="accountRows.length" class="identity-grid">
              <article v-for="row in accountRows" :key="row.actorId" class="identity-card">
                <div class="identity-card__header">
                  <div>
                    <strong>{{ row.displayName }}</strong>
                    <p>{{ row.actorId }} · {{ formatRoleLabel(row.actorRole) }}</p>
                  </div>
                  <span class="status-chip" :class="{ 'status-chip--danger': row.credentialStatus.effectiveStatus !== 'issued' }">
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
                    <dd>{{ formatRequestStatusLabel(row.status) }}</dd>
                  </div>
                  <div>
                    <dt>状态来源</dt>
                    <dd>{{ formatIdentityStatusSourceLabel(row.credentialStatus.source) }}</dd>
                  </div>
                  <div>
                    <dt>最近治理</dt>
                    <dd>{{ formatTime(row.credentialStatus.updatedAt) }}</dd>
                  </div>
                </dl>

                <p class="identity-card__hint">
                  {{ row.credentialStatus.reason || '当前账户 VC 状态暂无额外说明。' }}
                </p>

                <div class="history-timeline">
                  <div
                    v-for="entry in row.credentialHistory.slice(0, 3)"
                    :key="`${row.actorId}-${entry.id ?? entry.createdAt ?? entry.nextStatus}`"
                    class="history-timeline__item"
                  >
                    <strong>{{ formatHistoryTransition(entry.previousStatus, entry.nextStatus) }}</strong>
                    <p>{{ entry.reason || '该次治理未附带额外说明。' }}</p>
                    <span>{{ formatIdentityStatusSourceLabel(entry.source) }} · {{ entry.updatedBy || 'system' }} · {{ formatTime(entry.createdAt) }}</span>
                  </div>
                </div>
              </article>
            </div>
            <div v-else class="empty-state">{{ roleGuide.accountEmpty }}</div>
          </article>

          <article class="workspace-card glass-panel">
            <div class="workspace-card__header">
              <div>
                <p class="section-kicker">Organization Identity</p>
                <h2 class="section-title">{{ isAdmin ? '机构身份目录' : '我的机构身份' }}</h2>
              </div>
              <span class="status-chip">{{ organizationRows.length }} 个机构</span>
            </div>

            <div v-if="organizationRows.length" class="identity-grid">
              <article v-for="row in organizationRows" :key="row.organizationName" class="identity-card">
                <div class="identity-card__header">
                  <div>
                    <strong>{{ formatOrganizationLabel(row.organizationName) }}</strong>
                    <p>{{ row.organizationDid }}</p>
                  </div>
                  <span class="status-chip" :class="{ 'status-chip--danger': row.statusSnapshot.effectiveStatus !== 'issued' }">
                    {{ formatIdentityStatusLabel(row.statusSnapshot.effectiveStatus) }}
                  </span>
                </div>

                <dl class="identity-card__details">
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
                    <dt>最近治理</dt>
                    <dd>{{ formatTime(row.statusSnapshot.updatedAt) }}</dd>
                  </div>
                </dl>

                <p class="identity-card__hint">
                  {{ row.statusSnapshot.reason || '当前机构 VC 状态暂无额外说明。' }}
                </p>

                <div class="history-timeline">
                  <div
                    v-for="entry in row.credentialHistory.slice(0, 3)"
                    :key="`${row.organizationName}-${entry.id ?? entry.createdAt ?? entry.nextStatus}`"
                    class="history-timeline__item"
                  >
                    <strong>{{ formatHistoryTransition(entry.previousStatus, entry.nextStatus) }}</strong>
                    <p>{{ entry.reason || '该次治理未附带额外说明。' }}</p>
                    <span>{{ formatIdentityStatusSourceLabel(entry.source) }} · {{ entry.updatedBy || 'system' }} · {{ formatTime(entry.createdAt) }}</span>
                  </div>
                </div>
              </article>
            </div>
            <div v-else class="empty-state">{{ roleGuide.orgEmpty }}</div>
          </article>

          <article class="workspace-card glass-panel">
            <div class="workspace-card__header">
              <div>
                <p class="section-kicker">Identity Audit</p>
                <h2 class="section-title">身份审计流</h2>
                <p class="workspace-card__note workspace-card__note--inline">这里只保留认证、账户与 VC 治理相关事件，完整审计仍可跳转到审计中心继续筛查。</p>
              </div>
              <RouterLink class="workspace-card__link" to="/audits">打开审计中心</RouterLink>
            </div>

            <div v-if="identityAuditRows.length" class="audit-list">
              <article v-for="event in identityAuditRows.slice(0, 8)" :key="event.id" class="audit-card">
                <div class="audit-card__header">
                  <div>
                    <strong>{{ formatAuditActionLabel(event.action) }}</strong>
                    <p>{{ event.actorId }} · {{ formatRoleLabel(event.actorRole) }}</p>
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
                    <dd>{{ event.action }}</dd>
                  </div>
                  <div>
                    <dt>对象</dt>
                    <dd>{{ event.datasetId || '平台级事件' }}</dd>
                  </div>
                  <div>
                    <dt>发生时间</dt>
                    <dd>{{ formatTime(event.createdAt) }}</dd>
                  </div>
                </dl>

                <p class="identity-card__hint">{{ event.detail || '该事件未附带额外说明。' }}</p>
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
.identity-layout,
.identity-layout__side,
.identity-layout__main,
.identity-grid,
.audit-list,
.history-timeline {
  display: grid;
  gap: 18px;
}

.hero-panel h1,
.section-title {
  margin: 0;
  font-family: var(--display);
}

.hero-panel h1 {
  font-size: clamp(2.5rem, 5vw, 4rem);
  line-height: 0.96;
}

.hero-panel__lede,
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

.hero-panel__secondary,
.workspace-card__link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 42px;
  padding: 0 16px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: rgba(12, 24, 32, 0.92);
  color: var(--text-main);
  text-decoration: none;
  font-family: var(--display);
  letter-spacing: 0.08em;
  text-transform: uppercase;
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

.hero-panel__guide span,
.summary-strip__card span,
.hero-spotlight__kicker,
.hero-spotlight__meta span,
.identity-card dt {
  display: block;
  color: var(--text-faint);
  font-size: 0.72rem;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.hero-panel__guide strong,
.summary-strip__card strong,
.hero-spotlight__headline strong,
.hero-spotlight__meta strong,
.identity-card strong,
.audit-card strong,
.history-timeline__item strong {
  display: block;
  font-family: var(--display);
}

.hero-panel__guide strong {
  font-size: 0.94rem;
  line-height: 1.6;
}

.summary-strip {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.summary-strip__card,
.hero-spotlight,
.workspace-card,
.identity-card,
.audit-card {
  padding: 18px;
  border-radius: 24px;
  border: 1px solid rgba(108, 166, 186, 0.14);
  background:
    linear-gradient(180deg, rgba(4, 15, 21, 0.94), rgba(8, 17, 23, 0.78)),
    radial-gradient(circle at top right, rgba(116, 210, 220, 0.08), transparent 30%);
}

.summary-strip__card strong {
  margin-top: 10px;
  font-size: clamp(1.8rem, 3vw, 2.4rem);
}

.hero-spotlight,
.workspace-card {
  display: grid;
  gap: 14px;
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
}

.hero-spotlight__meta,
.identity-details,
.identity-card__details {
  display: grid;
  gap: 12px;
}

.hero-spotlight__meta {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.hero-spotlight__meta div,
.identity-details div,
.identity-card__details div,
.history-timeline__item {
  padding: 14px 16px;
  border-radius: 18px;
  border: 1px solid rgba(108, 166, 186, 0.1);
  background: rgba(8, 18, 25, 0.76);
}

.identity-layout {
  grid-template-columns: minmax(320px, 360px) minmax(0, 1fr);
}

.identity-details,
.identity-card__details {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.workspace-card__note--inline {
  margin-top: 10px;
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

  .hero-panel__actions,
  .workspace-card__header,
  .hero-spotlight__headline,
  .identity-card__header,
  .audit-card__header {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
