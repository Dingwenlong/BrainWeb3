<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import {
  createEvaluation,
  getDatasets,
  getEvaluations,
  getModelGovernanceLane,
  getModelRecords,
  updateModelGovernance,
} from '../api/client'
import { useActorProfile } from '../composables/useActorProfile'
import { useToast } from '../composables/useToast'
import type {
  AuditEvent,
  ChainBusinessRecord,
  DatasetSummary,
  EvaluationRun,
  ModelGovernanceSummary,
  ModelRecord,
  ModelVersionComparison,
} from '../types/api'
import {
  formatAuditActionLabel,
  formatChainEventLabel,
  formatChainPolicyLabel,
  formatModelGovernanceStatusLabel,
  formatOrganizationLabel,
  formatRoleLabel,
  formatRequestStatusLabel,
  formatVerificationStatusLabel,
} from '../utils/labels'
import HashValue from '../components/HashValue.vue'
import InfoHint from '../components/InfoHint.vue'
import { canInspectChainRecords } from '../utils/permissions'

const route = useRoute()
const { actorProfile } = useActorProfile()
const { pushToast } = useToast()

const datasets = ref<DatasetSummary[]>([])
const records = ref<ModelRecord[]>([])
const loading = ref(false)
const savingId = ref('')
const timelineLoading = ref(false)
const governanceAudits = ref<AuditEvent[]>([])
const governanceChains = ref<ChainBusinessRecord[]>([])
const selectedModelId = ref('')
const governanceSummary = ref<ModelGovernanceSummary | null>(null)
const versionComparison = ref<ModelVersionComparison | null>(null)
const relatedModels = ref<ModelRecord[]>([])
const chainVisible = ref(false)
const evaluations = ref<EvaluationRun[]>([])
const submittingEval = ref(false)
const evalForm = reactive({
  testSetHash: '',
  evalScriptHash: '',
  metricsJson: '',
  notes: '',
})
const canInspectChainWorkspace = computed(() => canInspectChainRecords(actorProfile.value.actorRole))

function verificationChipClass(status: string) {
  return {
    'status-chip--accent': status === 'third-party-verified',
    'status-chip--warn': status === 'self-reported',
    'status-chip--ghost': status === 'unverified' || !status,
  }
}

const filters = reactive({
  datasetId: typeof route.query.datasetId === 'string' ? route.query.datasetId : '',
  governanceStatus: '',
})

const governanceForms = reactive<Record<string, { status: string; note: string }>>({})

const focusTrainingJobId = computed(() =>
  typeof route.query.trainingJobId === 'string' ? route.query.trainingJobId : '',
)
const focusModelId = computed(() =>
  typeof route.query.focusModelId === 'string' ? route.query.focusModelId : '',
)
const focusedRecord = computed(() => {
  if (!records.value.length) {
    return null
  }
  const targetId = selectedModelId.value || focusModelId.value
  return records.value.find((record) => record.id === targetId) ?? records.value[0]
})
const governanceStats = computed(() => [
  { label: '治理审计', value: governanceAudits.value.length },
  { label: '链证明', value: governanceChains.value.length },
  { label: '允许下一步', value: focusedRecord.value?.allowedGovernanceTransitions.length ?? 0 },
  { label: '同数据集版本', value: governanceSummary.value?.datasetVersionCount ?? 0 },
])
const comparisonStats = computed(() => {
  if (!versionComparison.value) {
    return []
  }

  return [
    { label: '当前位次', value: `${versionComparison.value.currentVersionRank} / ${versionComparison.value.totalVisibleVersions}` },
    { label: '更新版本', value: versionComparison.value.newerVersionCount },
    { label: '更早版本', value: versionComparison.value.olderVersionCount },
    { label: '同算法版本', value: versionComparison.value.sameAlgorithmVersionCount },
  ]
})
const registryStats = computed(() => {
  const total = records.value.length
  const candidate = records.value.filter((record) => record.governanceStatus === 'candidate').length
  const active = records.value.filter((record) => record.governanceStatus === 'active').length
  const archived = records.value.filter((record) => record.governanceStatus === 'archived').length
  const governed = records.value.filter((record) => record.governedAt).length
  const activationRate = total ? `${Math.round((active / total) * 100)}%` : '0%'

  return [
    { label: '候选模型', value: candidate },
    { label: '已激活', value: active },
    { label: '已归档', value: archived },
    { label: '已治理占比', value: total ? `${Math.round((governed / total) * 100)}%` : '0%' },
    { label: '激活占比', value: activationRate },
  ]
})
const recentlyGovernedRecords = computed(() =>
  records.value
    .filter((record) => record.governedAt)
    .sort((left, right) => (right.governedAt ?? '').localeCompare(left.governedAt ?? ''))
    .slice(0, 3),
)
function governanceOptionsFor(record: ModelRecord) {
  return [record.governanceStatus, ...record.allowedGovernanceTransitions]
}

function transitionGuideFor(record: ModelRecord) {
  const nextSteps = record.allowedGovernanceTransitions.map((status) => formatModelGovernanceStatusLabel(status))
  if (!nextSteps.length) {
    return '当前状态已到治理终点，若需要重新启用，请从允许回流的状态进入。'
  }
  return `下一步可切换到：${nextSteps.join(' / ')}。`
}

