export interface ChainStatus {
  provider: string
  enabled: boolean
  mode: string
  group: string
  contractName: string
  contractAddress: string
  rpcPeers: string[]
  transportSecurity: string
}

export interface SystemStatus {
  application: string
  stage: string
  generatedAt: string
  chain: ChainStatus
  modules: Record<string, string>
}

export interface DatasetSummary {
  id: string
  subjectCode: string
  title: string
  ownerOrganization: string
  format: string
  uploadStatus: string
  proofStatus: string
  trainingReadiness: string
  updatedAt: string
}

export interface DataAssetProof {
  chainProvider: string
  chainGroup: string
  contractName: string
  contractAddress: string
  sm3Hash: string
  ipfsCid: string
  offChainReference: string
  chainTxHash: string
  didHolder: string
  accessPolicy: string
  auditState: string
}

export interface DatasetDetail extends DatasetSummary {
  description: string
  originalFilename: string
  fileSizeBytes: number
  channelCount: number
  sampleCount: number
  durationSeconds: number
  samplingRate: number
  tags: string[]
  proof: DataAssetProof
}

export interface DatasetUploadResponse {
  dataset: DatasetDetail
  uploadReceipt: string
}

export type BrainBand = 'delta' | 'theta' | 'alpha' | 'beta' | 'gamma'

export interface BrainRegion {
  code: string
  label: string
  electrodes: string[]
}

export interface RegionChannelMapping {
  regionCode: string
  electrodes: string[]
}

export interface ActivityFrame {
  timestamp: number
  intensities: Record<string, number>
}

export interface BandPowerSeries {
  band: BrainBand
  frames: ActivityFrame[]
}

export interface BrainActivityResponse {
  datasetId: string
  samplingRate: number
  band: BrainBand
  windowSize: number
  stepSize: number
  regions: BrainRegion[]
  frames: ActivityFrame[]
  qualityFlags: string[]
  generatedAt: string
}

export interface AccessRequest {
  id: string
  datasetId: string
  actorId: string
  actorRole: string
  actorOrg: string
  purpose: string
  requestedDurationHours: number
  reason: string
  status: string
  policyNote: string | null
  approvedDurationHours: number | null
  approverId: string | null
  approverRole: string | null
  approverOrg: string | null
  createdAt: string
  updatedAt: string
  decidedAt: string | null
  expiresAt: string | null
}

export interface AuditEvent {
  id: number
  datasetId: string | null
  actorId: string
  actorRole: string
  actorOrg: string
  action: string
  status: string
  detail: string | null
  createdAt: string
}
