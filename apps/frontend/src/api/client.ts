import type {
  AccountUser,
  AccessRequest,
  ActorIdentity,
  AuditEvent,
  BrainBand,
  BrainActivityResponse,
  ChainBusinessRecord,
  CredentialVerificationResult,
  ChainPolicyRule,
  ChainRecordFilters,
  DatasetDetail,
  DatasetSummary,
  DatasetUploadResponse,
  DestructionRequest,
  OrganizationIdentity,
  PasswordResetTicket,
  SystemStatus,
  TrainingJob,
  ModelRecord,
  ModelGovernanceLane,
} from '../types/api'
import type { ActorProfile } from '../composables/useActorProfile'
import { getAuthToken, refreshAuthSession } from '../composables/useActorProfile'

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? '/api/v1'

export class ApiError extends Error {
  status: number
  payload: unknown

  constructor(message: string, status: number, payload: unknown) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.payload = payload
  }
}

function buildActorHeaders(_actor: ActorProfile | null | undefined) {
  const token = getAuthToken()
  if (!token) {
    return {} as Record<string, string>
  }

  return {
    Authorization: `Bearer ${token}`,
  } satisfies Record<string, string>
}

async function parseResponse<T>(response: Response): Promise<T> {
  const text = await response.text()
  const payload = text ? (JSON.parse(text) as unknown) : null

  if (!response.ok) {
    const message =
      typeof payload === 'object' && payload && 'message' in payload
        ? String((payload as { message: string }).message)
        : `请求失败，状态码 ${response.status}`
    throw new ApiError(message, response.status, payload)
  }

  return payload as T
}

async function request<T>(path: string, init?: RequestInit, retryOnAuthFailure = true): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, init)
  if (response.status === 401 && retryOnAuthFailure && getAuthToken()) {
    const refreshed = await refreshAuthSession()
    if (refreshed) {
      return request<T>(path, init, false)
    }
  }
  return parseResponse<T>(response)
}

async function requestWithInit<T>(path: string, init: RequestInit): Promise<T> {
  return request<T>(path, init)
}

export function getSystemStatus() {
  return request<SystemStatus>('/system/status')
}

export function getDatasets() {
  return request<DatasetSummary[]>('/datasets')
}

export function getDataset(datasetId: string) {
  return request<DatasetDetail>(`/datasets/${datasetId}`)
}

export function retryDatasetFinalization(datasetId: string) {
  return requestWithInit<DatasetDetail>(`/datasets/${encodeURIComponent(datasetId)}/retry-finalization`, {
    method: 'POST',
    headers: buildActorHeaders(null),
  })
}

export function getBrainActivity(
  datasetId: string,
  band: BrainBand,
  actor: ActorProfile,
  options?: {
    windowSize?: number
    stepSize?: number
    timeStart?: number
    timeEnd?: number
  },
) {
  const windowSize = options?.windowSize ?? 2
  const stepSize = options?.stepSize ?? 0.5
  const timeStart =
    options?.timeStart !== undefined ? `&timeStart=${encodeURIComponent(String(options.timeStart))}` : ''
  const timeEnd =
    options?.timeEnd !== undefined ? `&timeEnd=${encodeURIComponent(String(options.timeEnd))}` : ''
  return request<BrainActivityResponse>(
    `/datasets/${datasetId}/brain-activity?band=${encodeURIComponent(band)}&windowSize=${encodeURIComponent(String(windowSize))}&stepSize=${encodeURIComponent(String(stepSize))}${timeStart}${timeEnd}`,
    {
      headers: buildActorHeaders(actor),
    },
  )
}

export function uploadDataset(payload: {
  file: File
  subjectCode: string
  title: string
  description: string
  ownerOrganization: string
  tags: string
}) {
  const formData = new FormData()
  formData.append('file', payload.file)
  formData.append('subjectCode', payload.subjectCode)
  formData.append('title', payload.title)
  formData.append('description', payload.description)
  formData.append('ownerOrganization', payload.ownerOrganization)

  if (payload.tags.trim()) {
    formData.append('tags', payload.tags)
  }

  return requestWithInit<DatasetUploadResponse>('/datasets', {
    method: 'POST',
    headers: buildActorHeaders(null),
    body: formData,
  })
}

