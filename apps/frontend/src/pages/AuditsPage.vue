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
      emptyStream: '暂无审计事件',
      emptyTraining: '暂无训练事件',
    }
  }
  if (role === 'owner' || role === 'approver') {
    return {
      emptyStream: '暂无审计事件',
      emptyTraining: '暂无训练事件',
    }
  }
  return {
    emptyStream: '暂无审计事件',
    emptyTraining: '暂无训练事件',
  }
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
      kicker="审计"
      title="操作审计"
      layout="balanced"
    >
      <template #actions>
        <span class="status-chip">{{ scopeLabel }}</span>
        <RouterLink class="hero-panel__secondary" to="/">返回总览</RouterLink>
      </template>

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
              <span class="mono-id">{{ (focusedModelAudit ?? spotlightEvent)?.actorId }}</span> ·
              {{ formatRoleLabel((focusedModelAudit ?? spotlightEvent)?.actorRole) }} ·
              {{ formatOrganizationLabel((focusedModelAudit ?? spotlightEvent)?.actorOrg) }}
            </p>
            <p v-if="(focusedModelAudit ?? spotlightEvent)?.detail" class="hero-spotlight__reason">
              {{ (focusedModelAudit ?? spotlightEvent)?.detail }}
            </p>
            <div class="hero-spotlight__meta">
              <div>
                <span>对象</span>
                <strong class="mono-id">{{ (focusedModelAudit ?? spotlightEvent)?.datasetId || '平台级事件' }}</strong>
              </div>
              <div>
                <span>动作</span>
                <strong class="mono-id">{{ (focusedModelAudit ?? spotlightEvent)?.action }}</strong>
              </div>
              <div>
                <span>时间</span>
                <strong class="mono-id">{{ formatTime((focusedModelAudit ?? spotlightEvent)?.createdAt || '') }}</strong>
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
              <p><span class="mono-id">{{ item.count }}</span> 条事件</p>
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
          <SurfaceCard kicker="检索" title="过滤器">

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
                  <option value="AUTH_LOGIN_SUCCEEDED">登录成功</option>
                  <option value="AUTH_REFRESH_SUCCEEDED">会话刷新成功</option>
                  <option value="ACCOUNT_REGISTERED">账户已注册</option>
                  <option value="ACCOUNT_UPDATED">账户已更新</option>
                  <option value="ACCESS_REQUEST_CREATED">访问申请已创建</option>
                  <option value="ACCESS_REQUEST_APPROVED">访问申请已批准</option>
                  <option value="DESTRUCTION_REQUEST_CREATED">销毁申请已创建</option>
                  <option value="DESTRUCTION_REQUEST_APPROVED">销毁申请已批准</option>
                  <option value="DESTRUCTION_REQUEST_REJECTED">销毁申请已拒绝</option>
                  <option value="DESTRUCTION_EXECUTED">销毁已执行</option>
                  <option value="DESTRUCTION_STORAGE_PURGE_REQUESTED">存储清理已请求</option>
                  <option value="DESTRUCTION_STORAGE_PURGE_COMPLETED">存储清理已完成</option>
                  <option value="DESTRUCTION_STORAGE_PURGE_FAILED">存储清理失败</option>
                  <option value="BRAIN_ACTIVITY_READ">脑活跃度已读取</option>
                  <option value="TRAINING_RUN_CREATED">训练任务已创建</option>
                  <option value="TRAINING_RUN_COMPLETED">训练任务已完成</option>
                  <option value="TRAINING_RUN_FAILED">训练任务失败</option>
                  <option value="MODEL_VERSION_REGISTERED">模型版本已登记</option>
                  <option value="MODEL_GOVERNANCE_UPDATED">模型治理已更新</option>
                </select>
              </label>
              <label>
                <span>状态</span>
                <select v-model="filters.status">
                  <option value="">全部状态</option>
                  <option value="success">成功</option>
                  <option value="failed">失败</option>
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

          <SurfaceCard kicker="训练筛选" title="训练事件">

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
                <strong class="mono-id">{{ extractTrainingJobId(event.detail) || event.datasetId || '训练任务' }}</strong>
                <small v-if="event.detail">{{ event.detail }}</small>
              </RouterLink>
            </div>
            <div v-else class="empty-state">{{ roleGuide.emptyTraining }}</div>

            <div v-if="modelAuditRows.length" class="training-preview">
              <RouterLink
                v-for="event in modelAuditRows"
                :key="`model-${event.id}`"
                class="training-preview__item training-preview__item--model"
                :to="modelLinkFor(event)"
              >
                <span>{{ formatAuditActionLabel(event.action) }}</span>
                <strong class="mono-id">{{ extractModelId(event.detail) || event.datasetId || '模型记录' }}</strong>
                <small v-if="event.detail">{{ event.detail }}</small>
              </RouterLink>
            </div>
          </SurfaceCard>
        </aside>

        <div class="audits-layout__main">
          <SurfaceCard kicker="列表" title="审计事件流">
            <template #meta>
              <span class="status-chip">{{ auditRows.length }} 条记录</span>
            </template>

            <div v-if="auditRows.length" class="audit-list">
              <article
                v-for="event in auditRows"
                :key="event.id"
                class="audit-card"
                :class="{
                  'audit-card--focus': focusModelId && extractModelId(event.detail) === focusModelId,
                  'audit-card--alert': event.status !== 'success',
                }"
              >
                <span class="audit-card__node" aria-hidden="true"></span>
                <div class="audit-card__header">
                  <div>
                    <strong>{{ formatAuditActionLabel(event.action) }}</strong>
                    <p><span class="mono-id">{{ event.actorId }}</span> · {{ formatRoleLabel(event.actorRole) }}</p>
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
                    <dd class="mono-id">{{ event.datasetId || '平台级事件' }}</dd>
                  </div>
                  <div>
                    <dt>原始动作</dt>
                    <dd class="mono-id">{{ event.action }}</dd>
                  </div>
                  <div>
                    <dt>发生时间</dt>
                    <dd class="mono-id">{{ formatTime(event.createdAt) }}</dd>
                  </div>
                </dl>

                <p v-if="event.detail" class="audit-card__detail">{{ event.detail }}</p>

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
.hero-lane__steps {
  display: grid;
  gap: var(--space-list);
}

