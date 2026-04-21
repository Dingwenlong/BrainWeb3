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

const route = useRoute()
const { actorProfile } = useActorProfile()
const { pushToast } = useToast()

const loading = ref(true)
const error = ref<string | null>(null)
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

const intakeHint = computed(() => {
  const source = String(route.query.source ?? '')
  if (source === 'dataset-detail' && filters.datasetId) {
    return `当前请求来自数据详情页，已自动带入数据集 ${filters.datasetId}。`
  }
  return ''
})

const roleGuide = computed(() => {
  if (isPrivilegedActor.value) {
    return {
      note: '这里负责销毁申请、审批和执行三个阶段。管理员可看全局，归属方与审批人主要处理本机构数据集的销毁闭环。',
      emptyState: '当前还没有销毁申请。可以先从数据详情页带入一个数据集，或在这里直接提交第一条申请。',
    }
  }
  return {
    note: '这里主要用于提交销毁申请和回看自己的申请状态。审批与执行动作会由归属方或管理员完成。',
    emptyState: '当前还没有你的销毁申请。先选择一个已获授权的数据集发起申请，再回来追踪状态。',
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
  loading.value = true
  error.value = null

  try {
    const [datasets, requests] = await Promise.all([
      getDatasets(),
      getDestructionRequests(actorProfile.value, {
        datasetId: filters.datasetId.trim() || undefined,
        actorId: filters.actorId.trim() || undefined,
        status: filters.status || undefined,
      }),
    ])
    datasetRows.value = datasets
    requestRows.value = requests
    if (!createForm.datasetId && filters.datasetId) {
      createForm.datasetId = filters.datasetId
    }
    syncDecisionForms(requestRows.value)
  } catch (loadError) {
    error.value = loadError instanceof Error ? loadError.message : '加载销毁工作区失败。'
  } finally {
    loading.value = false
  }
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
    error.value = submitError instanceof Error ? submitError.message : '提交销毁申请失败。'
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
    error.value = submitError instanceof Error ? submitError.message : '批准销毁申请失败。'
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
    error.value = submitError instanceof Error ? submitError.message : '拒绝销毁申请失败。'
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
    error.value = submitError instanceof Error ? submitError.message : '执行销毁失败。'
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
    error.value = submitError instanceof Error ? submitError.message : '执行物理清理失败。'
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
        <p class="section-kicker">Destruction Control</p>
        <h1>把销毁申请、审批、执行和留痕收成一条最小可演示闭环。</h1>
        <p class="hero-panel__lede">
          当前身份是 {{ actorProfile.actorId }} / {{ formatRoleLabel(actorProfile.actorRole) }} /
          {{ formatOrganizationLabel(actorProfile.actorOrg) }}。这页现在已经把“逻辑销毁 + 物理清理 + 审计 + 链凭证”收成一条可回看的最小闭环。
        </p>

        <div class="hero-panel__actions">
          <span class="status-chip">{{ isPrivilegedActor ? '销毁治理视图' : '销毁申请视图' }}</span>
          <RouterLink class="hero-panel__secondary" to="/">返回总览</RouterLink>
        </div>
        <p v-if="intakeHint" class="hero-panel__hint">{{ intakeHint }}</p>
        <div class="hero-panel__guide">
          <span>Role Guidance</span>
          <strong>{{ roleGuide.note }}</strong>
        </div>

        <div class="summary-strip">
          <article v-for="stat in destructionStats" :key="stat.label" class="summary-strip__card">
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
              <span class="status-chip" :class="{ 'status-chip--danger': latestRequest.status === 'rejected' || latestRequest.status === 'destroyed' }">
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
                <p class="section-kicker">Create Request</p>
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
                <p class="section-kicker">Filters</p>
                <h2 class="section-title">筛选条件</h2>
              </div>
            </div>

            <form class="form-grid" @submit.prevent="loadPage">
              <label>
                <span>Dataset ID</span>
                <input v-model="filters.datasetId" type="text" />
              </label>
              <label>
                <span>Actor ID</span>
                <input v-model="filters.actorId" type="text" :disabled="actorProfile.actorRole.toLowerCase() !== 'admin'" />
              </label>
              <label>
                <span>状态</span>
                <select v-model="filters.status">
                  <option value="">全部状态</option>
                  <option value="pending">pending</option>
                  <option value="approved">approved</option>
                  <option value="rejected">rejected</option>
                  <option value="destroyed">destroyed</option>
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
              <article v-for="row in requestRows" :key="row.id" class="request-card">
                <div class="request-card__header">
                  <div>
                    <strong>{{ row.datasetTitle }}</strong>
                    <p>{{ row.id }} · {{ row.requesterId }} / {{ formatRoleLabel(row.requesterRole) }}</p>
                  </div>
                  <span class="status-chip" :class="{ 'status-chip--danger': row.status === 'rejected' || row.status === 'destroyed' }">
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
                  <p>凭证引用：{{ row.cleanupEvidenceRef }}</p>
                  <p>凭证摘要：{{ row.cleanupEvidenceHash }}</p>
                  <p>确认人：{{ row.cleanupVerifiedBy || '暂无' }}</p>
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
                    v-if="row.cleanupEvidenceHash"
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
.destruction-layout,
.destruction-layout__side,
.destruction-layout__main,
.request-list {
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
.hero-panel__hint,
.request-card__reason,
.request-card__policy {
  color: var(--text-muted);
}

.request-card__policy--danger {
  color: var(--danger);
}

.request-card__evidence {
  display: grid;
  gap: 8px;
  padding: 14px 16px;
  border-radius: 18px;
  border: 1px solid rgba(108, 166, 186, 0.12);
  background: rgba(8, 18, 25, 0.76);
}

.request-card__evidence strong {
  font-family: var(--display);
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

.hero-panel__secondary,
.form-grid__secondary,
.request-card__link {
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

.hero-panel__hint,
.hero-panel__guide,
.request-card,
.workspace-card,
.hero-spotlight,
.summary-strip__card {
  padding: 18px;
  border-radius: 24px;
  border: 1px solid rgba(108, 166, 186, 0.14);
  background:
    linear-gradient(180deg, rgba(4, 15, 21, 0.94), rgba(8, 17, 23, 0.78)),
    radial-gradient(circle at top right, rgba(116, 210, 220, 0.08), transparent 30%);
}

.hero-panel__guide {
  display: grid;
  gap: 8px;
}

.hero-panel__guide span,
.summary-strip__card span,
.hero-spotlight__kicker,
.hero-spotlight__meta span,
.request-card dt,
.form-grid span,
.decision-form span {
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
.request-card strong {
  display: block;
  font-family: var(--display);
}

.summary-strip {
  grid-template-columns: repeat(4, minmax(0, 1fr));
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
.request-card p {
  margin: 0;
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
  padding: 14px 16px;
  border-radius: 18px;
  border: 1px solid rgba(108, 166, 186, 0.1);
  background: rgba(8, 18, 25, 0.76);
}

.destruction-layout {
  grid-template-columns: minmax(320px, 360px) minmax(0, 1fr);
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
  min-height: 44px;
  padding: 10px 12px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: rgba(8, 18, 25, 0.94);
  color: var(--text-main);
}

.form-grid__submit,
.decision-form__actions button {
  min-height: 42px;
  padding: 0 16px;
  border: 1px solid var(--line-warm);
  border-radius: 999px;
  background: linear-gradient(180deg, rgba(235, 178, 102, 0.24), rgba(235, 178, 102, 0.12));
  color: var(--text-main);
  font-family: var(--display);
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.request-card__details {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.request-card__links {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 14px;
}

.decision-form {
  display: grid;
  gap: 12px;
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid rgba(108, 166, 186, 0.12);
}

.decision-form__danger {
  border-color: rgba(242, 126, 126, 0.28) !important;
  color: var(--danger);
}

@media (max-width: 1040px) {
  .hero-panel,
  .destruction-layout,
  .summary-strip,
  .hero-spotlight__meta,
  .request-card__details {
    grid-template-columns: 1fr;
  }

  .hero-panel__actions,
  .workspace-card__header,
  .hero-spotlight__headline,
  .request-card__header,
  .form-grid__actions,
  .decision-form__actions {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
