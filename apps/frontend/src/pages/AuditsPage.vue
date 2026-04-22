<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import PageHero from '../components/PageHero.vue'
import SurfaceCard from '../components/SurfaceCard.vue'
import { getAudits, updateModelGovernance } from '../api/client'
import { useAsyncView } from '../composables/useAsyncView'
import { useActorProfile } from '../composables/useActorProfile'
import { useToast } from '../composables/useToast'
import type { AuditEvent } from '../types/api'
import {
  formatAuditActionLabel,
  formatOrganizationLabel,
  formatRequestStatusLabel,
  formatRoleLabel,
} from '../utils/labels'

const { actorProfile } = useActorProfile()
const route = useRoute()
const { pushToast } = useToast()

const { loading, error, run: runAuditLoad } = useAsyncView({
  initialLoading: true,
})
const auditRows = ref<AuditEvent[]>([])
const governingModelId = ref('')
const focusModelId = computed(() =>
  typeof route.query.focusModelId === 'string' ? route.query.focusModelId : '',
)
const focusedModelAudit = computed(() => {
  if (!focusModelId.value) {
    return null
  }
  return auditRows.value.find((event) => extractModelId(event.detail) === focusModelId.value) ?? null
})

const filters = reactive({
  datasetId: typeof route.query.datasetId === 'string' ? route.query.datasetId : '',
  actorId: typeof route.query.actorId === 'string' ? route.query.actorId : '',
  action: typeof route.query.action === 'string' ? route.query.action : '',
  status: typeof route.query.status === 'string' ? route.query.status : '',
  actorOrg: typeof route.query.actorOrg === 'string' ? route.query.actorOrg : '',
})

const scopeLabel = computed(() => {
  const role = actorProfile.value.actorRole.toLowerCase()
  if (role === 'admin') {
    return '全局监管视图'
  }
  if (role === 'owner' || role === 'approver') {
    return `机构审计视图 · ${formatOrganizationLabel(actorProfile.value.actorOrg)}`
  }
  return `个人审计视图 · ${actorProfile.value.actorId}`
})
const roleGuide = computed(() => {
  const role = actorProfile.value.actorRole.toLowerCase()
  if (role === 'admin') {
    return {
      note: '优先关注登录、账户治理和训练失败事件，监管视角下可以直接回跳训练页、数据详情或链轨迹继续排查。',
      emptyStream: '当前没有审计事件。先执行一次登录、建号、审批或训练动作。',
      emptyTraining: '当前没有训练类事件。创建或刷新一条训练任务后，这里会出现对应轨迹。',
    }
  }
  if (role === 'owner' || role === 'approver') {
    return {
      note: '这里会先收敛本机构的审批、训练和账户动作，适合从机构侧回看治理闭环。',
      emptyStream: '当前机构范围内没有审计事件。下一次审批、训练或账户操作会自动出现在这里。',
      emptyTraining: '当前机构范围内没有训练类事件。可先批准一条申请，再带入训练页触发任务。',
    }
  }
  return {
    note: '这里主要用来回看你自己的登录、申请、训练与读取轨迹，不会暴露其他角色的事件。',
    emptyStream: '当前个人范围内没有审计事件。登录、申请访问或训练后会逐步累积记录。',
    emptyTraining: '当前个人范围内没有训练类事件。先从已批准的数据集发起训练。',
  }
})
const intakeHint = computed(() => {
  const source = String(route.query.source ?? '')
  if (!source) {
    return ''
  }
  if (source === 'chain-record') {
    return filters.action
      ? `该视图来自链轨迹，已预填 ${filters.action} 对应的审计筛选。`
      : '该视图来自链轨迹，可继续核对链记录对应的审计事件。'
  }
  if (source === 'model-record' && focusModelId.value) {
    return `该视图来自模型库，已聚焦 ${focusModelId.value} 对应的治理审计。`
  }
  return '当前筛选条件由上一页带入。'
})

