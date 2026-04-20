<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { getDatasets, getSystemStatus, uploadDataset } from '../api/client'
import type { DatasetDetail, DatasetSummary, SystemStatus } from '../types/api'
import {
  formatApplicationLabel,
  formatContractLabel,
  formatModuleLabel,
  formatOrganizationLabel,
  formatProofStatusLabel,
  formatSystemToken,
  formatTrainingReadinessLabel,
} from '../utils/labels'

const router = useRouter()

const loading = ref(true)
const uploadLoading = ref(false)
const error = ref<string | null>(null)
const uploadError = ref<string | null>(null)
const uploadReceipt = ref<string | null>(null)
const systemStatus = ref<SystemStatus | null>(null)
const datasets = ref<DatasetSummary[]>([])
const selectedFile = ref<File | null>(null)

const uploadForm = reactive({
  subjectCode: 'PMMI-S301',
  title: '样板上传会话',
  description: '通过公开数据上传链路进入系统的新 EEG 资产。',
  ownerOrganization: 'Sichuan Neural Studio',
  tags: 'uploaded, public-validation, demo',
})

const featuredDataset = computed(() => datasets.value[0] ?? null)
const moduleEntries = computed(() =>
  Object.entries(systemStatus.value?.modules ?? {}).map(([moduleName, state]) => ({
    key: moduleName,
    label: formatModuleLabel(moduleName),
    state: formatSystemToken(state),
  })),
)
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
    updatedAt: dataset.updatedAt,
  }
}

function handleFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  selectedFile.value = input.files?.[0] ?? null
}

async function loadOverview() {
  loading.value = true
  error.value = null

  try {
    const [status, datasetRows] = await Promise.all([getSystemStatus(), getDatasets()])
    systemStatus.value = status
    datasets.value = datasetRows
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
    <section class="page-header glass-panel">
      <div>
        <p class="section-kicker">总览</p>
        <h1>神经数据工作台</h1>
        <p class="page-header__lede">
          从这里统一查看系统运行状态、数据资产目录和上传入口。页面按“摘要、数据、系统、操作”四个区块组织。
        </p>
      </div>

      <div class="page-header__actions">
        <RouterLink v-if="featuredDataset" class="page-header__primary" :to="`/datasets/${featuredDataset.id}`">
          打开最近数据
        </RouterLink>
        <RouterLink class="page-header__secondary" to="/access-requests">前往审批台</RouterLink>
      </div>
    </section>

    <div v-if="loading" class="loading-state">正在加载总览信息...</div>
    <div v-else-if="error" class="error-state">{{ error }}</div>

    <template v-else>
      <section class="summary-grid">
        <article v-for="stat in overviewStats" :key="stat.label" class="metric-card">
          <span>{{ stat.label }}</span>
          <strong>{{ stat.value }}</strong>
          <small>{{ stat.note }}</small>
        </article>
      </section>

      <section class="overview-layout">
        <div class="overview-layout__main">
          <article class="workspace-card glass-panel">
            <div class="workspace-card__header">
              <div>
                <p class="section-kicker">数据目录</p>
                <h2 class="section-title">数据集列表</h2>
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
                </div>
                <div class="dataset-row__status">
                  <span class="status-chip">{{ formatProofStatusLabel(dataset.proofStatus) }}</span>
                </div>
              </RouterLink>
            </div>
            <div v-else class="empty-state">当前还没有数据集记录。</div>
          </article>
        </div>

        <aside class="overview-layout__side">
          <article class="workspace-card glass-panel">
            <div class="workspace-card__header">
              <div>
                <p class="section-kicker">上传入口</p>
                <h2 class="section-title">上传 EEG 数据</h2>
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
                <h2 class="section-title">链路与模块</h2>
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

.page-header {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 16px;
  align-items: end;
  padding: 22px 24px;
  border-radius: 22px;
}

.page-header h1 {
  margin: 0;
  font-family: var(--display);
  font-size: clamp(2rem, 3vw, 2.8rem);
  line-height: 1;
}

.page-header__lede {
  margin: 12px 0 0;
  max-width: 64ch;
  color: var(--text-muted);
}

.page-header__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.page-header__primary,
.page-header__secondary,
.form-grid__submit {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 42px;
  padding: 0 16px;
  border-radius: 999px;
  text-decoration: none;
  font-family: var(--display);
  font-size: 0.82rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.page-header__primary,
.form-grid__submit {
  border: 1px solid var(--line-warm);
  background: linear-gradient(180deg, rgba(235, 178, 102, 0.2), rgba(235, 178, 102, 0.12));
  color: var(--text-main);
}

.page-header__secondary {
  border: 1px solid var(--line);
  background: rgba(12, 24, 32, 0.92);
  color: var(--text-main);
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.summary-grid .metric-card small {
  display: block;
  margin-top: 8px;
  color: var(--text-muted);
  font-size: 0.82rem;
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
  border-radius: 20px;
}

.workspace-card__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
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
  padding: 14px 16px;
  border-radius: 16px;
  border: 1px solid var(--line);
  background: var(--bg-panel-soft);
  text-decoration: none;
}

.dataset-row__main strong {
  display: block;
  font-family: var(--display);
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
  padding: 12px 14px;
  border-radius: 14px;
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

@media (max-width: 1040px) {
  .page-header,
  .overview-layout {
    grid-template-columns: 1fr;
  }

  .summary-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .dataset-row,
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
