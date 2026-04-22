export interface ChainStatus {
  provider: string
  enabled: boolean
  mode: string
  group: string
  contractName: string
  contractAddress: string
  rpcPeers: string[]
  transportSecurity: string
  detailsRedacted: boolean
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
  destructionStatus: string
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

export interface ChainBusinessRecord {
  id: number
  datasetId: string
  eventType: string
  referenceId: string
  businessStatus: string
  anchorPolicy: string
  anchorStatus: string
  actorId: string
  actorRole: string
  actorOrg: string
  chainProvider: string
  chainGroup: string
  contractName: string
  contractAddress: string
  eventHash: string
  chainTxHash: string
  detail: string
  anchorError: string
  anchoredAt: string
}

export interface ChainRecordFilters {
  datasetId?: string
  eventType?: string
  anchorStatus?: string
  businessStatus?: string
  chainTxHash?: string
}

export interface ChainPolicyRule {
  eventType: string
  anchorPolicy: string
}

export interface UploadAuditStep {
  action: string
  status: string
  message: string | null
  traceId: string
  createdAt: string
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
  lastUploadTraceId: string
  lastErrorMessage: string
  retryAllowed: boolean
  uploadAudits: UploadAuditStep[]
  proof: DataAssetProof
  chainRecords: ChainBusinessRecord[]
  destroyedAt: string | null
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

export interface DestructionRequest {
  id: string
  datasetId: string
  datasetTitle: string
  ownerOrganization: string
  requesterId: string
  requesterRole: string
  requesterOrg: string
  reason: string
  status: string
  policyNote: string
  approverId: string
  approverRole: string
  approverOrg: string
  executedBy: string
  cleanupStatus: string
  cleanupError: string
  cleanupEvidenceRef: string
  cleanupEvidenceHash: string
  cleanupVerifiedBy: string
  createdAt: string
  updatedAt: string
  decidedAt: string | null
  executedAt: string | null
  cleanupCompletedAt: string | null
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

export interface AuthActor {
  actorId: string
  actorRole: string
  actorOrg: string
  displayName: string
}

export interface AuthSession {
  token: string
  expiresAt: string
  refreshToken: string
  refreshExpiresAt: string
  actor: AuthActor
}

export interface PasswordResetTicket {
  actorId: string
  resetToken: string | null
  expiresAt: string
  deliveryMode: string
  tokenVisible: boolean
}

export interface AccountUser {
  actorId: string
  displayName: string
  actorRole: string
  actorOrg: string
  status: string
  credentialStatus: CredentialStatusSnapshot
  credentialHistory: CredentialHistoryEntry[]
  createdAt: string
  updatedAt: string
  passwordChangedAt: string | null
  lastLoginAt: string | null
}

export interface CredentialStatusSnapshot {
  effectiveStatus: string
  source: string
  reason: string
  updatedBy: string | null
  updatedAt: string | null
}

export interface CredentialHistoryEntry {
  id: number | null
  previousStatus: string | null
  nextStatus: string
  source: string
  reason: string
  updatedBy: string | null
  createdAt: string | null
}

export interface VerifiableCredential {
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
  verificationStatus: string
  claims: Record<string, string>
}

export interface ActorIdentity {
  actorId: string
  displayName: string
  actorRole: string
  actorOrg: string
  actorDid: string
  organizationDid: string
  credential: VerifiableCredential
}

export interface OrganizationIdentity {
  organizationName: string
  organizationDid: string
  credential: VerifiableCredential
  statusSnapshot: CredentialStatusSnapshot
  credentialHistory: CredentialHistoryEntry[]
}

export interface CredentialVerificationResult {
  verified: boolean
  status: string
  reason: string
}

export interface TrainingJob {
  id: string
  datasetId: string
  datasetTitle: string
  actorId: string
  actorRole: string
  actorOrg: string
  orchestrator: string
  algorithm: string
  modelName: string
  objective: string
  requestedRounds: number
  completedRounds: number
  status: string
  externalJobRef: string
  latestMessage: string
  metricSummary: string
  resultSummary: string
  createdAt: string
  updatedAt: string
  startedAt: string | null
  completedAt: string | null
}

export interface ModelRecord {
  id: string
  trainingJobId: string
  datasetId: string
  datasetTitle: string
  actorId: string
  actorRole: string
  actorOrg: string
  orchestrator: string
  algorithm: string
  modelName: string
  objective: string
  governanceStatus: string
  governanceNote: string
  artifactRef: string
  metricSummary: string
  resultSummary: string
  lastGovernedBy: string
  allowedGovernanceTransitions: string[]
  createdAt: string
  updatedAt: string
  governedAt: string | null
  completedAt: string | null
}

export interface ModelGovernanceSummary {
  datasetVersionCount: number
  candidateVersionCount: number
  activeVersionCount: number
  archivedVersionCount: number
  latestGovernedAt: string | null
  latestGovernedBy: string
}

export interface ModelVersionComparison {
  currentVersionRank: number
  totalVisibleVersions: number
  newerVersionCount: number
  olderVersionCount: number
  latestVersion: boolean
  latestVersionId: string
  latestVersionCompletedAt: string | null
  sameAlgorithmVersionCount: number
  sameStatusVersionCount: number
  latestActiveVersionId: string
  latestActiveGovernedAt: string | null
}

export interface ModelGovernanceLane {
  model: ModelRecord
  summary: ModelGovernanceSummary
  comparison: ModelVersionComparison
  relatedModels: ModelRecord[]
  auditEvents: AuditEvent[]
  chainRecords: ChainBusinessRecord[]
  chainVisible: boolean
}
