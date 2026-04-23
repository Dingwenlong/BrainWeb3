export function canInspectChainRecords(actorRole: string) {
  return ['owner', 'approver', 'admin'].includes(actorRole.toLowerCase())
}