const auditStats = computed(() => [
  { label: '事件总数', value: auditRows.value.length },
  { label: '成功事件', value: auditRows.value.filter((row) => row.status === 'success').length },
  {
    label: '身份事件',
    value: auditRows.value.filter((row) => row.action.startsWith('AUTH_') || row.action.startsWith('ACCOUNT_')).length,
  },
  {
    label: '训练事件',
    value: auditRows.value.filter((row) => row.action.startsWith('TRAINING_')).length,
  },
  {
    label: '模型事件',
    value: auditRows.value.filter((row) => row.action.startsWith('MODEL_')).length,
  },
])
const trainingAuditRows = computed(() => auditRows.value.filter((row) => row.action.startsWith('TRAINING_')).slice(0, 4))
const modelAuditRows = computed(() => auditRows.value.filter((row) => row.action.startsWith('MODEL_')).slice(0, 4))

const spotlightEvent = computed(() => auditRows.value[0] ?? null)
const actionPreview = computed(() => {
  const counts = new Map<string, number>()
  for (const row of auditRows.value) {
    counts.set(row.action, (counts.get(row.action) ?? 0) + 1)
  }

  return [...counts.entries()]
    .sort((left, right) => right[1] - left[1])
    .slice(0, 4)
    .map(([action, count]) => ({
      action,
      count,
    }))
})

function formatTime(value: string) {
  return new Date(value).toLocaleString()
}

function setActionFilter(value: string) {
  filters.action = value
  void loadAudits()
}

function extractTrainingJobId(detail: string | null | undefined) {
  if (!detail) {
    return ''
  }
  const match = detail.match(/\b(tj-\d+)\b/i)
  return match ? match[1] : ''
}

function trainingLinkFor(event: AuditEvent) {
  return {
    path: '/training-jobs',
    query: {
      source: 'audit',
      datasetId: event.datasetId ?? '',
      focusJobId: extractTrainingJobId(event.detail),
    },
  }
}

function extractModelId(detail: string | null | undefined) {
  if (!detail) {
    return ''
  }
  const match = detail.match(/\b(mr-\d+)\b/i)
  return match ? match[1] : ''
}

function modelLinkFor(event: AuditEvent) {
  return {
    path: '/model-records',
    query: {
      source: 'audit',
      datasetId: event.datasetId ?? '',
      focusModelId: extractModelId(event.detail),
    },
  }
}

function canGovernModelFromAudit(event: AuditEvent) {
  const modelId = extractModelId(event.detail)
  if (!event.action.startsWith('MODEL_') || !modelId) {
    return false
  }
  const role = actorProfile.value.actorRole.toLowerCase()
  if (role === 'admin') {
    return true
  }
  return (role === 'owner' || role === 'approver') && actorProfile.value.actorOrg.toLowerCase() === event.actorOrg.toLowerCase()
}

async function governModelFromAudit(event: AuditEvent, status: 'active' | 'archived') {
  const modelId = extractModelId(event.detail)
  if (!modelId) {
    return
  }

  governingModelId.value = modelId
  try {
    const updated = await updateModelGovernance(modelId, actorProfile.value, {
      status,
      note:
        status === 'active'
          ? 'Promoted from audit workspace after event review.'
          : 'Archived from audit workspace after event review.',
    })
    pushToast({
      tone: 'success',
      title: '模型治理已更新',
      message: `${updated.id} 已在审计中心切换为 ${formatRequestStatusLabel(updated.governanceStatus)}。`,
    })
    await loadAudits()
  } catch (governError) {
    const message = governError instanceof Error ? governError.message : '模型治理失败。'
    pushToast({
      tone: 'warning',
      title: '模型治理失败',
      message,
    })
  } finally {
    governingModelId.value = ''
  }
}

function resetFilters() {
  filters.datasetId = ''
  filters.actorId = ''
  filters.action = ''
  filters.status = ''
  filters.actorOrg = ''
  void loadAudits()
}

function applyRouteFilters() {
  filters.datasetId = typeof route.query.datasetId === 'string' ? route.query.datasetId : ''
  filters.actorId = typeof route.query.actorId === 'string' ? route.query.actorId : ''
  filters.action = typeof route.query.action === 'string' ? route.query.action : ''
  filters.status = typeof route.query.status === 'string' ? route.query.status : ''
  filters.actorOrg = typeof route.query.actorOrg === 'string' ? route.query.actorOrg : ''
}