export function getAccessRequests(
  actor: ActorProfile,
  query?: {
    datasetId?: string
    actorId?: string
    status?: string
  },
) {
  const params = new URLSearchParams()
  if (query?.datasetId) {
    params.set('datasetId', query.datasetId)
  }
  if (query?.actorId) {
    params.set('actorId', query.actorId)
  }
  if (query?.status) {
    params.set('status', query.status)
  }

  const suffix = params.toString() ? `?${params.toString()}` : ''
  return request<AccessRequest[]>(`/access-requests${suffix}`, {
    headers: buildActorHeaders(actor),
  })
}

export function createAccessRequest(
  actor: ActorProfile,
  payload: {
    datasetId: string
    purpose: string
    requestedDurationHours: number
    reason: string
  },
) {
  return requestWithInit<AccessRequest>('/access-requests', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...buildActorHeaders(actor),
    },
    body: JSON.stringify(payload),
  })
}

export function getDestructionRequests(
  actor: ActorProfile,
  query?: {
    datasetId?: string
    actorId?: string
    status?: string
  },
) {
  const params = new URLSearchParams()
  if (query?.datasetId) {
    params.set('datasetId', query.datasetId)
  }
  if (query?.actorId) {
    params.set('actorId', query.actorId)
  }
  if (query?.status) {
    params.set('status', query.status)
  }

  const suffix = params.toString() ? `?${params.toString()}` : ''
  return request<DestructionRequest[]>(`/destruction-requests${suffix}`, {
    headers: buildActorHeaders(actor),
  })
}

export function createDestructionRequest(
  actor: ActorProfile,
  payload: {
    datasetId: string
    reason: string
  },
) {
  return requestWithInit<DestructionRequest>('/destruction-requests', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...buildActorHeaders(actor),
    },
    body: JSON.stringify(payload),
  })
}

export function approveDestructionRequest(
  requestId: string,
  actor: ActorProfile,
  payload: {
    policy: string
  },
) {
  return requestWithInit<DestructionRequest>(`/destruction-requests/${requestId}/approve`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...buildActorHeaders(actor),
    },
    body: JSON.stringify(payload),
  })
}

export function rejectDestructionRequest(
  requestId: string,
  actor: ActorProfile,
  payload: {
    policy: string
  },
) {
  return requestWithInit<DestructionRequest>(`/destruction-requests/${requestId}/reject`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...buildActorHeaders(actor),
    },
    body: JSON.stringify(payload),
  })
}

export function executeDestructionRequest(requestId: string, actor: ActorProfile) {
  return requestWithInit<DestructionRequest>(`/destruction-requests/${requestId}/execute`, {
    method: 'POST',
    headers: buildActorHeaders(actor),
  })
}

export function purgeDestructionStorage(requestId: string, actor: ActorProfile) {
  return requestWithInit<DestructionRequest>(`/destruction-requests/${requestId}/purge-storage`, {
    method: 'POST',
    headers: buildActorHeaders(actor),
  })
}

export function approveAccessRequest(
  requestId: string,
  actor: ActorProfile,
  payload: {
    approvedDurationHours: number
    policy: string
  },
) {
  return requestWithInit<AccessRequest>(`/access-requests/${requestId}/approve`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...buildActorHeaders(actor),
    },
    body: JSON.stringify(payload),
  })
}

export function rejectAccessRequest(
  requestId: string,
  actor: ActorProfile,
  payload: {
    policy: string
  },
) {
  return requestWithInit<AccessRequest>(`/access-requests/${requestId}/reject`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...buildActorHeaders(actor),
    },
    body: JSON.stringify({
      approvedDurationHours: 1,
      policy: payload.policy,
    }),
  })
}