function canSubmitGovernance(record: ModelRecord) {
  const form = governanceForms[record.id]
  if (!form) {
    return false
  }
  if (savingId.value === record.id) {
    return false
  }
  return form.status !== record.governanceStatus && record.allowedGovernanceTransitions.includes(form.status)
}

function focusGovernanceLane(recordId: string) {
  selectedModelId.value = recordId
}

function auditLinkFor(record: ModelRecord) {
  return {
    path: '/audits',
    query: {
      source: 'model-record',
      datasetId: record.datasetId,
      action: 'MODEL_GOVERNANCE_UPDATED',
      focusModelId: record.id,
    },
  }
}

function chainLinkFor(record: ModelRecord) {
  return {
    path: '/chain-records',
    query: {
      datasetId: record.datasetId,
      eventType: 'MODEL_GOVERNED',
      focusModelId: record.id,
    },
  }
}

async function loadGovernanceTimeline() {
  const record = focusedRecord.value
  if (!record) {
    governanceAudits.value = []
    governanceChains.value = []
    governanceSummary.value = null
    versionComparison.value = null
    relatedModels.value = []
    chainVisible.value = false
    evaluations.value = []
    return
  }

  timelineLoading.value = true
  try {
    const [lane, evaluationPayload] = await Promise.all([
      getModelGovernanceLane(record.id, actorProfile.value),
      getEvaluations(record.id, actorProfile.value),
    ])
    governanceSummary.value = lane.summary
    versionComparison.value = lane.comparison
    governanceAudits.value = lane.auditEvents
    governanceChains.value = lane.chainRecords
    relatedModels.value = lane.relatedModels
    chainVisible.value = lane.chainVisible
    evaluations.value = evaluationPayload
  } catch (error) {
    pushToast({
      tone: 'warning',
      title: '治理主线加载失败',
      message: error instanceof Error ? error.message : '请稍后重试。',
    })
  } finally {
    timelineLoading.value = false
  }
}

function syncFocusedRecord() {
  const routeTarget = focusModelId.value
  if (routeTarget && records.value.some((record) => record.id === routeTarget)) {
    selectedModelId.value = routeTarget
    return
  }
  if (selectedModelId.value && records.value.some((record) => record.id === selectedModelId.value)) {
    return
  }
  selectedModelId.value = records.value[0]?.id ?? ''
}

function syncGovernanceForms() {
  for (const record of records.value) {
    governanceForms[record.id] = {
      status: governanceForms[record.id]?.status ?? record.governanceStatus,
      note: governanceForms[record.id]?.note ?? record.governanceNote,
    }
  }
}

async function loadPage() {
  loading.value = true
  try {
    const [datasetPayload, recordPayload] = await Promise.all([
      getDatasets(),
      getModelRecords(actorProfile.value, {
        datasetId: filters.datasetId || undefined,
        governanceStatus: filters.governanceStatus || undefined,
        trainingJobId: focusTrainingJobId.value || undefined,
      }),
    ])
    datasets.value = datasetPayload
    records.value = recordPayload
    syncGovernanceForms()
    syncFocusedRecord()
    await loadGovernanceTimeline()
  } catch (error) {
    pushToast({
      tone: 'warning',
      title: '模型注册表加载失败',
      message: error instanceof Error ? error.message : '请稍后重试。',
    })
  } finally {
    loading.value = false
  }
}

async function submitGovernance(record: ModelRecord) {
  const form = governanceForms[record.id]
  if (!form) {
    return
  }
  savingId.value = record.id
  try {
    const updated = await updateModelGovernance(record.id, actorProfile.value, {
      status: form.status,
      note: form.note,
    })
    records.value = records.value.map((item) => (item.id === record.id ? updated : item))
    syncGovernanceForms()
    pushToast({
      tone: 'success',
      title: '模型治理已更新',
      message: `${updated.id} 已切换为 ${formatModelGovernanceStatusLabel(updated.governanceStatus)}。`,
    })
  } catch (error) {
    pushToast({
      tone: 'warning',
      title: '模型治理更新失败',
      message: error instanceof Error ? error.message : '请稍后重试。',
    })
  } finally {
    savingId.value = ''
  }
}

async function submitEvaluation() {
  const record = focusedRecord.value
  if (!record) {
    return
  }
  if (!evalForm.testSetHash.trim() || !evalForm.evalScriptHash.trim() || !evalForm.metricsJson.trim()) {
    pushToast({
      tone: 'warning',
      title: '评测信息不完整',
      message: '请填写测试集指纹、评测脚本指纹与指标。',
    })
    return
  }
  submittingEval.value = true
  try {
    const evaluation = await createEvaluation(record.id, actorProfile.value, {
      testSetHash: evalForm.testSetHash.trim(),
      evalScriptHash: evalForm.evalScriptHash.trim(),
      metricsJson: evalForm.metricsJson.trim(),
      notes: evalForm.notes.trim(),
    })
    evaluations.value = [evaluation, ...evaluations.value]
    records.value = records.value.map((item) =>
      item.id === record.id
        ? {
            ...item,
            verificationStatus: evaluation.verificationStatus,
            latestEvaluationId: evaluation.id,
            latestResultHash: evaluation.resultHash,
          }
        : item,
    )
    evalForm.testSetHash = ''
    evalForm.evalScriptHash = ''
    evalForm.metricsJson = ''
    evalForm.notes = ''
    pushToast({
      tone: 'success',
      title: '评测证据已记录',
      message: `${evaluation.id} 已生成结果哈希并锚定上链（${formatVerificationStatusLabel(evaluation.verificationStatus)}）。`,
    })
    void loadGovernanceTimeline()
  } catch (error) {
    pushToast({
      tone: 'warning',
      title: '评测提交失败',
      message: error instanceof Error ? error.message : '请稍后重试。',
    })
  } finally {
    submittingEval.value = false
  }
}