async function loadAudits() {
  const rows = await runAuditLoad(
    () =>
      getAudits(actorProfile.value, {
      datasetId: filters.datasetId.trim() || undefined,
      actorId: filters.actorId.trim() || undefined,
      action: filters.action || undefined,
      status: filters.status || undefined,
      actorOrg: filters.actorOrg.trim() || undefined,
      }),
    '加载审计事件失败。',
  )

  if (!rows) {
    return
  }

  auditRows.value = rows
}

onMounted(() => {
  applyRouteFilters()
  void loadAudits()
})

watch(
  () => route.query,
  () => {
    applyRouteFilters()
    void loadAudits()
  },
)
</script>

<template>
  <div class="audits-page">
    <PageHero
      kicker="审计中心"
      title="把关键操作收进一条可追溯时间线。"
      :lede="`当前身份是 ${actorProfile.actorId} / ${formatRoleLabel(actorProfile.actorRole)} / ${formatOrganizationLabel(actorProfile.actorOrg)}。这个页面是独立审计中心，不再依附单个数据详情页。`"
      layout="balanced"
    >
      <template #actions>
        <span class="status-chip">{{ scopeLabel }}</span>
        <RouterLink class="hero-panel__secondary" to="/">返回总览</RouterLink>
      </template>

      <p v-if="intakeHint" class="hero-panel__hint">{{ intakeHint }}</p>
      <div class="hero-panel__guide">
        <span>当前提示</span>
        <strong>{{ roleGuide.note }}</strong>
      </div>

      <div class="summary-strip">
        <article v-for="stat in auditStats" :key="stat.label" class="summary-strip__card">
          <span>{{ stat.label }}</span>
          <strong>{{ stat.value }}</strong>
        </article>
      </div>

      <template #rail>
        <article class="hero-spotlight">
          <p class="hero-spotlight__kicker">{{ focusedModelAudit ? '模型治理焦点' : '最新事件' }}</p>
          <template v-if="focusedModelAudit || spotlightEvent">
            <div class="hero-spotlight__headline">
              <strong>{{ formatAuditActionLabel((focusedModelAudit ?? spotlightEvent)?.action) }}</strong>
              <span class="status-chip" :class="{ 'status-chip--danger': (focusedModelAudit ?? spotlightEvent)?.status !== 'success' }">
                {{ formatRequestStatusLabel((focusedModelAudit ?? spotlightEvent)?.status) }}
              </span>
            </div>
            <p class="hero-spotlight__context">
              {{ (focusedModelAudit ?? spotlightEvent)?.actorId }} ·
              {{ formatRoleLabel((focusedModelAudit ?? spotlightEvent)?.actorRole) }} ·
              {{ formatOrganizationLabel((focusedModelAudit ?? spotlightEvent)?.actorOrg) }}
            </p>
            <p class="hero-spotlight__reason">{{ (focusedModelAudit ?? spotlightEvent)?.detail || '该事件未附带额外说明。' }}</p>
            <div class="hero-spotlight__meta">
              <div>
                <span>对象</span>
                <strong>{{ (focusedModelAudit ?? spotlightEvent)?.datasetId || '平台级事件' }}</strong>
              </div>
              <div>
                <span>动作</span>
                <strong>{{ (focusedModelAudit ?? spotlightEvent)?.action }}</strong>
              </div>
              <div>
                <span>时间</span>
                <strong>{{ formatTime((focusedModelAudit ?? spotlightEvent)?.createdAt || '') }}</strong>
              </div>
            </div>
          </template>
          <p v-else class="hero-spotlight__empty">{{ roleGuide.emptyStream }}</p>
        </article>

        <article class="hero-lane">
          <div class="hero-lane__header">
            <div>
              <p class="section-kicker">事件分布</p>
              <h2 class="section-title">高频动作</h2>
            </div>
          </div>
          <div class="hero-lane__steps">
            <div v-for="item in actionPreview" :key="item.action" class="hero-lane__step">
              <strong>{{ formatAuditActionLabel(item.action) }}</strong>
              <p>{{ item.count }} 条事件</p>
            </div>
            <p v-if="!actionPreview.length" class="hero-lane__empty">暂无高频动作可展示。</p>
          </div>
        </article>
      </template>
    </PageHero>

    <div v-if="loading" class="loading-state">正在加载审计中心...</div>
    <div v-else-if="error" class="error-state">{{ error }}</div>

    <template v-else>
      <section class="audits-layout">
        <aside class="audits-layout__side">
          <SurfaceCard
            kicker="检索条件"
            title="过滤器"
            lede="后端会先按你的角色限制可见范围，再应用这些筛选项。"
          >

            <form class="form-grid" @submit.prevent="loadAudits">
              <label>
                <span>数据集 ID</span>
                <input v-model="filters.datasetId" type="text" />
              </label>
              <label>
                <span>用户 ID</span>
                <input v-model="filters.actorId" type="text" />
              </label>
              <label>
                <span>动作</span>
                <select v-model="filters.action">
                  <option value="">全部动作</option>
                  <option value="AUTH_LOGIN_SUCCEEDED">AUTH_LOGIN_SUCCEEDED</option>
                  <option value="AUTH_REFRESH_SUCCEEDED">AUTH_REFRESH_SUCCEEDED</option>
                  <option value="ACCOUNT_REGISTERED">ACCOUNT_REGISTERED</option>
                  <option value="ACCOUNT_UPDATED">ACCOUNT_UPDATED</option>
                  <option value="ACCESS_REQUEST_CREATED">ACCESS_REQUEST_CREATED</option>
                  <option value="ACCESS_REQUEST_APPROVED">ACCESS_REQUEST_APPROVED</option>
                  <option value="DESTRUCTION_REQUEST_CREATED">DESTRUCTION_REQUEST_CREATED</option>
                  <option value="DESTRUCTION_REQUEST_APPROVED">DESTRUCTION_REQUEST_APPROVED</option>
                  <option value="DESTRUCTION_REQUEST_REJECTED">DESTRUCTION_REQUEST_REJECTED</option>
                  <option value="DESTRUCTION_EXECUTED">DESTRUCTION_EXECUTED</option>
                  <option value="DESTRUCTION_STORAGE_PURGE_REQUESTED">DESTRUCTION_STORAGE_PURGE_REQUESTED</option>
                  <option value="DESTRUCTION_STORAGE_PURGE_COMPLETED">DESTRUCTION_STORAGE_PURGE_COMPLETED</option>
                  <option value="DESTRUCTION_STORAGE_PURGE_FAILED">DESTRUCTION_STORAGE_PURGE_FAILED</option>
                  <option value="BRAIN_ACTIVITY_READ">BRAIN_ACTIVITY_READ</option>
                  <option value="TRAINING_RUN_CREATED">TRAINING_RUN_CREATED</option>
                  <option value="TRAINING_RUN_COMPLETED">TRAINING_RUN_COMPLETED</option>
                  <option value="TRAINING_RUN_FAILED">TRAINING_RUN_FAILED</option>
                  <option value="MODEL_VERSION_REGISTERED">MODEL_VERSION_REGISTERED</option>
                  <option value="MODEL_GOVERNANCE_UPDATED">MODEL_GOVERNANCE_UPDATED</option>
                </select>
              </label>
              <label>
                <span>状态</span>
                <select v-model="filters.status">
                  <option value="">全部状态</option>
                  <option value="success">success</option>
                  <option value="failed">failed</option>
                </select>
              </label>
              <label>
                <span>机构</span>
                <input v-model="filters.actorOrg" type="text" :disabled="actorProfile.actorRole.toLowerCase() !== 'admin'" />
              </label>

              <div class="form-grid__actions">
                <button type="submit" class="form-grid__submit">刷新审计流</button>
                <button type="button" class="form-grid__secondary" @click="resetFilters">清空条件</button>
              </div>
            </form>
          </SurfaceCard>

          <SurfaceCard
            kicker="训练筛选"
            title="训练事件"
            lede="快速切到训练相关审计，再一键跳回训练编排页继续追踪任务。"
          >

            <div class="quick-actions">
              <button type="button" class="quick-action" @click="setActionFilter('TRAINING_RUN_CREATED')">只看创建</button>
              <button type="button" class="quick-action" @click="setActionFilter('TRAINING_RUN_COMPLETED')">只看完成</button>
              <button type="button" class="quick-action" @click="setActionFilter('TRAINING_RUN_FAILED')">只看失败</button>
              <button type="button" class="quick-action" @click="setActionFilter('')">恢复全部</button>
            </div>

            <div v-if="trainingAuditRows.length" class="training-preview">
              <RouterLink
                v-for="event in trainingAuditRows"
                :key="event.id"
                class="training-preview__item"
                :to="trainingLinkFor(event)"
              >
                <span>{{ formatAuditActionLabel(event.action) }}</span>
                <strong>{{ extractTrainingJobId(event.detail) || event.datasetId || '训练任务' }}</strong>
                <small>{{ event.detail || '打开训练编排页继续查看。' }}</small>
              </RouterLink>
            </div>
            <div v-else class="empty-state">{{ roleGuide.emptyTraining }}</div>

            <div v-if="modelAuditRows.length" class="training-preview">
              <RouterLink
                v-for="event in modelAuditRows"
                :key="`model-${event.id}`"
                class="training-preview__item"
                :to="modelLinkFor(event)"
              >
                <span>{{ formatAuditActionLabel(event.action) }}</span>
                <strong>{{ extractModelId(event.detail) || event.datasetId || '模型记录' }}</strong>
                <small>{{ event.detail || '打开模型库继续查看。' }}</small>
              </RouterLink>
            </div>
          </SurfaceCard>
        </aside>

        <div class="audits-layout__main">
          <SurfaceCard kicker="审计列表" title="审计事件流">
            <template #meta>
              <span class="status-chip">{{ auditRows.length }} 条记录</span>
            </template>

            <div v-if="auditRows.length" class="audit-list">
              <article
                v-for="event in auditRows"
                :key="event.id"
                class="audit-card"
                :class="{ 'audit-card--focus': focusModelId && extractModelId(event.detail) === focusModelId }"
              >
                <div class="audit-card__header">
                  <div>
                    <strong>{{ formatAuditActionLabel(event.action) }}</strong>
                    <p>{{ event.actorId }} · {{ formatRoleLabel(event.actorRole) }}</p>
                  </div>
                  <span class="status-chip" :class="{ 'status-chip--danger': event.status !== 'success' }">
                    {{ formatRequestStatusLabel(event.status) }}
                  </span>
                </div>

                <dl class="audit-card__details">
                  <div>
                    <dt>机构</dt>
                    <dd>{{ formatOrganizationLabel(event.actorOrg) }}</dd>
                  </div>
                  <div>
                    <dt>对象</dt>
                    <dd>{{ event.datasetId || '平台级事件' }}</dd>
                  </div>
                  <div>
                    <dt>原始动作</dt>
                    <dd>{{ event.action }}</dd>
                  </div>
                  <div>
                    <dt>发生时间</dt>
                    <dd>{{ formatTime(event.createdAt) }}</dd>
                  </div>
                </dl>

                <p class="audit-card__detail">{{ event.detail || '该事件未附带额外说明。' }}</p>

                <div v-if="event.action.startsWith('TRAINING_')" class="audit-card__actions">
                  <RouterLink class="audit-card__link" :to="trainingLinkFor(event)">
                    打开训练任务
                  </RouterLink>
                  <RouterLink v-if="event.datasetId" class="audit-card__link" :to="`/datasets/${event.datasetId}`">
                    打开数据详情
                  </RouterLink>
                </div>
                <div v-else-if="event.action.startsWith('MODEL_')" class="audit-card__actions">
                  <RouterLink class="audit-card__link" :to="modelLinkFor(event)">
                    打开模型库
                  </RouterLink>
                  <RouterLink v-if="event.datasetId" class="audit-card__link" :to="`/datasets/${event.datasetId}`">
                    打开数据详情
                  </RouterLink>
                  <button
                    v-if="canGovernModelFromAudit(event)"
                    type="button"
                    class="audit-card__link audit-card__button"
                    :disabled="governingModelId === extractModelId(event.detail)"
                    @click="governModelFromAudit(event, 'active')"
                  >
                    {{ governingModelId === extractModelId(event.detail) ? '提交中...' : '激活模型' }}
                  </button>
                  <button
                    v-if="canGovernModelFromAudit(event)"
                    type="button"
                    class="audit-card__link audit-card__button"
                    :disabled="governingModelId === extractModelId(event.detail)"
                    @click="governModelFromAudit(event, 'archived')"
                  >
                    {{ governingModelId === extractModelId(event.detail) ? '提交中...' : '归档模型' }}
                  </button>
                </div>
              </article>
            </div>

            <div v-else class="empty-state">{{ roleGuide.emptyStream }}</div>
          </SurfaceCard>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.audits-page {
  display: grid;
  gap: 18px;
}