export function revokeAccessRequest(requestId: string, actor: ActorProfile) {
  return requestWithInit<AccessRequest>(`/access-requests/${requestId}/revoke`, {
    method: 'POST',
    headers: buildActorHeaders(actor),
  })
}

export function getAudits(
  actor: ActorProfile,
  query?: {
    datasetId?: string
    actorId?: string
    action?: string
    status?: string
    actorOrg?: string
  },
) {
  const params = new URLSearchParams()
  if (query?.datasetId) {
    params.set('datasetId', query.datasetId)
  }
  if (query?.actorId) {
    params.set('actorId', query.actorId)
  }
  if (query?.action) {
    params.set('action', query.action)
  }
  if (query?.status) {
    params.set('status', query.status)
  }
  if (query?.actorOrg) {
    params.set('actorOrg', query.actorOrg)
  }

  const suffix = params.toString() ? `?${params.toString()}` : ''
  return request<AuditEvent[]>(`/audits${suffix}`, {
    headers: buildActorHeaders(actor),
  })
}

export function getChainRecords(
  actor: ActorProfile,
  query?: ChainRecordFilters,
) {
  const params = new URLSearchParams()
  if (query?.datasetId) {
    params.set('datasetId', query.datasetId)
  }
  if (query?.eventType) {
    params.set('eventType', query.eventType)
  }
  if (query?.anchorStatus) {
    params.set('anchorStatus', query.anchorStatus)
  }
  if (query?.businessStatus) {
    params.set('businessStatus', query.businessStatus)
  }
  if (query?.chainTxHash) {
    params.set('chainTxHash', query.chainTxHash)
  }

  const suffix = params.toString() ? `?${params.toString()}` : ''
  return request<ChainBusinessRecord[]>(`/chain-records${suffix}`, {
    headers: buildActorHeaders(actor),
  })
}

export function getChainPolicy(actor: ActorProfile) {
  return request<ChainPolicyRule[]>('/chain-policy', {
    headers: buildActorHeaders(actor),
  })
}

export function retryChainRecord(recordId: number, actor: ActorProfile) {
  return requestWithInit<ChainBusinessRecord>(`/chain-records/${recordId}/retry`, {
    method: 'POST',
    headers: buildActorHeaders(actor),
  })
}

export function getTrainingJobs(
  actor: ActorProfile,
  query?: {
    datasetId?: string
    status?: string
  },
) {
  const params = new URLSearchParams()
  if (query?.datasetId) {
    params.set('datasetId', query.datasetId)
  }
  if (query?.status) {
    params.set('status', query.status)
  }

  const suffix = params.toString() ? `?${params.toString()}` : ''
  return request<TrainingJob[]>(`/training-jobs${suffix}`, {
    headers: buildActorHeaders(actor),
  })
}

export function createTrainingJob(
  actor: ActorProfile,
  payload: {
    datasetId: string
    modelName: string
    objective: string
    algorithm: string
    requestedRounds: number
  },
) {
  return requestWithInit<TrainingJob>('/training-jobs', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...buildActorHeaders(actor),
    },
    body: JSON.stringify(payload),
  })
}

export function refreshTrainingJob(jobId: string, actor: ActorProfile) {
  return requestWithInit<TrainingJob>(`/training-jobs/${encodeURIComponent(jobId)}/refresh`, {
    method: 'POST',
    headers: buildActorHeaders(actor),
  })
}

export function getModelRecords(
  actor: ActorProfile,
  query?: {
    datasetId?: string
    governanceStatus?: string
    trainingJobId?: string
  },
) {
  const params = new URLSearchParams()
  if (query?.datasetId) {
    params.set('datasetId', query.datasetId)
  }
  if (query?.governanceStatus) {
    params.set('governanceStatus', query.governanceStatus)
  }
  if (query?.trainingJobId) {
    params.set('trainingJobId', query.trainingJobId)
  }

  const suffix = params.toString() ? `?${params.toString()}` : ''
  return request<ModelRecord[]>(`/model-records${suffix}`, {
    headers: buildActorHeaders(actor),
  })
}

