<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import {
  approveAccessRequest,
  getAccessRequests,
  rejectAccessRequest,
  revokeAccessRequest,
} from '../api/client'
import { useActorProfile } from '../composables/useActorProfile'
import { useToast } from '../composables/useToast'
import type { AccessRequest } from '../types/api'
import { formatOrganizationLabel, formatRequestStatusLabel, formatRoleLabel } from '../utils/labels'

const { actorProfile } = useActorProfile()
const { pushToast } = useToast()
const route = useRoute()

const loading = ref(true)
const actionLoadingId = ref<string | null>(null)
const error = ref<string | null>(null)
const requestRows = ref<AccessRequest[]>([])
const approvalPolicy = ref('仅限科研分析，默认开放 24 小时。')
const rejectionPolicy = ref('拒绝原因：业务说明不足，请补充用途与合规依据。')
const focusRequestId = computed(() => (typeof route.query.focusRequestId === 'string' ? route.query.focusRequestId : ''))

const isPrivilegedActor = computed(() =>
  ['owner', 'approver', 'admin'].includes(actorProfile.value.actorRole.toLowerCase()),
)
const roleGuide = computed(() => {
  const role = actorProfile.value.actorRole.toLowerCase()
  if (role === 'admin') {
    return {
      note: '你现在处于全局监管辅助视角，可以巡检申请流转、抽查审批说明，并把已批准记录带入训练页验证闭环。',
      emptySpotlight: '当前没有访问申请进入审批台。可以先从研究员视角发起一条申请，随后回到这里验证审批轨迹。',
      emptyQueue: '当前没有待审批记录，审批队列处于空闲状态。',
      emptyList: '当前没有访问申请记录。先创建一条访问申请，才能验证审批、训练与审计的串联效果。',
      policyHint: '管理员可用这里的说明文案做演示，但真正的授权边界仍以后端规则为准。',
    }
  }
  if (role === 'owner' || role === 'approver') {
    return {
      note: '优先处理待审批记录，再把已批准数据带入训练页，确认机构内授权到训练的接力顺畅。',
      emptySpotlight: '当前没有访问申请进入审批台，机构工作区暂时空闲。',
      emptyQueue: '当前没有待审批记录，下一条待决申请会优先出现在这里。',
      emptyList: '当前没有访问申请记录。等研究员发起访问后，这里会出现完整的审批轨迹。',
      policyHint: '这里填写的是本次动作回写到访问记录里的说明，用来解释你的审批决策。',
    }
  }
  return {
    note: '这里会优先展示你自己的申请记录；获批后可以直接把数据带入训练页，不用来回切页面。',
    emptySpotlight: '你还没有提交访问申请。先选一份数据发起申请，后续就能从这里继续追踪审批结果。',
    emptyQueue: '当前没有待审批记录，因为你的角色只读申请流，不参与审批。',
    emptyList: '你还没有访问申请记录。先提交一条申请，获批后就能直接发起训练。',
    policyHint: '你现在看到的是审批说明样板，真正执行批准或拒绝的是机构侧审批人。',
  }
})

const requestStats = computed(() => [
  { label: '待审批', value: requestRows.value.filter((row) => row.status === 'pending').length },
  { label: '已批准', value: requestRows.value.filter((row) => row.status === 'approved').length },
  {
    label: '已关闭',
    value: requestRows.value.filter((row) => row.status === 'rejected' || row.status === 'revoked').length,
  },
])
const intakeHint = computed(() => {
  const source = String(route.query.source ?? '')
  if (!source) {
    return ''
  }
  if (source === 'chain-record') {
    return focusRequestId.value
      ? `该视图来自链轨迹，已定位到 ${focusRequestId.value} 对应的审批记录。`
      : '该视图来自链轨迹，可继续核对访问申请与链上授权记录是否一致。'
  }
  return '当前审批视图由上一页带入。'
})
const spotlightRequest = computed(() =>
  requestRows.value.find((row) => row.id === focusRequestId.value)
    ?? requestRows.value.find((row) => row.status === 'pending')
    ?? requestRows.value[0]
    ?? null,
)
const queuePreview = computed(() => requestRows.value.filter((row) => row.status === 'pending').slice(0, 3))
const policyCards = computed(() => [
  {
    label: '审批口径',
    value: approvalPolicy.value,
  },
  {
    label: '拒绝口径',
    value: rejectionPolicy.value,
  },
  {
    label: '当前模式',
    value: isPrivilegedActor.value ? '可直接决策' : '只读巡检',
  },
])