function formatTime(value: string | null) {
  if (!value) {
    return '暂无'
  }
  return value.replace('T', ' ').slice(0, 16)
}

function canGovern(record: ModelRecord) {
  const role = actorProfile.value.actorRole.toLowerCase()
  if (role === 'admin') {
    return true
  }
  if (role === 'owner' || role === 'approver') {
    return actorProfile.value.actorOrg.toLowerCase() === record.actorOrg.toLowerCase()
  }
  return false
}

onMounted(() => {
  void loadPage()
})

watch(
  () => [filters.datasetId, filters.governanceStatus, route.query.trainingJobId],
  () => {
    void loadPage()
  },
)

watch(
  () => focusedRecord.value?.id,
  (nextId, previousId) => {
    if (nextId && nextId !== previousId) {
      void loadGovernanceTimeline()
    }
  },
)

watch(
  () => route.query,
  () => {
    filters.datasetId = typeof route.query.datasetId === 'string' ? route.query.datasetId : ''
    void loadPage()
  },
)
</script>

<template>
  <section class="model-page">
    <header class="model-hero glass-panel">
      <div>
        <p class="section-kicker">模型注册表</p>
        <h1 class="page-main-heading">模型治理</h1>
      </div>

      <div class="model-hero__stats">
        <article class="metric-card">
          <span>模型记录</span>
          <strong>{{ records.length }}</strong>
        </article>
        <article class="metric-card">
          <span>候选模型</span>
          <strong>{{ records.filter((record) => record.governanceStatus === 'candidate').length }}</strong>
        </article>
        <article class="metric-card">
          <span>已激活</span>
          <strong>{{ records.filter((record) => record.governanceStatus === 'active').length }}</strong>
        </article>
      </div>
    </header>

    <section class="model-panel glass-panel registry-overview">
      <div class="panel-head">
        <div>
          <p class="section-kicker">注册总览</p>
          <h2>治理总览</h2>
        </div>
      </div>

      <div class="registry-overview__grid">
        <article class="registry-overview__summary">
          <div class="registry-overview__stats">
            <article v-for="stat in registryStats" :key="stat.label" class="metric-card">
              <span>{{ stat.label }}</span>
              <strong>{{ stat.value }}</strong>
            </article>
          </div>
        </article>

        <article class="registry-overview__recent">
          <div class="panel-head">
            <div>
              <p class="section-kicker">最近治理</p>
              <h3>最近治理动作</h3>
            </div>
          </div>

          <div v-if="recentlyGovernedRecords.length" class="registry-overview__events">
            <button
              v-for="record in recentlyGovernedRecords"
              :key="`recent-${record.id}`"
              type="button"
              class="registry-overview__event"
              @click="focusGovernanceLane(record.id)"
            >
              <div class="registry-overview__event-head">
                <strong>{{ record.id }}</strong>
                <span
                  class="status-chip"
                  :class="{
                    'status-chip--active': record.governanceStatus === 'active',
                    'status-chip--ghost': record.governanceStatus === 'archived',
                  }"
                >{{ formatModelGovernanceStatusLabel(record.governanceStatus) }}</span>
              </div>
              <p>{{ record.modelName }}</p>
              <small>{{ formatTime(record.governedAt) }} · {{ record.lastGovernedBy || '系统同步' }}</small>
            </button>
          </div>
          <div v-else class="empty-state">暂无最近治理动作</div>
          
        </article>
      </div>
    </section>

    <section v-if="focusedRecord" class="model-panel glass-panel governance-lane">
      <div class="panel-head">
        <div>
          <p class="section-kicker">治理主线</p>
          <h2>模型治理主线</h2>
        </div>
      </div>

      <div class="governance-lane__grid">
        <article class="governance-lane__spotlight">
          <div class="governance-lane__headline">
            <div>
              <p class="record-card__eyebrow">{{ focusedRecord.trainingJobId }} · {{ focusedRecord.datasetId }}</p>
              <h3>{{ focusedRecord.modelName }}</h3>
            </div>
            <span
              class="status-chip"
              :class="{
                'status-chip--active': focusedRecord.governanceStatus === 'active',
                'status-chip--ghost': focusedRecord.governanceStatus === 'archived',
              }"
            >{{ formatModelGovernanceStatusLabel(focusedRecord.governanceStatus) }}</span>
          </div>

          <div class="evidence-banner">
            <span class="status-chip" :class="verificationChipClass(focusedRecord.verificationStatus)">
              {{ formatVerificationStatusLabel(focusedRecord.verificationStatus) }}
            </span>
            <span v-if="focusedRecord.latestResultHash" class="evidence-banner__hash">
              结果哈希 <HashValue :value="focusedRecord.latestResultHash" :head="12" :tail="6" />
            </span>
            <span v-else class="evidence-banner__hint">质量声明尚无可验证证据</span>
          </div>

          <p class="record-card__summary">{{ focusedRecord.objective }}</p>

          <div class="governance-lane__stats">
            <article v-for="stat in governanceStats" :key="stat.label" class="metric-card">
              <span>{{ stat.label }}</span>
              <strong>{{ stat.value }}</strong>
            </article>
          </div>

          <div class="record-card__timeline">
            <span>归属机构：{{ formatOrganizationLabel(focusedRecord.actorOrg) }}</span>
            <span>最近治理人：{{ focusedRecord.lastGovernedBy || '暂无' }}</span>
            <span>治理流转 <InfoHint :text="transitionGuideFor(focusedRecord)" /></span>
          </div>

          <div v-if="versionComparison" class="version-compare">
            <div class="panel-head">
              <div>
                <p class="section-kicker">版本对比</p>
                <h3>版本对比摘要</h3>
              </div>
              <span class="version-compare__rank">{{ versionComparison.currentVersionRank }} / {{ versionComparison.totalVisibleVersions }}</span>
            </div>

            <div class="version-compare__ids">
              <div class="version-compare__id">
                <span>最新版本</span>
                <strong class="version-id">{{ versionComparison.latestVersionId || '暂无' }}</strong>
                <small>{{ formatTime(versionComparison.latestVersionCompletedAt) }}</small>
              </div>
              <div class="version-compare__arrow">→</div>
              <div class="version-compare__id version-compare__id--active">
                <span>当前激活版</span>
                <strong class="version-id">{{ versionComparison.latestActiveVersionId || '暂无' }}</strong>
                <small>{{ formatTime(versionComparison.latestActiveGovernedAt) }}</small>
              </div>
            </div>

            <div class="version-compare__stats">
              <article v-for="stat in comparisonStats" :key="stat.label" class="metric-card">
                <span>{{ stat.label }}</span>
                <strong>{{ stat.value }}</strong>
              </article>
            </div>
          </div>

          <div class="record-card__links">
            <RouterLink class="ghost-link" :to="auditLinkFor(focusedRecord)">打开治理审计</RouterLink>
            <RouterLink v-if="chainVisible" class="ghost-link" :to="chainLinkFor(focusedRecord)">打开链证明</RouterLink>
          </div>
        </article>

        <article class="governance-lane__stream">
          <div class="panel-head">
            <div>
              <p class="section-kicker">审计轨迹</p>
              <h3>治理审计</h3>
            </div>
          </div>

          <div v-if="timelineLoading" class="empty-state">治理主线正在加载...</div>
          <div v-else-if="!governanceAudits.length" class="empty-state">暂无治理审计</div>
          <div v-else class="governance-lane__events">
            <article v-for="event in governanceAudits" :key="event.id" class="governance-event">
              <div class="governance-event__head">
                <strong>{{ formatAuditActionLabel(event.action) }}</strong>
                <span class="status-chip">{{ formatRequestStatusLabel(event.status) }}</span>
              </div>
              <p v-if="event.detail">{{ event.detail }}</p>
              <small>{{ formatTime(event.createdAt) }} · {{ event.actorId }}</small>
            </article>
          </div>
        </article>

        <article class="governance-lane__stream">
          <div class="panel-head">
            <div>
              <p class="section-kicker">链上证明</p>
              <h3>链证明</h3>
            </div>
          </div>

          <div v-if="timelineLoading" class="empty-state">治理主线正在加载...</div>
          <div v-else-if="!chainVisible" class="empty-state">当前身份无权查看链证明</div>
          <div v-else-if="!governanceChains.length" class="empty-state">暂无链证明</div>
          <div v-else class="governance-lane__events">
            <article v-for="record in governanceChains" :key="record.id" class="governance-event">
              <div class="governance-event__head">
                <strong>{{ formatChainEventLabel(record.eventType) }}</strong>
                <span class="status-chip status-chip--ghost">{{ formatChainPolicyLabel(record.anchorPolicy) }}</span>
              </div>
              <p v-if="record.detail">{{ record.detail }}</p>
              <small>{{ formatTime(record.anchoredAt) }} · {{ record.chainTxHash || '等待上链成功' }}</small>
            </article>
          </div>
        </article>

        <article class="governance-lane__stream">
          <div class="panel-head">
            <div>
              <p class="section-kicker">相关版本</p>
              <h3>同数据集版本</h3>
            </div>
          </div>

          <div v-if="timelineLoading" class="empty-state">治理主线正在加载...</div>
          <div v-else-if="!relatedModels.length" class="empty-state">暂无相关版本</div>
          <div v-else class="governance-lane__events">
            <button
              v-for="record in relatedModels"
              :key="`related-${record.id}`"
              type="button"
              class="registry-overview__event"
              @click="focusGovernanceLane(record.id)"
            >
              <div class="registry-overview__event-head">
                <strong>{{ record.id }}</strong>
                <span
                  class="status-chip"
                  :class="{
                    'status-chip--active': record.governanceStatus === 'active',
                    'status-chip--ghost': record.governanceStatus === 'archived',
                  }"
                >{{ formatModelGovernanceStatusLabel(record.governanceStatus) }}</span>
              </div>
              <p>{{ record.modelName }}</p>
              <small>{{ formatTime(record.completedAt) }} · {{ record.trainingJobId }}</small>
            </button>
          </div>
        </article>

        <article class="governance-lane__stream governance-lane__stream--evidence">
          <div class="panel-head">
            <div>
              <p class="section-kicker">领域验证 · L3</p>
              <h3>评测证据</h3>
            </div>
            <span class="status-chip" :class="verificationChipClass(focusedRecord.verificationStatus)">
              {{ formatVerificationStatusLabel(focusedRecord.verificationStatus) }}
            </span>
          </div>

          <div v-if="timelineLoading" class="empty-state">评测证据正在加载...</div>
          <div v-else-if="!evaluations.length" class="empty-state">
            尚无评测证据 <InfoHint text="模型质量仍是自我声明。提交一次带测试集 / 脚本指纹的评测，即可生成结果哈希并锚定上链，升级为可验证证据。" />
          </div>
          <div v-else class="governance-lane__events">
            <article v-for="evaluation in evaluations" :key="evaluation.id" class="governance-event">
              <div class="governance-event__head">
                <strong>{{ evaluation.id }} · {{ evaluation.metricsJson }}</strong>
                <span class="status-chip" :class="verificationChipClass(evaluation.verificationStatus)">
                  {{ formatVerificationStatusLabel(evaluation.verificationStatus) }}
                </span>
              </div>
              <p>评测方：{{ formatOrganizationLabel(evaluation.evaluatorOrg) }} · {{ evaluation.evaluatorActorId }}</p>
              <p class="evidence-hash">测试集 <HashValue :value="evaluation.testSetHash" :head="16" /></p>
              <p class="evidence-hash">脚本 <HashValue :value="evaluation.evalScriptHash" :head="16" /></p>
              <p class="evidence-hash">结果哈希 <HashValue :value="evaluation.resultHash" :head="12" :tail="6" /></p>
              <small>{{ formatTime(evaluation.createdAt) }}<template v-if="evaluation.notes"> · {{ evaluation.notes }}</template></small>
            </article>
          </div>

          <form class="evidence-form" @submit.prevent="submitEvaluation">
            <label>
              <span>测试集指纹</span>
              <input v-model="evalForm.testSetHash" type="text" placeholder="sha256:holdout-..." />
            </label>
            <label>
              <span>评测脚本指纹</span>
              <input v-model="evalForm.evalScriptHash" type="text" placeholder="sha256:eval-harness-..." />
            </label>
            <label class="evidence-form__wide">
              <span>指标 (JSON)</span>
              <input v-model="evalForm.metricsJson" type="text" placeholder='{&quot;auc&quot;:0.91,&quot;f1&quot;:0.87}' />
            </label>
            <label class="evidence-form__wide">
              <span>备注</span>
              <input v-model="evalForm.notes" type="text" placeholder="留出集评测说明（可选）" />
            </label>
            <button type="submit" class="primary-button evidence-form__submit" :disabled="submittingEval">
              {{ submittingEval ? '提交中...' : '提交评测并锚定上链' }}
            </button>
          </form>
        </article>
      </div>
    </section>

    <section class="model-panel glass-panel">
      <div class="panel-head">
        <div>
          <p class="section-kicker">筛选条件</p>
          <h2>模型筛选</h2>
        </div>
        <button type="button" class="ghost-button" :disabled="loading" @click="loadPage">刷新模型库</button>
      </div>

      <div class="filter-grid">
        <label>
          <span>数据集</span>
          <select v-model="filters.datasetId">
            <option value="">全部数据集</option>
            <option v-for="dataset in datasets" :key="dataset.id" :value="dataset.id">
              {{ dataset.id }} · {{ dataset.title }}
            </option>
          </select>
        </label>
        <label>
          <span>治理状态</span>
          <select v-model="filters.governanceStatus">
            <option value="">全部状态</option>
            <option value="candidate">候选</option>
            <option value="active">已激活</option>
            <option value="archived">已归档</option>
          </select>
        </label>
      </div>
    </section>

    <section class="model-panel glass-panel">
      <div class="panel-head">
        <div>
          <p class="section-kicker">注册表</p>
          <h2>模型记录</h2>
        </div>
      </div>

      <div v-if="loading" class="empty-state">模型记录正在加载...</div>
      <div v-else-if="!records.length" class="empty-state">暂无模型记录</div>

      <div v-else class="record-list">
        <article
          v-for="record in records"
          :key="record.id"
          class="record-card"
          :class="{ 'record-card--focus': focusTrainingJobId === record.trainingJobId || focusModelId === record.id || selectedModelId === record.id }"
        >
          <header class="record-card__head">
            <div>
              <p class="record-card__eyebrow">{{ record.id }} · {{ record.trainingJobId }}</p>
              <h3>{{ record.modelName }}</h3>
            </div>
            <span
              class="status-chip"
              :class="{
                'status-chip--active': record.governanceStatus === 'active',
                'status-chip--ghost': record.governanceStatus === 'archived',
              }"
            >{{ formatModelGovernanceStatusLabel(record.governanceStatus) }}</span>
          </header>

          <p class="record-card__summary">{{ record.objective }}</p>

          <div class="record-card__meta">
            <div>
              <span>数据集</span>
              <strong>{{ record.datasetId }} / {{ record.datasetTitle }}</strong>
            </div>
            <div>
              <span>归属机构</span>
              <strong>{{ formatOrganizationLabel(record.actorOrg) }}</strong>
            </div>
            <div>
              <span>发起人</span>
              <strong>{{ record.actorId }} / {{ formatRoleLabel(record.actorRole) }}</strong>
            </div>
            <div>
              <span>编排层</span>
              <strong>{{ record.orchestrator }}</strong>
            </div>
          </div>

          <div class="record-card__notes">
            <p><strong>验证状态：</strong>{{ formatVerificationStatusLabel(record.verificationStatus) }}<template v-if="record.latestResultHash"> · 哈希 <HashValue :value="record.latestResultHash" :head="12" /></template></p>
            <p v-if="record.metricSummary"><strong>指标摘要：</strong>{{ record.metricSummary }}</p>
            <p v-if="record.resultSummary"><strong>结果说明：</strong>{{ record.resultSummary }}</p>
            <p><strong>模型引用：</strong>{{ record.artifactRef }}</p>
            <p v-if="record.governanceNote"><strong>治理备注：</strong>{{ record.governanceNote }}</p>
          </div>

          <div class="record-card__timeline">
            <span>完成时间：{{ formatTime(record.completedAt) }}</span>
            <span>治理时间：{{ formatTime(record.governedAt) }}</span>
            <span>最近治理人：{{ record.lastGovernedBy || '暂无' }}</span>
          </div>

          <p v-if="canGovern(record)" class="record-card__transition-guide">
            治理流转 <InfoHint :text="transitionGuideFor(record)" />
          </p>

          <div class="record-card__links">
            <button type="button" class="ghost-button" @click="focusGovernanceLane(record.id)">聚焦治理线</button>
            <RouterLink
              class="ghost-link"
              :to="{ path: '/training-jobs', query: { source: 'model-record', focusJobId: record.trainingJobId } }"
            >
              回看训练任务
            </RouterLink>
            <RouterLink
              class="ghost-link"
              :to="{ path: '/audits', query: { source: 'model-record', datasetId: record.datasetId, action: 'MODEL_VERSION_REGISTERED', focusModelId: record.id } }"
            >
              打开审计流
            </RouterLink>
            <RouterLink
              v-if="canInspectChainWorkspace"
              class="ghost-link"
              :to="{ path: '/chain-records', query: { datasetId: record.datasetId, eventType: 'MODEL_REGISTERED', focusModelId: record.id } }"
            >
              打开链轨迹
            </RouterLink>
          </div>

          <form v-if="canGovern(record)" class="governance-form" @submit.prevent="submitGovernance(record)">
            <label>
              <span>治理状态</span>
              <select v-model="governanceForms[record.id].status" :disabled="savingId === record.id">
                <option
                  v-for="status in governanceOptionsFor(record)"
                  :key="`${record.id}-${status}`"
                  :value="status"
                >
                  {{ status }}
                </option>
              </select>
            </label>
            <label class="governance-form__note">
              <span>治理备注</span>
              <input v-model="governanceForms[record.id].note" type="text" :disabled="savingId === record.id" />
            </label>
            <button type="submit" class="primary-button" :disabled="!canSubmitGovernance(record)">
              {{ savingId === record.id ? '提交中...' : '更新治理状态' }}
            </button>
          </form>
        </article>
      </div>
    </section>
  </section>
