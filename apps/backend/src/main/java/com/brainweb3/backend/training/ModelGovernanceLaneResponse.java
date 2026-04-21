package com.brainweb3.backend.training;

import com.brainweb3.backend.audit.AuditEventResponse;
import com.brainweb3.backend.chain.ChainBusinessRecordResponse;
import java.util.List;

public record ModelGovernanceLaneResponse(
    ModelRecordResponse model,
    ModelGovernanceSummaryResponse summary,
    ModelVersionComparisonResponse comparison,
    List<ModelRecordResponse> relatedModels,
    List<AuditEventResponse> auditEvents,
    List<ChainBusinessRecordResponse> chainRecords,
    boolean chainVisible
) {
}