function formatTime(value: string | null) {
  if (!value) {
    return '暂无'
  }

  return new Date(value).toLocaleString()
}

function trainingLinkFor(row: AccessRequest) {
  return {
    path: '/training-jobs',
    query: {
      source: 'access-request',
      datasetId: row.datasetId,
      modelName: `Federated Run ${row.datasetId.toUpperCase()}`,
      objective: row.purpose,
      requestedRounds: '6',
    },
  }
}

async function loadRequests() {
  loading.value = true
  error.value = null

  try {
    requestRows.value = await getAccessRequests(actorProfile.value, {
      datasetId: typeof route.query.datasetId === 'string' ? route.query.datasetId : undefined,
      status: typeof route.query.status === 'string' ? route.query.status : undefined,
    })
  } catch (loadError) {
    error.value = loadError instanceof Error ? loadError.message : '加载访问申请失败。'
  } finally {
    loading.value = false
  }
}

async function approve(row: AccessRequest) {
  actionLoadingId.value = row.id
  try {
    await approveAccessRequest(row.id, actorProfile.value, {
      approvedDurationHours: 24,
      policy: approvalPolicy.value,
    })
    await loadRequests()
    pushToast({
      title: '访问已批准',
      message: `${row.id} 已获批，现在可以直接带入训练编排或返回数据详情页验证脑区读数。`,
      tone: 'success',
    })
  } catch (actionError) {
    error.value = actionError instanceof Error ? actionError.message : '批准访问申请失败。'
  } finally {
    actionLoadingId.value = null
  }
}

async function reject(row: AccessRequest) {
  actionLoadingId.value = row.id
  try {
    await rejectAccessRequest(row.id, actorProfile.value, {
      policy: rejectionPolicy.value,
    })
    await loadRequests()
    pushToast({
      title: '申请已拒绝',
      message: `${row.id} 已被拒绝，拒绝说明已写回访问记录。`,
      tone: 'warning',
    })
  } catch (actionError) {
    error.value = actionError instanceof Error ? actionError.message : '拒绝访问申请失败。'
  } finally {
    actionLoadingId.value = null
  }
}

async function revoke(row: AccessRequest) {
  actionLoadingId.value = row.id
  try {
    await revokeAccessRequest(row.id, actorProfile.value)
    await loadRequests()
    pushToast({
      title: '访问已撤销',
      message: `${row.id} 已撤销，后续读取会重新命中门禁。`,
      tone: 'warning',
    })
  } catch (actionError) {
    error.value = actionError instanceof Error ? actionError.message : '撤销访问申请失败。'
  } finally {
    actionLoadingId.value = null
  }
}

onMounted(loadRequests)

watch(
  () => route.query,
  () => {
    void loadRequests()
  },
)
</script>

