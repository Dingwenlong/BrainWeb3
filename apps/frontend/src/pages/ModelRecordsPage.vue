<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { getDatasets, getModelGovernanceLane, getModelRecords, updateModelGovernance } from '../api/client'
import { useActorProfile } from '../composables/useActorProfile'
import { useToast } from '../composables/useToast'
import type {
  AuditEvent,
  ChainBusinessRecord,
  DatasetSummary,
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
} from '../utils/labels'

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
const intakeHint = computed(() => {
  const source = String(route.query.source ?? '')
  if (source === 'training-job' && focusTrainingJobId.value) {
    return `该模型视图来自训练页，已定位到 ${focusTrainingJobId.value} 产出的模型记录。`
  }
  if (source === 'chain-record' && focusModelId.value) {
    return `该模型视图来自链轨迹，已定位到 ${focusModelId.value} 对应的模型记录。`
  }
  if (source === 'audit' && focusModelId.value) {
    return `该模型视图来自审计中心，已定位到 ${focusModelId.value} 对应的模型记录。`
  }
  return ''
})
const heroGuide = computed(() => {
  const role = actorProfile.value.actorRole.toLowerCase()
  if (role === 'admin') {
    return '这里适合从全局视角核对哪些训练结果已经进入模型库，哪些版本已经被激活或归档。'
  }
  if (role === 'owner' || role === 'approver') {
    return '这里适合把机构训练产物收进一个最小模型注册表，再决定是否激活给后续演示使用。'
  }
  return '这里会展示你参与产出的模型记录，便于从训练任务回看模型摘要与治理状态。'
})
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
const comparisonGuide = computed(() => {
  const comparison = versionComparison.value
  const record = focusedRecord.value
  if (!comparison || !record) {
    return ''
  }
  if (comparison.latestVersion) {
    return `当前模型是同数据集里最新的一版，适合拿来作为 ${formatModelGovernanceStatusLabel(record.governanceStatus)} 状态的主候选。`
  }
  if (comparison.latestActiveVersionId) {
    return `当前模型不是最新版本，且目前激活主版本是 ${comparison.latestActiveVersionId}，适合先做差异回看再决定是否切换。`
  }
  return '当前模型所在数据集还没有稳定的激活主版本，适合先在候选版本之间做收口。'
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
const registryGuide = computed(() => {
  if (!records.value.length) {
    return '当前没有模型记录。先完成一条训练任务，再回来查看治理分布。'
  }
  const activeCount = records.value.filter((record) => record.governanceStatus === 'active').length
  const candidateCount = records.value.filter((record) => record.governanceStatus === 'candidate').length
  if (!activeCount) {
    return '当前模型池里没有已激活模型，建议先从候选模型里挑一条进入治理主线。'
  }
  if (candidateCount > activeCount) {
    return '候选模型仍多于已激活模型，适合优先处理最近完成训练但还未收口的版本。'
  }
  return '当前模型池已经形成稳定的激活层，可以继续回看审计和链证明确认治理痕迹。'
})

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
    return
  }

  timelineLoading.value = true
  try {
    const lane = await getModelGovernanceLane(record.id, actorProfile.value)
    governanceSummary.value = lane.summary
    versionComparison.value = lane.comparison
    governanceAudits.value = lane.auditEvents
    governanceChains.value = lane.chainRecords
    relatedModels.value = lane.relatedModels
    chainVisible.value = lane.chainVisible
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
        <p class="section-kicker">Model Registry</p>
        <h1>把训练结果收进一个可治理的模型库。</h1>
        <p>
          成功训练后的产物会自动登记到这里，先以候选模型进入注册表，再由机构侧或管理员决定是否激活或归档。
        </p>
        <p v-if="intakeHint" class="model-hero__hint">{{ intakeHint }}</p>
        <div class="model-hero__guide">
          <span>治理提示</span>
          <strong>{{ heroGuide }}</strong>
        </div>
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
          <p class="section-kicker">Registry Overview</p>
          <h2>治理总览</h2>
          <p class="registry-overview__lede">{{ registryGuide }}</p>
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
              <p class="section-kicker">Recent Governance</p>
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
              <strong>{{ record.id }} · {{ formatModelGovernanceStatusLabel(record.governanceStatus) }}</strong>
              <p>{{ record.modelName }}</p>
              <small>{{ formatTime(record.governedAt) }} · {{ record.lastGovernedBy || '系统同步' }}</small>
            </button>
          </div>
          <div v-else class="empty-state">当前还没有治理完成的模型，第一次激活或归档后这里会出现最近动作。</div>
          
        </article>
      </div>
    </section>

    <section v-if="focusedRecord" class="model-panel glass-panel governance-lane">
      <div class="panel-head">
        <div>
          <p class="section-kicker">Governance Lane</p>
          <h2>模型治理主线</h2>
          <p class="governance-lane__lede">
            当前聚焦 {{ focusedRecord.id }}。这里把模型登记、治理审计和链证明压成一条连续工作线。
          </p>
        </div>
      </div>

      <div class="governance-lane__grid">
        <article class="governance-lane__spotlight">
          <div class="governance-lane__headline">
            <div>
              <p class="record-card__eyebrow">{{ focusedRecord.trainingJobId }} · {{ focusedRecord.datasetId }}</p>
              <h3>{{ focusedRecord.modelName }}</h3>
            </div>
            <span class="status-chip">{{ formatModelGovernanceStatusLabel(focusedRecord.governanceStatus) }}</span>
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
            <span>{{ transitionGuideFor(focusedRecord) }}</span>
          </div>

          <div v-if="versionComparison" class="version-compare">
            <div class="panel-head">
              <div>
                <p class="section-kicker">Version Compare</p>
                <h3>版本对比摘要</h3>
              </div>
            </div>

            <div class="version-compare__stats">
              <article v-for="stat in comparisonStats" :key="stat.label" class="metric-card">
                <span>{{ stat.label }}</span>
                <strong>{{ stat.value }}</strong>
              </article>
            </div>

            <p class="version-compare__guide">{{ comparisonGuide }}</p>

            <div class="record-card__timeline">
              <span>最新版本：{{ versionComparison.latestVersionId || '暂无' }}</span>
              <span>最新完成：{{ formatTime(versionComparison.latestVersionCompletedAt) }}</span>
              <span>当前激活版：{{ versionComparison.latestActiveVersionId || '暂无' }}</span>
              <span>激活治理时间：{{ formatTime(versionComparison.latestActiveGovernedAt) }}</span>
            </div>
          </div>

          <div class="record-card__links">
            <RouterLink class="ghost-link" :to="auditLinkFor(focusedRecord)">打开治理审计</RouterLink>
            <RouterLink class="ghost-link" :to="chainLinkFor(focusedRecord)">打开链证明</RouterLink>
          </div>
        </article>

        <article class="governance-lane__stream">
          <div class="panel-head">
            <div>
              <p class="section-kicker">Audit Trail</p>
              <h3>治理审计</h3>
            </div>
          </div>

          <div v-if="timelineLoading" class="empty-state">治理主线正在加载...</div>
          <div v-else-if="!governanceAudits.length" class="empty-state">
            当前模型没有独立治理审计事件。完成一次激活或归档后，这里会出现对应轨迹。
          </div>
          <div v-else class="governance-lane__events">
            <article v-for="event in governanceAudits" :key="event.id" class="governance-event">
              <div class="governance-event__head">
                <strong>{{ formatAuditActionLabel(event.action) }}</strong>
                <span class="status-chip">{{ formatRequestStatusLabel(event.status) }}</span>
              </div>
              <p>{{ event.detail || '该事件未附带额外说明。' }}</p>
              <small>{{ formatTime(event.createdAt) }} · {{ event.actorId }}</small>
            </article>
          </div>
        </article>

        <article class="governance-lane__stream">
          <div class="panel-head">
            <div>
              <p class="section-kicker">Chain Proof</p>
              <h3>链证明</h3>
            </div>
          </div>

          <div v-if="timelineLoading" class="empty-state">治理主线正在加载...</div>
          <div v-else-if="!chainVisible" class="empty-state">
            当前身份没有独立链轨迹查看权限，可从模型状态和治理审计继续回看这条主线。
          </div>
          <div v-else-if="!governanceChains.length" class="empty-state">
            当前模型没有独立链证明。模型登记或治理上链后，这里会自动形成对应锚点。
          </div>
          <div v-else class="governance-lane__events">
            <article v-for="record in governanceChains" :key="record.id" class="governance-event">
              <div class="governance-event__head">
                <strong>{{ formatChainEventLabel(record.eventType) }}</strong>
                <span class="status-chip">{{ formatChainPolicyLabel(record.anchorPolicy) }}</span>
              </div>
              <p>{{ record.detail || '该记录未附带额外说明。' }}</p>
              <small>{{ formatTime(record.anchoredAt) }} · {{ record.chainTxHash || '等待上链成功' }}</small>
            </article>
          </div>
        </article>

        <article class="governance-lane__stream">
          <div class="panel-head">
            <div>
              <p class="section-kicker">Related Versions</p>
              <h3>同数据集版本</h3>
            </div>
          </div>

          <div v-if="timelineLoading" class="empty-state">治理主线正在加载...</div>
          <div v-else-if="!relatedModels.length" class="empty-state">
            当前数据集下没有其他版本记录。下一次训练产出新模型后，这里会出现同池版本对照。
          </div>
          <div v-else class="governance-lane__events">
            <button
              v-for="record in relatedModels"
              :key="`related-${record.id}`"
              type="button"
              class="registry-overview__event"
              @click="focusGovernanceLane(record.id)"
            >
              <strong>{{ record.id }} · {{ formatModelGovernanceStatusLabel(record.governanceStatus) }}</strong>
              <p>{{ record.modelName }}</p>
              <small>{{ formatTime(record.completedAt) }} · {{ record.trainingJobId }}</small>
            </button>
          </div>
        </article>
      </div>
    </section>

    <section class="model-panel glass-panel">
      <div class="panel-head">
        <div>
          <p class="section-kicker">Filters</p>
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
            <option value="candidate">candidate</option>
            <option value="active">active</option>
            <option value="archived">archived</option>
          </select>
        </label>
      </div>
    </section>

    <section class="model-panel glass-panel">
      <div class="panel-head">
        <div>
          <p class="section-kicker">Registry</p>
          <h2>模型记录</h2>
        </div>
      </div>

      <div v-if="loading" class="empty-state">模型记录正在加载...</div>
      <div v-else-if="!records.length" class="empty-state">
        当前没有模型记录。先完成一条训练任务，再回到这里查看自动登记的模型版本。
      </div>

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
            <span class="status-chip">{{ formatModelGovernanceStatusLabel(record.governanceStatus) }}</span>
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
            <p><strong>指标摘要：</strong>{{ record.metricSummary || '暂无指标摘要。' }}</p>
            <p><strong>结果说明：</strong>{{ record.resultSummary || '暂无结果说明。' }}</p>
            <p><strong>模型引用：</strong>{{ record.artifactRef }}</p>
            <p><strong>治理备注：</strong>{{ record.governanceNote || '暂无治理备注。' }}</p>
          </div>

          <div class="record-card__timeline">
            <span>完成时间：{{ formatTime(record.completedAt) }}</span>
            <span>治理时间：{{ formatTime(record.governedAt) }}</span>
            <span>最近治理人：{{ record.lastGovernedBy || '暂无' }}</span>
          </div>

          <p v-if="canGovern(record)" class="record-card__transition-guide">
            {{ transitionGuideFor(record) }}
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
  padding-bottom: 8px;
}

.model-hero,
.model-panel {
  padding: var(--space-panel);
  border-radius: var(--radius-panel);
  background: var(--panel-gradient);
}

.model-hero {
  grid-template-columns: minmax(0, 1.3fr) minmax(260px, 0.8fr);
}

.model-hero h1,
.model-panel h2,
.record-card h3 {
  margin: 0;
  font-family: var(--display);
}

.model-hero p:last-child,
.record-card__summary,
.record-card__notes p,
.empty-state,
.record-card__transition-guide {
  margin: 0;
  color: var(--text-muted);
  line-height: 1.65;
}

.model-hero__hint,
.model-hero__guide,
.metric-card,
.record-card {
  padding: var(--space-subpanel);
  border-radius: var(--radius-block);
  border: 1px solid var(--line);
  background: var(--panel-soft-gradient);
}

.model-hero__hint {
  margin-top: 12px !important;
}

.model-hero__guide span,
.metric-card span,
.record-card__eyebrow,
.record-card__meta span,
.record-card__timeline span,
.filter-grid span,
.governance-form span {
  color: var(--text-faint);
  font-size: 0.76rem;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.model-hero__stats {
  display: grid;
  gap: 12px;
}

.metric-card strong {
  display: block;
  margin-top: 8px;
  font-family: var(--body);
  font-weight: 700;
  font-size: 1.9rem;
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

.filter-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.governance-lane__lede,
.registry-overview__lede,
.governance-event p,
.governance-event small,
.registry-overview__event p,
.registry-overview__event small,
.version-compare__guide {
  margin: 0;
  color: var(--text-muted);
  line-height: 1.65;
}

.registry-overview__grid {
  grid-template-columns: minmax(0, 1.15fr) minmax(280px, 0.85fr);
}

.registry-overview__stats {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.registry-overview__event {
  padding: var(--space-subpanel);
  border: 1px solid var(--line);
  border-radius: var(--radius-subpanel);
  background: var(--bg-panel-soft);
  color: var(--text-main);
  text-align: left;
  cursor: pointer;
}

.registry-overview__event strong {
  display: block;
  font-family: var(--body);
  font-weight: 700;
}

.governance-lane__grid {
  grid-template-columns: minmax(0, 1.1fr) repeat(2, minmax(260px, 0.8fr));
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

.version-compare {
  padding: var(--space-card);
  border-radius: var(--radius-subpanel);
  border: 1px solid var(--line-warm);
  background: var(--warm-panel-gradient);
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
  background: var(--bg-panel);
  color: var(--text-main);
  padding: var(--space-field-x);
}

.record-card--focus {
  border-color: rgba(156, 107, 54, 0.28);
  box-shadow: inset 0 0 0 1px rgba(156, 107, 54, 0.1);
}

.record-card__meta {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.record-card__timeline {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
}

.record-card__transition-guide {
  padding: var(--space-subpanel);
  border-radius: var(--radius-subpanel);
  border: 1px solid var(--line-warm);
  background: var(--warm-panel-gradient);
}

.ghost-link,
.status-chip,
.ghost-button,
.primary-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: var(--control-height);
  padding: var(--space-button);
  border-radius: var(--radius-pill);
  font-family: var(--body);
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  text-decoration: none;
}

.ghost-link,
.ghost-button,
.status-chip {
  border: 1px solid var(--line);
  background: var(--bg-panel-soft);
  color: var(--text-main);
}

.primary-button {
  border: 1px solid var(--line-warm);
  color: var(--text-main);
  background: var(--button-warm-gradient);
}

.governance-form {
  grid-template-columns: minmax(160px, 220px) minmax(0, 1fr) auto;
  align-items: end;
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
}
</style>
