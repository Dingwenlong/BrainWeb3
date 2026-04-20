<script setup lang="ts">
import { computed, defineAsyncComponent, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import {
  ApiError,
  createAccessRequest,
  getAccessRequests,
  getAudits,
  getBrainActivity,
  getDataset,
  getSystemStatus,
} from '../api/client'
import ActivityTimeline from '../components/ActivityTimeline.vue'
import RegionMetricsPanel from '../components/RegionMetricsPanel.vue'
import { useActorProfile } from '../composables/useActorProfile'
import { useToast } from '../composables/useToast'
import type {
  AccessRequest,
  AuditEvent,
  BrainActivityResponse,
  BrainBand,
  DatasetDetail,
  SystemStatus,
} from '../types/api'
import {
  formatAccessStateLabel,
  formatContractLabel,
  formatAuditActionLabel,
  formatProofStatusLabel,
  formatRequestStatusLabel,
  formatSecondsLabel,
  formatSystemToken,
} from '../utils/labels'

const Brain3DHeatmap = defineAsyncComponent(() => import('../components/Brain3DHeatmap.vue'))

const props = defineProps<{
  datasetId: string
}>()

const { actorProfile } = useActorProfile()
const { pushToast } = useToast()

const bands: BrainBand[] = ['delta', 'theta', 'alpha', 'beta', 'gamma']

const loading = ref(true)
const activityLoading = ref(true)
const sideLoading = ref(true)
const error = ref<string | null>(null)
const accessError = ref<string | null>(null)
const requestSubmitting = ref(false)
const dataset = ref<DatasetDetail | null>(null)
const activity = ref<BrainActivityResponse | null>(null)
const systemStatus = ref<SystemStatus | null>(null)
const accessRequests = ref<AccessRequest[]>([])
const audits = ref<AuditEvent[]>([])
const selectedBand = ref<BrainBand>('alpha')
const frameIndex = ref(0)
const playing = ref(false)
const accessState = ref<'granted' | 'pending' | 'denied' | 'idle'>('idle')
const hoveredRegionCode = ref<string | null>(null)

const activityRequest = reactive({
  windowSize: 2,
  stepSize: 0.5,
  timeStart: 0,
  timeEnd: 30,
})

const accessForm = reactive({
  purpose: '脑区分析',
  requestedDurationHours: 24,
  reason: '需要先查看阿尔法和贝塔频段的脑区活跃情况，再决定是否进入训练预览。',
})

let playbackTimer: number | null = null

const currentFrame = computed(() => activity.value?.frames[frameIndex.value] ?? null)
const frameCount = computed(() => activity.value?.frames.length ?? 0)
const currentTimestamp = computed(() => currentFrame.value?.timestamp ?? activityRequest.timeStart)
const recentAccessRequests = computed(() => accessRequests.value.slice(0, 3))
const isPrivilegedActor = computed(() =>
  ['owner', 'approver', 'admin'].includes(actorProfile.value.actorRole.toLowerCase()),
)
const latestAccessRequest = computed(() => {
  const rows = accessRequests.value.filter(
    (row) => row.actorId === actorProfile.value.actorId || isPrivilegedActor.value,
  )
  return rows[0] ?? null
})
const loadedRangeLabel = computed(
  () => `已载入 ${formatSecondsLabel(activityRequest.timeStart)} - ${formatSecondsLabel(activityRequest.timeEnd)}`,
)

function stopPlayback() {
  if (playbackTimer !== null) {
    window.clearInterval(playbackTimer)
    playbackTimer = null
  }
  playing.value = false
}

function togglePlayback() {
  if (!activity.value?.frames.length) {
    return
  }

  if (playing.value) {
    stopPlayback()
    return
  }

  playing.value = true
  playbackTimer = window.setInterval(() => {
    if (!activity.value?.frames.length) {
      return
    }

    frameIndex.value = (frameIndex.value + 1) % activity.value.frames.length
  }, 900)
}

function formatSeconds(value: number) {
  return formatSecondsLabel(value)
}

function formatBytes(value: number) {
  if (value < 1024) {
    return `${value} B`
  }
  if (value < 1024 * 1024) {
    return `${(value / 1024).toFixed(1)} KB`
  }
  return `${(value / (1024 * 1024)).toFixed(2)} MB`
}

function formatTime(value: string | null) {
  if (!value) {
    return '暂无'
  }
  return new Date(value).toLocaleString()
}

function normalizeRequestedRange() {
  const duration = dataset.value?.durationSeconds ?? 0
  if (!duration) {
    activityRequest.timeStart = 0
    activityRequest.timeEnd = 30
    return
  }

  const minSpan = Math.max(activityRequest.stepSize, 0.5)
  let start = Math.max(0, Math.min(activityRequest.timeStart, duration))
  let end = Math.max(0, Math.min(activityRequest.timeEnd, duration))

  if (end <= start) {
    end = Math.min(duration, start + minSpan)
  }
  if (end - start < minSpan) {
    end = Math.min(duration, start + minSpan)
  }
  if (end > duration) {
    end = duration
    start = Math.max(0, end - minSpan)
  }

  activityRequest.timeStart = Number(start.toFixed(1))
  activityRequest.timeEnd = Number(end.toFixed(1))
}

function resetDefaultRange() {
  const duration = dataset.value?.durationSeconds ?? 30
  activityRequest.timeStart = 0
  activityRequest.timeEnd = Number(Math.min(duration, 30).toFixed(1))
  normalizeRequestedRange()
}

async function loadDataset() {
  loading.value = true
  error.value = null

  try {
    const [datasetDetail, currentSystemStatus] = await Promise.all([
      getDataset(props.datasetId),
      getSystemStatus(),
    ])
    dataset.value = datasetDetail
    systemStatus.value = currentSystemStatus
    resetDefaultRange()
  } catch (loadError) {
    error.value = loadError instanceof Error ? loadError.message : '加载数据集详情失败。'
  } finally {
    loading.value = false
  }
}

async function loadSidePanels() {
  sideLoading.value = true

  try {
    const [requestRows, auditRows] = await Promise.all([
      getAccessRequests(actorProfile.value, {
        datasetId: props.datasetId,
        actorId: isPrivilegedActor.value ? undefined : actorProfile.value.actorId,
      }),
      getAudits(actorProfile.value, {
        datasetId: props.datasetId,
        actorId: isPrivilegedActor.value ? undefined : actorProfile.value.actorId,
      }),
    ])
    accessRequests.value = requestRows
    audits.value = auditRows
  } catch (sideError) {
    accessError.value =
      sideError instanceof Error ? sideError.message : '加载访问状态失败。'
  } finally {
    sideLoading.value = false
  }
}

function refreshAccessStateFromRequests() {
  const latest = latestAccessRequest.value
  if (!latest) {
    accessState.value = 'idle'
    return
  }

  if (latest.status === 'approved') {
    accessState.value = 'granted'
    return
  }
  if (latest.status === 'pending') {
    accessState.value = 'pending'
    return
  }
  accessState.value = 'denied'
}

async function loadActivity() {
  activityLoading.value = true
  accessError.value = null
  stopPlayback()
  normalizeRequestedRange()

  try {
    activity.value = await getBrainActivity(props.datasetId, selectedBand.value, actorProfile.value, {
      windowSize: activityRequest.windowSize,
      stepSize: activityRequest.stepSize,
      timeStart: activityRequest.timeStart,
      timeEnd: activityRequest.timeEnd,
    })
    frameIndex.value = 0
    accessState.value = 'granted'
  } catch (loadError) {
    activity.value = null
    if (loadError instanceof ApiError && loadError.status === 403) {
      accessState.value = latestAccessRequest.value?.status === 'pending' ? 'pending' : 'denied'
      accessError.value = '当前操作者尚未获批，神经活跃度数据已被门禁拦截。'
    } else {
      accessError.value =
        loadError instanceof Error ? loadError.message : '加载脑区活跃度失败。'
    }
  } finally {
    activityLoading.value = false
  }
}

async function loadAll() {
  await loadDataset()
  await loadSidePanels()
  refreshAccessStateFromRequests()
  await loadActivity()
}

async function applyActivityRange() {
  if (!dataset.value) {
    return
  }
  await loadActivity()
}

function handleBandUpdate(band: BrainBand) {
  selectedBand.value = band
}

async function submitAccessRequest() {
  requestSubmitting.value = true
  accessError.value = null

  try {
    await createAccessRequest(actorProfile.value, {
      datasetId: props.datasetId,
      purpose: accessForm.purpose,
      requestedDurationHours: accessForm.requestedDurationHours,
      reason: accessForm.reason,
    })
    await loadSidePanels()
    refreshAccessStateFromRequests()
    pushToast({
      title: '申请已提交',
      message: '访问申请已进入审批队列，可切到审批台或归属方视角继续处理。',
      tone: 'success',
    })
  } catch (submitError) {
    accessError.value =
      submitError instanceof Error ? submitError.message : '创建访问申请失败。'
  } finally {
    requestSubmitting.value = false
  }
}

watch(
  () => [props.datasetId, actorProfile.value.actorId, actorProfile.value.actorRole, actorProfile.value.actorOrg],
  async () => {
    await loadAll()
  },
)

watch(
  () => [selectedBand.value, activityRequest.windowSize, activityRequest.stepSize],
  async () => {
    if (dataset.value) {
      await loadActivity()
    }
  },
)

onMounted(loadAll)

onBeforeUnmount(() => {
  stopPlayback()
})
</script>

<template>
  <div class="detail-page">
    <section class="page-header glass-panel" v-if="dataset">
      <div>
        <RouterLink class="page-header__back" to="/">返回总览</RouterLink>
        <p class="section-kicker">数据集详情</p>
        <h1>{{ dataset.title }}</h1>
        <p class="page-header__lede">{{ dataset.description }}</p>
        <div class="page-header__chips">
          <span class="status-chip">{{ formatProofStatusLabel(dataset.proofStatus) }}</span>
          <span
            class="status-chip"
            :class="{
              'status-chip--warn': accessState === 'pending',
              'status-chip--danger': accessState === 'denied',
            }"
          >
            {{ formatAccessStateLabel(accessState) }}
          </span>
          <span class="status-chip">{{ dataset.format }}</span>
          <span class="status-chip status-chip--ghost">{{ loadedRangeLabel }}</span>
        </div>
      </div>

      <div class="page-header__summary metric-grid">
        <div class="metric-card">
          <span>通道数</span>
          <strong>{{ dataset.channelCount }}</strong>
        </div>
        <div class="metric-card">
          <span>采样率</span>
          <strong>{{ dataset.samplingRate }}Hz</strong>
        </div>
        <div class="metric-card">
          <span>时长</span>
          <strong>{{ formatSeconds(dataset.durationSeconds) }}</strong>
        </div>
        <div class="metric-card">
          <span>文件大小</span>
          <strong>{{ formatBytes(dataset.fileSizeBytes) }}</strong>
        </div>
      </div>
    </section>

    <div v-if="loading" class="loading-state">正在加载数据舱...</div>
    <div v-else-if="error" class="error-state">{{ error }}</div>

    <template v-else-if="dataset">
      <section class="content-layout">
        <div class="content-layout__main">
          <article class="workspace-card glass-panel">
            <div class="workspace-card__header">
              <div>
                <p class="section-kicker">主视图</p>
                <h2 class="section-title">脑模型与热力回放</h2>
              </div>
            </div>

            <Brain3DHeatmap
              :regions="activity?.regions ?? []"
              :frame="currentFrame"
              :band="selectedBand"
              :timestamp="currentTimestamp"
              @hover-region="hoveredRegionCode = $event"
            />
          </article>

          <article class="workspace-card glass-panel">
            <div class="workspace-card__header">
              <div>
                <p class="section-kicker">回放控制</p>
                <h2 class="section-title">时间窗与频段参数</h2>
              </div>
            </div>

            <ActivityTimeline
              :bands="bands"
              :selected-band="selectedBand"
              :window-size="activityRequest.windowSize"
              :step-size="activityRequest.stepSize"
              :time-start="activityRequest.timeStart"
              :time-end="activityRequest.timeEnd"
              :playing="playing"
              :loading="activityLoading"
              :error="accessError"
              :frame-index="frameIndex"
              :frame-count="frameCount"
              :current-timestamp="currentTimestamp"
              :quality-flags="activity?.qualityFlags ?? []"
              :frames="activity?.frames ?? []"
              @update:selected-band="handleBandUpdate"
              @update:window-size="activityRequest.windowSize = $event"
              @update:step-size="activityRequest.stepSize = $event"
              @update:time-start="activityRequest.timeStart = $event"
              @update:time-end="activityRequest.timeEnd = $event"
              @toggle-play="togglePlayback"
              @seek-frame="frameIndex = $event"
              @apply-range="applyActivityRange"
            />
          </article>
        </div>

        <aside class="content-layout__side">
          <article class="workspace-card glass-panel">
            <div class="workspace-card__header">
              <div>
                <p class="section-kicker">即时读数</p>
                <h2 class="section-title">当前脑区指标</h2>
              </div>
            </div>

            <RegionMetricsPanel
              :regions="activity?.regions ?? []"
              :frame="currentFrame"
              :hovered-region-code="hoveredRegionCode"
              :quality-flags="activity?.qualityFlags ?? []"
              :band="selectedBand"
              :timestamp="currentTimestamp"
            />
          </article>

          <article class="workspace-card glass-panel">
            <div class="workspace-card__header">
              <div>
                <p class="section-kicker">数据摘要</p>
                <h2 class="section-title">基础信息</h2>
              </div>
            </div>

            <dl class="detail-list">
              <div>
                <dt>源文件</dt>
                <dd>{{ dataset.originalFilename }}</dd>
              </div>
              <div>
                <dt>采样点数</dt>
                <dd>{{ dataset.sampleCount }}</dd>
              </div>
              <div>
                <dt>归属机构</dt>
                <dd>{{ dataset.ownerOrganization }}</dd>
              </div>
              <div>
                <dt>系统阶段</dt>
                <dd>{{ formatSystemToken(systemStatus?.stage ?? 'bootstrap') }}</dd>
              </div>
            </dl>
          </article>
        </aside>
      </section>

      <section class="info-grid">
        <article class="workspace-card glass-panel">
          <div class="workspace-card__header">
            <div>
              <p class="section-kicker">访问控制</p>
              <h2 class="section-title">访问门禁与申请状态</h2>
            </div>
          </div>

          <div class="access-stage" :class="`access-stage--${accessState}`">
            <strong>{{ formatAccessStateLabel(accessState) }}</strong>
            <p v-if="accessState === 'granted'">当前操作者已可读取脑区活跃度。</p>
            <p v-else-if="accessState === 'pending'">访问申请正在等待归属机构审批。</p>
            <p v-else-if="accessState === 'denied'">需要先提交申请，或切换到归属方/审批人视角处理审批。</p>
            <p v-else>当前还没有访问申请记录。</p>
          </div>

          <form class="access-form" @submit.prevent="submitAccessRequest">
            <label>
              <span>用途</span>
              <input v-model="accessForm.purpose" type="text" />
            </label>
            <label>
              <span>申请时长（小时）</span>
              <input v-model.number="accessForm.requestedDurationHours" type="number" min="1" max="720" />
            </label>
            <label>
              <span>申请原因</span>
              <textarea v-model="accessForm.reason" rows="4"></textarea>
            </label>
            <button type="submit" class="access-form__submit" :disabled="requestSubmitting">
              {{ requestSubmitting ? '提交中...' : '提交访问申请' }}
            </button>
          </form>

          <div v-if="latestAccessRequest" class="access-note">
            最新记录：{{ latestAccessRequest.id }} · {{ formatRequestStatusLabel(latestAccessRequest.status) }}
            <br />
            到期时间：{{ formatTime(latestAccessRequest.expiresAt) }}
          </div>

          <RouterLink class="access-link" to="/access-requests">前往审批台</RouterLink>

          <div class="request-preview" v-if="recentAccessRequests.length">
            <div v-for="row in recentAccessRequests" :key="row.id" class="request-preview__item">
              <div>
                <strong>{{ row.id }}</strong>
                <p>{{ row.actorId }} · {{ row.purpose }}</p>
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
          </div>
        </article>

        <article class="workspace-card glass-panel">
          <div class="workspace-card__header">
            <div>
              <p class="section-kicker">存证信息</p>
              <h2 class="section-title">存证摘要</h2>
            </div>
          </div>
          <dl class="proof-list">
            <div>
              <dt>链提供方</dt>
              <dd>{{ formatSystemToken(dataset.proof.chainProvider) }}</dd>
            </div>
            <div>
              <dt>链群组</dt>
              <dd>{{ dataset.proof.chainGroup }}</dd>
            </div>
            <div>
              <dt>合约名称</dt>
              <dd>{{ formatContractLabel(dataset.proof.contractName) }}</dd>
            </div>
            <div>
              <dt>合约地址</dt>
              <dd>{{ dataset.proof.contractAddress }}</dd>
            </div>
            <div>
              <dt>SM3 Hash</dt>
              <dd>{{ dataset.proof.sm3Hash }}</dd>
            </div>
            <div>
              <dt>存储引用</dt>
              <dd>{{ dataset.proof.offChainReference }}</dd>
            </div>
            <div>
              <dt>内容引用</dt>
              <dd>{{ dataset.proof.ipfsCid }}</dd>
            </div>
            <div>
              <dt>链上交易</dt>
              <dd>{{ dataset.proof.chainTxHash }}</dd>
            </div>
            <div>
              <dt>DID Holder</dt>
              <dd>{{ dataset.proof.didHolder }}</dd>
            </div>
            <div>
              <dt>访问策略</dt>
              <dd>{{ dataset.proof.accessPolicy }}</dd>
            </div>
            <div>
              <dt>审计状态</dt>
              <dd>{{ dataset.proof.auditState }}</dd>
            </div>
          </dl>
        </article>

        <article class="workspace-card glass-panel">
          <div class="workspace-card__header">
            <div>
              <p class="section-kicker">审计记录</p>
              <h2 class="section-title">审计追踪</h2>
            </div>
          </div>

          <div v-if="sideLoading" class="loading-state">正在加载访问台账...</div>
          <template v-else>
            <div class="audit-timeline" v-if="audits.length">
              <div v-for="event in audits.slice(0, 6)" :key="event.id" class="audit-timeline__item">
                <div class="audit-timeline__rail">
                  <span class="audit-timeline__dot"></span>
                </div>
                <div class="audit-timeline__card">
                  <div class="audit-timeline__headline">
                    <strong>{{ formatAuditActionLabel(event.action) }}</strong>
                    <span>{{ formatRequestStatusLabel(event.status) }}</span>
                  </div>
                  <p>{{ event.detail?.replace('brain-activity', '脑区活跃度接口') ?? '暂无详细说明。' }}</p>
                  <time>{{ formatTime(event.createdAt) }}</time>
                </div>
              </div>
            </div>
            <div v-else class="empty-state">当前视角下还没有审计事件。</div>
          </template>
        </article>
      </section>
    </template>
  </div>
