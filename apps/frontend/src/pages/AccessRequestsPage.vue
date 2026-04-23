<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import PageHero from '../components/PageHero.vue'
import SurfaceCard from '../components/SurfaceCard.vue'
import {
  approveAccessRequest,
  getAccessRequests,
  rejectAccessRequest,
  revokeAccessRequest,
} from '../api/client'
import { toErrorMessage, useAsyncView } from '../composables/useAsyncView'
import { useActorProfile } from '../composables/useActorProfile'
import { useToast } from '../composables/useToast'
import type { AccessRequest } from '../types/api'
import { formatOrganizationLabel, formatRequestStatusLabel, formatRoleLabel } from '../utils/labels'

const { actorProfile } = useActorProfile()
const { pushToast } = useToast()
const route = useRoute()

const { loading, error, run: runRequestLoad, setErrorMessage } = useAsyncView({
  initialLoading: true,
})
const actionLoadingId = ref<string | null>(null)
const requestRows = ref<AccessRequest[]>([])
const approvalPolicy = ref('仅限科研分析，默认开放 24 小时。')
const rejectionPolicy = ref('拒绝原因：业务说明不足，请补充用途与合规依据。')
const focusRequestId = computed(() => (typeof route.query.focusRequestId === 'string' ? route.query.focusRequestId : ''))

const isPrivilegedActor = computed(() =>
  ['owner', 'approver', 'admin'].includes(actorProfile.value.actorRole.toLowerCase()),
)
const roleGuide = computed(() => {
  const role = actorProfile.value.actorRole.toLowerCase()
  if (role === 'admin') {
    return {
      emptySpotlight: '暂无访问申请',
      emptyQueue: '暂无待审批记录',
      emptyList: '暂无访问申请记录',
    }
  }
  if (role === 'owner' || role === 'approver') {
    return {
      emptySpotlight: '暂无访问申请',
      emptyQueue: '暂无待审批记录',
      emptyList: '暂无访问申请记录',
    }
  }
  return {
    emptySpotlight: '暂无访问申请',
    emptyQueue: '暂无待审批记录',
    emptyList: '暂无访问申请记录',
  }
})

const requestStats = computed(() => [
  { label: '待审批', value: requestRows.value.filter((row) => row.status === 'pending').length },
  { label: '已批准', value: requestRows.value.filter((row) => row.status === 'approved').length },
  {
    label: '已关闭',
    value: requestRows.value.filter((row) => row.status === 'rejected' || row.status === 'revoked').length,
  },
])
const spotlightRequest = computed(() =>
  requestRows.value.find((row) => row.id === focusRequestId.value)
    ?? requestRows.value.find((row) => row.status === 'pending')
    ?? requestRows.value[0]
    ?? null,
)
const queuePreview = computed(() => requestRows.value.filter((row) => row.status === 'pending').slice(0, 3))
const policyCards = computed(() => [
  {
    label: '审批口径',
    value: approvalPolicy.value,
  },
  {
    label: '拒绝口径',
    value: rejectionPolicy.value,
  },
  {
    label: '当前模式',
    value: isPrivilegedActor.value ? '可直接决策' : '只读巡检',
  },
])

function formatTime(value: string | null) {
  if (!value) {
    return '暂无'
  }

  return new Date(value).toLocaleString()
}

function trainingLinkFor(row: AccessRequest) {
  return {
    path: '/training-jobs',
    query: {
      source: 'access-request',
      datasetId: row.datasetId,
      modelName: `Federated Run ${row.datasetId.toUpperCase()}`,
      objective: row.purpose,
      requestedRounds: '6',
    },
  }
}

async function loadRequests() {
  const rows = await runRequestLoad(
    () =>
      getAccessRequests(actorProfile.value, {
      datasetId: typeof route.query.datasetId === 'string' ? route.query.datasetId : undefined,
      status: typeof route.query.status === 'string' ? route.query.status : undefined,
      }),
    '加载访问申请失败。',
  )

  if (!rows) {
    return
  }

  requestRows.value = rows
}

async function approve(row: AccessRequest) {
  actionLoadingId.value = row.id
  try {
    await approveAccessRequest(row.id, actorProfile.value, {
      approvedDurationHours: 24,
      policy: approvalPolicy.value,
    })
    await loadRequests()
    pushToast({
      title: '访问已批准',
      message: `${row.id} 已获批，现在可以直接带入训练编排或返回数据详情页验证脑区读数。`,
      tone: 'success',
    })
  } catch (actionError) {
    setErrorMessage(toErrorMessage(actionError, '批准访问申请失败。'))
  } finally {
    actionLoadingId.value = null
  }
}

