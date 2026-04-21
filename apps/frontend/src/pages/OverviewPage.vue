<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import {
  getAccessRequests,
  getAudits,
  getDatasets,
  getSystemStatus,
  getTrainingJobs,
  uploadDataset,
} from '../api/client'
import { useActorProfile } from '../composables/useActorProfile'
import type { AccessRequest, AuditEvent, DatasetDetail, DatasetSummary, SystemStatus, TrainingJob } from '../types/api'
import {
  formatAuditActionLabel,
  formatApplicationLabel,
  formatContractLabel,
  formatModuleLabel,
  formatOrganizationLabel,
  formatProofStatusLabel,
  formatRequestStatusLabel,
  formatRoleLabel,
  formatSystemToken,
  formatTrainingReadinessLabel,
} from '../utils/labels'

const router = useRouter()
const { actorProfile } = useActorProfile()

const loading = ref(true)
const uploadLoading = ref(false)
const error = ref<string | null>(null)
const uploadError = ref<string | null>(null)
const uploadReceipt = ref<string | null>(null)
const systemStatus = ref<SystemStatus | null>(null)
const datasets = ref<DatasetSummary[]>([])
const accessRequests = ref<AccessRequest[]>([])
const trainingJobs = ref<TrainingJob[]>([])
const auditRows = ref<AuditEvent[]>([])
const selectedFile = ref<File | null>(null)

const uploadForm = reactive({
  subjectCode: 'PMMI-S301',
  title: '样板上传会话',
  description: '通过公开数据上传链路进入系统的新 EEG 资产。',
  ownerOrganization: 'Sichuan Neural Studio',
  tags: 'uploaded, public-validation, demo',
})