</template>

<style scoped>
.detail-page {
  display: grid;
  gap: 18px;
}

.page-header {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(320px, 0.9fr);
  gap: 18px;
  align-items: end;
  padding: 22px 24px;
  border-radius: 22px;
}

.page-header__back {
  display: inline-flex;
  margin-bottom: 12px;
  color: var(--text-muted);
  text-decoration: none;
  font-family: var(--display);
  font-size: 0.8rem;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.page-header h1 {
  margin: 0;
  font-family: var(--display);
  font-size: clamp(2rem, 3vw, 2.8rem);
  line-height: 1;
}

.page-header__lede {
  margin: 12px 0 0;
  max-width: 60ch;
  color: var(--text-muted);
}

.page-header__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 16px;
}

.page-header__summary {
  align-self: stretch;
}

.content-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(320px, 0.8fr);
  gap: 18px;
}

.content-layout__main,
.content-layout__side,
.info-grid {
  display: grid;
  gap: 18px;
}

.info-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.workspace-card {
  padding: 20px;
  border-radius: 20px;
}

.workspace-card__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.detail-list,
.proof-list {
  display: grid;
  gap: 12px;
}

.detail-list div,
.proof-list div {
  padding: 12px 14px;
  border-radius: 14px;
  border: 1px solid var(--line);
  background: var(--bg-panel-soft);
}

