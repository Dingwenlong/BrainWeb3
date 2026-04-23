<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { ApiError, getChainPolicy, getChainRecords, retryChainRecord, updateModelGovernance } from '../api/client'
import { toErrorMessage, useAsyncView } from '../composables/useAsyncView'
import { useActorProfile } from '../composables/useActorProfile'
import { useToast } from '../composables/useToast'
import type { ChainBusinessRecord, ChainPolicyRule } from '../types/api'
import {
  formatChainEventLabel,
  formatChainPolicyLabel,
  formatContractLabel,
  formatOrganizationLabel,
  formatRequestStatusLabel,
  formatRoleLabel,
  formatSystemToken,
} from '../utils/labels'
import { canInspectChainRecords } from '../utils/permissions'

const route = useRoute()
const { actorProfile } = useActorProfile()
const { pushToast } = useToast()

const { loading, error, run: runRecordLoad, setErrorMessage } = useAsyncView({
  initialLoading: true,
})
const records = ref<ChainBusinessRecord[]>([])
const policies = ref<ChainPolicyRule[]>([])
const retryingRecordId = ref<number | null>(null)
const governingModelId = ref('')
const actionMessage = ref<string | null>(null)
const canInspectChainWorkspace = computed(() => canInspectChainRecords(actorProfile.value.actorRole))
const focusModelId = computed(() =>
  typeof route.query.focusModelId === 'string' ? route.query.focusModelId : '',
)
const focusedModelRecord = computed(() => {
  if (!focusModelId.value) {
    return null
  }
  return records.value.find((record) => record.referenceId === focusModelId.value) ?? null
})

const filters = reactive({
  datasetId: typeof route.query.datasetId === 'string' ? route.query.datasetId : '',
  eventType: typeof route.query.eventType === 'string' ? route.query.eventType : '',
  anchorStatus: typeof route.query.anchorStatus === 'string' ? route.query.anchorStatus : '',
  businessStatus: typeof route.query.businessStatus === 'string' ? route.query.businessStatus : '',
  chainTxHash: typeof route.query.chainTxHash === 'string' ? route.query.chainTxHash : '',
})

const roleGuide = computed(() => {
  const role = actorProfile.value.actorRole.toLowerCase()
  if (role === 'admin') {
    return {
      scope: '全局链监管视图',
      empty: '暂无链业务记录',
    }
  }
  return {
    scope: `机构链轨迹 · ${formatOrganizationLabel(actorProfile.value.actorOrg)}`,
    empty: '暂无链业务记录',
  }
})

const chainStats = computed(() => [
  { label: '链记录总数', value: records.value.length },
  { label: '已上链', value: records.value.filter((row) => row.anchorStatus === 'anchored').length },
  { label: '授权动作', value: records.value.filter((row) => row.eventType.startsWith('ACCESS_')).length },
  { label: '训练动作', value: records.value.filter((row) => row.eventType.startsWith('TRAINING_')).length },
  { label: '模型动作', value: records.value.filter((row) => row.eventType.startsWith('MODEL_')).length },
])
const spotlightRecord = computed(() => records.value[0] ?? null)
const policyStats = computed(() => [
  { label: '必须上链', value: policies.value.filter((row) => row.anchorPolicy === 'required').length },
  { label: '可选上链', value: policies.value.filter((row) => row.anchorPolicy === 'optional').length },
  { label: '仅审计', value: policies.value.filter((row) => row.anchorPolicy === 'audit-only').length },
])
function formatTime(value: string) {
  return new Date(value).toLocaleString()
}

function auditActionFor(record: ChainBusinessRecord) {
  switch (record.eventType) {
    case 'ACCESS_APPROVED':
      return 'ACCESS_REQUEST_APPROVED'
    case 'ACCESS_REVOKED':
      return 'ACCESS_REQUEST_REVOKED'
    case 'TRAINING_COMPLETED':
      return 'TRAINING_RUN_COMPLETED'
    case 'TRAINING_FAILED':
      return 'TRAINING_RUN_FAILED'
    case 'MODEL_REGISTERED':
      return 'MODEL_VERSION_REGISTERED'
    case 'MODEL_GOVERNED':
      return 'MODEL_GOVERNANCE_UPDATED'
    default:
      return ''
  }
}