async function reject(row: AccessRequest) {
  actionLoadingId.value = row.id
  try {
    await rejectAccessRequest(row.id, actorProfile.value, {
      policy: rejectionPolicy.value,
    })
    await loadRequests()
    pushToast({
      title: '申请已拒绝',
      message: `${row.id} 已被拒绝，拒绝说明已写回访问记录。`,
      tone: 'warning',
    })
  } catch (actionError) {
    setErrorMessage(toErrorMessage(actionError, '拒绝访问申请失败。'))
  } finally {
    actionLoadingId.value = null
  }
}

async function revoke(row: AccessRequest) {
  actionLoadingId.value = row.id
  try {
    await revokeAccessRequest(row.id, actorProfile.value)
    await loadRequests()
    pushToast({
      title: '访问已撤销',
      message: `${row.id} 已撤销，后续读取会重新命中门禁。`,
      tone: 'warning',
    })
  } catch (actionError) {
    setErrorMessage(toErrorMessage(actionError, '撤销访问申请失败。'))
  } finally {
    actionLoadingId.value = null
  }
}

onMounted(loadRequests)

watch(
  () => route.query,
  () => {
    void loadRequests()
  },
)
</script>

<template>
  <div class="requests-page">
    <PageHero
      kicker="访问治理"
      title="把访问申请处理成一条清晰、可追踪的业务流程。"
      layout="balanced"
    >
      <template #actions>
        <span class="status-chip">{{ isPrivilegedActor ? '审批模式' : '只读模式' }}</span>
        <RouterLink class="hero-panel__secondary" to="/">返回总览</RouterLink>
      </template>

      <div class="summary-strip">
        <article v-for="stat in requestStats" :key="stat.label" class="summary-strip__card">
          <span>{{ stat.label }}</span>
          <strong>{{ stat.value }}</strong>
        </article>
      </div>

      <template #rail>
        <article class="hero-spotlight">
          <p class="hero-spotlight__kicker">优先事项</p>
          <template v-if="spotlightRequest">
            <div class="hero-spotlight__headline">
              <strong>{{ spotlightRequest.id }}</strong>
              <span
                class="status-chip"
                :class="{
                  'status-chip--warn': spotlightRequest.status === 'pending',
                  'status-chip--danger':
                    spotlightRequest.status === 'rejected' || spotlightRequest.status === 'revoked',
                }"
              >
                {{ formatRequestStatusLabel(spotlightRequest.status) }}
              </span>
            </div>
            <p class="hero-spotlight__context">
              {{ spotlightRequest.datasetId }} · {{ spotlightRequest.actorId }} ·
              {{ formatOrganizationLabel(spotlightRequest.actorOrg) }}
            </p>
            <p class="hero-spotlight__reason">{{ spotlightRequest.reason }}</p>
            <div class="hero-spotlight__meta">
              <div>
                <span>用途</span>
                <strong>{{ spotlightRequest.purpose }}</strong>
              </div>
              <div>
                <span>申请时长</span>
                <strong>{{ spotlightRequest.requestedDurationHours }} 小时</strong>
              </div>
              <div>
                <span>提交时间</span>
                <strong>{{ formatTime(spotlightRequest.createdAt) }}</strong>
              </div>
            </div>
          </template>
          <p v-else class="hero-spotlight__empty">{{ roleGuide.emptySpotlight }}</p>
        </article>

      </template>
    </PageHero>

    <div v-if="loading" class="loading-state">正在加载审批台...</div>
    <div v-else-if="error" class="error-state">{{ error }}</div>

    <template v-else>
      <section class="requests-layout">
        <aside class="requests-layout__side">
          <SurfaceCard kicker="审批策略" title="策略编辑台">

            <div class="form-grid">
              <label>
                <span>批准策略</span>
                <input v-model="approvalPolicy" type="text" />
              </label>
              <label>
                <span>拒绝说明</span>
                <input v-model="rejectionPolicy" type="text" />
              </label>
            </div>

            <div class="policy-cards">
              <div v-for="card in policyCards" :key="card.label" class="policy-cards__item">
                <span>{{ card.label }}</span>
                <strong>{{ card.value }}</strong>
              </div>
            </div>
          </SurfaceCard>

          <SurfaceCard class="queue-card" kicker="待决队列" title="优先处理视图">
            <template #meta>
              <span class="status-chip status-chip--warn">{{ queuePreview.length }} 条待处理</span>
            </template>

            <div v-if="queuePreview.length" class="queue-preview">
              <div v-for="row in queuePreview" :key="row.id" class="queue-preview__item">
                <div>
                  <strong>{{ row.id }}</strong>
                  <p>{{ row.datasetId }} · {{ row.actorId }}</p>
                </div>
                <span>{{ formatTime(row.createdAt) }}</span>
              </div>
            </div>
            <div v-else class="empty-state">{{ roleGuide.emptyQueue }}</div>
          </SurfaceCard>
        </aside>

        <div class="requests-layout__main">
          <SurfaceCard kicker="申请列表" title="访问申请记录">
            <template #meta>
              <span class="status-chip">{{ requestRows.length }} 条记录</span>
            </template>

            <div class="request-list" v-if="requestRows.length">
              <article
                v-for="row in requestRows"
                :key="row.id"
                class="request-card"
                :class="{ 'request-card--focus': focusRequestId === row.id }"
              >
                <div class="request-card__header">
                  <div>
                    <strong>{{ row.id }}</strong>
                    <p>{{ row.datasetId }} · {{ row.purpose }}</p>
                  </div>
                  <span
                    class="status-chip"
                    :class="{
                      'status-chip--warn': row.status === 'pending',
                      'status-chip--danger': row.status === 'rejected' || row.status === 'revoked',
                    }"
                  >
                    {{ formatRequestStatusLabel(row.status) }}
                  </span>
                </div>

                <p class="request-card__reason">{{ row.reason }}</p>

                <dl class="request-card__details">
                  <div>
                    <dt>提交时间</dt>
                    <dd>{{ formatTime(row.createdAt) }}</dd>
                  </div>
                  <div>
                    <dt>申请人</dt>
                    <dd>{{ row.actorId }}</dd>
                  </div>
                  <div>
                    <dt>角色</dt>
                    <dd>{{ formatRoleLabel(row.actorRole) }}</dd>
                  </div>
                  <div>
                    <dt>机构</dt>
                    <dd>{{ formatOrganizationLabel(row.actorOrg) }}</dd>
                  </div>
                  <div>
                    <dt>申请时长</dt>
                    <dd>{{ row.requestedDurationHours }} 小时</dd>
                  </div>
                  <div v-if="row.policyNote">
                    <dt>策略说明</dt>
                    <dd>{{ row.policyNote }}</dd>
                  </div>
                  <div v-if="row.expiresAt">
                    <dt>到期时间</dt>
                    <dd>{{ formatTime(row.expiresAt) }}</dd>
                  </div>
                </dl>

                <div class="request-card__footer">
                  <RouterLink class="request-card__link" :to="`/datasets/${row.datasetId}`">
                    打开数据详情
                  </RouterLink>

                  <div v-if="isPrivilegedActor && row.status === 'pending'" class="request-card__actions">
                    <button type="button" @click="approve(row)" :disabled="actionLoadingId === row.id">
                      {{ actionLoadingId === row.id ? '处理中...' : '批准 24 小时' }}
                    </button>
                    <button
                      type="button"
                      class="request-card__danger"
                      @click="reject(row)"
                      :disabled="actionLoadingId === row.id"
                    >
                      拒绝
                    </button>
                  </div>

                  <div v-else-if="isPrivilegedActor && row.status === 'approved'" class="request-card__actions">
                    <RouterLink class="request-card__link" :to="trainingLinkFor(row)">
                      带入训练页
                    </RouterLink>
                    <button
                      type="button"
                      class="request-card__danger"
                      @click="revoke(row)"
                      :disabled="actionLoadingId === row.id"
                    >
                      撤销访问
                    </button>
                  </div>
                  <div v-else-if="!isPrivilegedActor && row.status === 'approved'" class="request-card__actions">
                    <RouterLink class="request-card__link" :to="trainingLinkFor(row)">
                      发起训练
                    </RouterLink>
                  </div>
                </div>
              </article>
            </div>
            <div v-else class="empty-state">{{ roleGuide.emptyList }}</div>
          </SurfaceCard>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.requests-page {
  display: grid;
  gap: 18px;
}

