const ROLE_LABELS: Record<string, string> = {
  researcher: '研究员',
  owner: '归属方',
  approver: '审批人',
  admin: '管理员',
}

const ACCESS_STATE_LABELS: Record<string, string> = {
  granted: '已授权',
  pending: '待审批',
  denied: '已拒绝',
  idle: '未申请',
}

const REQUEST_STATUS_LABELS: Record<string, string> = {
  pending: '待审批',
  approved: '已批准',
  rejected: '已拒绝',
  revoked: '已撤销',
  granted: '已授权',
  denied: '已拒绝',
  accepted: '已接收',
  success: '成功',
  failed: '失败',
}

const PROOF_STATUS_LABELS: Record<string, string> = {
  notarized: '已存证',
  'hash-pending': '哈希待写入',
  'pending-storage': '待入库',
  failed: '存证失败',
}

const TRAINING_READINESS_LABELS: Record<string, string> = {
  'authorized-ready': '可用于训练',
  'review-required': '待复核',
  blocked: '已阻断',
}

const BAND_LABELS: Record<string, string> = {
  delta: '德尔塔',
  theta: '西塔',
  alpha: '阿尔法',
  beta: '贝塔',
  gamma: '伽马',
}

const QUALITY_FLAG_LABELS: Record<string, string> = {
  'real-data': '真实数据',
  'derived-from-source': '源文件分析',
  'fallback-data': '回退数据',
  'source-unavailable': '源文件不可用',
  'metadata-read': '已读取元数据',
}

const REGION_LABELS: Record<string, string> = {
  LEFT_FRONTAL: '左额叶',
  RIGHT_FRONTAL: '右额叶',
  LEFT_PARIETAL: '左顶叶',
  RIGHT_PARIETAL: '右顶叶',
  LEFT_TEMPORAL: '左颞叶',
  RIGHT_TEMPORAL: '右颞叶',
  OCCIPITAL: '枕叶',
  CENTRAL_MOTOR: '中央运动区',
}

const MODULE_LABELS: Record<string, string> = {
  backend: '后端编排',
  chain: '区块链网关',
  frontend: '前端控制台',
  'eeg-service': 'EEG 服务',
  'federated-service': '联邦训练服务',
  contracts: '合约层',
}

const SYSTEM_TOKEN_LABELS: Record<string, string> = {
  bootstrap: '引导阶段',
  ready: '就绪',
  scaffolded: '已搭建',
  mock: '模拟',
  'mock-active': '模拟运行',
  sandbox: '沙箱',
  demo: '演示',
  tls: 'TLS',
  disabled: '已禁用',
  enabled: '已启用',
  'group-pending': '群组待定',
  group0: '群组 0',
}

const ORGANIZATION_LABELS: Record<string, string> = {
  'Sichuan Neuro Lab': '四川神经实验室',
  'Sichuan Neural Studio': '四川神经工作室',
  'Huaxi Medical Union': '华西医疗联合体',
  'West China Research Lab': '华西科研实验室',
}

const APPLICATION_LABELS: Record<string, string> = {
  'brainweb3-backend': '脑域链医后端',
}

const CONTRACT_LABELS: Record<string, string> = {
  DataAssetProof: '数据资产存证合约',
  MockDataNotary: '模拟数据存证合约',
  BootstrapDataNotary: '引导态数据存证合约',
}

const AUDIT_ACTION_LABELS: Record<string, string> = {
  UPLOAD_ACCEPTED: '上传已接收',
  STORAGE_PERSISTED: '文件已入库',
  CHAIN_REGISTERED: '链上登记完成',
  UPLOAD_FAILED: '上传失败',
  BRAIN_ACTIVITY_READ: '脑区活跃度读取',
  ACCESS_REQUEST_CREATED: '已创建访问申请',
  ACCESS_REQUEST_APPROVED: '已批准访问申请',
  ACCESS_REQUEST_REJECTED: '已拒绝访问申请',
  ACCESS_REQUEST_REVOKED: '已撤销访问申请',
}

function mapLabel(source: Record<string, string>, raw: string | null | undefined) {
  if (!raw) {
    return '-'
  }

  return source[raw.toLowerCase()] ?? source[raw] ?? raw
}

export function formatRoleLabel(role: string | null | undefined) {
  return mapLabel(ROLE_LABELS, role)
}

export function formatAccessStateLabel(state: string | null | undefined) {
  return mapLabel(ACCESS_STATE_LABELS, state)
}

export function formatRequestStatusLabel(status: string | null | undefined) {
  return mapLabel(REQUEST_STATUS_LABELS, status)
}

export function formatProofStatusLabel(status: string | null | undefined) {
  return mapLabel(PROOF_STATUS_LABELS, status)
}

export function formatTrainingReadinessLabel(status: string | null | undefined) {
  return mapLabel(TRAINING_READINESS_LABELS, status)
}

export function formatBandLabel(band: string | null | undefined) {
  const label = mapLabel(BAND_LABELS, band)
  return label === '-' ? label : `${label}频段`
}

export function formatQualityFlagLabel(flag: string | null | undefined) {
  return mapLabel(QUALITY_FLAG_LABELS, flag)
}

export function formatQualityFlags(flags: string[]) {
  if (!flags.length) {
    return '无'
  }

  return flags.map((flag) => formatQualityFlagLabel(flag)).join('、')
}

export function formatRegionLabel(code: string | null | undefined, fallback?: string | null) {
  if (code && REGION_LABELS[code]) {
    return REGION_LABELS[code]
  }

  return fallback || code || '-'
}

export function formatModuleLabel(moduleName: string) {
  return MODULE_LABELS[moduleName] ?? moduleName
}

export function formatOrganizationLabel(value: string | null | undefined) {
  return mapLabel(ORGANIZATION_LABELS, value)
}

export function formatApplicationLabel(value: string | null | undefined) {
  return mapLabel(APPLICATION_LABELS, value)
}

export function formatContractLabel(value: string | null | undefined) {
  return mapLabel(CONTRACT_LABELS, value)
}

export function formatSystemToken(value: string | null | undefined) {
  if (!value) {
    return '-'
  }

  const direct = mapLabel(SYSTEM_TOKEN_LABELS, value)
  if (direct !== value) {
    return direct
  }

  const segments = value.split(/[-_]/).filter(Boolean)
  if (!segments.length) {
    return value
  }

  const translated = segments.map((segment) => mapLabel(SYSTEM_TOKEN_LABELS, segment))
  const changed = translated.some((segment, index) => segment !== segments[index])
  return changed ? translated.join(' / ') : value
}

export function formatAuditActionLabel(action: string | null | undefined) {
  if (!action) {
    return '-'
  }

  return AUDIT_ACTION_LABELS[action] ?? action
}

export function formatSecondsLabel(value: number) {
  return `${value.toFixed(1)} 秒`
}