<template>
  <div class="requests-page">
    <section class="hero-panel glass-panel">
      <div class="hero-panel__copy">
        <p class="section-kicker">Approval Command</p>
        <h1>把访问申请处理成一条清晰、可追踪的决策流。</h1>
        <p class="hero-panel__lede">
          当前身份是 {{ actorProfile.actorId }} / {{ formatRoleLabel(actorProfile.actorRole) }} /
          {{ formatOrganizationLabel(actorProfile.actorOrg) }}。这个页面不再只是读一串记录，而是先把待决事项拉到前台，再把策略说明和处置动作放到同一块视野里。
        </p>

        <div class="hero-panel__actions">
          <span class="status-chip">{{ isPrivilegedActor ? '审批模式' : '只读模式' }}</span>
          <RouterLink class="hero-panel__secondary" to="/">返回总览</RouterLink>
        </div>
        <p v-if="intakeHint" class="hero-panel__hint">{{ intakeHint }}</p>
        <div class="hero-panel__guide">
          <span>Role Guidance</span>
          <strong>{{ roleGuide.note }}</strong>
        </div>

        <div class="summary-strip">
          <article v-for="stat in requestStats" :key="stat.label" class="summary-strip__card">
            <span>{{ stat.label }}</span>
            <strong>{{ stat.value }}</strong>
          </article>
        </div>
      </div>

      <div class="hero-panel__rail">
        <article class="hero-spotlight">
          <p class="hero-spotlight__kicker">优先事项</p>
          <template v-if="spotlightRequest">
            <div class="hero-spotlight__headline">
              <strong>{{ spotlightRequest.id }}</strong>
              <span
                class="status-chip"
                :class="{
                  'status-chip--warn': spotlightRequest.status === 'pending',
                  'status-chip--danger':
                    spotlightRequest.status === 'rejected' || spotlightRequest.status === 'revoked',
                }"
              >
                {{ formatRequestStatusLabel(spotlightRequest.status) }}
              </span>
            </div>
            <p class="hero-spotlight__context">
              {{ spotlightRequest.datasetId }} · {{ spotlightRequest.actorId }} ·
              {{ formatOrganizationLabel(spotlightRequest.actorOrg) }}
            </p>
            <p class="hero-spotlight__reason">{{ spotlightRequest.reason }}</p>
            <div class="hero-spotlight__meta">
              <div>
                <span>用途</span>
                <strong>{{ spotlightRequest.purpose }}</strong>
              </div>
              <div>
                <span>申请时长</span>
                <strong>{{ spotlightRequest.requestedDurationHours }} 小时</strong>
              </div>
              <div>
                <span>提交时间</span>
                <strong>{{ formatTime(spotlightRequest.createdAt) }}</strong>
              </div>
            </div>
          </template>
          <p v-else class="hero-spotlight__empty">{{ roleGuide.emptySpotlight }}</p>
        </article>

        <article class="hero-lane">
          <div class="hero-lane__header">
            <div>
              <p class="section-kicker">Moderation Lane</p>
              <h2 class="section-title">决策节奏</h2>
            </div>
          </div>
          <div class="hero-lane__steps">
            <div class="hero-lane__step">
              <strong>Scan</strong>
              <p>先识别待审批记录和申请背景。</p>
            </div>
            <div class="hero-lane__step">
              <strong>Policy</strong>
              <p>确认批准或拒绝说明是否足够清楚。</p>
            </div>
            <div class="hero-lane__step">
              <strong>Resolve</strong>
              <p>直接批准、拒绝或撤销，并留下可回看的记录。</p>
            </div>
          </div>
        </article>
      </div>
    </section>

    <div v-if="loading" class="loading-state">正在加载审批台...</div>
    <div v-else-if="error" class="error-state">{{ error }}</div>

    <template v-else>
      <section class="requests-layout">
        <aside class="requests-layout__side">
          <article class="workspace-card glass-panel">
            <div class="workspace-card__header">
              <div>
                <p class="section-kicker">审批策略</p>
                <h2 class="section-title">策略编辑台</h2>
                <p class="workspace-card__lede">{{ roleGuide.policyHint }}</p>
              </div>
            </div>

            <div class="form-grid">
              <label>
                <span>批准策略</span>
                <input v-model="approvalPolicy" type="text" />
              </label>
              <label>
                <span>拒绝说明</span>
                <input v-model="rejectionPolicy" type="text" />
              </label>
            </div>

            <p class="workspace-card__note">
              {{ roleGuide.policyHint }}
            </p>

            <div class="policy-cards">
              <div v-for="card in policyCards" :key="card.label" class="policy-cards__item">
                <span>{{ card.label }}</span>
                <strong>{{ card.value }}</strong>
              </div>
            </div>
          </article>

          <article class="workspace-card glass-panel">
            <div class="workspace-card__header">
              <div>
                <p class="section-kicker">待决队列</p>
                <h2 class="section-title">优先处理视图</h2>
              </div>
              <span class="status-chip status-chip--warn">{{ queuePreview.length }} 条待处理</span>
            </div>

            <div v-if="queuePreview.length" class="queue-preview">
              <div v-for="row in queuePreview" :key="row.id" class="queue-preview__item">
                <div>
                  <strong>{{ row.id }}</strong>
                  <p>{{ row.datasetId }} · {{ row.actorId }}</p>
                </div>
                <span>{{ formatTime(row.createdAt) }}</span>
              </div>
            </div>
            <div v-else class="empty-state">{{ roleGuide.emptyQueue }}</div>
          </article>
        </aside>

        <div class="requests-layout__main">
          <article class="workspace-card glass-panel">
            <div class="workspace-card__header">
              <div>
                <p class="section-kicker">申请列表</p>
                <h2 class="section-title">访问申请记录</h2>
                <p class="workspace-card__lede">每条记录都保留申请背景、决策状态和跳转入口，便于在审批与数据页之间往返处理。</p>
              </div>
              <span class="status-chip">{{ requestRows.length }} 条记录</span>
            </div>

            <div class="request-list" v-if="requestRows.length">
              <article
                v-for="row in requestRows"
                :key="row.id"
                class="request-card"
                :class="{ 'request-card--focus': focusRequestId === row.id }"
              >
                <div class="request-card__header">
                  <div>
                    <strong>{{ row.id }}</strong>
                    <p>{{ row.datasetId }} · {{ row.purpose }}</p>
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

                <p class="request-card__reason">{{ row.reason }}</p>
                <p v-if="focusRequestId === row.id" class="request-card__focus-note">该申请由链轨迹页定位而来。</p>

                <dl class="request-card__details">
                  <div>
                    <dt>提交时间</dt>
                    <dd>{{ formatTime(row.createdAt) }}</dd>
                  </div>
                  <div>
                    <dt>申请人</dt>
                    <dd>{{ row.actorId }}</dd>
                  </div>
                  <div>
                    <dt>角色</dt>
                    <dd>{{ formatRoleLabel(row.actorRole) }}</dd>
                  </div>
                  <div>
                    <dt>机构</dt>
                    <dd>{{ formatOrganizationLabel(row.actorOrg) }}</dd>
                  </div>
                  <div>
                    <dt>申请时长</dt>
                    <dd>{{ row.requestedDurationHours }} 小时</dd>
                  </div>
                  <div v-if="row.policyNote">
                    <dt>策略说明</dt>
                    <dd>{{ row.policyNote }}</dd>
                  </div>
                  <div v-if="row.expiresAt">
                    <dt>到期时间</dt>
                    <dd>{{ formatTime(row.expiresAt) }}</dd>
                  </div>
                </dl>

                <div class="request-card__footer">
                  <RouterLink class="request-card__link" :to="`/datasets/${row.datasetId}`">
                    打开数据详情
                  </RouterLink>

                  <div v-if="isPrivilegedActor && row.status === 'pending'" class="request-card__actions">
                    <button type="button" @click="approve(row)" :disabled="actionLoadingId === row.id">
                      {{ actionLoadingId === row.id ? '处理中...' : '批准 24 小时' }}
                    </button>
                    <button
                      type="button"
                      class="request-card__danger"
                      @click="reject(row)"
                      :disabled="actionLoadingId === row.id"
                    >
                      拒绝
                    </button>
                  </div>

                  <div v-else-if="isPrivilegedActor && row.status === 'approved'" class="request-card__actions">
                    <RouterLink class="request-card__link" :to="trainingLinkFor(row)">
                      带入训练页
                    </RouterLink>
                    <button
                      type="button"
                      class="request-card__danger"
                      @click="revoke(row)"
                      :disabled="actionLoadingId === row.id"
                    >
                      撤销访问
                    </button>
                  </div>
                  <div v-else-if="!isPrivilegedActor && row.status === 'approved'" class="request-card__actions">
                    <RouterLink class="request-card__link" :to="trainingLinkFor(row)">
                      发起训练
                    </RouterLink>
                  </div>
                </div>
              </article>
            </div>
            <div v-else class="empty-state">{{ roleGuide.emptyList }}</div>
          </article>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.requests-page {
  display: grid;
  gap: 18px;
}