</template>

<style scoped>
.model-page,
.model-hero,
.model-panel,
.governance-lane__grid,
.governance-lane__spotlight,
.governance-lane__stream,
.governance-lane__stats,
.governance-lane__events,
.version-compare,
.version-compare__stats,
.registry-overview__grid,
.registry-overview__summary,
.registry-overview__recent,
.registry-overview__stats,
.registry-overview__events,
.registry-overview__event,
.governance-event,
.record-list,
.record-card,
.record-card__meta,
.record-card__notes,
.governance-form,
.filter-grid {
  display: grid;
  gap: var(--space-list);
}

.model-page {
  display: grid;
  gap: 18px;
  padding-bottom: 8px;
}

.model-hero,
.model-panel {
  padding: var(--space-panel);
  border-radius: var(--radius-panel);
  border: 1px solid var(--line);
  background: var(--bg-panel);
  animation: consoleRise 0.5s ease both;
}

.model-hero {
  grid-template-columns: minmax(0, 1.3fr) minmax(260px, 0.8fr);
  position: relative;
  overflow: hidden;
  border-color: var(--line-strong);
  background: var(--bg-panel);
}

/* Staggered console boot per section */
.model-panel:nth-of-type(1) {
  animation-delay: 0.08s;
}

.model-panel:nth-of-type(2) {
  animation-delay: 0.16s;
}