/* Mono data tokens: actor IDs, dataset IDs, raw action codes, timestamps */
.mono-id {
  font-family: var(--mono);
  color: var(--text-strong);
  letter-spacing: 0.01em;
  font-variant-numeric: tabular-nums;
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
  border-radius: var(--radius-control);
  background: var(--bg-panel-soft);
  color: var(--text-main);
  text-decoration: none;
  font-family: var(--body);
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  transition:
    border-color 0.22s ease,
    color 0.22s ease;
}

.hero-panel__secondary:hover,
.form-grid__secondary:hover {
  border-color: var(--line-strong);
  color: var(--text-strong);
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
  border-radius: var(--radius-control);
  background: var(--bg-panel-soft);
  color: var(--text-main);
  text-decoration: none;
  font-family: var(--mono);
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  transition:
    border-color 0.2s ease,
    color 0.2s ease;
}

.quick-action {
  justify-content: flex-start;
  position: relative;
  padding-left: 30px;
  font-size: 0.78rem;
}

.quick-action::before {
  content: '';
  position: absolute;
  left: 14px;
  width: 6px;
  height: 6px;
  background: var(--accent);
}

.quick-action:hover,
.audit-card__link:hover {
  border-color: var(--line-strong);
  color: var(--text-strong);
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
  background: var(--bg-panel);
}

/* Summary strip = primary signal surface with staggered console boot */
.summary-strip__card {
  border-color: var(--line-strong);
  background: var(--bg-panel-soft);
  animation: consoleRise 0.5s ease both;
}

.summary-strip__card:nth-child(1) {
  animation-delay: 0.08s;
}

.summary-strip__card:nth-child(2) {
  animation-delay: 0.15s;
}

.summary-strip__card:nth-child(3) {
  animation-delay: 0.22s;
}

.summary-strip__card:nth-child(4) {
  animation-delay: 0.29s;
}

.summary-strip__card:nth-child(5) {
  animation-delay: 0.36s;
}

.summary-strip__card span,
.hero-spotlight__kicker,
.hero-spotlight__meta span,
.audit-card dt {
  display: block;
  color: var(--text-faint);
  font-family: var(--mono);
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
  font-family: var(--mono);
  font-size: clamp(1.8rem, 3vw, 2.4rem);
  color: var(--text-strong);
  line-height: 1.05;
}

.hero-spotlight,
.hero-lane {
  display: grid;
  gap: 14px;
  animation: consoleRise 0.5s ease both;
}

.hero-spotlight {
  border-color: var(--line-strong);
  animation-delay: 0.1s;
}

.hero-lane {
  animation-delay: 0.18s;
}

.hero-spotlight__kicker {
  margin: 0;
  color: var(--accent);
}

