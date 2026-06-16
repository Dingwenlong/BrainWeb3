<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import {
  approveDestructionRequest,
  createDestructionRequest,
  executeDestructionRequest,
  getDatasets,
  getDestructionRequests,
  purgeDestructionStorage,
  rejectDestructionRequest,
} from '../api/client'
import { toErrorMessage, useAsyncView } from '../composables/useAsyncView'
import { useActorProfile } from '../composables/useActorProfile'
import { useToast } from '../composables/useToast'
import type { DatasetSummary, DestructionRequest } from '../types/api'
import {
  formatCleanupStatusLabel,
  formatDestructionStatusLabel,
  formatOrganizationLabel,
  formatRequestStatusLabel,
  formatRoleLabel,
} from '../utils/labels'
import { canInspectChainRecords } from '../utils/permissions'

const route = useRoute()
const { actorProfile } = useActorProfile()
const { pushToast } = useToast()

const { loading, error, run: runPageLoad, setErrorMessage } = useAsyncView({
  initialLoading: true,
})
const actionLoadingId = ref<string | null>(null)
const requestRows = ref<DestructionRequest[]>([])
const datasetRows = ref<DatasetSummary[]>([])

const filters = reactive({
  datasetId: typeof route.query.datasetId === 'string' ? route.query.datasetId : '',
  actorId: typeof route.query.actorId === 'string' ? route.query.actorId : '',
  status: typeof route.query.status === 'string' ? route.query.status : '',
})

const createForm = reactive({
  datasetId: typeof route.query.datasetId === 'string' ? route.query.datasetId : '',
  reason: '达到保留期限，准备进入逻辑销毁与留痕流程。',
})

const decisionForms = reactive<Record<string, { policy: string }>>({})

const isPrivilegedActor = computed(() =>
  ['owner', 'approver', 'admin'].includes(actorProfile.value.actorRole.toLowerCase()),
)
const canInspectChainWorkspace = computed(() => canInspectChainRecords(actorProfile.value.actorRole))

const roleGuide = computed(() => {
  if (isPrivilegedActor.value) {
    return {
      note: '这里负责销毁申请、审批和执行三个阶段。管理员可看全局，归属方与审批人主要处理本机构数据集的销毁闭环。',
      emptyState: '当前没有销毁申请。可以从数据详情页带入数据集，或在这里直接提交第一条申请。',
    }
  }
  return {
    note: '这里主要用于提交销毁申请和回看自己的申请状态。审批与执行动作会由归属方或管理员完成。',
    emptyState: '当前没有你的销毁申请。先选择一个已获授权的数据集发起申请，再回来追踪状态。',
  }
})

const destructionStats = computed(() => [
  { label: '申请总数', value: String(requestRows.value.length) },
  { label: '待审批', value: String(requestRows.value.filter((row) => row.status === 'pending').length) },
  { label: '待执行', value: String(requestRows.value.filter((row) => row.status === 'approved').length) },
  { label: '已销毁', value: String(requestRows.value.filter((row) => row.status === 'destroyed').length) },
])

const latestRequest = computed(() => requestRows.value[0] ?? null)

function formatTime(value: string | null | undefined) {
  if (!value) {
    return '暂无'
  }
  return new Date(value).toLocaleString()
}

function datasetLabel(datasetId: string) {
  const dataset = datasetRows.value.find((row) => row.id === datasetId)
  if (!dataset) {
    return datasetId
  }
  return `${dataset.id} · ${dataset.title}`
}

function syncDecisionForms(rows: DestructionRequest[]) {
  for (const key of Object.keys(decisionForms)) {
    delete decisionForms[key]
  }
  for (const row of rows) {
    decisionForms[row.id] = {
      policy: row.policyNote || '保留销毁审计与链上证明，物理删除后续补齐。',
    }
  }
}

function canManage(row: DestructionRequest) {
  if (actorProfile.value.actorRole.toLowerCase() === 'admin') {
    return true
  }
  if (!isPrivilegedActor.value) {
    return false
  }
  return row.ownerOrganization.toLowerCase() === actorProfile.value.actorOrg.toLowerCase()
}

