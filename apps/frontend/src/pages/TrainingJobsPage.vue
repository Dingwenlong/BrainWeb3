<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { createTrainingJob, getDatasets, getModelRecords, getTrainingJobs, refreshTrainingJob } from '../api/client'
import { useActorProfile } from '../composables/useActorProfile'
import { useToast } from '../composables/useToast'
import type { DatasetSummary, ModelRecord, TrainingJob } from '../types/api'
import {
  formatModelGovernanceStatusLabel,
  formatOrganizationLabel,
  formatRequestStatusLabel,
  formatRoleLabel,
  formatTrainingReadinessLabel,
} from '../utils/labels'

const { actorProfile } = useActorProfile()
const { pushToast } = useToast()
const route = useRoute()

const datasets = ref<DatasetSummary[]>([])
const jobs = ref<TrainingJob[]>([])
const modelRecords = ref<ModelRecord[]>([])
const loading = ref(false)
const creating = ref(false)
const refreshingJobId = ref('')

const form = reactive({
  datasetId: 'ds-101',
  modelName: 'Motor Intent Decoder',
  objective: 'cross-site rehearsal',
  algorithm: 'hetero-logistic-regression',
  requestedRounds: 6,
})

const selectedDataset = computed(() => datasets.value.find((item) => item.id === form.datasetId) ?? null)
const eligibleDatasets = computed(() => datasets.value.filter((item) => item.trainingReadiness.toLowerCase().includes('ready')))
const modelRecordByJobId = computed(() =>
  Object.fromEntries(modelRecords.value.map((record) => [record.trainingJobId, record])),
)
const focusJobId = computed(() => (typeof route.query.focusJobId === 'string' ? route.query.focusJobId : ''))
const roleGuide = computed(() => {
  const role = actorProfile.value.actorRole.toLowerCase()
  if (role === 'admin') {
    return {
      title: '监管推进',
      note: '先创建或定位一条任务，再用刷新动作确认当前联邦编排是否顺利收口。',
      emptyJobs: '当前没有训练任务。先创建一条任务，再回到审计中心核对事件。',
      noDataset: '当前没有可训练数据。先上传样板数据，或确认已有数据进入训练就绪状态。',
    }
  }
  if (role === 'owner' || role === 'approver') {
    return {
      title: '机构编排',
      note: '优先把已批准访问的数据带入训练页，确认机构内授权与训练任务能顺畅衔接。',
      emptyJobs: '当前没有训练任务。审批通过后可直接把数据带入这里发起训练。',
      noDataset: '当前没有可训练数据。先审批一条访问申请，或确认目标数据已进入训练就绪状态。',
    }
  }
  return {
    title: '研究执行',
    note: '先确认你手里有已批准、可训练的数据，再创建第一条最小联邦训练任务。',
    emptyJobs: '当前没有训练任务。先去访问申请页申请数据，获批后可直接带入这里发起训练。',
    noDataset: '当前没有可训练数据。请先申请访问，或等待数据进入训练就绪状态。',
  }
})
const intakeHint = computed(() => {
  const source = String(route.query.source ?? '')
  if (!source) {
    return ''
  }
  if (source === 'access-request') {
    return '该任务来自审批结果带入，数据集和用途已预填。'
  }
  if (source === 'dataset-detail') {
    return '该任务来自数据详情页带入，可直接基于当前资产发起训练。'
  }
  if (source === 'audit') {
    return focusJobId.value
      ? `该任务来自审计中心，已定位到 ${focusJobId.value}。`
      : '该任务来自审计中心，可继续追踪训练状态。'
  }
  if (source === 'chain-record') {
    return '该任务来自链轨迹页，可继续核对训练结果与链上记录是否一致。'
  }
  return '该任务参数已由上一页带入。'
})

function applyRoutePrefill() {
  const datasetId = typeof route.query.datasetId === 'string' ? route.query.datasetId : ''
  const modelName = typeof route.query.modelName === 'string' ? route.query.modelName : ''
  const objective = typeof route.query.objective === 'string' ? route.query.objective : ''
  const algorithm = typeof route.query.algorithm === 'string' ? route.query.algorithm : ''
  const requestedRounds = typeof route.query.requestedRounds === 'string' ? Number(route.query.requestedRounds) : NaN

  if (datasetId) {
    form.datasetId = datasetId
  }
  if (modelName) {
    form.modelName = modelName
  }
  if (objective) {
    form.objective = objective
  }
  if (algorithm) {
    form.algorithm = algorithm
  }
  if (Number.isFinite(requestedRounds) && requestedRounds >= 1 && requestedRounds <= 20) {
    form.requestedRounds = requestedRounds
  }
}

