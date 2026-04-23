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
      <div>
        <RouterLink class="page-header__back" to="/">返回总览</RouterLink>
        <p class="section-kicker">数据集详情</p>
        <h1 class="page-main-heading">{{ dataset.title }}</h1>
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
                <dd>{{ dataset.lastUploadTraceId || '暂无' }}</dd>
              </div>
              <div>
                <dt>当前状态</dt>
                <dd>{{ formatUploadStatusLabel(dataset.uploadStatus) }} / {{ formatProofStatusLabel(dataset.proofStatus) }}</dd>
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

          <div v-if="requestError" class="error-state access-note">{{ requestError }}</div>

          <div v-if="latestAccessRequest" class="access-note">
            最新记录：{{ latestAccessRequest.id }} · {{ formatRequestStatusLabel(latestAccessRequest.status) }}
            <br />
            到期时间：{{ formatTime(latestAccessRequest.expiresAt) }}
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

        <article v-if="ownerIdentity" class="workspace-card glass-panel">
          <div class="workspace-card__header">
            <div>
              <p class="section-kicker">身份信息</p>
              <h2 class="section-title">归属机构身份凭证</h2>
            </div>
          </div>
          <dl class="proof-list">
            <div>
              <dt>机构名称</dt>
              <dd>{{ ownerIdentity.organizationName }}</dd>
            </div>
            <div>
              <dt>机构 DID</dt>
              <dd>{{ ownerIdentity.organizationDid }}</dd>
            </div>
            <div>
              <dt>凭证类型</dt>
              <dd>{{ ownerIdentity.credential.type }}</dd>
            </div>
            <div>
              <dt>凭证状态</dt>
              <dd>{{ formatIdentityStatusLabel(ownerIdentity.credential.credentialStatus) }}</dd>
            </div>
            <div>
              <dt>校验状态</dt>
              <dd>{{ formatIdentityStatusLabel(ownerCredentialVerification?.status ?? ownerIdentity.credential.verificationStatus) }}</dd>
            </div>
            <div>
              <dt>状态来源</dt>
              <dd>{{ formatIdentityStatusSourceLabel(ownerIdentity.statusSnapshot.source) }}</dd>
            </div>
            <div>
              <dt>数据持有方</dt>
              <dd>{{ dataset.proof.didHolder }}</dd>
            </div>
            <div>
              <dt>DID 对齐</dt>
              <dd>{{ proofMatchesOwnerDid ? '已对齐' : '未对齐' }}</dd>
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
              <h2 class="section-title">销毁状态与执行入口</h2>
            </div>
          </div>

          <dl class="detail-list">
            <div>
              <dt>销毁状态</dt>
              <dd>{{ formatDestructionStatusLabel(dataset.destructionStatus) }}</dd>
            </div>
            <div>
              <dt>销毁时间</dt>
              <dd>{{ formatTime(dataset.destroyedAt) }}</dd>
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
              <dt>SM3 哈希</dt>
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
              <dt>DID 持有方</dt>
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
              <p class="section-kicker">链上业务轨迹</p>
              <h2 class="section-title">授权与训练记录</h2>
            </div>
          </div>

          <div v-if="dataset.chainRecords.length" class="chain-records">
            <div v-for="record in dataset.chainRecords" :key="record.id" class="chain-record">
              <div class="chain-record__headline">
                <div>
                  <strong>{{ formatChainEventLabel(record.eventType) }}</strong>
                  <p>{{ record.referenceId }} · {{ record.actorId }} / {{ record.actorRole }}</p>
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
                  <dd>{{ formatRequestStatusLabel(record.businessStatus) }}</dd>
                </div>
                <div>
                  <dt>链提供方</dt>
                  <dd>{{ formatSystemToken(record.chainProvider) }}</dd>
                </div>
                <div>
                  <dt>链群组</dt>
                  <dd>{{ record.chainGroup || '暂无' }}</dd>
                </div>
                <div>
                  <dt>交易哈希</dt>
                  <dd>{{ record.chainTxHash || '等待上链成功' }}</dd>
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
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(320px, 0.9fr);
  gap: 18px;
  align-items: end;
  padding: var(--space-panel);
  border-radius: var(--radius-panel);
}