async function loadPage() {
  const payload = await runPageLoad(async () => {
    const [datasets, requests] = await Promise.all([
      getDatasets(),
      getDestructionRequests(actorProfile.value, {
        datasetId: filters.datasetId.trim() || undefined,
        actorId: filters.actorId.trim() || undefined,
        status: filters.status || undefined,
      }),
    ])

    return {
      datasets,
      requests,
    }
  }, '加载销毁工作区失败。')

  if (!payload) {
    return
  }

  datasetRows.value = payload.datasets
  requestRows.value = payload.requests
  if (!createForm.datasetId && filters.datasetId) {
    createForm.datasetId = filters.datasetId
  }
  syncDecisionForms(requestRows.value)
}

async function submitCreateRequest() {
  actionLoadingId.value = 'create'
  try {
    await createDestructionRequest(actorProfile.value, {
      datasetId: createForm.datasetId,
      reason: createForm.reason,
    })
    pushToast({
      title: '销毁申请已提交',
      message: `${createForm.datasetId} 已进入销毁审批流。`,
      tone: 'success',
    })
    await loadPage()
  } catch (submitError) {
    setErrorMessage(toErrorMessage(submitError, '提交销毁申请失败。'))
  } finally {
    actionLoadingId.value = null
  }
}

async function approveRequest(row: DestructionRequest) {
  actionLoadingId.value = row.id
  try {
    await approveDestructionRequest(row.id, actorProfile.value, {
      policy: decisionForms[row.id].policy,
    })
    pushToast({
      title: '销毁申请已批准',
      message: `${row.id} 已进入待执行状态。`,
      tone: 'success',
    })
    await loadPage()
  } catch (submitError) {
    setErrorMessage(toErrorMessage(submitError, '批准销毁申请失败。'))
  } finally {
    actionLoadingId.value = null
  }
}

async function rejectRequest(row: DestructionRequest) {
  actionLoadingId.value = row.id
  try {
    await rejectDestructionRequest(row.id, actorProfile.value, {
      policy: decisionForms[row.id].policy,
    })
    pushToast({
      title: '销毁申请已拒绝',
      message: `${row.id} 已被退回。`,
      tone: 'warning',
    })
    await loadPage()
  } catch (submitError) {
    setErrorMessage(toErrorMessage(submitError, '拒绝销毁申请失败。'))
  } finally {
    actionLoadingId.value = null
  }
}

async function executeRequest(row: DestructionRequest) {
  actionLoadingId.value = row.id
  try {
    await executeDestructionRequest(row.id, actorProfile.value)
    pushToast({
      title: '逻辑销毁已执行',
      message: `${row.datasetId} 已切换为已销毁状态，并留下审计与链上证明。`,
      tone: 'warning',
    })
    await loadPage()
  } catch (submitError) {
    setErrorMessage(toErrorMessage(submitError, '执行销毁失败。'))
  } finally {
    actionLoadingId.value = null
  }
}

async function purgeStorage(row: DestructionRequest) {
  actionLoadingId.value = row.id
  try {
    const result = await purgeDestructionStorage(row.id, actorProfile.value)
    pushToast({
      title: result.cleanupStatus === 'completed' ? '物理清理已完成' : '物理清理失败',
      message:
        result.cleanupStatus === 'completed'
          ? `${row.datasetId} 的存储对象已完成清理。`
          : `${row.datasetId} 的存储清理失败，可稍后重试。`,
      tone: result.cleanupStatus === 'completed' ? 'success' : 'warning',
    })
    await loadPage()
  } catch (submitError) {
    setErrorMessage(toErrorMessage(submitError, '执行物理清理失败。'))
  } finally {
    actionLoadingId.value = null
  }
}

function resetFilters() {
  filters.datasetId = ''
  filters.actorId = ''
  filters.status = ''
  void loadPage()
}

onMounted(loadPage)

watch(
  () => route.query,
  () => {
    filters.datasetId = typeof route.query.datasetId === 'string' ? route.query.datasetId : ''
    filters.actorId = typeof route.query.actorId === 'string' ? route.query.actorId : ''
    filters.status = typeof route.query.status === 'string' ? route.query.status : ''
    if (typeof route.query.datasetId === 'string') {
      createForm.datasetId = route.query.datasetId
    }
    void loadPage()
  },
)
</script>