function modelRecordFor(job: TrainingJob) {
  return modelRecordByJobId.value[job.id] ?? null
}

function governanceGuide(record: ModelRecord | null) {
  if (!record) {
    return '模型登记仍在同步中，下一次刷新后会出现在治理主线里。'
  }
  if (!record.allowedGovernanceTransitions.length) {
    return '当前治理状态已收口，可直接进入模型治理线回看审计与链证明。'
  }
  return `下一步可切换到 ${record.allowedGovernanceTransitions
    .map((status) => formatModelGovernanceStatusLabel(status))
    .join(' / ')}。`
}

async function syncModelRecords(trainingJobId?: string) {
  const payload = await getModelRecords(actorProfile.value, {
    trainingJobId: trainingJobId || undefined,
  })
  if (trainingJobId) {
    modelRecords.value = [
      ...modelRecords.value.filter((record) => record.trainingJobId !== trainingJobId),
      ...payload,
    ]
    return
  }
  modelRecords.value = payload
}

async function loadPage() {
  loading.value = true
  try {
    const [datasetPayload, jobPayload, modelPayload] = await Promise.all([
      getDatasets(),
      getTrainingJobs(actorProfile.value),
      getModelRecords(actorProfile.value),
    ])
    datasets.value = datasetPayload
    jobs.value = jobPayload
    modelRecords.value = modelPayload
    applyRoutePrefill()
    if (!datasetPayload.some((item) => item.id === form.datasetId)) {
      form.datasetId = datasetPayload[0]?.id ?? ''
    }
  } catch (error) {
    pushToast({
      tone: 'warning',
      title: '训练编排加载失败',
      message: error instanceof Error ? error.message : '请稍后重试。',
    })
  } finally {
    loading.value = false
  }
}

async function submitTrainingJob() {
  creating.value = true
  try {
    const job = await createTrainingJob(actorProfile.value, {
      datasetId: form.datasetId,
      modelName: form.modelName,
      objective: form.objective,
      algorithm: form.algorithm,
      requestedRounds: form.requestedRounds,
    })
    jobs.value = [job, ...jobs.value]
    pushToast({
      tone: 'success',
      title: '训练任务已创建',
      message: `${job.id} 已进入 ${job.orchestrator} 编排队列。`,
    })
  } catch (error) {
    pushToast({
      tone: 'warning',
      title: '训练任务创建失败',
      message: error instanceof Error ? error.message : '请检查输入后重试。',
    })
  } finally {
    creating.value = false
  }
}

async function refreshJob(jobId: string) {
  refreshingJobId.value = jobId
  try {
    const refreshed = await refreshTrainingJob(jobId, actorProfile.value)
    jobs.value = jobs.value.map((job) => (job.id === jobId ? refreshed : job))
    pushToast({
      tone: refreshed.status === 'succeeded' ? 'success' : 'warning',
      title: refreshed.status === 'succeeded' ? '训练任务已完成' : '训练任务状态已刷新',
      message: refreshed.latestMessage || refreshed.resultSummary || '已同步最新编排状态。',
    })
    if (refreshed.status === 'succeeded') {
      await syncModelRecords(jobId)
    }
  } catch (error) {
    pushToast({
      tone: 'warning',
      title: '训练状态刷新失败',
      message: error instanceof Error ? error.message : '请稍后再试。',
    })
  } finally {
    refreshingJobId.value = ''
  }
}

function statusTone(status: string) {
  switch (status) {
    case 'succeeded':
      return 'success'
    case 'failed':
      return 'danger'
    default:
      return 'running'
  }
}

onMounted(() => {
  void loadPage()
})

watch(
  () => route.query,
  () => {
    applyRoutePrefill()
  },
)
</script>

