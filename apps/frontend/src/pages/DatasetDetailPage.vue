<script setup lang="ts">
import { computed, defineAsyncComponent, onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import {
  getAccessRequests,
  getAudits,
  getDataset,
  getOrganizationIdentity,
  retryDatasetFinalization,
  getSystemStatus,
  verifyCredential,
} from '../api/client'
import ActivityTimeline from '../components/ActivityTimeline.vue'
import RegionMetricsPanel from '../components/RegionMetricsPanel.vue'
import HashValue from '../components/HashValue.vue'
import { useDatasetAccessWorkflow } from '../composables/useDatasetAccessWorkflow'
import { useDatasetActivity } from '../composables/useDatasetActivity'
import { toErrorMessage, useAsyncView } from '../composables/useAsyncView'
import { useActorProfile } from '../composables/useActorProfile'
import { useToast } from '../composables/useToast'
import type {
  AccessRequest,
  AuditEvent,
  BrainBand,
  CredentialVerificationResult,
  DatasetDetail,
  OrganizationIdentity,
  SystemStatus,
} from '../types/api'
import {
  formatAccessStateLabel,
  formatContractLabel,
  formatAuditActionLabel,
  formatChainEventLabel,
  formatDestructionStatusLabel,
  formatIdentityStatusLabel,
  formatIdentityStatusSourceLabel,
  formatProofStatusLabel,
  formatRequestStatusLabel,
  formatSecondsLabel,
  formatSignalSourceLabel,
  formatSystemToken,
  formatUploadStatusLabel,
} from '../utils/labels'

const Brain3DHeatmap = defineAsyncComponent(() => import('../components/Brain3DHeatmap.vue'))

const props = defineProps<{
  datasetId: string
}>()

const { actorProfile } = useActorProfile()
const { pushToast } = useToast()

const bands: BrainBand[] = ['delta', 'theta', 'alpha', 'beta', 'gamma']

const { loading, error, run: runMainLoad, setErrorMessage: setPageError } = useAsyncView({
  initialLoading: true,
})
const { loading: sideLoading, error: sideError, run: runSideLoad } = useAsyncView({
  initialLoading: true,
})
const retrySubmitting = ref(false)
const dataset = ref<DatasetDetail | null>(null)
const systemStatus = ref<SystemStatus | null>(null)
const ownerIdentity = ref<OrganizationIdentity | null>(null)
const ownerCredentialVerification = ref<CredentialVerificationResult | null>(null)
const accessRequests = ref<AccessRequest[]>([])
const audits = ref<AuditEvent[]>([])
const hoveredRegionCode = ref<string | null>(null)
const cortexOpacity = ref(0.94)
const heatContrast = ref(1)
const isPrivilegedActor = computed(() =>
  ['owner', 'approver', 'admin'].includes(actorProfile.value.actorRole.toLowerCase()),
)
const {
  accessForm,
  accessState,
  latestAccessRequest,
  recentAccessRequests,
  requestSubmitting,
  requestError,
  refreshAccessStateFromRequests,
  submitAccessRequest,
} = useDatasetAccessWorkflow({
  datasetId: props.datasetId,
  actorProfile,
  isPrivilegedActor,
  accessRequests,
  reloadSidePanels: loadSidePanels,
  pushToast,
})
const {
  activity,
  activityError,
  activityLoading,
  activityRequest,
  applyActivityRange,
  currentFrame,
  currentTimestamp,
  frameCount,
  frameIndex,
  handleBandUpdate,
  loadedRangeLabel,
  loadActivity,
  playing,
  resetDefaultRange,
  seekFrame,
  selectedBand,
  togglePlayback,
} = useDatasetActivity({
  datasetId: props.datasetId,
  actorProfile,
  latestAccessRequest,
  getDurationSeconds: () => dataset.value?.durationSeconds ?? 0,
  onAccessStateChange: (state) => {
    accessState.value = state
  },
})
const timelineError = computed(() => activityError.value)
const canRetryUploadFinalization = computed(() => {
  if (!dataset.value?.retryAllowed) {
    return false
  }
  return ['owner', 'approver', 'admin'].includes(actorProfile.value.actorRole.toLowerCase())
})
const canLaunchTraining = computed(() => {
  if (!dataset.value) {
    return false
  }
  const destructionReady = dataset.value.destructionStatus === 'active'
  const proofReady = dataset.value.proofStatus === 'notarized'
  const readinessReady = dataset.value.trainingReadiness.toLowerCase().includes('ready')
  const sameOrgPrivileged =
    isPrivilegedActor.value && actorProfile.value.actorOrg.toLowerCase() === dataset.value.ownerOrganization.toLowerCase()
  return destructionReady && proofReady && readinessReady && (sameOrgPrivileged || accessState.value === 'granted')
})
const proofMatchesOwnerDid = computed(() => {
  if (!dataset.value || !ownerIdentity.value) {
    return false
  }
  return dataset.value.proof.didHolder === ownerIdentity.value.organizationDid
})
const trainingLink = computed(() => ({
  path: '/training-jobs',
  query: {
    source: 'dataset-detail',
    datasetId: props.datasetId,
    modelName: `Federated Run ${props.datasetId.toUpperCase()}`,
    objective: selectedBand.value === 'alpha' ? 'alpha-band rehearsal' : `${selectedBand.value}-band rehearsal`,
    requestedRounds: '6',
  },
}))

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

function formatOpacityPercent(value: number) {
  return `${Math.round(value * 100)}%`
}

function formatContrastPercent(value: number) {
  return `${Math.round(value * 100)}%`
}

function resetCortexOpacity() {
  cortexOpacity.value = 0.94
}

function resetHeatContrast() {
  heatContrast.value = 1
}

function resetBrainVisuals() {
  resetCortexOpacity()
  resetHeatContrast()
}

function formatHistoryTransition(previousStatus: string | null, nextStatus: string) {
  if (!previousStatus) {
    return formatIdentityStatusLabel(nextStatus)
  }
  return `${formatIdentityStatusLabel(previousStatus)} -> ${formatIdentityStatusLabel(nextStatus)}`
}

async function loadDataset() {
  const payload = await runMainLoad(async () => {
    const [datasetDetail, currentSystemStatus] = await Promise.all([
      getDataset(props.datasetId),
      getSystemStatus(),
    ])
    const organizationIdentity = await getOrganizationIdentity(datasetDetail.ownerOrganization)
    const organizationVerification = await verifyCredential({
      id: organizationIdentity.credential.id,
      type: organizationIdentity.credential.type,
      issuerDid: organizationIdentity.credential.issuerDid,
      holderDid: organizationIdentity.credential.holderDid,
      subjectDid: organizationIdentity.credential.subjectDid,
      subjectType: organizationIdentity.credential.subjectType,
      issuedAt: organizationIdentity.credential.issuedAt,
      expiresAt: organizationIdentity.credential.expiresAt,
      proof: organizationIdentity.credential.proof,
      credentialStatus: organizationIdentity.credential.credentialStatus,
      claims: organizationIdentity.credential.claims,
    })

    return {
      datasetDetail,
      currentSystemStatus,
      organizationIdentity,
      organizationVerification,
    }
  }, '加载数据集详情失败。')

  if (!payload) {
    return
  }

  dataset.value = payload.datasetDetail
  systemStatus.value = payload.currentSystemStatus
  ownerIdentity.value = payload.organizationIdentity
  ownerCredentialVerification.value = payload.organizationVerification
  resetDefaultRange()
}

async function loadSidePanels() {
  const payload = await runSideLoad(async () => {
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

    return {
      requestRows,
      auditRows,
    }
  }, '加载访问状态失败。')

  if (!payload) {
    return
  }

  accessRequests.value = payload.requestRows
  audits.value = payload.auditRows
}

async function loadAll() {
  await loadDataset()
  await loadSidePanels()
  refreshAccessStateFromRequests()
  await loadActivity()
}

async function retryUploadFinalization() {
  if (!dataset.value) {
    return
  }

  retrySubmitting.value = true
  setPageError(null)

  try {
    dataset.value = await retryDatasetFinalization(dataset.value.id)
    await loadSidePanels()
    refreshAccessStateFromRequests()
    pushToast({
      title: '补偿收口完成',
      message: `${dataset.value.id} 已重新完成链上收口与状态更新。`,
      tone: 'success',
    })
  } catch (retryError) {
    setPageError(toErrorMessage(retryError, '重新收口失败。'))
  } finally {
    retrySubmitting.value = false
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
</script>

<template>
  <div class="detail-page">
    <section class="page-header glass-panel" v-if="dataset">
      <div class="page-header__lead">
        <RouterLink class="page-header__back" to="/">返回总览</RouterLink>
        <div class="page-header__title-line">
          <p class="section-kicker">数据集详情</p>
          <span class="page-header__id">{{ dataset.id }}</span>
        </div>
        <h1 class="page-main-heading">{{ dataset.title }}</h1>
        <p class="page-main-lede">
          在这里查看这份脑电数据的脑区活跃度，并管理它的访问与溯源。一般先看「脑区热力图」；若提示无权限，先到「访问门禁」提交访问申请。其余面板（存证摘要、授权与训练记录、审计追踪等）记录这份数据的链上存证与操作轨迹，供核验与追溯。
        </p>
        <div class="page-header__chips">
          <span class="status-chip">{{ formatProofStatusLabel(dataset.proofStatus) }}</span>
          <span class="status-chip status-chip--ghost">{{ formatUploadStatusLabel(dataset.uploadStatus) }}</span>
          <span class="status-chip" :class="{ 'status-chip--danger': dataset.destructionStatus === 'destroyed', 'status-chip--warn': dataset.destructionStatus !== 'active' && dataset.destructionStatus !== 'destroyed' }">
            {{ formatDestructionStatusLabel(dataset.destructionStatus) }}
          </span>
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
          <span class="status-chip status-chip--accent">{{ formatSignalSourceLabel(dataset.signalSource) }}</span>
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

    <div v-if="loading" class="loading-state">正在加载数据详情...</div>
    <div v-else-if="error" class="error-state">{{ error }}</div>

    <template v-else-if="dataset">
      <section class="content-layout">
        <div class="content-layout__main">
          <article class="workspace-card glass-panel">
            <div class="workspace-card__header">
              <div>
                <p class="section-kicker">活跃度视图</p>
                <h2 class="section-title">脑区热力图</h2>
              </div>
              <div class="brain-visual-control">
                <label class="brain-visual-control__field">
                  <span>模型透明度</span>
                  <input v-model.number="cortexOpacity" type="range" min="0.88" max="0.98" step="0.01" />
                </label>
                <strong class="brain-visual-control__value">{{ formatOpacityPercent(cortexOpacity) }}</strong>
                <label class="brain-visual-control__field">
                  <span>热力对比度</span>
                  <input v-model.number="heatContrast" type="range" min="0.78" max="1.4" step="0.02" />
                </label>
                <strong class="brain-visual-control__value">{{ formatContrastPercent(heatContrast) }}</strong>
                <button type="button" class="brain-visual-control__reset" @click="resetBrainVisuals">重置</button>
              </div>
            </div>

            <Brain3DHeatmap
              :regions="activity?.regions ?? []"
              :frame="currentFrame"
              :band="selectedBand"
              :timestamp="currentTimestamp"
              :surface-opacity="cortexOpacity"
              :heat-contrast="heatContrast"
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
              :error="timelineError"
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
              @seek-frame="seekFrame($event)"
              @apply-range="applyActivityRange"
            />
          </article>
        </div>

        <aside class="content-layout__side">
          <article class="workspace-card glass-panel">
            <div class="workspace-card__header">
              <div>
                <p class="section-kicker">当前读数</p>
                <h2 class="section-title">脑区指标</h2>
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
                <dd class="is-mono">{{ dataset.originalFilename }}</dd>
              </div>
              <div>
                <dt>采样点数</dt>
                <dd class="is-mono">{{ dataset.sampleCount }}</dd>
              </div>
              <div>
                <dt>归属机构</dt>
                <dd>{{ dataset.ownerOrganization }}</dd>
              </div>
              <div>
                <dt>系统阶段</dt>
                <dd class="is-mono">{{ formatSystemToken(systemStatus?.stage ?? 'bootstrap') }}</dd>
              </div>
            </dl>
          </article>

          <article class="workspace-card glass-panel">
            <div class="workspace-card__header">
              <div>
                <p class="section-kicker">上传处理</p>
                <h2 class="section-title">持久化与补偿</h2>
              </div>
              <button
                v-if="canRetryUploadFinalization"
                type="button"
                class="retry-button"
                :disabled="retrySubmitting"
                @click="retryUploadFinalization"
              >
                {{ retrySubmitting ? '补偿中...' : '重新收口' }}
              </button>
            </div>

            <dl class="detail-list">
              <div>
                <dt>上传回执</dt>
                <dd class="is-mono">{{ dataset.lastUploadTraceId || '暂无' }}</dd>
              </div>
              <div>
                <dt>当前状态</dt>
                <dd class="is-mono">{{ formatUploadStatusLabel(dataset.uploadStatus) }} / {{ formatProofStatusLabel(dataset.proofStatus) }}</dd>
              </div>
            </dl>

            <div v-if="dataset.lastErrorMessage" class="compensation-note compensation-note--danger">
              最近失败：{{ dataset.lastErrorMessage }}
            </div>
            <div v-else-if="dataset.retryAllowed" class="compensation-note">
              可直接重试收口。
            </div>

            <div v-if="dataset.uploadAudits.length" class="upload-flow">
              <div v-for="step in dataset.uploadAudits.slice(0, 6)" :key="`${step.traceId}-${step.createdAt}-${step.action}`" class="upload-flow__item">
                <div class="upload-flow__headline">
                  <strong>{{ formatAuditActionLabel(step.action) }}</strong>
                  <span>{{ formatRequestStatusLabel(step.status) }}</span>
                </div>
                <p v-if="step.message">{{ step.message }}</p>
                <time>{{ formatTime(step.createdAt) }}</time>
              </div>
            </div>
          </article>
        </aside>
      </section>

      <section class="info-grid">
        <article class="workspace-card glass-panel">
          <div class="workspace-card__header">
            <div>
              <p class="section-kicker section-kicker--violet">访问控制</p>
              <h2 class="section-title">访问门禁</h2>
            </div>
            <span
              class="status-chip"
              :class="{
                'status-chip--warn': accessState === 'pending',
                'status-chip--danger': accessState === 'denied',
              }"
            >
              {{ formatAccessStateLabel(accessState) }}
            </span>
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

          <div v-if="requestError" class="error-state access-note">{{ requestError }}</div>

          <div v-if="latestAccessRequest" class="access-note">
            最新记录：<span class="is-mono">{{ latestAccessRequest.id }}</span> · {{ formatRequestStatusLabel(latestAccessRequest.status) }}
            <br />
            到期时间：<span class="is-mono">{{ formatTime(latestAccessRequest.expiresAt) }}</span>
          </div>

          <div class="access-actions">
            <RouterLink class="access-link" to="/access-requests">前往访问申请</RouterLink>
            <RouterLink
              class="access-link"
              :to="{ path: '/destruction-requests', query: { source: 'dataset-detail', datasetId: props.datasetId } }"
            >
              打开销毁流程
            </RouterLink>
            <RouterLink
              v-if="isPrivilegedActor"
              class="access-link"
              :to="{ path: '/chain-records', query: { datasetId: props.datasetId } }"
            >
              查看链记录
            </RouterLink>
            <RouterLink v-if="canLaunchTraining" class="access-link access-link--warm" :to="trainingLink">
              带入训练任务
            </RouterLink>
          </div>

          <div class="request-preview" v-if="recentAccessRequests.length">
            <div v-for="row in recentAccessRequests" :key="row.id" class="request-preview__item">
              <div>
                <strong class="is-mono">{{ row.id }}</strong>
                <p><span class="is-mono">{{ row.actorId }}</span> · {{ row.purpose }}</p>
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

        <article v-if="ownerIdentity" class="workspace-card workspace-card--violet glass-panel">
          <div class="workspace-card__header">
            <div>
              <p class="section-kicker section-kicker--violet">身份信息</p>
              <h2 class="section-title">机构身份凭证</h2>
            </div>
            <span
              class="status-chip"
              :class="proofMatchesOwnerDid ? 'status-chip--ghost' : 'status-chip--danger'"
            >
              {{ proofMatchesOwnerDid ? 'DID 已对齐' : 'DID 未对齐' }}
            </span>
          </div>
          <dl class="proof-list">
            <div>
              <dt>机构名称</dt>
              <dd>{{ ownerIdentity.organizationName }}</dd>
            </div>
            <div class="proof-list__wide">
              <dt>机构 DID</dt>
              <dd class="is-mono">{{ ownerIdentity.organizationDid }}</dd>
            </div>
            <div>
              <dt>凭证类型</dt>
              <dd class="is-mono">{{ ownerIdentity.credential.type }}</dd>
            </div>
            <div>
              <dt>凭证状态</dt>
              <dd class="is-mono">{{ formatIdentityStatusLabel(ownerIdentity.credential.credentialStatus) }}</dd>
            </div>
            <div>
              <dt>校验状态</dt>
              <dd class="is-mono">{{ formatIdentityStatusLabel(ownerCredentialVerification?.status ?? ownerIdentity.credential.verificationStatus) }}</dd>
            </div>
            <div>
              <dt>状态来源</dt>
              <dd class="is-mono">{{ formatIdentityStatusSourceLabel(ownerIdentity.statusSnapshot.source) }}</dd>
            </div>
            <div class="proof-list__wide">
              <dt>数据持有方</dt>
              <dd class="is-mono">{{ dataset.proof.didHolder }}</dd>
            </div>
            <div>
              <dt>DID 对齐</dt>
              <dd class="is-mono" :class="proofMatchesOwnerDid ? 'is-aligned' : 'is-misaligned'">{{ proofMatchesOwnerDid ? '已对齐' : '未对齐' }}</dd>
            </div>
          </dl>
          <p v-if="ownerIdentity.statusSnapshot.reason" class="compensation-note">
            {{ ownerIdentity.statusSnapshot.reason }}
          </p>
          <div class="upload-flow" v-if="ownerIdentity.credentialHistory.length">
            <div
              v-for="entry in ownerIdentity.credentialHistory.slice(0, 3)"
              :key="`${entry.id ?? entry.createdAt ?? entry.nextStatus}`"
              class="upload-flow__item"
            >
              <div class="upload-flow__headline">
                <strong>{{ formatHistoryTransition(entry.previousStatus, entry.nextStatus) }}</strong>
                <span>{{ formatIdentityStatusSourceLabel(entry.source) }}</span>
              </div>
              <p v-if="entry.reason">{{ entry.reason }}</p>
              <time>{{ formatTime(entry.createdAt) }} · {{ entry.updatedBy || '系统' }}</time>
            </div>
          </div>
        </article>

        <article class="workspace-card glass-panel">
          <div class="workspace-card__header">
            <div>
              <p class="section-kicker">销毁闭环</p>
              <h2 class="section-title">销毁状态</h2>
            </div>
          </div>

          <dl class="detail-list">
            <div>
              <dt>销毁状态</dt>
              <dd class="is-mono">{{ formatDestructionStatusLabel(dataset.destructionStatus) }}</dd>
            </div>
            <div>
              <dt>销毁时间</dt>
              <dd class="is-mono">{{ formatTime(dataset.destroyedAt) }}</dd>
            </div>
          </dl>

          <div class="access-actions">
            <RouterLink
              class="access-link access-link--warm"
              :to="{ path: '/destruction-requests', query: { source: 'dataset-detail', datasetId: props.datasetId } }"
            >
              发起或查看销毁
            </RouterLink>
          </div>

          <div v-if="dataset.destructionStatus === 'destroyed'" class="compensation-note compensation-note--danger">
            当前数据集已进入已销毁状态，训练编排与后续使用会被阻断。
          </div>
          <div v-else-if="dataset.destructionStatus !== 'active'" class="compensation-note">
            当前数据集正处于销毁流程中，建议先在销毁台完成审批或执行，再继续其他治理动作。
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
              <dd class="is-mono">{{ formatSystemToken(dataset.proof.chainProvider) }}</dd>
            </div>
            <div>
              <dt>链群组</dt>
              <dd class="is-mono">{{ dataset.proof.chainGroup }}</dd>
            </div>
            <div>
              <dt>合约名称</dt>
              <dd>{{ formatContractLabel(dataset.proof.contractName) }}</dd>
            </div>
            <div>
              <dt>合约地址</dt>
              <dd class="is-mono">{{ dataset.proof.contractAddress }}</dd>
            </div>
            <div class="proof-list__wide">
              <dt>SM3 哈希</dt>
              <dd class="is-mono is-hash"><HashValue :value="dataset.proof.sm3Hash" :head="18" :tail="8" /></dd>
            </div>
            <div>
              <dt>存储引用</dt>
              <dd class="is-mono">{{ dataset.proof.offChainReference }}</dd>
            </div>
            <div>
              <dt>内容引用</dt>
              <dd class="is-mono"><HashValue :value="dataset.proof.ipfsCid" :head="18" /></dd>
            </div>
            <div class="proof-list__wide">
              <dt>链上交易</dt>
              <dd class="is-mono is-hash"><HashValue :value="dataset.proof.chainTxHash" :head="18" :tail="8" /></dd>
            </div>
            <div>
              <dt>DID 持有方</dt>
              <dd class="is-mono">{{ dataset.proof.didHolder }}</dd>
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
              <p class="section-kicker">链上业务轨迹</p>
              <h2 class="section-title">授权与训练记录</h2>
            </div>
          </div>

          <div v-if="dataset.chainRecords.length" class="chain-records">
            <div v-for="record in dataset.chainRecords" :key="record.id" class="chain-record">
              <div class="chain-record__headline">
                <div>
                  <strong>{{ formatChainEventLabel(record.eventType) }}</strong>
                  <p><span class="is-mono">{{ record.referenceId }}</span> · <span class="is-mono">{{ record.actorId }}</span> / {{ record.actorRole }}</p>
                </div>
                <span
                  class="status-chip"
                  :class="{
                    'status-chip--danger': record.anchorStatus !== 'anchored',
                    'status-chip--warn': record.businessStatus === 'revoked' || record.businessStatus === 'failed',
                  }"
                >
                  {{ formatRequestStatusLabel(record.anchorStatus) }}
                </span>
              </div>

              <dl class="chain-record__meta">
                <div>
                  <dt>业务状态</dt>
                  <dd class="is-mono">{{ formatRequestStatusLabel(record.businessStatus) }}</dd>
                </div>
                <div>
                  <dt>链提供方</dt>
                  <dd class="is-mono">{{ formatSystemToken(record.chainProvider) }}</dd>
                </div>
                <div>
                  <dt>链群组</dt>
                  <dd class="is-mono">{{ record.chainGroup || '暂无' }}</dd>
                </div>
                <div class="chain-record__meta-wide">
                  <dt>交易哈希</dt>
                  <dd class="is-mono is-hash">{{ record.chainTxHash || '等待上链成功' }}</dd>
                </div>
              </dl>

              <p v-if="record.detail" class="chain-record__detail">{{ record.detail }}</p>
              <p v-if="record.anchorError" class="chain-record__error">失败原因：{{ record.anchorError }}</p>
              <time class="chain-record__time">{{ formatTime(record.anchoredAt) }}</time>
            </div>
          </div>
          <div v-else class="empty-state">暂无链上业务记录</div>
        </article>

        <article class="workspace-card glass-panel">
          <div class="workspace-card__header">
            <div>
              <p class="section-kicker">审计记录</p>
              <h2 class="section-title">审计追踪</h2>
            </div>
          </div>

          <div v-if="sideLoading" class="loading-state">正在加载访问台账...</div>
          <div v-else-if="sideError" class="error-state">{{ sideError }}</div>
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
                  <p v-if="event.detail">{{ event.detail.replace('brain-activity', '脑区活跃度接口') }}</p>
                  <time>{{ formatTime(event.createdAt) }}</time>
                </div>
              </div>
            </div>
            <div v-else class="empty-state">暂无审计事件</div>
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
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(320px, 0.9fr);
  gap: 18px;
  align-items: end;
  padding: var(--space-panel);
  border-radius: var(--radius-panel);
  overflow: hidden;
  animation: consoleRise 0.5s ease both;
}