function auditLinkFor(record: ChainBusinessRecord) {
  return {
    path: '/audits',
    query: {
      source: 'chain-record',
      datasetId: record.datasetId,
      action: auditActionFor(record),
    },
  }
}

function requestLinkFor(record: ChainBusinessRecord) {
  return {
    path: '/access-requests',
    query: {
      source: 'chain-record',
      datasetId: record.datasetId,
      status: record.businessStatus === 'revoked' ? 'revoked' : 'approved',
      focusRequestId: record.referenceId,
    },
  }
}

function modelLinkFor(record: ChainBusinessRecord) {
  return {
    path: '/model-records',
    query: {
      source: 'chain-record',
      datasetId: record.datasetId,
      focusModelId: record.referenceId,
    },
  }
}

function canGovernModelFromRecord(record: ChainBusinessRecord) {
  if (!record.eventType.startsWith('MODEL_') || !record.referenceId.toLowerCase().startsWith('mr-')) {
    return false
  }
  const role = actorProfile.value.actorRole.toLowerCase()
  if (role === 'admin') {
    return true
  }
  return (role === 'owner' || role === 'approver') && actorProfile.value.actorOrg.toLowerCase() === record.actorOrg.toLowerCase()
}

async function governModel(record: ChainBusinessRecord, status: 'active' | 'archived') {
  if (!canGovernModelFromRecord(record)) {
    return
  }

  governingModelId.value = record.referenceId
  actionMessage.value = null
  setErrorMessage(null)

  try {
    const updated = await updateModelGovernance(record.referenceId, actorProfile.value, {
      status,
      note:
        status === 'active'
          ? 'Promoted from chain workspace after registry review.'
          : 'Archived from chain workspace after governance review.',
    })
    actionMessage.value = `模型 ${updated.id} 已切换为 ${formatRequestStatusLabel(updated.governanceStatus)}。`
    pushToast({
      tone: 'success',
      title: '模型治理已更新',
      message: `${updated.id} 已在链记录页完成治理动作。`,
    })
    await loadRecords()
  } catch (governError) {
    const message = toErrorMessage(governError, '模型治理失败。')
    setErrorMessage(message)
    pushToast({
      tone: 'warning',
      title: '模型治理失败',
      message,
    })
  } finally {
    governingModelId.value = ''
  }
}

async function loadRecords() {
  if (!canInspectChainWorkspace.value) {
    await runRecordLoad(async () => [], '加载链轨迹失败。')
    records.value = []
    setErrorMessage('当前身份无权查看链记录，请切换到 owner、approver 或 admin。')
    return
  }

  const rows = await runRecordLoad(
    async () => {
      try {
        return await getChainRecords(actorProfile.value, {
          datasetId: filters.datasetId.trim() || undefined,
          eventType: filters.eventType || undefined,
          anchorStatus: filters.anchorStatus || undefined,
          businessStatus: filters.businessStatus || undefined,
          chainTxHash: filters.chainTxHash.trim() || undefined,
        })
      } catch (loadError) {
        if (loadError instanceof ApiError && loadError.status === 403) {
          throw new Error('当前身份无权查看链记录，请切换到 owner、approver 或 admin。')
        }
        throw loadError
      }
    },
    '加载链轨迹失败。',
  )

  if (!rows) {
    return
  }

  records.value = rows
}

async function loadPolicy() {
  if (!canInspectChainWorkspace.value) {
    policies.value = []
    return
  }

  try {
    policies.value = await getChainPolicy(actorProfile.value)
  } catch (loadError) {
    if (loadError instanceof ApiError && loadError.status === 403) {
      policies.value = []
      setErrorMessage('当前身份无权查看链记录，请切换到 owner、approver 或 admin。')
      return
    }
    throw loadError
  }
}