const featuredDataset = computed(() => datasets.value[0] ?? null)
const recentDatasets = computed(() => datasets.value.slice(0, 4))
const moduleEntries = computed(() =>
  Object.entries(systemStatus.value?.modules ?? {}).map(([moduleName, state]) => ({
    key: moduleName,
    label: formatModuleLabel(moduleName),
    state: formatSystemToken(state),
  })),
)
const moduleHighlights = computed(() => moduleEntries.value.slice(0, 4))
const overviewStats = computed(() => [
  {
    label: '数据资产',
    value: datasets.value.length,
    note: '当前目录中的数据集数量',
  },
  {
    label: '已存证',
    value: datasets.value.filter((item) => item.proofStatus === 'notarized').length,
    note: '已生成链上回执的数据',
  },
  {
    label: '可训练',
    value: datasets.value.filter((item) => item.trainingReadiness.toLowerCase().includes('ready')).length,
    note: '可进入训练或预览的数据',
  },
])
const isPrivilegedActor = computed(() =>
  ['owner', 'approver', 'admin'].includes(actorProfile.value.actorRole.toLowerCase()),
)
const roleLens = computed(() => {
  const role = actorProfile.value.actorRole.toLowerCase()
  if (role === 'admin') {
    return {
      title: '全局监管视角',
      note: '优先观察全局审计、训练任务和账号治理脉冲。',
    }
  }
  if (role === 'owner' || role === 'approver') {
    return {
      title: '机构决策视角',
      note: '把待审批申请、训练编排和机构内审计收拢到同一条任务面。 ',
    }
  }
  return {
    title: '研究执行视角',
    note: '优先处理数据申请、训练发起和个人审计回看。',
  }
})
const roleGuide = computed(() => {
  const role = actorProfile.value.actorRole.toLowerCase()
  if (role === 'admin') {
    return {
      nextStep: '先扫一遍训练与审计脉冲，再处理账户治理和异常回放。',
      emptyDataset: '当前还没有数据资产进入目录，管理员可以先用上传入口投递样板数据，再从审计中心确认链路已留痕。',
      emptyTraining: '还没有训练任务，先从训练编排页创建一条最小联邦任务，验证治理链路是否完整。',
      emptyAudit: '还没有审计事件，登录、建号、审批或训练后会在监管审计里出现第一批事件。',
      uploadHint: '管理员可先投递一份样板 EEG 资产，随后回看链路回执、审计事件与账户权限表现。',
    }
  }
  if (role === 'owner' || role === 'approver') {
    return {
      nextStep: '先看待审批记录，再把已批准数据带入训练编排。',
      emptyDataset: '当前还没有数据资产进入目录，先引入样板数据，后续审批和训练入口才会形成完整闭环。',
      emptyTraining: '还没有训练任务，等申请获批后可直接从审批台把数据带入训练页。',
      emptyAudit: '还没有机构审计事件，下一次审批、训练或账户动作会先出现在这里。',
      uploadHint: '机构侧可以先投递一份 EEG 资产，再通过审批台验证授权和训练的衔接是否顺畅。',
    }
  }
  return {
    nextStep: '先申请可用数据，再从训练编排页发起你的第一条任务。',
    emptyDataset: '当前还没有数据资产进入目录，先投递一份样板 EEG 或等待机构侧完成首批数据导入。',
    emptyTraining: '还没有训练任务，先去申请可训练数据，批准后就能直接带入训练页。',
    emptyAudit: '还没有个人审计事件，登录、申请或训练后会自动生成第一批可回看记录。',
    uploadHint: '研究侧可以先上传样板数据，随后申请访问、触发训练并回看自己的审计轨迹。',
  }
})
const latestTrainingJob = computed(() => trainingJobs.value[0] ?? null)
const latestAuditEvent = computed(() => auditRows.value[0] ?? null)
const portalActions = computed(() => [
  {
    to: '/training-jobs',
    label: '训练编排',
    note: latestTrainingJob.value
      ? `${latestTrainingJob.value.id} · ${formatRequestStatusLabel(latestTrainingJob.value.status)}`
      : '创建第一条联邦训练任务',
  },
  {
    to: '/access-requests',
    label: isPrivilegedActor.value ? '审批队列' : '我的申请',
    note: isPrivilegedActor.value
      ? `${accessRequests.value.filter((item) => item.status === 'pending').length} 条待审批`
      : `${accessRequests.value.filter((item) => item.status === 'approved').length} 条已批准`,
  },
  {
    to: '/audits',
    label: actorProfile.value.actorRole.toLowerCase() === 'admin' ? '监管审计' : '审计追踪',
    note: latestAuditEvent.value ? formatAuditActionLabel(latestAuditEvent.value.action) : '打开最近事件流',
  },
  {
    to: '/accounts',
    label: actorProfile.value.actorRole.toLowerCase() === 'admin' ? '账户治理' : '我的账户',
    note: `${formatRoleLabel(actorProfile.value.actorRole)} · ${formatOrganizationLabel(actorProfile.value.actorOrg)}`,
  },
])
const operationCards = computed(() => [
  {
    label: isPrivilegedActor.value ? '待审批申请' : '我的申请',
    value: isPrivilegedActor.value
      ? accessRequests.value.filter((item) => item.status === 'pending').length
      : accessRequests.value.length,
    note: isPrivilegedActor.value ? '等待机构决策' : '当前会话可见的访问申请',
  },
  {
    label: '训练任务',
    value: trainingJobs.value.length,
    note: `${trainingJobs.value.filter((item) => item.status === 'running').length} 条仍在运行`,
  },
  {
    label: '审计事件',
    value: auditRows.value.length,
    note: latestAuditEvent.value ? formatAuditActionLabel(latestAuditEvent.value.action) : '等待最新事件',
  },
])
const generatedAtLabel = computed(() =>
  systemStatus.value?.generatedAt ? new Date(systemStatus.value.generatedAt).toLocaleString() : '等待系统上报',
)
const commandSignals = computed(() => [
  {
    label: '链路模式',
    value: formatSystemToken(systemStatus.value?.chain.mode ?? 'mock'),
    note: formatSystemToken(systemStatus.value?.chain.provider ?? 'mock'),
  },
  {
    label: '合约面',
    value: formatContractLabel(systemStatus.value?.chain.contractName ?? 'MockDataNotary'),
    note: formatSystemToken(systemStatus.value?.chain.group ?? 'group-pending'),
  },
  {
    label: 'RPC 节点',
    value: String(systemStatus.value?.chain.rpcPeers.length ?? 0),
    note: '链路心跳',
  },
  {
    label: '系统阶段',
    value: formatSystemToken(systemStatus.value?.stage ?? 'bootstrap'),
    note: generatedAtLabel.value,
  },
])