<template>
  <div class="destruction-page">
    <section class="hero-panel glass-panel">
      <div class="hero-panel__copy">
        <div class="hero-panel__masthead">
          <p class="section-kicker">销毁控制</p>
          <span class="hero-panel__stamp">IRREVERSIBLE</span>
        </div>
        <h1 class="page-main-heading">数据销毁</h1>
        <div class="hero-panel__actions">
          <span class="status-chip status-chip--danger">{{ isPrivilegedActor ? '销毁治理视图' : '销毁申请视图' }}</span>
          <RouterLink class="hero-panel__secondary" to="/">返回总览</RouterLink>
        </div>
        <div class="summary-strip">
          <article
            v-for="stat in destructionStats"
            :key="stat.label"
            class="summary-strip__card"
            :class="{
              'summary-strip__card--warn': stat.label === '待审批',
              'summary-strip__card--danger': stat.label === '已销毁',
            }"
          >
            <span>{{ stat.label }}</span>
            <strong>{{ stat.value }}</strong>
          </article>
        </div>
      </div>

      <div class="hero-panel__rail">
        <article class="hero-spotlight">
          <p class="hero-spotlight__kicker">最新申请</p>
          <template v-if="latestRequest">
            <div class="hero-spotlight__headline">
              <strong>{{ latestRequest.datasetTitle }}</strong>
              <span
                class="status-chip"
                :class="{
                  'status-chip--danger': latestRequest.status === 'rejected' || latestRequest.status === 'destroyed',
                  'status-chip--warn': latestRequest.status === 'pending' || latestRequest.status === 'approved',
                }"
              >
                {{ formatRequestStatusLabel(latestRequest.status) }}
              </span>
            </div>
            <p class="hero-spotlight__context">
              {{ latestRequest.id }} · {{ latestRequest.requesterId }} · {{ formatOrganizationLabel(latestRequest.ownerOrganization) }}
            </p>
            <p class="hero-spotlight__reason">{{ latestRequest.reason }}</p>
            <div class="hero-spotlight__meta">
              <div>
                <span>数据集状态</span>
                <strong>{{ formatDestructionStatusLabel(datasetRows.find((row) => row.id === latestRequest.datasetId)?.destructionStatus) }}</strong>
              </div>
              <div>
                <span>决策时间</span>
                <strong>{{ formatTime(latestRequest.decidedAt) }}</strong>
              </div>
              <div>
                <span>执行时间</span>
                <strong>{{ formatTime(latestRequest.executedAt) }}</strong>
              </div>
            </div>
          </template>
          <p v-else class="hero-spotlight__reason">{{ roleGuide.emptyState }}</p>
        </article>
      </div>
    </section>

    <div v-if="loading" class="loading-state">正在加载销毁工作区...</div>
    <div v-else-if="error" class="error-state">{{ error }}</div>

    <template v-else>
      <section class="destruction-layout">
        <aside class="destruction-layout__side">
          <article class="workspace-card glass-panel">
            <div class="workspace-card__header">
              <div>
                <p class="section-kicker">发起申请</p>
                <h2 class="section-title">发起销毁申请</h2>
              </div>
            </div>

            <form class="form-grid" @submit.prevent="submitCreateRequest">
              <label>
                <span>数据集</span>
                <select v-model="createForm.datasetId">
                  <option value="">请选择数据集</option>
                  <option v-for="dataset in datasetRows" :key="dataset.id" :value="dataset.id">
                    {{ datasetLabel(dataset.id) }}
                  </option>
                </select>
              </label>
              <label>
                <span>销毁原因</span>
                <textarea v-model="createForm.reason" rows="4"></textarea>
              </label>
              <button type="submit" class="form-grid__submit" :disabled="actionLoadingId === 'create' || !createForm.datasetId">
                {{ actionLoadingId === 'create' ? '提交中...' : '提交销毁申请' }}
              </button>
            </form>
          </article>

          <article class="workspace-card glass-panel">
            <div class="workspace-card__header">
              <div>
                <p class="section-kicker">筛选条件</p>
                <h2 class="section-title">筛选条件</h2>
              </div>
            </div>

            <form class="form-grid" @submit.prevent="loadPage">
              <label>
                <span>数据集 ID</span>
                <input v-model="filters.datasetId" type="text" />
              </label>
              <label>
                <span>账户 ID</span>
                <input v-model="filters.actorId" type="text" :disabled="actorProfile.actorRole.toLowerCase() !== 'admin'" />
              </label>
              <label>
                <span>状态</span>
                <select v-model="filters.status">
                  <option value="">全部状态</option>
                  <option value="pending">待处理</option>
                  <option value="approved">已批准</option>
                  <option value="rejected">已拒绝</option>
                  <option value="destroyed">已销毁</option>
                </select>
              </label>
              <div class="form-grid__actions">
                <button type="submit" class="form-grid__submit">刷新列表</button>
                <button type="button" class="form-grid__secondary" @click="resetFilters">清空条件</button>
              </div>
            </form>
          </article>
        </aside>

        <div class="destruction-layout__main">
          <article class="workspace-card glass-panel">
            <div class="workspace-card__header">
              <div>
                <p class="section-kicker">Request Stream</p>
                <h2 class="section-title">销毁申请流</h2>
              </div>
              <span class="status-chip">{{ requestRows.length }} 条记录</span>
            </div>

            <div v-if="requestRows.length" class="request-list">
              <article
                v-for="row in requestRows"
                :key="row.id"
                class="request-card"
                :class="{
                  'request-card--danger': row.status === 'rejected' || row.status === 'destroyed',
                  'request-card--warn': row.status === 'pending' || row.status === 'approved',
                }"
              >
                <div class="request-card__header">
                  <div>
                    <strong>{{ row.datasetTitle }}</strong>
                    <p class="request-card__ident">{{ row.id }} · {{ row.requesterId }} / {{ formatRoleLabel(row.requesterRole) }}</p>
                  </div>
                  <span
                    class="status-chip"
                    :class="{
                      'status-chip--danger': row.status === 'rejected' || row.status === 'destroyed',
                      'status-chip--warn': row.status === 'pending' || row.status === 'approved',
                    }"
                  >
                    {{ formatRequestStatusLabel(row.status) }}
                  </span>
                </div>

                <dl class="request-card__details">
                  <div>
                    <dt>数据集</dt>
                    <dd>{{ row.datasetId }}</dd>
                  </div>
                  <div>
                    <dt>归属机构</dt>
                    <dd>{{ formatOrganizationLabel(row.ownerOrganization) }}</dd>
                  </div>
                  <div>
                    <dt>数据状态</dt>
                    <dd>{{ formatDestructionStatusLabel(datasetRows.find((dataset) => dataset.id === row.datasetId)?.destructionStatus) }}</dd>
                  </div>
                  <div>
                    <dt>创建时间</dt>
                    <dd>{{ formatTime(row.createdAt) }}</dd>
                  </div>
                </dl>

                <p class="request-card__reason">{{ row.reason }}</p>
                <p v-if="row.policyNote" class="request-card__policy">治理说明：{{ row.policyNote }}</p>
                <p v-if="row.executedBy" class="request-card__policy">执行人：{{ row.executedBy }} · {{ formatTime(row.executedAt) }}</p>
                <p v-if="row.status === 'destroyed'" class="request-card__policy">
                  存储清理：{{ formatCleanupStatusLabel(row.cleanupStatus) }} · {{ formatTime(row.cleanupCompletedAt) }}
                </p>
                <p v-if="row.cleanupError" class="request-card__policy request-card__policy--danger">清理失败：{{ row.cleanupError }}</p>
                <div v-if="row.cleanupEvidenceHash" class="request-card__evidence">
                  <strong>清理凭证</strong>
                  <p>凭证引用：<span class="request-card__hash">{{ row.cleanupEvidenceRef }}</span></p>
                  <p>凭证摘要：<span class="request-card__hash">{{ row.cleanupEvidenceHash }}</span></p>
                  <p>确认人：<span class="request-card__hash">{{ row.cleanupVerifiedBy || '暂无' }}</span></p>
                </div>

                <div class="request-card__links">
                  <RouterLink class="request-card__link" :to="`/datasets/${row.datasetId}`">打开数据详情</RouterLink>
                  <RouterLink
                    class="request-card__link"
                    :to="{
                      path: '/audits',
                      query: {
                        datasetId: row.datasetId,
                        action: row.cleanupStatus === 'completed' ? 'DESTRUCTION_STORAGE_PURGE_COMPLETED' : 'DESTRUCTION_EXECUTED',
                      },
                    }"
                  >
                    打开审计流
                  </RouterLink>
                  <RouterLink
                    v-if="row.cleanupEvidenceHash && canInspectChainWorkspace"
                    class="request-card__link"
                    :to="{ path: '/chain-records', query: { datasetId: row.datasetId, eventType: 'DESTRUCTION_STORAGE_PURGED' } }"
                  >
                    打开清理凭证
                  </RouterLink>
                </div>

                <div v-if="canManage(row)" class="decision-form">
                  <label>
                    <span>治理说明</span>
                    <input v-model="decisionForms[row.id].policy" type="text" />
                  </label>
                  <div class="decision-form__actions">
                    <button
                      v-if="row.status === 'pending'"
                      type="button"
                      class="decision-form__primary"
                      @click="approveRequest(row)"
                      :disabled="actionLoadingId === row.id"
                    >
                      批准销毁
                    </button>
                    <button
                      v-if="row.status === 'pending'"
                      type="button"
                      class="decision-form__danger"
                      @click="rejectRequest(row)"
                      :disabled="actionLoadingId === row.id"
                    >
                      拒绝申请
                    </button>
                    <button
                      v-if="row.status === 'approved'"
                      type="button"
                      class="decision-form__danger"
                      @click="executeRequest(row)"
                      :disabled="actionLoadingId === row.id"
                    >
                      执行销毁
                    </button>
                    <button
                      v-if="row.status === 'destroyed' && row.cleanupStatus !== 'completed'"
                      type="button"
                      class="decision-form__danger"
                      @click="purgeStorage(row)"
                      :disabled="actionLoadingId === row.id"
                    >
                      {{ row.cleanupStatus === 'failed' ? '重试物理清理' : '执行物理清理' }}
                    </button>
                  </div>
                </div>
              </article>
            </div>
            <div v-else class="empty-state">{{ roleGuide.emptyState }}</div>
          </article>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.destruction-page {
  display: grid;
  gap: 18px;
  padding-bottom: 8px;
}