function resetFilters() {
  filters.datasetId = ''
  filters.eventType = ''
  filters.anchorStatus = ''
  filters.businessStatus = ''
  filters.chainTxHash = ''
  void loadRecords()
}

function applyRouteFilters() {
  filters.datasetId = typeof route.query.datasetId === 'string' ? route.query.datasetId : ''
  filters.eventType = typeof route.query.eventType === 'string' ? route.query.eventType : ''
  filters.anchorStatus = typeof route.query.anchorStatus === 'string' ? route.query.anchorStatus : ''
  filters.businessStatus = typeof route.query.businessStatus === 'string' ? route.query.businessStatus : ''
  filters.chainTxHash = typeof route.query.chainTxHash === 'string' ? route.query.chainTxHash : ''
}

async function handleRetry(record: ChainBusinessRecord) {
  retryingRecordId.value = record.id
  actionMessage.value = null
  setErrorMessage(null)

  try {
    const refreshed = await retryChainRecord(record.id, actorProfile.value)
    actionMessage.value = `链记录 ${refreshed.referenceId} 已重新提交，当前状态为 ${formatRequestStatusLabel(refreshed.anchorStatus)}。`
    await loadRecords()
  } catch (retryError) {
    setErrorMessage(toErrorMessage(retryError, '重试上链失败。'))
  } finally {
    retryingRecordId.value = null
  }
}

onMounted(() => {
  applyRouteFilters()
  void loadRecords()
})
onMounted(loadPolicy)

watch(
  () => route.query,
  () => {
    applyRouteFilters()
    void loadRecords()
  },
)
</script>