const commandSteps = [
  {
    title: 'Ingest',
    note: '接入 EDF/GDF/BDF 文件并生成资产目录。',
  },
  {
    title: 'Attest',
    note: '把哈希、合约和链路回执写入可信面。',
  },
  {
    title: 'Govern',
    note: '以访问申请和审批策略控制读取边界。',
  },
  {
    title: 'Train',
    note: '进入联邦训练编排并观察状态收口。',
  },
  {
    title: 'Audit',
    note: '把操作回放到独立审计中心。',
  },
]

function toSummary(dataset: DatasetDetail): DatasetSummary {
  return {
    id: dataset.id,
    subjectCode: dataset.subjectCode,
    title: dataset.title,
    ownerOrganization: dataset.ownerOrganization,
    format: dataset.format,
    uploadStatus: dataset.uploadStatus,
    proofStatus: dataset.proofStatus,
    trainingReadiness: dataset.trainingReadiness,
    destructionStatus: dataset.destructionStatus,
    updatedAt: dataset.updatedAt,
  }
}

function handleFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  selectedFile.value = input.files?.[0] ?? null
}

function formatUpdatedAt(value: string) {
  return new Date(value).toLocaleString()
}

async function loadOverview() {
  loading.value = true
  error.value = null

  try {
    const [status, datasetRows, requestRows, trainingRows, audits] = await Promise.all([
      getSystemStatus(),
      getDatasets(),
      getAccessRequests(actorProfile.value),
      getTrainingJobs(actorProfile.value),
      getAudits(actorProfile.value),
    ])
    systemStatus.value = status
    datasets.value = datasetRows
    accessRequests.value = requestRows
    trainingJobs.value = trainingRows
    auditRows.value = audits.slice(0, 8)
  } catch (loadError) {
    error.value = loadError instanceof Error ? loadError.message : '加载总览信息失败。'
  } finally {
    loading.value = false
  }
}

async function submitUpload() {
  if (!selectedFile.value) {
    uploadError.value = '请先选择一个 EDF、GDF 或 BDF 文件。'
    return
  }

  uploadLoading.value = true
  uploadError.value = null
  uploadReceipt.value = null

  try {
    const response = await uploadDataset({
      file: selectedFile.value,
      subjectCode: uploadForm.subjectCode,
      title: uploadForm.title,
      description: uploadForm.description,
      ownerOrganization: uploadForm.ownerOrganization,
      tags: uploadForm.tags,
    })

    uploadReceipt.value = response.uploadReceipt
    datasets.value = [toSummary(response.dataset), ...datasets.value]
    await router.push(`/datasets/${response.dataset.id}`)
  } catch (submitError) {
    uploadError.value = submitError instanceof Error ? submitError.message : '上传数据集失败。'
  } finally {
    uploadLoading.value = false
  }
}

onMounted(loadOverview)
</script>