/* ===== Hero — danger-led mission masthead ===== */
.hero-panel {
  position: relative;
  overflow: hidden;
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(320px, 0.85fr);
  gap: 20px;
  padding: var(--space-hero);
  border-radius: var(--radius-hero);
  border: 1px solid var(--line-strong);
  background:
    radial-gradient(120% 130% at 0% 0%, rgba(255, 97, 115, 0.1), transparent 46%),
    radial-gradient(120% 140% at 100% 100%, rgba(160, 123, 255, 0.08), transparent 52%),
    var(--panel-gradient);
  animation: consoleRise 0.55s ease both;
}

.hero-panel::before {
  content: '';
  position: absolute;
  inset: 0 0 auto;
  height: 3px;
  background: linear-gradient(90deg, var(--danger), var(--accent-2) 70%, transparent);
  box-shadow: 0 0 16px rgba(255, 97, 115, 0.5);
}

.hero-panel__copy,
.hero-panel__rail,
.summary-strip,
.destruction-layout,
.destruction-layout__side,
.destruction-layout__main,
.request-list {
  display: grid;
  gap: var(--space-list);
}

.hero-panel__copy {
  position: relative;
  z-index: 1;
  align-content: start;
}

.hero-panel__masthead {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.hero-panel__stamp {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 12px;
  border-radius: var(--radius-pill);
  border: 1px solid var(--danger-soft);
  background: rgba(255, 97, 115, 0.08);
  color: var(--danger);
  font-family: var(--mono);
  font-size: 0.68rem;
  font-weight: 600;
  letter-spacing: 0.22em;
  text-transform: uppercase;
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

.hero-panel__lede,
.hero-panel__hint,
.request-card__reason,
.request-card__policy {
  color: var(--text-muted);
  font-size: var(--supporting-text-size);
  line-height: var(--supporting-text-line-height);
}

.request-card__policy--danger {
  color: var(--danger);
}

.request-card__evidence {
  display: grid;
  gap: 8px;
  padding: var(--space-subpanel);
  border-radius: var(--radius-subpanel);
  border: 1px solid var(--line);
  background: var(--bg-panel-muted);
}

.request-card__evidence strong {
  font-family: var(--display);
  font-weight: 600;
  letter-spacing: 0.02em;
  color: var(--text-strong);
}

.request-card__hash {
  font-family: var(--mono);
  font-size: 0.82rem;
  color: var(--text-strong);
  overflow-wrap: anywhere;
}

.hero-panel__actions,
.workspace-card__header,
.hero-spotlight__headline,
.request-card__header,
.form-grid__actions,
.decision-form__actions {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.hero-panel__actions {
  align-items: center;
}

.hero-panel__secondary,
.form-grid__secondary,
.request-card__link {
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
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    color 0.2s ease;
}

.hero-panel__secondary:hover,
.form-grid__secondary:hover {
  border-color: var(--line-warm);
  box-shadow: 0 0 18px rgba(52, 225, 214, 0.14);
  color: var(--text-strong);
}

.request-card,
.workspace-card,
.hero-spotlight,
.summary-strip__card {
  padding: var(--space-card);
  border-radius: var(--radius-panel);
  border: 1px solid var(--line);
  background: var(--panel-gradient);
}

.summary-strip__card span,
.hero-spotlight__kicker,
.hero-spotlight__meta span,
.request-card dt,
.form-grid span,
.decision-form span {
  display: block;
  color: var(--text-faint);
  font-size: var(--field-label-size);
  letter-spacing: var(--field-label-letter-spacing);
  text-transform: uppercase;
}

.hero-spotlight__kicker,
.request-card dt {
  font-family: var(--mono);
}

.summary-strip__card strong,
.hero-spotlight__headline strong,
.hero-spotlight__meta strong,
.request-card strong {
  display: block;
  font-family: var(--body);
  color: var(--text-strong);
}

/* ===== Summary strip — instrument readouts ===== */
.summary-strip {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.summary-strip__card {
  border-color: var(--line-warm);
  background: var(--warm-panel-gradient);
  box-shadow:
    inset 0 0 0 1px rgba(52, 225, 214, 0.06),
    0 0 24px rgba(52, 225, 214, 0.05);
  animation: consoleRise 0.5s ease both;
}

.summary-strip__card:nth-child(1) {
  animation-delay: 0.08s;
}

.summary-strip__card:nth-child(2) {
  animation-delay: 0.14s;
}

.summary-strip__card:nth-child(3) {
  animation-delay: 0.2s;
}

.summary-strip__card:nth-child(4) {
  animation-delay: 0.26s;
}

.summary-strip__card span {
  font-family: var(--mono);
}

.summary-strip__card strong {
  margin-top: 10px;
  font-family: var(--mono);
  font-size: clamp(1.8rem, 3vw, 2.4rem);
  color: var(--text-strong);
}

.summary-strip__card--warn {
  border-color: var(--amber-soft);
  background:
    linear-gradient(180deg, rgba(242, 178, 89, 0.08), rgba(242, 178, 89, 0.015)),
    var(--panel-gradient);
  box-shadow:
    inset 0 0 0 1px rgba(242, 178, 89, 0.08),
    0 0 24px rgba(242, 178, 89, 0.05);
}

.summary-strip__card--warn strong {
  color: var(--amber);
}

.summary-strip__card--danger {
  border-color: var(--danger-soft);
  background:
    linear-gradient(180deg, rgba(255, 97, 115, 0.09), rgba(255, 97, 115, 0.02)),
    var(--panel-gradient);
  box-shadow:
    inset 0 0 0 1px rgba(255, 97, 115, 0.1),
    0 0 26px rgba(255, 97, 115, 0.06);
}

.summary-strip__card--danger strong {
  color: var(--danger);
}

/* ===== Hero spotlight — latest request ===== */
.hero-panel__rail {
  position: relative;
  z-index: 1;
}

.hero-spotlight,
.workspace-card {
  display: grid;
  gap: 14px;
  background: var(--panel-gradient);
}

.hero-spotlight {
  animation: consoleRise 0.55s ease 0.12s both;
}

.hero-spotlight__kicker {
  margin: 0;
}

.hero-spotlight__headline strong {
  font-size: 1.3rem;
  line-height: 1.18;
}

.hero-spotlight__context {
  font-family: var(--mono);
  color: var(--text-muted);
}

.hero-spotlight__context,
.hero-spotlight__reason,
.request-card p {
  margin: 0;
  font-size: var(--supporting-text-size);
  line-height: var(--supporting-text-line-height);
}

.hero-spotlight__reason {
  color: var(--text-main);
}

.hero-spotlight__meta,
.request-card__details,
.form-grid {
  display: grid;
  gap: 12px;
}

.hero-spotlight__meta {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.hero-spotlight__meta div,
.request-card__details div {
  padding: var(--space-subpanel);
  border-radius: var(--radius-subpanel);
  border: 1px solid var(--line);
  background: var(--panel-soft-gradient);
}

.hero-spotlight__meta span {
  font-family: var(--mono);
}

.hero-spotlight__meta strong {
  margin-top: 8px;
  font-family: var(--mono);
  font-size: 0.92rem;
  line-height: 1.45;
}

/* ===== Workspace cards (forms + stream) ===== */
.destruction-layout {
  grid-template-columns: minmax(320px, 360px) minmax(0, 1fr);
}

.workspace-card {
  animation: consoleRise 0.5s ease both;
}

.destruction-layout__side .workspace-card:nth-child(1) {
  animation-delay: 0.16s;
}

.destruction-layout__side .workspace-card:nth-child(2) {
  animation-delay: 0.24s;
}

.destruction-layout__main .workspace-card {
  animation-delay: 0.2s;
}

.workspace-card__header .section-title {
  margin-top: 4px;
  font-size: 1.18rem;
  color: var(--text-strong);
}

.form-grid label,
.decision-form label {
  display: grid;
  gap: 8px;
}

.form-grid input,
.form-grid select,
.form-grid textarea,
.decision-form input {
  width: 100%;
  min-height: var(--field-height);
  padding: var(--space-field-x);
  border: 1px solid var(--line);
  border-radius: var(--radius-control);
  background: var(--bg-panel);
  color: var(--text-main);
}

.form-grid textarea {
  min-height: 92px;
  resize: vertical;
}

/* Primary cyan->violet action (submit / refresh) */
.form-grid__submit,
.decision-form__primary {
  min-height: var(--control-height);
  padding: var(--space-button);
  border: 1px solid var(--line-warm);
  border-radius: var(--radius-pill);
  background: var(--button-warm-gradient);
  color: var(--text-strong);
  font-family: var(--body);
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  box-shadow:
    0 12px 24px rgba(0, 0, 0, 0.36),
    0 0 18px rgba(52, 225, 214, 0.12);
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease;
}

.form-grid__submit:hover:not(:disabled),
.decision-form__primary:hover:not(:disabled) {
  border-color: rgba(52, 225, 214, 0.6);
  box-shadow:
    0 14px 28px rgba(0, 0, 0, 0.42),
    0 0 26px rgba(52, 225, 214, 0.26);
}

.form-grid__submit:disabled,
.decision-form__actions button:disabled {
  opacity: 0.55;
  cursor: progress;
}

.form-grid__actions {
  align-items: stretch;
}

.form-grid__actions .form-grid__submit {
  flex: 1;
}

.request-card__details {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.request-card__details dd {
  margin: 6px 0 0;
  font-family: var(--mono);
  font-size: 0.86rem;
  color: var(--text-strong);
}

.request-card__ident {
  font-family: var(--mono);
  color: var(--text-muted);
}

/* ===== Request stream cards ===== */
.request-card {
  position: relative;
  display: grid;
  gap: 12px;
  animation: consoleRise 0.5s ease both;
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease;
}

.request-card::before {
  content: '';
  position: absolute;
  inset: 0 auto 0 0;
  width: 2px;
  border-radius: var(--radius-panel) 0 0 var(--radius-panel);
  background: var(--line-strong);
}

.request-card:nth-child(1) {
  animation-delay: 0.04s;
}

.request-card:nth-child(2) {
  animation-delay: 0.1s;
}

.request-card:nth-child(3) {
  animation-delay: 0.16s;
}

.request-card:nth-child(4) {
  animation-delay: 0.22s;
}

.request-card:nth-child(n + 5) {
  animation-delay: 0.28s;
}

.request-card:hover {
  border-color: var(--line-strong);
  box-shadow: 0 0 22px rgba(52, 225, 214, 0.08);
}

.request-card--warn::before {
  background: linear-gradient(180deg, var(--amber), transparent);
  box-shadow: 0 0 12px rgba(242, 178, 89, 0.35);
}

.request-card--warn:hover {
  border-color: var(--amber-soft);
  box-shadow: 0 0 22px rgba(242, 178, 89, 0.1);
}

.request-card--danger::before {
  background: linear-gradient(180deg, var(--danger), transparent);
  box-shadow: 0 0 12px rgba(255, 97, 115, 0.4);
}

.request-card--danger:hover {
  border-color: var(--danger-soft);
  box-shadow: 0 0 22px rgba(255, 97, 115, 0.1);
}

.request-card__header strong {
  font-size: 1.06rem;
  line-height: 1.3;
}

.request-card__policy {
  color: var(--text-muted);
}

.request-card__links {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 4px;
}

.request-card__link {
  min-height: 38px;
  font-size: 0.78rem;
}

.request-card__link:hover {
  border-color: var(--line-warm);
  box-shadow: 0 0 16px rgba(52, 225, 214, 0.14);
  color: var(--text-strong);
}

/* ===== Decision form — destructive gravity ===== */
.decision-form {
  display: grid;
  gap: 12px;
  margin-top: 8px;
  padding-top: 16px;
  border-top: 1px solid var(--line);
}

.decision-form__actions {
  flex-wrap: wrap;
  gap: 10px;
}

.decision-form__actions button {
  min-height: var(--control-height);
  padding: var(--space-button);
  border-radius: var(--radius-pill);
  font-family: var(--body);
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

/* Destructive: reject / execute / purge — must read as serious */
.decision-form__danger {
  border: 1px solid var(--danger-soft);
  background:
    linear-gradient(180deg, rgba(255, 97, 115, 0.14), rgba(255, 97, 115, 0.04)),
    var(--button-soft-gradient);
  color: var(--danger);
  box-shadow:
    inset 0 0 0 1px rgba(255, 97, 115, 0.08),
    0 10px 22px rgba(0, 0, 0, 0.3);
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    background 0.2s ease;
}

.decision-form__danger:hover:not(:disabled) {
  border-color: var(--danger);
  background:
    linear-gradient(180deg, rgba(255, 97, 115, 0.22), rgba(255, 97, 115, 0.06)),
    var(--button-soft-gradient);
  box-shadow:
    inset 0 0 0 1px rgba(255, 97, 115, 0.18),
    0 12px 26px rgba(0, 0, 0, 0.4),
    0 0 22px rgba(255, 97, 115, 0.28);
}

.empty-state {
  font-family: var(--body);
}

@media (max-width: 1040px) {
  .hero-panel,
  .destruction-layout,
  .summary-strip,
  .hero-spotlight__meta,
  .request-card__details {
    grid-template-columns: 1fr;
  }

  .workspace-card__header,
  .hero-spotlight__headline,
  .request-card__header {
    flex-direction: column;
    align-items: flex-start;
  }

  .form-grid__actions,
  .decision-form__actions {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