<template>
  <div class="chain-page">
    <section class="hero-panel glass-panel">
      <div class="hero-panel__copy">
        <p class="section-kicker">链记录</p>
        <h1 class="page-main-heading">把授权、训练和模型动作汇成可查询的链记录。</h1>

        <div class="hero-panel__actions">
          <span class="status-chip">{{ roleGuide.scope }}</span>
          <RouterLink class="hero-panel__secondary" to="/">返回总览</RouterLink>
        </div>

        <div class="summary-strip">
          <article v-for="stat in chainStats" :key="stat.label" class="summary-strip__card">
            <span>{{ stat.label }}</span>
            <strong>{{ stat.value }}</strong>
          </article>
        </div>
      </div>

      <div class="hero-panel__rail">
        <article class="hero-spotlight">
          <p class="hero-spotlight__kicker">{{ focusedModelRecord ? '模型链焦点' : '最新链记录' }}</p>
          <template v-if="focusedModelRecord || spotlightRecord">
            <div class="hero-spotlight__headline">
              <strong>{{ formatChainEventLabel((focusedModelRecord ?? spotlightRecord)?.eventType) }}</strong>
              <span class="status-chip" :class="{ 'status-chip--danger': (focusedModelRecord ?? spotlightRecord)?.anchorStatus !== 'anchored' }">
                {{ formatRequestStatusLabel((focusedModelRecord ?? spotlightRecord)?.anchorStatus) }}
              </span>
            </div>
            <p class="hero-spotlight__context">
              {{ (focusedModelRecord ?? spotlightRecord)?.referenceId }} · {{ (focusedModelRecord ?? spotlightRecord)?.actorId }} ·
              {{ formatOrganizationLabel((focusedModelRecord ?? spotlightRecord)?.actorOrg) }}
            </p>
            <p v-if="(focusedModelRecord ?? spotlightRecord)?.detail" class="hero-spotlight__reason">
              {{ (focusedModelRecord ?? spotlightRecord)?.detail }}
            </p>
            <div class="hero-spotlight__meta">
              <div>
                <span>对象</span>
                <strong>{{ (focusedModelRecord ?? spotlightRecord)?.datasetId }}</strong>
              </div>
              <div>
                <span>合约</span>
                <strong>{{ (focusedModelRecord ?? spotlightRecord)?.contractName || '未声明' }}</strong>
              </div>
              <div>
                <span>交易</span>
                <strong>{{ (focusedModelRecord ?? spotlightRecord)?.chainTxHash || '等待上链成功' }}</strong>
              </div>
              <div>
                <span>时间</span>
                <strong>{{ formatTime((focusedModelRecord ?? spotlightRecord)?.anchoredAt || '') }}</strong>
              </div>
            </div>
          </template>
          <p v-else class="hero-spotlight__empty">{{ roleGuide.empty }}</p>
        </article>

        <article class="hero-lane">
          <div class="hero-lane__header">
            <div>
              <p class="section-kicker">策略概览</p>
              <h2 class="section-title">上链策略</h2>
            </div>
          </div>
          <div class="hero-lane__steps">
            <div v-for="stat in policyStats" :key="stat.label" class="hero-lane__step">
              <strong>{{ stat.label }}</strong>
              <p>{{ stat.value }} 类事件</p>
            </div>
            <p v-if="!policies.length" class="hero-lane__empty">暂无链策略可展示。</p>
          </div>
        </article>
      </div>
    </section>

    <div v-if="loading" class="loading-state">正在加载链轨迹...</div>
    <div v-else-if="error" class="error-state">{{ error }}</div>

    <template v-else>
      <section class="chain-layout">
        <aside class="chain-layout__side">
          <article class="workspace-card glass-panel">
            <div class="workspace-card__header">
              <div>
                <p class="section-kicker">上链规则</p>
                <h2 class="section-title">上链边界</h2>
              </div>
            </div>

            <div v-if="policies.length" class="policy-list">
              <article v-for="policy in policies" :key="policy.eventType" class="policy-card">
                <div class="policy-card__header">
                  <strong>{{ formatChainEventLabel(policy.eventType) }}</strong>
                  <span class="status-chip" :class="{ 'status-chip--danger': policy.anchorPolicy === 'audit-only' }">
                    {{ formatChainPolicyLabel(policy.anchorPolicy) }}
                  </span>
                </div>
                <p>
                  {{ policy.anchorPolicy === 'required'
                    ? '该动作会被平台视为强制上链事项。'
                    : policy.anchorPolicy === 'audit-only'
                      ? '该动作当前只保留审计记录，不进入链锚点。'
                      : '该动作允许保留为可选上链，用于后续扩展。'
                  }}
                </p>
              </article>
            </div>
            <div v-else class="empty-state">当前没有可展示的链策略。</div>
          </article>

          <article class="workspace-card glass-panel">
            <div class="workspace-card__header">
              <div>
                <p class="section-kicker">检索条件</p>
                <h2 class="section-title">过滤器</h2>
              </div>
            </div>

            <form class="form-grid" @submit.prevent="loadRecords">
              <label>
                <span>数据集 ID</span>
                <input v-model="filters.datasetId" type="text" />
              </label>
              <label>
                <span>事件类型</span>
                <select v-model="filters.eventType">
                  <option value="">全部类型</option>
                  <option value="ACCESS_APPROVED">访问已批准</option>
                  <option value="ACCESS_REVOKED">访问已撤销</option>
                  <option value="TRAINING_COMPLETED">训练已完成</option>
                  <option value="TRAINING_FAILED">训练失败</option>
                  <option value="DESTRUCTION_STORAGE_PURGED">存储已清理</option>
                  <option value="MODEL_REGISTERED">模型已登记</option>
                  <option value="MODEL_GOVERNED">模型已治理</option>
                </select>
              </label>
              <label>
                <span>上链状态</span>
                <select v-model="filters.anchorStatus">
                  <option value="">全部状态</option>
                  <option value="anchored">已上链</option>
                  <option value="failed">失败</option>
                </select>
              </label>
              <label>
                <span>业务状态</span>
                <select v-model="filters.businessStatus">
                  <option value="">全部状态</option>
                  <option value="approved">已批准</option>
                  <option value="revoked">已撤销</option>
                  <option value="succeeded">已成功</option>
                  <option value="failed">失败</option>
                  <option value="completed">已完成</option>
                  <option value="candidate">候选</option>
                  <option value="active">已激活</option>
                  <option value="archived">已归档</option>
                </select>
              </label>
              <label>
                <span>交易哈希</span>
                <input v-model="filters.chainTxHash" type="text" placeholder="支持输入局部 tx hash" />
              </label>

              <div class="form-grid__actions">
                <button type="submit" class="form-grid__submit">刷新链轨迹</button>
                <button type="button" class="form-grid__secondary" @click="resetFilters">清空条件</button>
              </div>
            </form>
          </article>
        </aside>

        <div class="chain-layout__main">
          <article class="workspace-card glass-panel">
            <div class="workspace-card__header">
              <div>
                <p class="section-kicker">记录列表</p>
                <h2 class="section-title">链业务记录</h2>
              </div>
              <span class="status-chip">{{ records.length }} 条记录</span>
            </div>

            <p v-if="actionMessage" class="workspace-card__flash">{{ actionMessage }}</p>

            <div v-if="records.length" class="chain-list">
              <article
                v-for="record in records"
                :key="record.id"
                class="chain-card"
                :class="{ 'chain-card--focus': focusModelId && record.referenceId === focusModelId }"
              >
                <div class="chain-card__header">
                  <div>
                    <strong>{{ formatChainEventLabel(record.eventType) }}</strong>
                    <p>{{ record.referenceId }} · {{ record.actorId }} · {{ formatRoleLabel(record.actorRole) }}</p>
                  </div>
                  <div class="chain-card__chips">
                    <span class="status-chip" :class="{ 'status-chip--danger': record.anchorPolicy === 'audit-only' }">
                      {{ formatChainPolicyLabel(record.anchorPolicy) }}
                    </span>
                    <span class="status-chip" :class="{ 'status-chip--danger': record.anchorStatus !== 'anchored' && record.anchorStatus !== 'policy-skipped' }">
                      {{ formatRequestStatusLabel(record.anchorStatus) }}
                    </span>
                  </div>
                </div>

                <dl class="chain-card__details">
                  <div>
                    <dt>数据集</dt>
                    <dd>{{ record.datasetId }}</dd>
                  </div>
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
                    <dt>合约</dt>
                    <dd>{{ formatContractLabel(record.contractName || '未声明') }}</dd>
                  </div>
                  <div>
                    <dt>合约地址</dt>
                    <dd>{{ record.contractAddress || '暂无' }}</dd>
                  </div>
                  <div>
                    <dt>交易哈希</dt>
                    <dd>{{ record.chainTxHash || '等待上链成功' }}</dd>
                  </div>
                  <div>
                    <dt>事件哈希</dt>
                    <dd>{{ record.eventHash || '暂无' }}</dd>
                  </div>
                </dl>

                <p v-if="record.detail" class="chain-card__detail">{{ record.detail }}</p>
                <p v-if="record.anchorError" class="chain-card__error">失败原因：{{ record.anchorError }}</p>

                <div class="chain-card__actions">
                  <RouterLink class="chain-card__link" :to="`/datasets/${record.datasetId}`">打开数据详情</RouterLink>
                  <RouterLink class="chain-card__link" :to="auditLinkFor(record)">打开审计记录</RouterLink>
                  <RouterLink
                    v-if="record.eventType.startsWith('ACCESS_')"
                    class="chain-card__link"
                    :to="requestLinkFor(record)"
                  >
                    打开访问申请
                  </RouterLink>
                  <RouterLink
                    v-if="record.eventType.startsWith('TRAINING_')"
                    class="chain-card__link"
                    :to="{ path: '/training-jobs', query: { source: 'chain-record', datasetId: record.datasetId } }"
                  >
                    打开训练任务
                  </RouterLink>
                  <RouterLink
                    v-if="record.eventType.startsWith('MODEL_')"
                    class="chain-card__link"
                    :to="modelLinkFor(record)"
                  >
                    打开模型库
                  </RouterLink>
                  <button
                    v-if="canGovernModelFromRecord(record)"
                    type="button"
                    class="chain-card__action-button"
                    :disabled="governingModelId === record.referenceId"
                    @click="governModel(record, 'active')"
                  >
                    {{ governingModelId === record.referenceId ? '提交中...' : '激活模型' }}
                  </button>
                  <button
                    v-if="canGovernModelFromRecord(record)"
                    type="button"
                    class="chain-card__action-button"
                    :disabled="governingModelId === record.referenceId"
                    @click="governModel(record, 'archived')"
                  >
                    {{ governingModelId === record.referenceId ? '提交中...' : '归档模型' }}
                  </button>
                  <button
                    v-if="record.anchorStatus === 'failed' && record.anchorPolicy !== 'audit-only'"
                    type="button"
                    class="chain-card__action-button"
                    :disabled="retryingRecordId === record.id"
                    @click="handleRetry(record)"
                  >
                    {{ retryingRecordId === record.id ? '重试中...' : '重试上链' }}
                  </button>
                </div>

                <time class="chain-card__time">{{ formatTime(record.anchoredAt) }}</time>
              </article>
            </div>
            <div v-else class="empty-state">{{ roleGuide.empty }}</div>
          </article>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.chain-page {
  display: grid;
  gap: 18px;
}