<template>
  <div class="overview-page">
    <section class="hero-panel glass-panel">
      <div class="hero-panel__copy">
        <p class="section-kicker">Neural Command Deck</p>
        <h1>把可信神经数据流转压缩进一座可操作的控制台。</h1>
        <p class="hero-panel__lede">
          这里不是单纯的数据列表页，而是把上传入口、链路态势、访问门禁和分析回放收拢进同一条工作流视野里。
        </p>

        <div class="hero-role-card">
          <span>Role Lens</span>
          <strong>{{ roleLens.title }}</strong>
          <p>{{ roleLens.note }}</p>
          <small>{{ roleGuide.nextStep }}</small>
        </div>

        <div class="hero-panel__actions">
          <RouterLink v-if="featuredDataset" class="hero-panel__primary" :to="`/datasets/${featuredDataset.id}`">
            打开最近数据
          </RouterLink>
          <RouterLink class="hero-panel__secondary" :to="isPrivilegedActor ? '/access-requests' : '/training-jobs'">
            {{ isPrivilegedActor ? '进入审批台' : '进入训练编排' }}
          </RouterLink>
        </div>

        <div class="hero-protocol">
          <div v-for="step in commandSteps" :key="step.title" class="hero-protocol__step">
            <strong>{{ step.title }}</strong>
            <p>{{ step.note }}</p>
          </div>
        </div>
      </div>

      <div class="hero-panel__rail">
        <article class="hero-spotlight">
          <p class="hero-spotlight__kicker">最近数据</p>
          <template v-if="featuredDataset">
            <div class="hero-spotlight__headline">
              <strong>{{ featuredDataset.title }}</strong>
              <span class="status-chip">{{ formatProofStatusLabel(featuredDataset.proofStatus) }}</span>
            </div>
            <p class="hero-spotlight__subject">
              {{ featuredDataset.subjectCode }} · {{ formatOrganizationLabel(featuredDataset.ownerOrganization) }}
            </p>
            <div class="hero-spotlight__meta">
              <div>
                <span>格式</span>
                <strong>{{ featuredDataset.format }}</strong>
              </div>
              <div>
                <span>训练态</span>
                <strong>{{ formatTrainingReadinessLabel(featuredDataset.trainingReadiness) }}</strong>
              </div>
              <div>
                <span>更新时间</span>
                <strong>{{ formatUpdatedAt(featuredDataset.updatedAt) }}</strong>
              </div>
            </div>
          </template>
          <p v-else class="hero-spotlight__empty">{{ roleGuide.emptyDataset }}</p>
        </article>

        <article class="hero-status">
          <div class="hero-status__header">
            <div>
              <p class="section-kicker">Telemetry</p>
              <h2 class="section-title">链路态势</h2>
            </div>
            <span class="status-chip">{{ formatApplicationLabel(systemStatus?.application) }}</span>
          </div>

          <div class="hero-status__grid">
            <div v-for="signal in commandSignals" :key="signal.label" class="hero-status__card">
              <span>{{ signal.label }}</span>
              <strong>{{ signal.value }}</strong>
              <small>{{ signal.note }}</small>
            </div>
          </div>
        </article>
      </div>
    </section>

    <div v-if="loading" class="loading-state">正在加载总览信息...</div>
    <div v-else-if="error" class="error-state">{{ error }}</div>

    <template v-else>
      <section class="summary-strip">
        <article v-for="stat in overviewStats" :key="stat.label" class="summary-strip__card">
          <span>{{ stat.label }}</span>
          <strong>{{ stat.value }}</strong>
          <small>{{ stat.note }}</small>
        </article>
      </section>

      <section class="mission-grid">
        <article class="workspace-card glass-panel">
          <div class="workspace-card__header">
            <div>
              <p class="section-kicker">Quick Launch</p>
              <h2 class="section-title">门户入口</h2>
              <p class="workspace-card__lede">把训练、审批、审计和账户域统一收进角色化入口，不再靠用户自己找页面。</p>
            </div>
            <span class="status-chip">{{ formatRoleLabel(actorProfile.actorRole) }}</span>
          </div>

          <div class="launch-grid">
            <RouterLink v-for="action in portalActions" :key="action.to" class="launch-card" :to="action.to">
              <span>{{ action.label }}</span>
              <strong>{{ action.note }}</strong>
            </RouterLink>
          </div>
        </article>

        <article class="workspace-card glass-panel">
          <div class="workspace-card__header">
            <div>
              <p class="section-kicker">Ops Pulse</p>
              <h2 class="section-title">运行脉冲</h2>
              <p class="workspace-card__lede">这里先看审批、训练和审计的即时压力，再决定跳转到哪个工作区。</p>
            </div>
          </div>

          <div class="operation-grid">
            <div v-for="card in operationCards" :key="card.label" class="operation-card">
              <span>{{ card.label }}</span>
              <strong>{{ card.value }}</strong>
              <small>{{ card.note }}</small>
            </div>
          </div>
        </article>

        <article class="workspace-card glass-panel">
          <div class="workspace-card__header">
            <div>
              <p class="section-kicker">Recent Activity</p>
              <h2 class="section-title">任务与审计摘要</h2>
            </div>
          </div>

          <div class="activity-stack">
            <div class="activity-card">
              <span>最近训练</span>
              <strong>{{ latestTrainingJob?.modelName ?? '还没有训练任务' }}</strong>
              <small>
                {{
                  latestTrainingJob
                    ? `${latestTrainingJob.id} · ${formatRequestStatusLabel(latestTrainingJob.status)}`
                    : roleGuide.emptyTraining
                }}
              </small>
            </div>
            <div class="activity-card">
              <span>最近审计</span>
              <strong>{{ latestAuditEvent ? formatAuditActionLabel(latestAuditEvent.action) : '还没有审计事件' }}</strong>
              <small>
                {{
                  latestAuditEvent
                    ? `${latestAuditEvent.actorId} · ${formatRequestStatusLabel(latestAuditEvent.status)}`
                    : roleGuide.emptyAudit
                }}
              </small>
            </div>
          </div>
        </article>
      </section>

      <section class="overview-layout">
        <div class="overview-layout__main">
          <article class="workspace-card glass-panel">
            <div class="workspace-card__header">
              <div>
                <p class="section-kicker">数据目录</p>
                <h2 class="section-title">资产舱清单</h2>
                <p class="workspace-card__lede">按最近更新时间排列，优先把已经存证或可训练的数据拉到操作路径前面。</p>
              </div>
              <span class="status-chip">{{ datasets.length }} 条记录</span>
            </div>

            <div class="dataset-table" v-if="datasets.length">
              <RouterLink
                v-for="dataset in datasets"
                :key="dataset.id"
                class="dataset-row"
                :to="`/datasets/${dataset.id}`"
              >
                <div class="dataset-row__main">
                  <strong>{{ dataset.title }}</strong>
                  <p>{{ dataset.subjectCode }} · {{ formatOrganizationLabel(dataset.ownerOrganization) }}</p>
                </div>
                <div class="dataset-row__meta">
                  <span>{{ dataset.format }}</span>
                  <span>{{ formatTrainingReadinessLabel(dataset.trainingReadiness) }}</span>
                  <small>{{ formatUpdatedAt(dataset.updatedAt) }}</small>
                </div>
                <div class="dataset-row__status">
                  <span class="status-chip">{{ formatProofStatusLabel(dataset.proofStatus) }}</span>
                </div>
              </RouterLink>
            </div>
            <div v-else class="empty-state">{{ roleGuide.emptyDataset }}</div>

            <div v-if="recentDatasets.length" class="dataset-glance">
              <div v-for="dataset in recentDatasets" :key="`${dataset.id}-glance`" class="dataset-glance__item">
                <span>{{ dataset.subjectCode }}</span>
                <strong>{{ dataset.title }}</strong>
              </div>
            </div>
          </article>
        </div>

        <aside class="overview-layout__side">
          <article class="workspace-card glass-panel">
            <div class="workspace-card__header">
              <div>
                <p class="section-kicker">上传入口</p>
                <h2 class="section-title">投递新 EEG 资产</h2>
                <p class="workspace-card__lede">{{ roleGuide.uploadHint }}</p>
              </div>
              <span class="status-chip">{{ formatSystemToken(systemStatus?.chain.mode ?? 'mock') }}</span>
            </div>

            <form class="form-grid" @submit.prevent="submitUpload">
              <label>
                <span>受试者编号</span>
                <input v-model="uploadForm.subjectCode" type="text" required />
              </label>
              <label>
                <span>标题</span>
                <input v-model="uploadForm.title" type="text" required />
              </label>
              <label>
                <span>归属机构</span>
                <input v-model="uploadForm.ownerOrganization" type="text" required />
              </label>
              <label class="form-grid__wide">
                <span>描述</span>
                <textarea v-model="uploadForm.description" rows="3"></textarea>
              </label>
              <label class="form-grid__wide">
                <span>标签</span>
                <input v-model="uploadForm.tags" type="text" placeholder="physionet,motor,public" />
              </label>
              <label class="form-grid__wide">
                <span>EEG 文件</span>
                <input accept=".edf,.gdf,.bdf" type="file" @change="handleFileChange" />
                <small>{{ selectedFile?.name ?? '尚未选择文件' }}</small>
              </label>

              <button class="form-grid__submit" type="submit" :disabled="uploadLoading">
                {{ uploadLoading ? '上传中...' : '上传并打开详情' }}
              </button>
            </form>

            <div v-if="uploadError" class="error-state form-grid__message">{{ uploadError }}</div>
            <div v-else-if="uploadReceipt" class="workspace-card__note">上传回执：{{ uploadReceipt }}</div>
          </article>

          <article class="workspace-card glass-panel">
            <div class="workspace-card__header">
              <div>
                <p class="section-kicker">系统状态</p>
                <h2 class="section-title">链路扫描</h2>
                <p class="workspace-card__lede">这里保持工作台级别的运行摘要，不把用户逼进单独的系统页。</p>
              </div>
              <span class="status-chip">{{ formatApplicationLabel(systemStatus?.application) }}</span>
            </div>

            <div class="metric-grid" v-if="systemStatus">
              <div class="metric-card">
                <span>链提供方</span>
                <strong>{{ formatSystemToken(systemStatus.chain.provider) }}</strong>
              </div>
              <div class="metric-card">
                <span>运行阶段</span>
                <strong>{{ formatSystemToken(systemStatus.stage) }}</strong>
              </div>
              <div class="metric-card">
                <span>合约</span>
                <strong>{{ formatContractLabel(systemStatus.chain.contractName) }}</strong>
              </div>
              <div class="metric-card">
                <span>RPC 节点数</span>
                <strong>{{ systemStatus.chain.rpcPeers.length }}</strong>
              </div>
            </div>

            <div class="module-list">
              <div v-for="moduleEntry in moduleEntries" :key="moduleEntry.key" class="module-list__item">
                <span>{{ moduleEntry.label }}</span>
                <strong>{{ moduleEntry.state }}</strong>
              </div>
            </div>

            <div v-if="moduleHighlights.length" class="module-highlight">
              <div v-for="entry in moduleHighlights" :key="`${entry.key}-highlight`" class="module-highlight__item">
                <span>{{ entry.label }}</span>
                <strong>{{ entry.state }}</strong>
              </div>
            </div>
          </article>
        </aside>
      </section>
    </template>
  </div>