<template>
  <section class="training-page">
    <header class="training-hero glass-panel">
      <div>
        <p class="section-kicker">训练任务</p>
        <h1>联邦训练工作台</h1>
        <p>
          这里只有一条清晰的业务规则：获得访问权限、已经完成存证并满足训练条件的数据，才能进入训练流程。
          当前页面用于发起任务、查看进度和回看结果，不再强调演示式包装。
        </p>
        <p v-if="intakeHint" class="training-hero__hint">{{ intakeHint }}</p>
        <div class="training-hero__guide">
          <span>{{ roleGuide.title }}</span>
          <strong>{{ roleGuide.note }}</strong>
        </div>
      </div>

      <div class="training-hero__stats">
        <article class="metric-card">
          <span>可训练数据集</span>
          <strong>{{ eligibleDatasets.length }}</strong>
        </article>
        <article class="metric-card">
          <span>当前任务</span>
          <strong>{{ jobs.length }}</strong>
        </article>
        <article class="metric-card">
          <span>运行中</span>
          <strong>{{ jobs.filter((job) => job.status === 'running').length }}</strong>
        </article>
      </div>
    </header>

    <div class="training-layout">
      <section class="training-panel glass-panel">
        <div class="panel-head">
          <div>
            <p class="section-kicker">新建任务</p>
            <h2>发起训练任务</h2>
          </div>
          <button type="button" class="ghost-button" :disabled="loading" @click="loadPage">
            刷新数据
          </button>
        </div>

        <form class="training-form" @submit.prevent="submitTrainingJob">
          <label>
            <span>数据集</span>
            <select v-model="form.datasetId" required>
              <option v-for="dataset in datasets" :key="dataset.id" :value="dataset.id">
                {{ dataset.id }} · {{ dataset.title }}
              </option>
            </select>
          </label>

          <label>
            <span>模型名称</span>
            <input v-model="form.modelName" type="text" required />
          </label>

          <label>
            <span>目标说明</span>
            <input v-model="form.objective" type="text" required />
          </label>

          <label>
            <span>算法</span>
            <select v-model="form.algorithm">
              <option value="hetero-logistic-regression">Hetero LR</option>
              <option value="hetero-neural-network">Hetero NN</option>
              <option value="failover-probe">故障探测</option>
            </select>
          </label>

          <label>
            <span>训练轮数</span>
            <input v-model.number="form.requestedRounds" type="number" min="1" max="20" required />
          </label>

          <button type="submit" class="primary-button" :disabled="creating || !form.datasetId">
            {{ creating ? '提交中...' : '创建训练任务' }}
          </button>
        </form>

        <div v-if="selectedDataset" class="dataset-brief">
          <p class="section-kicker">当前数据</p>
          <h3>{{ selectedDataset.title }}</h3>
          <div class="dataset-brief__grid">
            <div>
              <span>归属机构</span>
              <strong>{{ formatOrganizationLabel(selectedDataset.ownerOrganization) }}</strong>
            </div>
            <div>
              <span>训练就绪度</span>
              <strong>{{ formatTrainingReadinessLabel(selectedDataset.trainingReadiness) }}</strong>
            </div>
            <div>
              <span>存证状态</span>
              <strong>{{ selectedDataset.proofStatus }}</strong>
            </div>
          </div>
        </div>
        <div v-else class="dataset-brief dataset-brief--empty">
          <p class="section-kicker">数据状态</p>
          <h3>等待可训练数据</h3>
          <p>{{ roleGuide.noDataset }}</p>
        </div>
      </section>

      <section class="training-panel glass-panel">
        <div class="panel-head">
          <div>
            <p class="section-kicker">任务列表</p>
            <h2>任务看板</h2>
          </div>
          <span class="hint-chip">{{ formatRoleLabel(actorProfile.actorRole) }}</span>
        </div>

        <div v-if="loading" class="empty-state">训练任务正在加载...</div>
        <div v-else-if="!jobs.length" class="empty-state">{{ roleGuide.emptyJobs }}</div>

        <div v-else class="job-list">
          <article v-for="job in jobs" :key="job.id" class="job-card" :class="{ 'job-card--focus': focusJobId === job.id }">
            <header class="job-card__head">
              <div>
                <p class="job-card__eyebrow">{{ job.id }} · {{ job.datasetId }}</p>
                <h3>{{ job.modelName }}</h3>
              </div>
              <span class="status-chip" :class="`status-chip--${statusTone(job.status)}`">
                {{ formatRequestStatusLabel(job.status) }}
              </span>
            </header>

            <p class="job-card__summary">{{ job.objective }}</p>
            <p v-if="focusJobId === job.id" class="job-card__focus-note">该任务由审计中心定位而来。</p>

            <div class="job-card__meta">
              <div>
                <span>发起人</span>
                <strong>{{ job.actorId }} / {{ formatRoleLabel(job.actorRole) }}</strong>
              </div>
              <div>
                <span>编排层</span>
                <strong>{{ job.orchestrator }}</strong>
              </div>
              <div>
                <span>进度</span>
                <strong>{{ job.completedRounds }} / {{ job.requestedRounds }} 轮</strong>
              </div>
              <div>
                <span>算法</span>
                <strong>{{ job.algorithm }}</strong>
              </div>
            </div>

            <div class="job-card__notes">
              <p><strong>最新消息：</strong>{{ job.latestMessage || '暂无状态消息。' }}</p>
              <p><strong>指标摘要：</strong>{{ job.metricSummary || '等待刷新结果。' }}</p>
              <p><strong>结果说明：</strong>{{ job.resultSummary || '任务仍在运行中。' }}</p>
            </div>

            <div v-if="job.status === 'succeeded'" class="job-card__governance">
              <p class="section-kicker">模型治理</p>
              <template v-if="modelRecordFor(job)">
                <div class="job-card__governance-head">
                  <strong>{{ modelRecordFor(job)?.id }} · {{ formatModelGovernanceStatusLabel(modelRecordFor(job)?.governanceStatus) }}</strong>
                  <span>{{ modelRecordFor(job)?.lastGovernedBy || '尚未人工治理' }}</span>
                </div>
                <p>{{ governanceGuide(modelRecordFor(job)) }}</p>
              </template>
              <template v-else>
                <p>{{ governanceGuide(null) }}</p>
              </template>
            </div>

            <div class="job-card__actions">
              <RouterLink
                v-if="job.status === 'succeeded'"
                class="ghost-link"
                :to="{ path: '/model-records', query: { source: 'training-job', trainingJobId: job.id, focusModelId: modelRecordFor(job)?.id || '' } }"
              >
                打开模型库
              </RouterLink>
              <RouterLink
                v-if="job.status === 'succeeded' && modelRecordFor(job)"
                class="ghost-link"
                :to="{ path: '/audits', query: { source: 'model-record', datasetId: job.datasetId, action: 'MODEL_GOVERNANCE_UPDATED', focusModelId: modelRecordFor(job)?.id } }"
              >
                打开治理审计
              </RouterLink>
              <RouterLink
                v-if="job.status === 'succeeded' && modelRecordFor(job)"
                class="ghost-link"
                :to="{ path: '/chain-records', query: { source: 'model-record', datasetId: job.datasetId, eventType: 'MODEL_GOVERNED', focusModelId: modelRecordFor(job)?.id } }"
              >
                打开治理链
              </RouterLink>
              <button
                type="button"
                class="ghost-button"
                :disabled="refreshingJobId === job.id || job.status !== 'running'"
                @click="refreshJob(job.id)"
              >
                {{ refreshingJobId === job.id ? '刷新中...' : '刷新状态' }}
              </button>
              <span class="job-card__stamp">{{ job.createdAt.replace('T', ' ').slice(0, 16) }}</span>
            </div>
          </article>
        </div>
      </section>
    </div>
  </section>