.detail-list dt,
.proof-list dt {
  color: var(--text-faint);
  font-size: 0.74rem;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.detail-list dd,
.proof-list dd {
  margin: 6px 0 0;
  color: var(--text-main);
  word-break: break-word;
}

.access-form__submit {
  border: 1px solid var(--line-warm);
  border-radius: 999px;
  min-height: 42px;
  padding: 0 16px;
  background: linear-gradient(180deg, rgba(235, 178, 102, 0.2), rgba(235, 178, 102, 0.12));
  color: var(--text-main);
  font-family: var(--display);
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.access-form {
  display: grid;
  gap: 12px;
  margin-top: 18px;
}

.access-form label {
  display: grid;
  gap: 8px;
}

.access-form span {
  color: var(--text-muted);
  font-size: 0.78rem;
  text-transform: uppercase;
  letter-spacing: 0.12em;
}

.access-form input,
.access-form textarea {
  width: 100%;
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 10px 12px;
  background: rgba(8, 18, 25, 0.94);
  color: var(--text-main);
}

.access-stage {
  padding: 16px 18px;
  border-radius: 14px;
  border: 1px solid var(--line);
  background: var(--bg-panel-soft);
}

.access-stage strong {
  font-family: var(--display);
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.access-stage p,
.access-note {
  margin: 10px 0 0;
  color: var(--text-muted);
}

.access-link {
  display: inline-flex;
  margin-top: 14px;
  color: var(--accent);
  text-decoration: none;
  font-family: var(--display);
  letter-spacing: 0.08em;
  text-transform: uppercase;
  font-size: 0.8rem;
}

.request-preview {
  display: grid;
  gap: 10px;
  margin-top: 18px;
}

.request-preview__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 14px;
  background: var(--bg-panel-soft);
  border: 1px solid var(--line);
}

.request-preview__item strong {
  font-family: var(--display);
}

.request-preview__item p {
  margin: 4px 0 0;
  color: var(--text-muted);
  font-size: 0.84rem;
}

.access-stage--granted {
  border-color: var(--line-strong);
}

.access-stage--pending {
  border-color: var(--line-warm);
}

.access-stage--denied {
  border-color: rgba(242, 126, 126, 0.28);
}

.audit-timeline {
  display: grid;
  gap: 12px;
}

.audit-timeline__item {
  display: grid;
  grid-template-columns: 26px minmax(0, 1fr);
  gap: 10px;
}

.audit-timeline__rail {
  position: relative;
  display: grid;
  justify-items: center;
}

.audit-timeline__rail::after {
  content: '';
  position: absolute;
  top: 8px;
  bottom: -18px;
  width: 1px;
  background: linear-gradient(180deg, rgba(119, 235, 237, 0.36), transparent);
}

.audit-timeline__item:last-child .audit-timeline__rail::after {
  display: none;
}

.audit-timeline__dot {
  width: 12px;
  height: 12px;
  margin-top: 6px;
  border-radius: 999px;
  background: radial-gradient(circle, var(--amber), var(--accent));
  box-shadow: 0 0 14px rgba(119, 235, 237, 0.42);
}

.audit-timeline__card {
  display: grid;
  gap: 0;
  padding: 14px 16px;
  border-radius: 14px;
  background: var(--bg-panel-soft);
  border: 1px solid var(--line);
}

.audit-timeline__headline {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.audit-timeline__headline span {
  color: var(--amber);
  font-family: var(--display);
  font-size: 0.78rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.audit-timeline__card strong {
  font-family: var(--display);
}

.audit-timeline__card p {
  margin: 5px 0 0;
  color: var(--text-muted);
  font-size: 0.88rem;
}

.audit-timeline__card time {
  margin-top: 10px;
  color: var(--text-muted);
  font-size: 0.76rem;
  letter-spacing: 0.04em;
}

@media (max-width: 1040px) {
  .page-header,
  .content-layout,
  .info-grid {
    grid-template-columns: 1fr;
  }
}
</style>