</template>

<style scoped>
.overview-page {
  display: grid;
  gap: 18px;
}

.hero-panel {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(360px, 0.92fr);
  gap: 20px;
  padding: 26px;
  border-radius: 30px;
  background:
    linear-gradient(135deg, rgba(7, 19, 26, 0.96), rgba(6, 13, 18, 0.84)),
    radial-gradient(circle at top left, rgba(116, 210, 220, 0.14), transparent 36%);
}

.hero-panel__copy,
.hero-panel__rail {
  display: grid;
  gap: 18px;
}

.hero-panel h1 {
  margin: 0;
  font-family: var(--display);
  font-size: clamp(2.6rem, 5vw, 4.4rem);
  line-height: 0.94;
  letter-spacing: 0.02em;
}

.hero-panel__lede {
  margin: 12px 0 0;
  max-width: 62ch;
  color: var(--text-muted);
  font-size: 1rem;
  line-height: 1.8;
}

.hero-panel__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.hero-panel__primary,
.hero-panel__secondary,
.form-grid__submit {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 44px;
  padding: 0 18px;
  border-radius: 999px;
  text-decoration: none;
  font-family: var(--display);
  font-size: 0.82rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.hero-panel__primary,
.form-grid__submit {
  border: 1px solid var(--line-warm);
  background: linear-gradient(180deg, rgba(235, 178, 102, 0.24), rgba(235, 178, 102, 0.12));
  color: var(--text-main);
  box-shadow: 0 10px 30px rgba(235, 178, 102, 0.08);
}

.hero-panel__secondary {
  border: 1px solid var(--line);
  background: rgba(12, 24, 32, 0.92);
  color: var(--text-main);
}

.hero-protocol {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
}

.hero-role-card {
  display: grid;
  gap: 8px;
  max-width: 420px;
  padding: 16px 18px;
  border-radius: 22px;
  border: 1px solid rgba(108, 166, 186, 0.14);
  background: rgba(6, 18, 24, 0.78);
}

.hero-role-card span,
.launch-card span,
.operation-card span,
.activity-card span {
  color: var(--text-faint);
  font-size: 0.72rem;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.hero-role-card strong,
.launch-card strong,
.operation-card strong,
.activity-card strong {
  font-family: var(--display);
}

.hero-role-card p,
.hero-role-card small,
.activity-card small,
.operation-card small {
  margin: 0;
  color: var(--text-muted);
  line-height: 1.6;
}

.hero-role-card small {
  display: block;
  color: var(--amber);
}

.hero-protocol__step,
.hero-spotlight,
.hero-status {
  padding: 18px;
  border-radius: 24px;
  border: 1px solid rgba(108, 166, 186, 0.14);
  background:
    linear-gradient(180deg, rgba(4, 15, 21, 0.94), rgba(8, 17, 23, 0.78)),
    radial-gradient(circle at top right, rgba(116, 210, 220, 0.08), transparent 30%);
}

.hero-protocol__step strong,
.hero-spotlight__headline strong,
.hero-status__card strong,
.dataset-glance__item strong {
  display: block;
  font-family: var(--display);
}

.hero-protocol__step strong {
  font-size: 0.92rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.hero-protocol__step p {
  margin: 8px 0 0;
  color: var(--text-muted);
  font-size: 0.84rem;
  line-height: 1.6;
}

.hero-spotlight__kicker,
.hero-spotlight__subject,
.hero-status__card span,
.hero-status__card small,
.summary-strip__card small,
.workspace-card__lede,
.dataset-glance__item span,
.module-highlight__item span,
.dataset-row__meta small {
  color: var(--text-muted);
}

.hero-spotlight {
  display: grid;
  gap: 14px;
  min-height: 200px;
}

.hero-spotlight__kicker {
  margin: 0;
  font-family: var(--display);
  font-size: 0.74rem;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.hero-spotlight__headline {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.hero-spotlight__headline strong {
  font-size: 1.34rem;
  line-height: 1.15;
}

.hero-spotlight__subject,
.hero-spotlight__empty {
  margin: 0;
  line-height: 1.7;
}

.hero-spotlight__meta {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.hero-spotlight__meta div {
  padding: 14px;
  border-radius: 18px;
  border: 1px solid rgba(108, 166, 186, 0.1);
  background: rgba(8, 18, 25, 0.76);
}

.hero-spotlight__meta span {
  display: block;
  color: var(--text-faint);
  font-size: 0.72rem;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.hero-spotlight__meta strong {
  margin-top: 10px;
  font-size: 0.94rem;
  line-height: 1.5;
}

.hero-status {
  display: grid;
  gap: 16px;
}

.hero-status__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.hero-status__grid,
.summary-strip,
.module-highlight {
  display: grid;
  gap: 12px;
}

.hero-status__grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.hero-status__card,
.summary-strip__card,
.module-highlight__item {
  padding: 16px;
  border-radius: 18px;
  border: 1px solid rgba(108, 166, 186, 0.12);
  background: rgba(6, 18, 24, 0.78);
}

.hero-status__card span,
.summary-strip__card span,
.module-highlight__item span {
  display: block;
  font-size: 0.72rem;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.hero-status__card strong,
.summary-strip__card strong,
.module-highlight__item strong {
  margin-top: 10px;
  font-size: 1rem;
  line-height: 1.3;
}

.hero-status__card small,
.summary-strip__card small {
  display: block;
  margin-top: 8px;
  font-size: 0.82rem;
}

.summary-strip {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.mission-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
}

.summary-strip__card {
  background:
    linear-gradient(180deg, rgba(5, 18, 24, 0.92), rgba(5, 15, 21, 0.78)),
    radial-gradient(circle at top right, rgba(235, 178, 102, 0.08), transparent 34%);
}

.summary-strip__card strong {
  font-size: clamp(1.8rem, 3vw, 2.5rem);
}

.overview-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(340px, 0.85fr);
  gap: 18px;
}

.overview-layout__side {
  display: grid;
  gap: 18px;
}

.workspace-card {
  padding: 20px;
  border-radius: 24px;
}

.workspace-card__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.workspace-card__lede {
  margin: 10px 0 0;
  max-width: 52ch;
  line-height: 1.7;
  font-size: 0.9rem;
}

.workspace-card__note {
  margin-top: 12px;
  color: var(--accent);
  font-size: 0.9rem;
}

.dataset-table {
  display: grid;
  gap: 10px;
}

.dataset-row {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(180px, 0.7fr) auto;
  gap: 12px;
  align-items: center;
  padding: 16px 18px;
  border-radius: 20px;
  border: 1px solid var(--line);
  background:
    linear-gradient(180deg, rgba(8, 18, 25, 0.92), rgba(11, 22, 29, 0.72)),
    radial-gradient(circle at left center, rgba(116, 210, 220, 0.08), transparent 26%);
  text-decoration: none;
}

.dataset-row__main strong {
  display: block;
  font-family: var(--display);
  font-size: 1rem;
  line-height: 1.3;
}

.dataset-row__main p {
  margin: 6px 0 0;
  color: var(--text-muted);
  font-size: 0.88rem;
}

.dataset-row__meta {
  display: grid;
  gap: 4px;
  color: var(--text-muted);
  font-size: 0.84rem;
}

.dataset-row__meta small {
  font-size: 0.76rem;
}

.dataset-glance {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-top: 14px;
}

.dataset-glance__item {
  padding: 14px 16px;
  border-radius: 18px;
  border: 1px solid rgba(108, 166, 186, 0.12);
  background: rgba(6, 18, 24, 0.76);
}

.dataset-glance__item span {
  display: block;
  font-size: 0.7rem;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.dataset-glance__item strong {
  margin-top: 8px;
  font-size: 0.9rem;
  line-height: 1.5;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.form-grid label {
  display: grid;
  gap: 8px;
}

.form-grid span {
  color: var(--text-faint);
  font-size: 0.74rem;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.form-grid input,
.form-grid textarea {
  width: 100%;
  min-height: 44px;
  padding: 10px 12px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: rgba(8, 18, 25, 0.94);
  color: var(--text-main);
}

.form-grid textarea {
  min-height: 90px;
  resize: vertical;
}

.form-grid small {
  color: var(--text-muted);
  font-size: 0.82rem;
}

.form-grid__wide,
.form-grid__submit {
  grid-column: 1 / -1;
}

.form-grid__message {
  margin-top: 12px;
}

.module-list {
  display: grid;
  gap: 10px;
  margin-top: 16px;
}

.module-list__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 13px 14px;
  border-radius: 18px;
  border: 1px solid var(--line);
  background: var(--bg-panel-soft);
}

.module-list__item span {
  color: var(--text-muted);
}

.module-list__item strong {
  font-family: var(--display);
  font-size: 0.9rem;
}

.module-highlight {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin-top: 14px;
}

.launch-grid,
.operation-grid,
.activity-stack {
  display: grid;
  gap: 12px;
}

.launch-card,
.operation-card,
.activity-card {
  padding: 16px;
  border-radius: 18px;
  border: 1px solid rgba(108, 166, 186, 0.12);
  background: rgba(6, 18, 24, 0.78);
}

.launch-card {
  text-decoration: none;
}

.launch-card strong,
.operation-card strong,
.activity-card strong {
  display: block;
  margin-top: 10px;
  font-size: 1rem;
  line-height: 1.5;
  color: var(--text-main);
}

.launch-card:hover {
  border-color: rgba(235, 178, 102, 0.22);
}

.operation-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

@media (max-width: 1040px) {
  .hero-panel,
  .overview-layout,
  .mission-grid {
    grid-template-columns: 1fr;
  }

  .hero-protocol,
  .summary-strip,
  .dataset-glance,
  .module-highlight,
  .operation-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .hero-panel {
    padding: 20px;
  }

  .hero-panel h1 {
    font-size: 2.3rem;
  }

  .hero-spotlight__headline,
  .dataset-row,
  .form-grid {
    grid-template-columns: 1fr;
  }

  .hero-status__grid,
  .hero-spotlight__meta {
    grid-template-columns: 1fr;
  }
}
</style>