.page-header::before {
  content: '';
  position: absolute;
  inset: 0 0 auto;
  height: 1px;
  background: var(--line-warm);
}

.page-header__lead {
  position: relative;
  z-index: 1;
}

.page-header__title-line {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.page-header__id {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 0 10px;
  border-radius: var(--radius-control);
  border: 1px solid var(--line-warm);
  background: var(--bg-panel-soft);
  color: var(--accent);
  font-family: var(--mono);
  font-size: 0.72rem;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.page-header__back {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 12px;
  min-height: var(--control-height);
  padding: var(--space-button);
  border: 1px solid var(--line);
  border-radius: var(--radius-control);
  background: var(--bg-panel-soft);
  color: var(--text-muted);
  text-decoration: none;
  font-family: var(--body);
  font-size: 0.8rem;
  font-weight: 600;
  letter-spacing: 0.04em;
  transition:
    border-color 0.2s ease,
    color 0.2s ease;
}

.page-header__back::before {
  content: '←';
  margin-right: 8px;
  font-family: var(--mono);
  color: var(--accent);
}

.page-header__back:hover {
  border-color: var(--line-strong);
  color: var(--text-strong);
}

.page-header h1 {
  margin: 0;
  color: var(--text-strong);
  font-family: var(--display);
  font-size: var(--page-heading-size);
  font-weight: 600;
  line-height: var(--page-heading-line-height);
  letter-spacing: var(--page-heading-letter-spacing);
  text-wrap: balance;
}

.page-header__lede {
  margin: 12px 0 0;
  max-width: 60ch;
  color: var(--text-muted);
  font-size: var(--supporting-text-size);
  line-height: var(--supporting-text-line-height);
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
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
}

.workspace-card {
  position: relative;
  padding: 20px;
  border-radius: var(--radius-panel);
  animation: consoleRise 0.5s ease both;
  transition: border-color 0.25s ease;
}

.content-layout__main .workspace-card:nth-child(1) {
  animation-delay: 0.06s;
}

.content-layout__main .workspace-card:nth-child(2) {
  animation-delay: 0.14s;
}

.content-layout__side .workspace-card:nth-child(1) {
  animation-delay: 0.1s;
}

.content-layout__side .workspace-card:nth-child(2) {
  animation-delay: 0.18s;
}

.content-layout__side .workspace-card:nth-child(3) {
  animation-delay: 0.26s;
}

.info-grid .workspace-card:nth-child(1) {
  animation-delay: 0.08s;
}

.info-grid .workspace-card:nth-child(2) {
  animation-delay: 0.16s;
}

.info-grid .workspace-card:nth-child(3) {
  animation-delay: 0.24s;
}

.info-grid .workspace-card:nth-child(4) {
  animation-delay: 0.32s;
}

.info-grid .workspace-card:nth-child(5) {
  animation-delay: 0.4s;
}

.info-grid .workspace-card:nth-child(6) {
  animation-delay: 0.48s;
}

.workspace-card:hover {
  border-color: var(--line-strong);
}

.workspace-card--violet::before {
  content: '';
  position: absolute;
  inset: 0 0 auto;
  height: 1px;
  border-radius: var(--radius-panel) var(--radius-panel) 0 0;
  background: var(--line-strong);
}

.workspace-card--violet:hover {
  border-color: var(--line-strong);
}

.workspace-card__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.section-kicker--violet::before {
  background: var(--text-muted);
}

.brain-visual-control {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid var(--line);
  border-radius: var(--radius-subpanel);
  background: var(--bg-panel-soft);
  flex-wrap: wrap;
  justify-content: flex-end;
}

.brain-visual-control__field {
  display: grid;
  gap: 6px;
  min-width: 168px;
  flex: 1 1 168px;
}

.brain-visual-control__field span {
  color: var(--text-faint);
  font-family: var(--mono);
  font-size: 0.72rem;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.brain-visual-control__field input {
  width: 100%;
  accent-color: var(--accent);
}

.brain-visual-control__value {
  min-width: 3.8rem;
  color: var(--accent);
  text-align: right;
  font-family: var(--mono);
  font-size: 0.9rem;
  font-weight: 600;
}

.brain-visual-control__reset {
  min-height: var(--control-height);
  padding: var(--space-button);
  border: 1px solid var(--line);
  border-radius: var(--radius-control);
  background: var(--bg-panel-soft);
  color: var(--text-main);
  font-family: var(--body);
  font-size: 0.78rem;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  transition:
    border-color 0.2s ease,
    color 0.2s ease;
}

.brain-visual-control__reset:hover {
  border-color: var(--line-strong);
  color: var(--text-strong);
}

.detail-list,
.proof-list {
  display: grid;
  gap: 10px;
}

.proof-list {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.proof-list__wide {
  grid-column: 1 / -1;
}

.detail-list div,
.proof-list div {
  padding: var(--space-subpanel);
  border-radius: var(--radius-subpanel);
  border: 1px solid var(--line);
  background: var(--bg-panel-soft);
  transition:
    border-color 0.2s ease,
    background 0.2s ease;
}

.detail-list div:hover,
.proof-list div:hover {
  border-color: var(--line-strong);
  background: var(--bg-panel-muted);
}

.detail-list dt,
.proof-list dt {
  color: var(--text-faint);
  font-family: var(--mono);
  font-size: 0.72rem;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.detail-list dd,
.proof-list dd {
  margin: 6px 0 0;
  color: var(--text-main);
  word-break: break-word;
}

.detail-list dd.is-mono,
.proof-list dd.is-mono,
.chain-record__meta dd.is-mono {
  color: var(--text-strong);
  font-family: var(--mono);
  font-size: 0.86rem;
  letter-spacing: 0.01em;
}

.is-hash {
  font-size: 0.78rem !important;
  color: var(--accent) !important;
  overflow-wrap: anywhere;
}

.is-aligned {
  color: var(--accent) !important;
}

.is-misaligned {
  color: var(--danger) !important;
}

span.is-mono {
  font-family: var(--mono);
  letter-spacing: 0.01em;
}

.access-form__submit,
.retry-button {
  border: 1px solid var(--line-warm);
  border-radius: var(--radius-control);
  min-height: var(--control-height);
  padding: var(--space-button);
  background: var(--bg-panel-soft);
  color: var(--text-strong);
  font-family: var(--body);
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  transition:
    border-color 0.2s ease,
    background 0.2s ease;
}

.access-form__submit:hover:not(:disabled),
.retry-button:hover:not(:disabled) {
  border-color: var(--accent);
  background: var(--bg-panel-muted);
}

.access-form__submit:disabled,
.retry-button:disabled {
  opacity: 0.58;
  cursor: progress;
}

.compensation-note {
  margin-top: 14px;
  padding: 12px 14px;
  border-radius: var(--radius-block);
  border: 1px solid var(--line);
  border-left: 2px solid var(--accent);
  background: var(--bg-panel-soft);
  color: var(--text-muted);
}

.compensation-note--danger {
  border-color: var(--danger-soft);
  border-left-color: var(--danger);
  background: var(--danger-soft);
  color: var(--text-main);
}

.upload-flow {
  display: grid;
  gap: var(--space-list-tight);
  margin-top: 16px;
}

.upload-flow__item {
  padding: var(--space-subpanel);
  border-radius: var(--radius-subpanel);
  border: 1px solid var(--line);
  border-left: 2px solid var(--line-strong);
  background: var(--bg-panel-soft);
  transition:
    border-color 0.2s ease,
    border-left-color 0.2s ease;
}

.upload-flow__item:hover {
  border-color: var(--line-strong);
  border-left-color: var(--accent);
}

.upload-flow__headline {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.upload-flow__headline strong {
  font-family: var(--body);
  font-weight: 700;
  color: var(--text-strong);
}

.upload-flow__headline span {
  color: var(--accent);
  font-family: var(--mono);
  font-size: 0.74rem;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.upload-flow time {
  color: var(--text-faint);
  font-family: var(--mono);
  font-size: 0.74rem;
  letter-spacing: 0.04em;
}

.upload-flow p {
  color: var(--text-muted);
}

.upload-flow p,
.upload-flow time {
  display: block;
  margin: 8px 0 0;
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
  color: var(--text-faint);
  font-size: var(--field-label-size);
  text-transform: uppercase;
  letter-spacing: var(--field-label-letter-spacing);
}

.access-form input,
.access-form textarea {
  width: 100%;
  border: 1px solid var(--line);
  border-radius: var(--radius-control);
  min-height: var(--field-height);
  padding: var(--space-field-x);
  background: var(--bg-panel);
  color: var(--text-main);
}

.access-stage {
  position: relative;
  padding: var(--space-card);
  padding-left: calc(var(--space-card) + 4px);
  border-radius: var(--radius-subpanel);
  border: 1px solid var(--line);
  border-left: 3px solid var(--line-strong);
  background: var(--bg-panel-soft);
}

.access-stage strong {
  font-family: var(--body);
  font-weight: 700;
  letter-spacing: 0.02em;
  color: var(--text-strong);
}

.access-stage p,
.access-note {
  margin: 10px 0 0;
  color: var(--text-muted);
}

.access-note .is-mono {
  color: var(--accent);
  font-size: 0.84rem;
}

.access-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-top: 14px;
  min-height: var(--control-height);
  padding: var(--space-button);
  border: 1px solid var(--line);
  border-radius: var(--radius-control);
  background: var(--bg-panel-soft);
  color: var(--text-main);
  text-decoration: none;
  font-family: var(--body);
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  font-size: 0.8rem;
  transition:
    border-color 0.2s ease,
    color 0.2s ease;
}

.access-link:hover {
  border-color: var(--line-strong);
  color: var(--text-strong);
}

.access-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
}

.access-link--warm {
  border-color: var(--line-warm);
  background: var(--bg-panel-soft);
  color: var(--text-strong);
}

.access-link--warm:hover {
  border-color: var(--accent);
  background: var(--bg-panel-muted);
}

.request-preview {
  display: grid;
  gap: var(--space-list-tight);
  margin-top: 18px;
}

.request-preview__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: var(--space-subpanel);
  border-radius: var(--radius-subpanel);
  background: var(--bg-panel-soft);
  border: 1px solid var(--line);
  transition: border-color 0.2s ease;
}

.request-preview__item:hover {
  border-color: var(--line-strong);
}

.request-preview__item strong {
  font-family: var(--mono);
  font-weight: 600;
  color: var(--text-strong);
  font-size: 0.86rem;
}

.request-preview__item p {
  margin: 4px 0 0;
  color: var(--text-muted);
  font-size: 0.84rem;
}

.request-preview__item .is-mono {
  color: var(--text-main);
}

.access-stage--granted {
  border-left-color: var(--accent);
}

.access-stage--granted strong {
  color: var(--accent);
}

.access-stage--pending {
  border-left-color: var(--amber);
}

.access-stage--pending strong {
  color: var(--amber);
}

.access-stage--denied {
  border-left-color: var(--danger);
}

.access-stage--denied strong {
  color: var(--danger);
}

.audit-timeline {
  display: grid;
  gap: var(--space-list-tight);
}

.chain-records,
.chain-record__meta {
  display: grid;
  gap: var(--space-list-tight);
}

.chain-record {
  padding: var(--space-subpanel);
  border-radius: var(--radius-subpanel);
  border: 1px solid var(--line);
  border-left: 2px solid var(--line-strong);
  background: var(--bg-panel-soft);
  transition:
    border-color 0.2s ease,
    border-left-color 0.2s ease;
}

.chain-record:hover {
  border-color: var(--line-strong);
  border-left-color: var(--text-muted);
}

.chain-record__headline {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.chain-record__headline strong {
  font-family: var(--body);
  font-weight: 700;
  color: var(--text-strong);
}

.chain-record__headline p,
.chain-record__detail,
.chain-record__time,
.chain-record__error {
  margin: 6px 0 0;
  color: var(--text-muted);
  overflow-wrap: anywhere;
}

.chain-record__headline p .is-mono {
  color: var(--text-main);
  font-size: 0.84rem;
}

.chain-record__meta {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin-top: 14px;
}

.chain-record__meta-wide {
  grid-column: 1 / -1;
}

.chain-record__meta div {
  padding: var(--space-subpanel);
  border-radius: var(--radius-subpanel);
  border: 1px solid var(--line);
  background: var(--bg-panel);
}

.chain-record__meta dt {
  color: var(--text-faint);
  font-family: var(--mono);
  font-size: 0.72rem;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.chain-record__meta dd {
  margin: 6px 0 0;
  color: var(--text-main);
  word-break: break-word;
}

.chain-record__time {
  display: block;
  font-family: var(--mono);
  font-size: 0.74rem;
  color: var(--text-faint);
}

.chain-record__detail,
.chain-record__error {
  display: block;
}

.chain-record__error {
  color: var(--danger);
  font-size: 0.86rem;
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
  background: var(--line);
}

.audit-timeline__item:last-child .audit-timeline__rail::after {
  display: none;
}

.audit-timeline__dot {
  width: 8px;
  height: 8px;
  margin-top: 8px;
  border-radius: 2px;
  background: var(--accent);
}

.audit-timeline__card {
  display: grid;
  gap: 0;
  padding: var(--space-subpanel);
  border-radius: var(--radius-subpanel);
  background: var(--bg-panel-soft);
  border: 1px solid var(--line);
  transition: border-color 0.2s ease;
}

.audit-timeline__card:hover {
  border-color: var(--line-strong);
}

.audit-timeline__headline {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.audit-timeline__headline span {
  color: var(--accent);
  font-family: var(--mono);
  font-size: 0.74rem;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.audit-timeline__card strong {
  font-family: var(--body);
  font-weight: 700;
  color: var(--text-strong);
}

.audit-timeline__card p {
  margin: 5px 0 0;
  color: var(--text-muted);
  font-size: 0.88rem;
  overflow-wrap: anywhere;
}

.audit-timeline__card time {
  margin-top: 10px;
  color: var(--text-faint);
  font-family: var(--mono);
  font-size: 0.74rem;
  letter-spacing: 0.04em;
}

@media (max-width: 1040px) {
  .page-header,
  .content-layout,
  .info-grid {
    grid-template-columns: 1fr;
  }

  .brain-visual-control {
    width: 100%;
    justify-content: stretch;
  }

  .brain-visual-control__field {
    min-width: 100%;
    flex-basis: 100%;
  }

  .brain-visual-control__value {
    min-width: auto;
  }
}

@media (max-width: 760px) {
  .chain-record__headline {
    flex-direction: column;
  }

  .chain-record__meta,
  .proof-list {
    grid-template-columns: 1fr;
  }

  .page-header__title-line {
    align-items: flex-start;
  }
}
</style>
