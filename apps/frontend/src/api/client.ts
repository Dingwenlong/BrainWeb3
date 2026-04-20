import type {
  AccessRequest,
  AuditEvent,
  BrainBand,
  BrainActivityResponse,
  DatasetDetail,
  DatasetSummary,
  DatasetUploadResponse,
  SystemStatus,
} from '../types/api'
import type { ActorProfile } from '../composables/useActorProfile'

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

function buildActorHeaders(actor: ActorProfile | null | undefined) {
  if (!actor) {
    return {} as Record<string, string>
  }

  return {
    'X-Actor-Id': actor.actorId,
    'X-Actor-Role': actor.actorRole,
    'X-Actor-Org': actor.actorOrg,
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

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, init)
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
  },
) {
  const params = new URLSearchParams()
  if (query?.datasetId) {
    params.set('datasetId', query.datasetId)
  }
  if (query?.actorId) {
    params.set('actorId', query.actorId)
  }

  const suffix = params.toString() ? `?${params.toString()}` : ''
  return request<AuditEvent[]>(`/audits${suffix}`, {
    headers: buildActorHeaders(actor),
  })
}