.hero-spotlight__headline {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.hero-spotlight__headline strong {
  font-size: 1.24rem;
  line-height: 1.2;
  color: var(--text-strong);
}

.hero-spotlight__context,
.hero-spotlight__reason,
.hero-lane__step p,
.audit-card__detail {
  margin: 0;
  color: var(--text-muted);
  font-size: var(--supporting-text-size);
  line-height: var(--supporting-text-line-height);
}

.hero-spotlight__reason {
  padding: var(--space-subpanel);
  border-radius: var(--radius-subpanel);
  border: 1px solid var(--line);
  border-left: 2px solid var(--line-warm);
  background: var(--bg-panel-soft);
  color: var(--text-main);
}

.hero-spotlight__empty {
  margin: 0;
  color: var(--text-muted);
  font-size: var(--supporting-text-size);
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

.hero-spotlight__meta strong {
  margin-top: 10px;
  font-size: 0.9rem;
  line-height: 1.5;
  word-break: break-word;
}

.hero-spotlight__meta div,
.audit-card__details div,
.hero-lane__step {
  padding: var(--space-subpanel);
  border-radius: var(--radius-subpanel);
  border: 1px solid var(--line);
  background: var(--bg-panel-soft);
}

.hero-lane__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.hero-lane__step strong {
  font-family: var(--body);
  color: var(--text-main);
}

.hero-lane__empty {
  margin: 0;
  color: var(--text-muted);
  font-size: var(--supporting-text-size);
}

.audits-layout {
  grid-template-columns: minmax(320px, 360px) minmax(0, 1fr);
  align-items: start;
}

.form-grid label {
  display: grid;
  gap: 8px;
}

.form-grid span {
  color: var(--text-faint);
  font-family: var(--mono);
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
  background: #0e1013;
  color: var(--text-main);
}

.form-grid__submit {
  min-height: var(--control-height);
  padding: var(--space-button);
  border: 1px solid var(--line-warm);
  border-radius: var(--radius-control);
  background: var(--bg-panel-soft);
  color: var(--text-strong);
  font-family: var(--body);
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  transition: border-color 0.22s ease;
}

.form-grid__submit:hover {
  border-color: var(--accent);
}

/* === Audit event stream: instrument log rhythm === */
.audit-list {
  display: grid;
  gap: var(--space-list);
  position: relative;
  padding-left: 22px;
}

/* The vertical stream rail running through the log */
.audit-list::before {
  content: '';
  position: absolute;
  top: 8px;
  bottom: 8px;
  left: 5px;
  width: 1px;
  background: var(--line);
}

.audit-card {
  position: relative;
  animation: consoleRise 0.5s ease both;
  transition: border-color 0.22s ease;
}

.audit-card:nth-child(1) { animation-delay: 0.06s; }
.audit-card:nth-child(2) { animation-delay: 0.12s; }
.audit-card:nth-child(3) { animation-delay: 0.18s; }
.audit-card:nth-child(4) { animation-delay: 0.24s; }
.audit-card:nth-child(5) { animation-delay: 0.3s; }
.audit-card:nth-child(n + 6) { animation-delay: 0.36s; }

.audit-card:hover {
  border-color: var(--line-strong);
}

/* Stream node connecting each card to the rail */
.audit-card__node {
  position: absolute;
  top: 26px;
  left: -19px;
  width: 8px;
  height: 8px;
  background: var(--bg-page);
  border: 1px solid var(--accent);
}

.audit-card--alert {
  border-color: var(--amber-soft);
}

.audit-card--alert .audit-card__node {
  border-color: var(--amber);
}

.audit-card--alert:hover {
  border-color: var(--amber);
}

.audit-card--focus {
  border-color: var(--line-warm);
}

.audit-card--focus .audit-card__node {
  border-color: var(--accent);
}

.audit-card__header strong {
  font-size: 1.04rem;
  line-height: 1.3;
  color: var(--text-strong);
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

.audit-card dd.mono-id {
  word-break: break-word;
  font-size: 0.86rem;
}

.audit-card__detail {
  margin-top: 14px;
  padding: var(--space-subpanel);
  border-radius: var(--radius-subpanel);
  border: 1px solid var(--line);
  border-left: 2px solid var(--line-warm);
  background: var(--bg-panel-soft);
  color: var(--text-main);
  line-height: var(--supporting-text-line-height);
}

.audit-card--alert .audit-card__detail {
  border-left-color: var(--amber);
}

.training-preview__item {
  display: grid;
  justify-content: flex-start;
  gap: 8px;
  min-height: unset;
  padding: var(--space-subpanel);
  border-radius: var(--radius-subpanel);
  border-left: 2px solid var(--line-warm);
  text-transform: none;
}

.training-preview__item--model {
  border-left-color: var(--line-strong);
}

.training-preview__item--model:hover {
  border-color: var(--line-strong);
}

.training-preview__item span,
.training-preview__item small {
  color: var(--text-muted);
  font-family: var(--body);
  font-size: 0.82rem;
  text-transform: none;
  letter-spacing: normal;
}

.training-preview__item strong {
  color: var(--text-strong);
}

.audit-card__actions {
  grid-template-columns: repeat(2, minmax(0, max-content));
  margin-top: 14px;
}

.audit-card__link {
  font-size: 0.76rem;
}

.audit-card__button:disabled {
  opacity: 0.55;
  cursor: progress;
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

  .audit-card__actions {
    grid-template-columns: 1fr;
  }
}
</style>