.hero-panel {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(360px, 0.95fr);
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
  font-size: clamp(2.5rem, 5vw, 4rem);
  line-height: 0.96;
}

.hero-panel__lede {
  margin: 12px 0 0;
  max-width: 70ch;
  color: var(--text-muted);
  line-height: 1.8;
}

.hero-panel__actions,
.hero-spotlight__headline,
.hero-lane__header {
  display: flex;
  gap: 10px;
  align-items: center;
}

.hero-panel__actions,
.hero-lane__header {
  flex-wrap: wrap;
}

.hero-panel__secondary {
  display: inline-flex;
  align-items: center;
  min-height: 42px;
  padding: 0 16px;
  border-radius: 999px;
  border: 1px solid var(--line);
  background: rgba(12, 24, 32, 0.92);
  color: var(--text-main);
  text-decoration: none;
  font-family: var(--display);
  font-size: 0.82rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.hero-panel__guide {
  display: grid;
  gap: 8px;
  max-width: 720px;
  padding: 14px 16px;
  border-radius: 18px;
  border: 1px solid rgba(235, 178, 102, 0.18);
  background: rgba(17, 24, 16, 0.42);
}

.hero-panel__hint {
  margin: 0;
  padding: 12px 14px;
  border-radius: 16px;
  border: 1px solid rgba(108, 166, 186, 0.14);
  background: rgba(6, 18, 24, 0.72);
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
  font-family: var(--display);
  font-size: 0.94rem;
  line-height: 1.6;
}

.summary-strip,
.hero-lane__steps,
.policy-cards,
.queue-preview {
  display: grid;
  gap: 12px;
}

.summary-strip {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.summary-strip__card,
.hero-spotlight,
.hero-lane,
.policy-cards__item,
.queue-preview__item {
  padding: 16px 18px;
  border-radius: 22px;
  border: 1px solid rgba(108, 166, 186, 0.14);
  background:
    linear-gradient(180deg, rgba(4, 15, 21, 0.94), rgba(8, 17, 23, 0.78)),
    radial-gradient(circle at top right, rgba(116, 210, 220, 0.08), transparent 30%);
}

.summary-strip__card span,
.hero-spotlight__kicker,
.hero-spotlight__meta span,
.policy-cards__item span,
.queue-preview__item span,
.workspace-card__lede {
  color: var(--text-muted);
}

.summary-strip__card span,
.hero-spotlight__kicker,
.hero-spotlight__meta span,
.policy-cards__item span {
  display: block;
  font-size: 0.72rem;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.summary-strip__card strong,
.hero-spotlight__headline strong,
.hero-spotlight__meta strong,
.hero-lane__step strong,
.policy-cards__item strong,
.queue-preview__item strong {
  display: block;
  font-family: var(--display);
}

.summary-strip__card strong {
  margin-top: 10px;
  font-size: clamp(1.8rem, 3vw, 2.4rem);
}

.hero-spotlight {
  display: grid;
  gap: 14px;
}

.hero-spotlight__headline {
  justify-content: space-between;
  align-items: flex-start;
}

.hero-spotlight__headline strong {
  font-size: 1.3rem;
}

.hero-spotlight__context,
.hero-spotlight__reason,
.hero-spotlight__empty,
.hero-lane__step p,
.workspace-card__note,
.queue-preview__item p {
  margin: 0;
  color: var(--text-muted);
  line-height: 1.7;
}

.hero-spotlight__meta {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.hero-spotlight__meta div,
.hero-lane__step {
  padding: 14px 16px;
  border-radius: 18px;
  border: 1px solid rgba(108, 166, 186, 0.1);
  background: rgba(8, 18, 25, 0.76);
}

.hero-spotlight__meta strong,
.policy-cards__item strong {
  margin-top: 10px;
  font-size: 0.94rem;
  line-height: 1.6;
}

.hero-lane {
  display: grid;
  gap: 16px;
}

.hero-lane__steps {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.hero-lane__step strong {
  font-size: 0.9rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.requests-layout {
  display: grid;
  grid-template-columns: minmax(300px, 360px) minmax(0, 1fr);
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
  color: var(--text-muted);
  line-height: 1.7;
  font-size: 0.9rem;
}

.workspace-card__note {
  margin: 14px 0 0;
}

.policy-cards {
  margin-top: 14px;
}

.queue-preview__item {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.queue-preview__item strong {
  font-size: 0.94rem;
}

.queue-preview__item span {
  font-size: 0.76rem;
  line-height: 1.5;
}

.form-grid {
  display: grid;
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

.form-grid input {
  width: 100%;
  min-height: 44px;
  padding: 10px 12px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: rgba(8, 18, 25, 0.94);
  color: var(--text-main);
}

.request-list {
  display: grid;
  gap: 14px;
}

.request-card {
  padding: 18px;
  border-radius: 20px;
  border: 1px solid var(--line);
  background:
    linear-gradient(180deg, rgba(8, 18, 25, 0.92), rgba(11, 22, 29, 0.72)),
    radial-gradient(circle at left center, rgba(116, 210, 220, 0.08), transparent 26%);
}

.request-card--focus {
  border-color: rgba(235, 178, 102, 0.28);
  box-shadow: inset 0 0 0 1px rgba(235, 178, 102, 0.08);
}

.request-card__header,
.request-card__footer {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.request-card__header strong {
  display: block;
  font-family: var(--display);
}

.request-card__header p,
.request-card__reason {
  margin: 6px 0 0;
  color: var(--text-muted);
}

.request-card__focus-note {
  margin: 10px 0 0;
  color: var(--amber);
  font-size: 0.88rem;
}

.request-card__details {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 12px;
  margin: 14px 0 0;
}

.request-card__details dt {
  color: var(--text-faint);
  font-size: 0.74rem;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.request-card__details dd {
  margin: 6px 0 0;
  color: var(--text-main);
}

.request-card__footer {
  margin-top: 16px;
  align-items: center;
}

.request-card__link {
  color: var(--accent);
  text-decoration: none;
  font-family: var(--display);
  font-size: 0.82rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.request-card__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.request-card__actions button {
  min-height: 40px;
  padding: 0 14px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: rgba(12, 24, 32, 0.92);
  color: var(--text-main);
  font-family: var(--display);
  font-size: 0.8rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.request-card__danger {
  border-color: rgba(242, 126, 126, 0.28) !important;
  color: var(--danger) !important;
}

@media (max-width: 1040px) {
  .hero-panel,
  .requests-layout {
    grid-template-columns: 1fr;
  }

  .summary-strip,
  .hero-lane__steps {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .hero-panel {
    padding: 20px;
  }

  .hero-panel h1 {
    font-size: 2.2rem;
  }

  .hero-spotlight__headline,
  .hero-spotlight__meta,
  .request-card__details {
    grid-template-columns: 1fr;
  }

  .request-card__footer {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