.hero-spotlight__headline,
.hero-lane__header {
  display: flex;
  gap: 10px;
  align-items: center;
}

.hero-lane__header {
  flex-wrap: wrap;
}

.hero-panel__secondary {
  display: inline-flex;
  align-items: center;
  min-height: var(--control-height);
  padding: var(--space-button);
  border-radius: var(--radius-pill);
  border: 1px solid var(--line);
  background: var(--button-soft-gradient);
  color: var(--text-main);
  text-decoration: none;
  font-family: var(--body);
  font-size: 0.82rem;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
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

.hero-panel__hint {
  margin: 0;
  padding: var(--space-subpanel);
  border-radius: var(--radius-subpanel);
  border: 1px solid var(--line);
  background: var(--panel-soft-gradient);
  color: var(--text-muted);
  line-height: 1.7;
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

.summary-strip,
.hero-lane__steps,
.policy-cards,
.queue-preview {
  display: grid;
  gap: 12px;
}

.summary-strip {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.summary-strip__card,
.hero-spotlight,
.hero-lane,
.policy-cards__item,
.queue-preview__item {
  padding: var(--space-card);
  border-radius: var(--radius-panel);
  border: 1px solid var(--line);
  background: var(--panel-gradient);
}

.summary-strip__card span,
.hero-spotlight__kicker,
.hero-spotlight__meta span,
.policy-cards__item span,
.queue-preview__item span,
.workspace-card__lede {
  color: var(--text-muted);
}

.summary-strip__card span,
.hero-spotlight__kicker,
.hero-spotlight__meta span,
.policy-cards__item span {
  display: block;
  font-size: 0.72rem;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.summary-strip__card strong,
.hero-spotlight__headline strong,
.hero-spotlight__meta strong,
.hero-lane__step strong,
.policy-cards__item strong,
.queue-preview__item strong {
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

.hero-spotlight__headline {
  justify-content: space-between;
  align-items: flex-start;
}

.hero-spotlight__headline strong {
  font-size: 1.3rem;
}

.hero-spotlight__context,
.hero-spotlight__reason,
.hero-spotlight__empty,
.hero-lane__step p,
.workspace-card__note,
.queue-preview__item p {
  margin: 0;
  color: var(--text-muted);
  font-size: var(--supporting-text-size);
  line-height: var(--supporting-text-line-height);
}

.hero-spotlight__meta {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.hero-spotlight__meta div,
.hero-lane__step {
  padding: var(--space-subpanel);
  border-radius: var(--radius-subpanel);
  border: 1px solid var(--line);
  background: var(--panel-soft-gradient);
}

.hero-spotlight__meta strong,
.policy-cards__item strong {
  margin-top: 10px;
  font-size: 0.94rem;
  line-height: 1.6;
}

.hero-lane {
  display: grid;
  gap: 16px;
}

.hero-lane__steps {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.hero-lane__step strong {
  font-size: 0.9rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.requests-layout {
  display: grid;
  grid-template-columns: minmax(300px, 360px) minmax(0, 1fr);
  gap: 18px;
}

.policy-cards {
  margin-top: 14px;
}

.queue-card {
  margin-top: 10px;
}

.queue-preview__item {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.queue-preview__item strong {
  font-size: 0.94rem;
}

.queue-preview__item span {
  font-size: 0.76rem;
  line-height: 1.5;
}

.form-grid {
  display: grid;
  gap: 12px;
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

.form-grid input {
  width: 100%;
  min-height: var(--field-height);
  padding: var(--space-field-x);
  border: 1px solid var(--line);
  border-radius: var(--radius-control);
  background: var(--bg-panel);
  color: var(--text-main);
}

.request-list {
  display: grid;
  gap: var(--space-list);
}

.request-card {
  padding: var(--space-card);
  border-radius: var(--radius-block);
  border: 1px solid var(--line);
  background: var(--panel-gradient);
}

.request-card--focus {
  border-color: rgba(156, 107, 54, 0.26);
  box-shadow: inset 0 0 0 1px rgba(156, 107, 54, 0.08);
}

.request-card__header,
.request-card__footer {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.request-card__header strong {
  display: block;
  font-family: var(--body);
}

.request-card__header p,
.request-card__reason {
  margin: 6px 0 0;
  color: var(--text-muted);
  font-size: var(--supporting-text-size);
  line-height: var(--supporting-text-line-height);
}

.request-card__focus-note {
  margin: 10px 0 0;
  color: var(--amber);
  font-size: 0.88rem;
}

.request-card__details {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 12px;
  margin: 14px 0 0;
}

.request-card__details dt {
  color: var(--text-faint);
  font-size: 0.74rem;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.request-card__details dd {
  margin: 6px 0 0;
  color: var(--text-main);
}

.request-card__footer {
  margin-top: 16px;
  align-items: center;
}

.request-card__link {
  color: var(--accent);
  text-decoration: none;
  font-family: var(--body);
  font-size: 0.82rem;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.request-card__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.request-card__actions button {
  min-height: var(--control-height);
  padding: var(--space-button);
  border: 1px solid var(--line);
  border-radius: var(--radius-pill);
  background: var(--button-soft-gradient);
  color: var(--text-main);
  font-family: var(--body);
  font-size: 0.8rem;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.request-card__danger {
  border-color: rgba(242, 126, 126, 0.28) !important;
  color: var(--danger) !important;
}

@media (max-width: 1040px) {
  .page-hero,
  .requests-layout {
    grid-template-columns: 1fr;
  }

  .summary-strip,
  .hero-lane__steps {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .hero-spotlight__headline,
  .hero-spotlight__meta,
  .request-card__details {
    grid-template-columns: 1fr;
  }

  .request-card__footer {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