export function updateModelGovernance(
  modelId: string,
  actor: ActorProfile,
  payload: {
    status: string
    note?: string
  },
) {
  return requestWithInit<ModelRecord>(`/model-records/${encodeURIComponent(modelId)}/governance`, {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json',
      ...buildActorHeaders(actor),
    },
    body: JSON.stringify(payload),
  })
}

export function getModelGovernanceLane(modelId: string, actor: ActorProfile) {
  return request<ModelGovernanceLane>(`/model-records/${encodeURIComponent(modelId)}/governance-lane`, {
    headers: buildActorHeaders(actor),
  })
}

export function changePassword(payload: {
  currentPassword: string
  nextPassword: string
}) {
  return requestWithInit<null>('/auth/change-password', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...buildActorHeaders(null),
    },
    body: JSON.stringify(payload),
  })
}

export function requestPasswordResetTicket(payload: {
  actorId: string
}) {
  return requestWithInit<PasswordResetTicket>('/auth/password-reset/request', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(payload),
  })
}

export function confirmPasswordReset(payload: {
  resetToken: string
  nextPassword: string
}) {
  return requestWithInit<null>('/auth/password-reset/confirm', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(payload),
  })
}

export function getCurrentAccount() {
  return request<AccountUser>('/accounts/me', {
    headers: buildActorHeaders(null),
  })
}

export function getCurrentIdentity() {
  return request<ActorIdentity>('/identity/me', {
    headers: buildActorHeaders(null),
  })
}

export function getOrganizationIdentity(name: string) {
  return request<OrganizationIdentity>(`/identity/organizations?name=${encodeURIComponent(name)}`, {
    headers: buildActorHeaders(null),
  })
}

export function updateOrganizationCredentialStatus(payload: {
  organizationName: string
  status: string
  reason?: string
}) {
  return requestWithInit<OrganizationIdentity>('/identity/organizations/credential-status', {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json',
      ...buildActorHeaders(null),
    },
    body: JSON.stringify(payload),
  })
}

export function verifyCredential(payload: {
  id: string
  type: string
  issuerDid: string
  holderDid: string
  subjectDid: string
  subjectType: string
  issuedAt: string
  expiresAt: string
  proof: string
  credentialStatus: string
  claims: Record<string, string>
}) {
  return requestWithInit<CredentialVerificationResult>('/identity/verify', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...buildActorHeaders(null),
    },
    body: JSON.stringify(payload),
  })
}

export function getAccounts() {
  return request<AccountUser[]>('/accounts', {
    headers: buildActorHeaders(null),
  })
}

export function createAccount(payload: {
  actorId: string
  displayName: string
  actorRole: string
  actorOrg: string
  status: string
  password: string
}) {
  return requestWithInit<AccountUser>('/accounts', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...buildActorHeaders(null),
    },
    body: JSON.stringify(payload),
  })
}

export function updateAccount(
  actorId: string,
  payload: {
    displayName?: string
    actorRole?: string
    actorOrg?: string
    status?: string
  },
) {
  return requestWithInit<AccountUser>(`/accounts/${encodeURIComponent(actorId)}`, {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json',
      ...buildActorHeaders(null),
    },
    body: JSON.stringify(payload),
  })
}

export function updateAccountCredentialStatus(
  actorId: string,
  payload: {
    status: string
    reason?: string
  },
) {
  return requestWithInit<AccountUser>(`/accounts/${encodeURIComponent(actorId)}/credential-status`, {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json',
      ...buildActorHeaders(null),
    },
    body: JSON.stringify(payload),
  })
}

export function resetAccountPassword(
  actorId: string,
  payload: {
    nextPassword: string
  },
) {
  return requestWithInit<AccountUser>(`/accounts/${encodeURIComponent(actorId)}/reset-password`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...buildActorHeaders(null),
    },
    body: JSON.stringify(payload),
  })
}