.page-header__back {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 12px;
  min-height: var(--control-height);
  padding: var(--space-button);
  border: 1px solid var(--line);
  border-radius: var(--radius-pill);
  background: var(--button-soft-gradient);
  color: var(--text-muted);
  text-decoration: none;
  font-family: var(--body);
  font-size: 0.8rem;
  font-weight: 600;
  letter-spacing: 0.04em;
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
  padding: 20px;
  border-radius: var(--radius-panel);
}

.workspace-card__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.brain-visual-control {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid var(--line);
  border-radius: var(--radius-subpanel);
  background: var(--panel-soft-gradient);
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
  color: var(--text-muted);
  font-size: 0.74rem;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.brain-visual-control__field input {
  width: 100%;
  accent-color: color-mix(in srgb, var(--accent) 78%, #f2ba63 22%);
}

.brain-visual-control__value {
  min-width: 3.8rem;
  color: var(--text-strong);
  text-align: right;
  font-size: 0.9rem;
}

.brain-visual-control__reset {
  min-height: var(--control-height);
  padding: var(--space-button);
  border: 1px solid var(--line);
  border-radius: var(--radius-pill);
  background: var(--button-soft-gradient);
  color: var(--text-main);
  font-family: var(--body);
  font-size: 0.78rem;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.detail-list,
.proof-list {
  display: grid;
  gap: 12px;
}

.detail-list div,
.proof-list div {
  padding: var(--space-subpanel);
  border-radius: var(--radius-subpanel);
  border: 1px solid var(--line);
  background: var(--panel-soft-gradient);
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
  border-radius: var(--radius-pill);
  min-height: var(--control-height);
  padding: var(--space-button);
  background: var(--button-warm-gradient);
  color: var(--text-strong);
  font-family: var(--body);
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.retry-button {
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
}

.compensation-note {
  margin-top: 14px;
  padding: 12px 14px;
  border-radius: 14px;
  border: 1px solid var(--line);
  background: var(--bg-panel-soft);
  color: var(--text-muted);
}

.compensation-note--danger {
  border-color: rgba(242, 126, 126, 0.28);
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
  background: var(--panel-soft-gradient);
}

.upload-flow__headline {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.upload-flow__headline span,
.upload-flow time,
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
  color: var(--text-muted);
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
  padding: var(--space-card);
  border-radius: var(--radius-subpanel);
  border: 1px solid var(--line);
  background: var(--panel-soft-gradient);
}

.access-stage strong {
  font-family: var(--body);
  font-weight: 700;
  letter-spacing: 0.02em;
}

.access-stage p,
.access-note {
  margin: 10px 0 0;
  color: var(--text-muted);
}

.access-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-top: 14px;
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
  font-size: 0.8rem;
}

.access-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
}

.access-link--warm {
  border-color: var(--line-warm);
  background: var(--button-warm-gradient);
  color: var(--text-strong);
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
  background: var(--panel-soft-gradient);
  border: 1px solid var(--line);
}

.request-preview__item strong {
  font-family: var(--body);
  font-weight: 700;
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
  background: var(--panel-soft-gradient);
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
}

.chain-record__headline p,
.chain-record__detail,
.chain-record__time,
.chain-record__error {
  margin: 6px 0 0;
  color: var(--text-muted);
}

.chain-record__meta {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin-top: 14px;
}

.chain-record__meta div {
  padding: var(--space-subpanel);
  border-radius: var(--radius-subpanel);
  border: 1px solid var(--line);
  background: var(--bg-panel);
}

.chain-record__meta dt {
  color: var(--text-faint);
  font-size: 0.74rem;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.chain-record__meta dd {
  margin: 6px 0 0;
  color: var(--text-main);
  word-break: break-word;
}

.chain-record__detail,
.chain-record__time,
.chain-record__error {
  display: block;
}

.chain-record__error {
  color: var(--danger);
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
  background: linear-gradient(180deg, rgba(49, 87, 102, 0.24), transparent);
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
  box-shadow: 0 0 10px rgba(49, 87, 102, 0.18);
}

.audit-timeline__card {
  display: grid;
  gap: 0;
  padding: var(--space-subpanel);
  border-radius: var(--radius-subpanel);
  background: var(--panel-soft-gradient);
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
  font-family: var(--body);
  font-size: 0.78rem;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.audit-timeline__card strong {
  font-family: var(--body);
  font-weight: 700;
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

  .chain-record__meta {
    grid-template-columns: 1fr;
  }
}
</style>