.hero-panel {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(320px, 0.85fr);
  gap: 20px;
  padding: var(--space-hero);
  border-radius: var(--radius-hero);
  background: var(--panel-gradient);
}

.hero-panel__copy,
.hero-panel__rail,
.summary-strip,
.chain-layout,
.chain-layout__side,
.chain-layout__main,
.chain-list,
.policy-list,
.hero-lane__steps {
  display: grid;
  gap: var(--space-list);
}

.hero-panel h1,
.section-title {
  margin: 0;
  font-family: var(--display);
}

.hero-panel h1 {
  color: var(--text-strong);
  font-size: var(--page-heading-size);
  font-weight: 600;
  line-height: var(--page-heading-line-height);
  letter-spacing: var(--page-heading-letter-spacing);
  text-wrap: balance;
}

.hero-panel__lede,
.workspace-card__lede,
.workspace-card__flash,
.hero-panel__hint {
  color: var(--text-muted);
  font-size: var(--supporting-text-size);
  line-height: var(--supporting-text-line-height);
}

.workspace-card__flash {
  margin: 0 0 14px;
  padding: var(--space-subpanel);
  border: 1px solid rgba(49, 87, 102, 0.16);
  border-radius: var(--radius-subpanel);
  background: var(--panel-soft-gradient);
}

