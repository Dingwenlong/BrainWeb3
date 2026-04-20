<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
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

const loading = ref(true)
const actionLoadingId = ref<string | null>(null)
const error = ref<string | null>(null)
const requestRows = ref<AccessRequest[]>([])
const approvalPolicy = ref('仅限科研分析，默认开放 24 小时。')
const rejectionPolicy = ref('拒绝原因：业务说明不足，请补充用途与合规依据。')

const isPrivilegedActor = computed(() =>
  ['owner', 'approver', 'admin'].includes(actorProfile.value.actorRole.toLowerCase()),
)

const requestStats = computed(() => [
  { label: '待审批', value: requestRows.value.filter((row) => row.status === 'pending').length },
  { label: '已批准', value: requestRows.value.filter((row) => row.status === 'approved').length },
  {
    label: '已关闭',
    value: requestRows.value.filter((row) => row.status === 'rejected' || row.status === 'revoked').length,
  },
])

async function loadRequests() {
  loading.value = true
  error.value = null

  try {
    requestRows.value = await getAccessRequests(actorProfile.value)
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
      message: `${row.id} 已获批，可返回数据详情页验证脑区读数。`,
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
</script>

<template>
  <div class="requests-page">
    <section class="page-header glass-panel">
      <div>
        <p class="section-kicker">审批台</p>
        <h1>访问申请工作区</h1>
        <p class="page-header__lede">
          当前身份是 {{ actorProfile.actorId }} / {{ formatRoleLabel(actorProfile.actorRole) }} /
          {{ formatOrganizationLabel(actorProfile.actorOrg) }}。这个页面分成“摘要、策略、申请列表”三个区块，方便直接处理审批。
        </p>
      </div>

      <div class="page-header__actions">
        <span class="status-chip">{{ isPrivilegedActor ? '审批模式' : '只读模式' }}</span>
        <RouterLink class="page-header__secondary" to="/">返回总览</RouterLink>
      </div>
    </section>

    <div v-if="loading" class="loading-state">正在加载审批台...</div>
    <div v-else-if="error" class="error-state">{{ error }}</div>

    <template v-else>
      <section class="summary-grid">
        <article v-for="stat in requestStats" :key="stat.label" class="metric-card">
          <span>{{ stat.label }}</span>
          <strong>{{ stat.value }}</strong>
        </article>
      </section>

      <section class="requests-layout">
        <aside class="requests-layout__side">
          <article class="workspace-card glass-panel">
            <div class="workspace-card__header">
              <div>
                <p class="section-kicker">审批策略</p>
                <h2 class="section-title">默认处理规则</h2>
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
              这里设置的是当前页面操作时会回写到访问记录里的审批说明，不会修改后端策略定义。
            </p>
          </article>
        </aside>

        <div class="requests-layout__main">
          <article class="workspace-card glass-panel">
            <div class="workspace-card__header">
              <div>
                <p class="section-kicker">申请列表</p>
                <h2 class="section-title">访问申请记录</h2>
              </div>
              <span class="status-chip">{{ requestRows.length }} 条记录</span>
            </div>

            <div class="request-list" v-if="requestRows.length">
              <article v-for="row in requestRows" :key="row.id" class="request-card">
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

                <dl class="request-card__details">
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
                    <dd>{{ new Date(row.expiresAt).toLocaleString() }}</dd>
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
                    <button
                      type="button"
                      class="request-card__danger"
                      @click="revoke(row)"
                      :disabled="actionLoadingId === row.id"
                    >
                      撤销访问
                    </button>
                  </div>
                </div>
              </article>
            </div>
            <div v-else class="empty-state">当前没有访问申请记录。</div>
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
  max-width: 70ch;
  color: var(--text-muted);
}

.page-header__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}

.page-header__secondary {
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

.summary-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.requests-layout {
  display: grid;
  grid-template-columns: minmax(300px, 360px) minmax(0, 1fr);
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
  margin: 14px 0 0;
  color: var(--text-muted);
  line-height: 1.6;
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
  gap: 12px;
}

.request-card {
  padding: 16px;
  border-radius: 16px;
  border: 1px solid var(--line);
  background: var(--bg-panel-soft);
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
  .page-header,
  .requests-layout {
    grid-template-columns: 1fr;
  }

  .summary-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .request-card__details {
    grid-template-columns: 1fr;
  }

  .request-card__footer {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