.model-panel:nth-of-type(3) {
  animation-delay: 0.24s;
}

.model-panel:nth-of-type(4) {
  animation-delay: 0.32s;
}

.model-hero h1,
.model-panel h2,
.record-card h3 {
  margin: 0;
  font-family: var(--display);
}

.model-panel h2 {
  color: var(--text-strong);
  font-size: var(--section-title-size);
  line-height: var(--section-title-line-height);
}

.panel-head h3,
.governance-lane__headline h3 {
  margin: 0;
  color: var(--text-strong);
  font-family: var(--display);
  font-size: var(--card-title-size);
  line-height: var(--card-title-line-height);
}

.record-card h3 {
  color: var(--text-strong);
  font-size: var(--card-title-size);
  line-height: var(--card-title-line-height);
}

.model-hero p:last-child,
.record-card__summary,
.record-card__notes p,
.empty-state,
.record-card__transition-guide {
  margin: 0;
  color: var(--text-muted);
  font-size: var(--supporting-text-size);
  line-height: var(--supporting-text-line-height);
}

.record-card__notes p strong {
  color: var(--text-main);
  font-family: var(--body);
  font-weight: 600;
}

.metric-card,
.record-card {
  padding: var(--space-subpanel);
  border-radius: var(--radius-block);
  border: 1px solid var(--line);
  background: var(--bg-panel-soft);
}