.hero-panel__hint {
  margin: 0;
  padding: var(--space-subpanel);
  border-radius: var(--radius-subpanel);
  border: 1px solid var(--line);
  background: var(--panel-soft-gradient);
}

.hero-panel__actions,
.workspace-card__header,
.chain-card__header,
.form-grid__actions,
.chain-card__actions {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.hero-panel__secondary,
.form-grid__secondary,
.chain-card__link,
.chain-card__action-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
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
  cursor: pointer;
}

.chain-card__action-button:disabled {
  opacity: 0.65;
  cursor: wait;
}

.hero-panel__guide {
  display: grid;
  gap: 8px;
  max-width: 720px;
  padding: var(--space-subpanel);
  border-radius: var(--radius-subpanel);
  border: 1px solid var(--line-warm);
  background: var(--panel-soft-gradient);
}

.hero-panel__guide span,
.summary-strip__card span,
.hero-spotlight__kicker,
.hero-spotlight__meta span,
.chain-card dt {
  display: block;
  color: var(--text-faint);
  font-size: var(--field-label-size);
  letter-spacing: var(--field-label-letter-spacing);
  text-transform: uppercase;
}

.hero-panel__guide strong,
.summary-strip__card strong,
.hero-spotlight__headline strong,
.hero-spotlight__meta strong,
.chain-card strong,
.hero-lane__step strong {
  display: block;
  font-family: var(--body);
}