.hero-panel__rail,
.summary-strip,
.audits-layout,
.audits-layout__side,
.audits-layout__main,
.audit-list,
.hero-lane__steps {
  display: grid;
  gap: var(--space-list);
}

.section-title {
  margin: 0;
  font-family: var(--display);
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

.hero-panel__hint {
  margin: 0;
  padding: var(--space-subpanel);
  border-radius: var(--radius-subpanel);
  border: 1px solid var(--line);
  background: var(--panel-soft-gradient);
  color: var(--text-muted);
  line-height: 1.7;
}

.hero-panel__guide span {
  color: var(--text-faint);
  font-size: 0.72rem;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.hero-panel__guide strong {
  font-family: var(--body);
  font-size: 0.94rem;
  line-height: 1.6;
}

.audit-card__header,
.form-grid__actions {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.hero-panel__secondary,
.form-grid__secondary {
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
}

.quick-actions,
.training-preview,
.audit-card__actions {
  display: grid;
  gap: 12px;
}

.quick-action,
.audit-card__link,
.training-preview__item {
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
}

.quick-action {
  justify-content: flex-start;
}

.summary-strip {
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
}

.summary-strip__card,
.hero-spotlight,
.hero-lane,
.audit-card {
  padding: var(--space-card);
  border-radius: var(--radius-panel);
  border: 1px solid var(--line);
  background: var(--panel-gradient);
}

.audit-card--focus {
  border-color: rgba(156, 107, 54, 0.24);
  box-shadow: inset 0 0 0 1px rgba(156, 107, 54, 0.08);
}

.summary-strip__card span,
.hero-spotlight__kicker,
.hero-spotlight__meta span,
.audit-card dt {
  display: block;
  color: var(--text-faint);
  font-size: 0.72rem;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.summary-strip__card strong,
.hero-spotlight__headline strong,
.hero-spotlight__meta strong,
.audit-card strong,
.hero-lane__step strong {
  display: block;
  font-family: var(--body);
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

.hero-spotlight__context,
.hero-spotlight__reason,
.hero-lane__step p,
.audit-card__detail {
  margin: 0;
  color: var(--text-muted);
}

.hero-spotlight__meta,
.audit-card__details,
.form-grid {
  display: grid;
  gap: 12px;
}

.hero-spotlight__meta {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.hero-spotlight__meta div,
.audit-card__details div,
.hero-lane__step {
  padding: var(--space-subpanel);
  border-radius: var(--radius-subpanel);
  border: 1px solid var(--line);
  background: var(--panel-soft-gradient);
}

.audits-layout {
  grid-template-columns: minmax(320px, 360px) minmax(0, 1fr);
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

.audit-card__header p,
.audit-card dd {
  margin: 6px 0 0;
  color: var(--text-muted);
}

.audit-card__details {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin-top: 14px;
}

.audit-card__detail {
  margin-top: 14px;
  line-height: 1.7;
}

.training-preview__item {
  display: grid;
  justify-content: flex-start;
  gap: 8px;
  min-height: unset;
  padding: var(--space-subpanel);
  border-radius: var(--radius-subpanel);
  text-transform: none;
}

.training-preview__item span,
.training-preview__item small {
  color: var(--text-muted);
  font-size: 0.82rem;
}

.training-preview__item strong {
  font-family: var(--body);
  color: var(--text-main);
}

.audit-card__actions {
  grid-template-columns: repeat(2, minmax(0, max-content));
  margin-top: 14px;
}

@media (max-width: 1040px) {
  .audits-layout,
  .summary-strip,
  .hero-spotlight__meta,
  .audit-card__details {
    grid-template-columns: 1fr;
  }

  .audit-card__header,
  .form-grid__actions {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