</template>

<style scoped>
.training-page {
  display: grid;
  gap: 18px;
}

.training-hero,
.training-panel {
  display: grid;
  gap: 18px;
  padding: var(--space-panel);
  border-radius: var(--radius-panel);
}

.training-hero {
  grid-template-columns: minmax(0, 1.3fr) minmax(260px, 0.8fr);
  align-items: stretch;
}

.training-hero h1,
.training-panel h2,
.dataset-brief h3,
.job-card h3 {
  margin: 0;
  font-family: var(--display);
}

.training-hero p:last-child,
.job-card__summary,
.job-card__notes p,
.empty-state {
  margin: 0;
  color: var(--text-muted);
  line-height: 1.65;
}

.training-hero__hint {
  margin-top: 12px !important;
  padding: var(--space-subpanel);
  border-radius: var(--radius-subpanel);
  border: 1px solid var(--line);
  background: var(--panel-soft-gradient);
}

.training-hero__guide {
  display: grid;
  gap: 8px;
  margin-top: 14px;
  max-width: 560px;
  padding: var(--space-subpanel);
  border-radius: var(--radius-subpanel);
  border: 1px solid var(--line-warm);
  background: var(--panel-soft-gradient);
}

.training-hero__guide span {
  color: var(--text-faint);
  font-size: 0.74rem;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.training-hero__guide strong {
  font-family: var(--body);
  font-size: 0.94rem;
  line-height: 1.6;
}

.job-card__focus-note {
  margin: -4px 0 0;
  color: var(--amber);
  font-size: 0.88rem;
}

.training-hero__stats {
  display: grid;
  gap: 12px;
}

.metric-card,
.dataset-brief,
.job-card {
  padding: var(--space-card);
  border-radius: var(--radius-block);
  border: 1px solid var(--line);
  background: var(--panel-gradient);
}

.job-card--focus {
  border-color: rgba(156, 107, 54, 0.24);
  box-shadow: inset 0 0 0 1px rgba(156, 107, 54, 0.08);
}

.dataset-brief--empty p:last-child {
  margin: 0;
  color: var(--text-muted);
  line-height: 1.7;
}

.metric-card span,
.dataset-brief__grid span,
.job-card__meta span,
.job-card__eyebrow,
.job-card__stamp {
  color: var(--text-faint);
  font-size: 0.76rem;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.metric-card strong {
  display: block;
  margin-top: 8px;
  font-family: var(--body);
  font-weight: 700;
  font-size: 1.9rem;
}

.training-layout {
  display: grid;
  grid-template-columns: minmax(320px, 420px) minmax(0, 1fr);
  gap: 18px;
}

.panel-head,
.job-card__head,
.job-card__actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.training-form,
.dataset-brief__grid,
.job-list,
.job-card,
.job-card__meta,
.job-card__notes,
.job-card__governance {
  display: grid;
  gap: var(--space-list);
}

.training-form label {
  display: grid;
  gap: 8px;
}

.training-form span {
  color: var(--text-faint);
  font-size: 0.78rem;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.training-form input,
.training-form select {
  min-height: var(--field-height);
  border-radius: var(--radius-control);
  border: 1px solid var(--line);
  background: var(--bg-panel);
  color: var(--text-main);
  padding: var(--space-field-x);
}

.primary-button,
.ghost-button,
.ghost-link {
  min-height: var(--control-height);
  border-radius: var(--radius-pill);
  padding: var(--space-button);
  font-family: var(--body);
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.primary-button {
  border: 1px solid var(--line-warm);
  color: var(--text-strong);
  background: var(--button-warm-gradient);
}

.ghost-button,
.ghost-link {
  border: 1px solid var(--line);
  background: var(--button-soft-gradient);
  color: var(--text-main);
  text-decoration: none;
}

.status-chip,
.hint-chip {
  display: inline-flex;
  align-items: center;
  min-height: 34px;
  padding: 0 12px;
  border-radius: 999px;
  font-family: var(--body);
  font-size: 0.74rem;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.hint-chip,
.status-chip--running {
  background: rgba(49, 87, 102, 0.1);
  border: 1px solid rgba(49, 87, 102, 0.16);
}

.status-chip--success {
  background: rgba(120, 166, 123, 0.12);
  border: 1px solid rgba(120, 166, 123, 0.18);
}

.status-chip--danger {
  background: rgba(178, 85, 76, 0.1);
  border: 1px solid rgba(178, 85, 76, 0.18);
}

.job-card__meta {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.job-card__meta strong,
.dataset-brief__grid strong {
  display: block;
  margin-top: 6px;
}

.job-card__governance {
  padding: var(--space-subpanel);
  border-radius: var(--radius-subpanel);
  border: 1px solid var(--line-warm);
  background: var(--warm-panel-gradient);
}

.job-card__governance p {
  margin: 0;
  color: var(--text-muted);
  line-height: 1.65;
}

.job-card__governance-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.job-card__governance-head strong {
  font-family: var(--body);
  font-weight: 700;
}

.job-card__governance-head span {
  color: var(--text-faint);
  font-size: 0.78rem;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

@media (max-width: 980px) {
  .training-hero,
  .training-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .panel-head,
  .job-card__head,
  .job-card__actions,
  .job-card__governance-head {
    flex-direction: column;
    align-items: flex-start;
  }

  .job-card__meta {
    grid-template-columns: 1fr;
  }
}
</style>