.metric-card span,
.record-card__eyebrow,
.record-card__meta span,
.record-card__timeline span,
.filter-grid span,
.governance-form span,
.registry-overview__event span,
.version-compare__id span {
  color: var(--text-faint);
  font-family: var(--mono);
  font-size: var(--field-label-size);
  letter-spacing: var(--field-label-letter-spacing);
  text-transform: uppercase;
}

.record-card__eyebrow {
  color: var(--accent);
}

.model-hero__stats {
  display: grid;
  gap: 12px;
}

/* Numeric readouts -> mono + strong (let the global metric-card win, but keep large scale) */
.metric-card strong {
  display: block;
  margin-top: 8px;
  font-family: var(--mono);
  font-weight: 700;
  font-size: 1.7rem;
  color: var(--text-strong);
}

.panel-head,
.governance-event__head,
.record-card__head,
.record-card__links {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.record-card__head,
.governance-lane__headline,
.panel-head {
  align-items: flex-start;
}

.filter-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.governance-lane__lede,
.registry-overview__lede,
.governance-event p,
.registry-overview__event p,
.version-compare__guide {
  margin: 0;
  color: var(--text-muted);
  font-size: var(--supporting-text-size);
  line-height: var(--supporting-text-line-height);
  overflow-wrap: anywhere;
}

/* IDs / hashes / timestamps -> mono readouts */
.governance-event small,
.registry-overview__event small {
  display: block;
  margin: 0;
  color: var(--text-faint);
  font-family: var(--mono);
  font-size: 0.74rem;
  letter-spacing: 0.02em;
}

.registry-overview__grid {
  grid-template-columns: minmax(0, 1.15fr) minmax(280px, 0.85fr);
}

.registry-overview__stats {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.registry-overview__event {
  gap: 8px;
  padding: var(--space-subpanel);
  border: 1px solid var(--line);
  border-radius: var(--radius-subpanel);
  background: var(--bg-panel-soft);
  color: var(--text-main);
  text-align: left;
  cursor: pointer;
  transition:
    border-color 0.2s ease,
    background 0.2s ease;
}

.registry-overview__event:hover {
  border-color: var(--line-strong);
  background: var(--bg-panel-muted);
}

.registry-overview__event-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.registry-overview__event strong {
  font-family: var(--mono);
  font-weight: 700;
  color: var(--text-strong);
  letter-spacing: 0.02em;
}

.governance-lane {
  border-color: var(--line-strong);
}

.governance-lane__grid {
  grid-template-columns: minmax(0, 1.1fr) repeat(2, minmax(260px, 0.8fr));
}

.governance-lane__spotlight {
  padding: var(--space-card);
  border-radius: var(--radius-block);
  border: 1px solid var(--line);
  background: var(--bg-panel-soft);
}

.governance-lane__headline {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.governance-lane__stats {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.governance-lane__stream {
  padding: var(--space-card);
  border-radius: var(--radius-block);
  border: 1px solid var(--line);
  background: var(--bg-panel-soft);
}

.governance-event {
  gap: 8px;
  padding: var(--space-subpanel);
  border: 1px solid var(--line);
  border-radius: var(--radius-subpanel);
  background: var(--bg-panel-soft);
  transition: border-color 0.2s ease;
}

.governance-event:hover {
  border-color: var(--line-strong);
}

.governance-event__head strong {
  font-family: var(--body);
  font-weight: 600;
  color: var(--text-strong);
}

/* ---- Version comparison: clear mono version ids ---- */
.version-compare {
  gap: 14px;
  padding: var(--space-card);
  border-radius: var(--radius-subpanel);
  border: 1px solid var(--line);
  background: var(--bg-panel-soft);
}

.version-compare__rank {
  font-family: var(--mono);
  font-size: 0.92rem;
  font-weight: 700;
  letter-spacing: 0.04em;
  color: var(--text-muted);
}

.version-compare__ids {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  gap: 12px;
}

.version-compare__id {
  display: grid;
  gap: 6px;
  padding: var(--space-subpanel);
  border-radius: var(--radius-subpanel);
  border: 1px solid var(--line);
  background: var(--bg-panel);
}

.version-compare__id--active {
  border-color: var(--line-warm);
}

.version-compare__id .version-id {
  font-family: var(--mono);
  font-size: 1.02rem;
  font-weight: 700;
  letter-spacing: 0.02em;
  color: var(--text-strong);
  word-break: break-all;
}

.version-compare__id--active .version-id {
  color: var(--accent);
}

.version-compare__id small {
  color: var(--text-faint);
  font-family: var(--mono);
  font-size: 0.72rem;
  letter-spacing: 0.02em;
}

.version-compare__arrow {
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: 1.3rem;
}

.version-compare__stats {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.filter-grid label,
.governance-form label {
  display: grid;
  gap: 8px;
}

.filter-grid select,
.governance-form select,
.governance-form input {
  min-height: var(--field-height);
  border-radius: var(--radius-control);
  border: 1px solid var(--line);
  background: #0e1013;
  color: var(--text-main);
  padding: var(--space-field-x);
}

/* Focused record -> cyan workspace signal (was warm brown) */
.record-card {
  transition: border-color 0.2s ease;
}

.record-card:hover {
  border-color: var(--line-strong);
}

.record-card--focus {
  border-color: var(--line-warm);
}

.record-card--focus .record-card__eyebrow {
  color: var(--accent);
}

.record-card__meta {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.record-card__meta strong {
  font-family: var(--mono);
  font-weight: 600;
  font-size: 0.92rem;
  color: var(--text-strong);
}

.record-card__timeline {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
}

.record-card__timeline span {
  font-family: var(--mono);
  letter-spacing: 0.02em;
  text-transform: none;
  color: var(--text-faint);
}

/* Transition guide -> neutral governance affordance */
.record-card__transition-guide {
  padding: var(--space-subpanel);
  border-radius: var(--radius-subpanel);
  border: 1px solid var(--line);
  background: var(--bg-panel-soft);
}

/* ---- Governance domain status chips ---- */
.status-chip--active {
  color: var(--accent);
  border-color: var(--line-warm);
  background: var(--bg-panel-soft);
}

.status-chip--ghost {
  color: var(--text-muted);
  border-color: var(--line);
}

.ghost-link,
.ghost-button,
.primary-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: var(--control-height);
  padding: var(--space-button);
  border-radius: var(--radius-control);
  font-family: var(--body);
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  text-decoration: none;
  transition:
    border-color 0.2s ease,
    color 0.2s ease;
}

.ghost-link,
.ghost-button {
  border: 1px solid var(--line);
  background: var(--bg-panel-soft);
  color: var(--text-main);
}

.ghost-link:hover,
.ghost-button:hover:not(:disabled) {
  border-color: var(--line-strong);
  color: var(--text-strong);
}

.ghost-button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

/* Primary governance action -> single accent signal */
.primary-button {
  border: 1px solid var(--line-warm);
  color: var(--accent);
  background: var(--bg-panel-soft);
}

.primary-button:hover:not(:disabled) {
  border-color: var(--accent);
  color: var(--text-strong);
}

.primary-button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.governance-form {
  grid-template-columns: minmax(160px, 220px) minmax(0, 1fr) auto;
  align-items: end;
  padding: var(--space-card);
  border-radius: var(--radius-block);
  border: 1px solid var(--line);
  background: var(--bg-panel-soft);
}

@media (max-width: 980px) {
  .model-hero {
    grid-template-columns: 1fr;
  }

  .governance-lane__grid,
  .registry-overview__grid,
  .registry-overview__stats,
  .governance-lane__stats,
  .version-compare__stats,
  .filter-grid,
  .record-card__meta,
  .governance-form {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .panel-head,
  .governance-lane__headline,
  .governance-event__head,
  .record-card__head,
  .record-card__links {
    flex-direction: column;
    align-items: flex-start;
  }

  .version-compare__ids {
    grid-template-columns: 1fr;
  }

  .version-compare__arrow {
    transform: rotate(90deg);
    justify-self: center;
  }
}

.evidence-banner {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 4px;
}

.evidence-banner__hash {
  font-family: var(--mono);
  font-size: 0.74rem;
  color: var(--accent);
  letter-spacing: 0.04em;
}

.evidence-banner__hint {
  font-size: 0.74rem;
  color: var(--text-faint);
}

.governance-lane__stream--evidence {
  grid-column: 1 / -1;
}

.evidence-hash {
  font-family: var(--mono);
  font-size: 0.72rem;
  color: var(--text-faint);
  word-break: break-all;
}

.evidence-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px solid var(--line);
}

.evidence-form label {
  display: grid;
  gap: 6px;
  font-size: 0.78rem;
  color: var(--text-muted);
}

.evidence-form__wide {
  grid-column: 1 / -1;
}

.evidence-form__submit {
  grid-column: 1 / -1;
  justify-self: start;
}

@media (max-width: 720px) {
  .evidence-form {
    grid-template-columns: 1fr;
  }
}
</style>