.hero-panel__guide strong {
  font-size: 0.94rem;
  line-height: 1.6;
}

.summary-strip {
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
}

.summary-strip__card,
.hero-spotlight,
.hero-lane,
.policy-card,
.chain-card {
  padding: var(--space-card);
  border-radius: var(--radius-panel);
  border: 1px solid var(--line);
  background: var(--panel-gradient);
}

.chain-card--focus {
  border-color: rgba(156, 107, 54, 0.24);
  box-shadow: inset 0 0 0 1px rgba(156, 107, 54, 0.08);
}

.summary-strip__card strong {
  margin-top: 10px;
  font-size: clamp(1.8rem, 3vw, 2.4rem);
}

.hero-spotlight,
.hero-lane {
  display: grid;
  gap: 14px;
}

.hero-spotlight__headline {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.policy-card__header,
.chain-card__chips {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.hero-spotlight__context,
.hero-spotlight__reason,
.hero-lane__step p,
.policy-card p,
.chain-card__detail,
.chain-card__time,
.chain-card__error,
.chain-card__header p,
.chain-card dd {
  margin: 0;
  color: var(--text-muted);
  font-size: var(--supporting-text-size);
  line-height: var(--supporting-text-line-height);
}

.hero-spotlight__meta,
.chain-card__details,
.form-grid,
.policy-list {
  display: grid;
  gap: var(--space-list-tight);
}

.hero-spotlight__meta {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.hero-spotlight__meta div,
.chain-card__details div,
.hero-lane__step {
  padding: var(--space-subpanel);
  border-radius: var(--radius-subpanel);
  border: 1px solid var(--line);
  background: var(--panel-soft-gradient);
}

.chain-layout {
  grid-template-columns: minmax(320px, 360px) minmax(0, 1fr);
}

.workspace-card {
  padding: 20px;
  border-radius: var(--radius-panel);
}

.form-grid label {
  display: grid;
  gap: 8px;
}

.form-grid span {
  color: var(--text-faint);
  font-size: var(--field-label-size);
  letter-spacing: var(--field-label-letter-spacing);
  text-transform: uppercase;
}

.form-grid input,
.form-grid select {
  width: 100%;
  min-height: var(--field-height);
  padding: var(--space-field-x);
  border: 1px solid var(--line);
  border-radius: var(--radius-control);
  background: var(--bg-panel);
  color: var(--text-main);
}

.form-grid__submit {
  min-height: var(--control-height);
  padding: var(--space-button);
  border: 1px solid var(--line-warm);
  border-radius: var(--radius-pill);
  background: var(--button-warm-gradient);
  color: var(--text-main);
  font-family: var(--body);
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.chain-card__details {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin-top: 14px;
}

.chain-card__detail {
  margin-top: 14px;
  line-height: 1.7;
}

.chain-card__actions {
  justify-content: flex-start;
  flex-wrap: wrap;
  margin-top: 14px;
}

.chain-card__time {
  display: block;
  margin-top: 12px;
  font-size: 0.76rem;
  letter-spacing: 0.04em;
}

.chain-card__error {
  margin-top: 10px;
  color: var(--danger);
}

@media (max-width: 1040px) {
  .hero-panel,
  .chain-layout,
  .summary-strip,
  .hero-spotlight__meta,
  .chain-card__details {
    grid-template-columns: 1fr;
  }

  .hero-panel__actions,
  .workspace-card__header,
  .chain-card__header,
  .form-grid__actions {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
