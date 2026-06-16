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
import { canInspectChainRecords } from '../utils/permissions'

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
const canInspectChainWorkspace = computed(() => canInspectChainRecords(actorProfile.value.actorRole))
const roleGuide = computed(() => {
  const role = actorProfile.value.actorRole.toLowerCase()
  if (role === 'admin') {
    return {
      title: '监管推进',
      emptyJobs: '暂无训练任务',
      noDataset: '暂无可训练数据',
    }
  }
  if (role === 'owner' || role === 'approver') {
    return {
      title: '机构编排',
      emptyJobs: '暂无训练任务',
      noDataset: '暂无可训练数据',
    }
  }
  return {
    title: '研究执行',
    emptyJobs: '暂无训练任务',
    noDataset: '暂无可训练数据',
  }
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
    return '等待模型登记'
  }
  if (!record.allowedGovernanceTransitions.length) {
    return '当前状态已收口'
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
      <div class="training-hero__copy">
        <div class="training-hero__masthead">
          <p class="section-kicker">训练任务</p>
          <span class="training-hero__stamp">{{ roleGuide.title }}</span>
        </div>
        <h1 class="page-main-heading">联邦训练工作台</h1>
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
        <article class="metric-card metric-card--live">
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
              <option value="hetero-logistic-regression">异构逻辑回归</option>
              <option value="hetero-neural-network">异构神经网络</option>
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
          <article v-for="job in jobs" :key="job.id" class="job-card" :class="[`job-card--${statusTone(job.status)}`, { 'job-card--focus': focusJobId === job.id }]">
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

            <div class="job-card__progress">
              <div class="job-card__progress-head">
                <span>训练轮次</span>
                <strong>{{ job.completedRounds }} / {{ job.requestedRounds }}</strong>
              </div>
              <div class="job-card__progress-track" role="presentation">
                <div
                  class="job-card__progress-fill"
                  :style="{ width: `${Math.min(100, Math.round((job.completedRounds / Math.max(1, job.requestedRounds)) * 100))}%` }"
                ></div>
              </div>
            </div>

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
              <p v-if="job.latestMessage"><strong>最新消息：</strong>{{ job.latestMessage }}</p>
              <p v-if="job.metricSummary"><strong>指标摘要：</strong>{{ job.metricSummary }}</p>
              <p v-if="job.resultSummary"><strong>结果说明：</strong>{{ job.resultSummary }}</p>
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
                v-if="canInspectChainWorkspace && job.status === 'succeeded' && modelRecordFor(job)"
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
  animation: consoleRise 0.5s ease both;
}

.training-hero {
  position: relative;
  overflow: hidden;
  grid-template-columns: minmax(0, 1.3fr) minmax(260px, 0.8fr);
  align-items: stretch;
}

.training-hero::after {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
  background:
    radial-gradient(120% 130% at 0% 0%, rgba(52, 225, 214, 0.08), transparent 46%),
    radial-gradient(120% 140% at 100% 100%, rgba(160, 123, 255, 0.07), transparent 50%);
}

.training-panel {
  animation-delay: 0.08s;
}

.training-panel:nth-of-type(2) {
  animation-delay: 0.14s;
}

.training-hero__copy {
  position: relative;
  z-index: 1;
  display: grid;
  gap: 12px;
  align-content: start;
}

.training-hero__masthead {
  display: flex;
  align-items: center;
  gap: 12px;
}

.training-hero__stamp {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 12px;
  border-radius: var(--radius-pill);
  border: 1px solid var(--line-warm);
  background: rgba(52, 225, 214, 0.08);
  color: var(--accent-strong);
  font-family: var(--mono);
  font-size: 0.7rem;
  font-weight: 600;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.training-hero h1,
.training-panel h2,
.dataset-brief h3,
.job-card h3 {
  margin: 0;
  font-family: var(--display);
}

.training-panel h2 {
  color: var(--text-strong);
  font-size: var(--section-title-size);
  line-height: var(--section-title-line-height);
}

.dataset-brief h3,
.job-card h3 {
  color: var(--text-strong);
  font-size: var(--card-title-size);
  line-height: var(--card-title-line-height);
}

.training-hero__lede,
.job-card__summary,
.job-card__notes p,
.empty-state {
  margin: 0;
  color: var(--text-muted);
  font-size: var(--supporting-text-size);
  line-height: var(--supporting-text-line-height);
}

.training-hero__lede {
  max-width: 52ch;
}

.training-hero__stats {
  position: relative;
  z-index: 1;
  display: grid;
  gap: 12px;
  align-content: center;
}

.metric-card,
.dataset-brief,
.job-card {
  padding: var(--space-card);
  border-radius: var(--radius-block);
  border: 1px solid var(--line);
  background: var(--panel-gradient);
}

.metric-card {
  border-color: var(--line-warm);
  background: var(--warm-panel-gradient);
  box-shadow:
    inset 0 0 0 1px rgba(52, 225, 214, 0.06),
    0 0 24px rgba(52, 225, 214, 0.05);
}

.metric-card--live {
  position: relative;
}

.metric-card--live::before {
  content: '';
  position: absolute;
  top: 16px;
  right: 16px;
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: var(--accent);
  box-shadow: 0 0 10px var(--accent);
}

.dataset-brief {
  background: var(--panel-soft-gradient);
}

.job-card {
  position: relative;
  overflow: hidden;
  transition:
    border-color 0.22s ease,
    box-shadow 0.22s ease,
    transform 0.22s ease;
}

.job-card::before {
  content: '';
  position: absolute;
  inset: 0 auto 0 0;
  width: 3px;
  background: var(--line-strong);
}

.job-card--running::before {
  background: linear-gradient(180deg, var(--accent), var(--accent-2));
  box-shadow: 0 0 14px rgba(52, 225, 214, 0.45);
}

.job-card--success::before {
  background: var(--accent-strong);
  box-shadow: 0 0 12px rgba(52, 225, 214, 0.4);
}

.job-card--danger::before {
  background: var(--danger);
  box-shadow: 0 0 12px var(--danger-soft);
}

.job-card:hover {
  border-color: rgba(52, 225, 214, 0.32);
  box-shadow: 0 0 24px rgba(52, 225, 214, 0.1);
}

.job-card--danger:hover {
  border-color: rgba(255, 97, 115, 0.34);
  box-shadow: 0 0 24px var(--danger-soft);
}

.job-card--focus {
  border-color: rgba(52, 225, 214, 0.45);
  box-shadow:
    inset 0 0 0 1px rgba(52, 225, 214, 0.18),
    0 0 28px rgba(52, 225, 214, 0.14);
}

.dataset-brief--empty p:last-child {
  margin: 0;
  color: var(--text-muted);
  line-height: 1.7;
}

.metric-card span,
.dataset-brief__grid span,
.job-card__meta span,
.job-card__progress-head span,
.job-card__eyebrow,
.job-card__stamp {
  color: var(--text-faint);
  font-size: 0.76rem;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.job-card__eyebrow,
.job-card__stamp {
  font-family: var(--mono);
  color: var(--text-muted);
}

.metric-card strong {
  display: block;
  margin-top: 8px;
  font-family: var(--mono);
  font-weight: 700;
  font-size: clamp(1.8rem, 3vw, 2.3rem);
  color: var(--text-strong);
}

.training-layout {
  display: grid;
  grid-template-columns: minmax(320px, 420px) minmax(0, 1fr);
  gap: 18px;
}

.panel-head,
.job-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.job-card__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
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

.job-list {
  gap: 14px;
}

.job-card {
  animation: consoleRise 0.5s ease both;
}

.job-card:nth-child(1) { animation-delay: 0.06s; }
.job-card:nth-child(2) { animation-delay: 0.13s; }
.job-card:nth-child(3) { animation-delay: 0.2s; }
.job-card:nth-child(4) { animation-delay: 0.27s; }
.job-card:nth-child(n + 5) { animation-delay: 0.32s; }

.training-form label {
  display: grid;
  gap: 8px;
}

.training-form span {
  color: var(--text-faint);
  font-size: var(--field-label-size);
  letter-spacing: var(--field-label-letter-spacing);
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

.training-form input[type='number'] {
  font-family: var(--mono);
}

.primary-button,
.ghost-button,
.ghost-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: var(--control-height);
  border-radius: var(--radius-pill);
  padding: var(--space-button);
  font-family: var(--body);
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    color 0.2s ease;
}

.primary-button {
  border: 1px solid var(--line-warm);
  color: var(--text-strong);
  background: var(--button-warm-gradient);
  box-shadow:
    0 14px 28px rgba(0, 0, 0, 0.4),
    0 0 20px rgba(52, 225, 214, 0.14);
}

.primary-button:hover:not(:disabled) {
  border-color: rgba(52, 225, 214, 0.6);
  box-shadow:
    0 16px 32px rgba(0, 0, 0, 0.46),
    0 0 28px rgba(52, 225, 214, 0.28);
}

.primary-button:disabled {
  opacity: 0.55;
  cursor: progress;
}

.ghost-button,
.ghost-link {
  border: 1px solid var(--line);
  background: var(--button-soft-gradient);
  color: var(--text-main);
  text-decoration: none;
}

.ghost-button:hover:not(:disabled),
.ghost-link:hover {
  border-color: rgba(52, 225, 214, 0.34);
  color: var(--text-strong);
  box-shadow: 0 0 18px rgba(52, 225, 214, 0.08);
}

.ghost-button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.job-card__stamp {
  margin-left: auto;
}

.hint-chip {
  display: inline-flex;
  align-items: center;
  min-height: 34px;
  padding: 0 12px;
  border-radius: var(--radius-pill);
  border: 1px solid var(--line);
  background: var(--bg-panel-soft);
  color: var(--text-main);
  font-family: var(--mono);
  font-size: 0.74rem;
  font-weight: 600;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

/* Domain status mapping: running=cyan (live), succeeded=cyan-strong, failed=danger (global) */
.status-chip--running {
  color: var(--accent);
  border-color: var(--line-warm);
}

.status-chip--running::before {
  animation: signalPulse 1.8s ease-in-out infinite;
}

.status-chip--success {
  color: var(--accent-strong);
  border-color: var(--line-warm);
}

.job-card__progress {
  display: grid;
  gap: 8px;
}

.job-card__progress-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.job-card__progress-head strong {
  font-family: var(--mono);
  font-weight: 700;
  color: var(--text-strong);
}

.job-card__progress-track {
  position: relative;
  height: 6px;
  border-radius: 999px;
  overflow: hidden;
  background: var(--bg-panel-muted);
  border: 1px solid var(--line);
}

.job-card__progress-fill {
  position: absolute;
  inset: 0 auto 0 0;
  border-radius: 999px;
  background: linear-gradient(90deg, var(--accent), var(--accent-2));
  box-shadow: 0 0 12px rgba(52, 225, 214, 0.4);
  transition: width 0.5s ease;
}

.job-card--success .job-card__progress-fill {
  background: var(--accent-strong);
}

.job-card--danger .job-card__progress-fill {
  background: linear-gradient(90deg, var(--danger), var(--amber));
  box-shadow: 0 0 12px var(--danger-soft);
}

.job-card__meta {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.job-card__meta div {
  padding: var(--space-subpanel);
  border-radius: var(--radius-subpanel);
  border: 1px solid var(--line);
  background: var(--bg-panel-soft);
}

.job-card__meta strong,
.dataset-brief__grid strong {
  display: block;
  margin-top: 6px;
  font-family: var(--mono);
  color: var(--text-strong);
}

.dataset-brief__grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.dataset-brief__grid div {
  padding: var(--space-subpanel);
  border-radius: var(--radius-subpanel);
  border: 1px solid var(--line);
  background: var(--bg-panel-soft);
}

.job-card__notes p {
  font-size: var(--supporting-text-size);
}

.job-card__notes strong {
  color: var(--text-main);
}

.job-card__governance {
  padding: var(--space-subpanel);
  border-radius: var(--radius-subpanel);
  border: 1px solid var(--line-warm);
  background: var(--warm-panel-gradient);
  box-shadow: inset 0 0 0 1px rgba(52, 225, 214, 0.05);
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
  font-family: var(--mono);
  font-weight: 700;
  color: var(--text-strong);
}

.job-card__governance-head span {
  color: var(--text-faint);
  font-family: var(--mono);
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

  .job-card__meta,
  .dataset-brief__grid {
    grid-template-columns: 1fr;
  }

  .job-card__stamp {
    margin-left: 0;
  }
}
</style>
