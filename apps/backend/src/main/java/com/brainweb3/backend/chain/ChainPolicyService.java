package com.brainweb3.backend.chain;

import com.brainweb3.backend.config.ChainProperties;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class ChainPolicyService {

  private static final List<String> DEFAULT_EVENT_ORDER = List.of(
      "ACCESS_APPROVED",
      "ACCESS_REVOKED",
      "TRAINING_COMPLETED",
      "TRAINING_FAILED",
      "DESTRUCTION_COMPLETED",
      "DESTRUCTION_STORAGE_PURGED",
      "MODEL_REGISTERED",
      "MODEL_GOVERNED"
  );

  private final Set<String> requiredEventTypes;
  private final Set<String> optionalEventTypes;
  private final Set<String> disabledEventTypes;

  public ChainPolicyService(ChainProperties chainProperties) {
    this.requiredEventTypes = normalize(chainProperties.getRequiredEventTypes());
    this.optionalEventTypes = normalize(chainProperties.getOptionalEventTypes());
    this.disabledEventTypes = normalize(chainProperties.getDisabledEventTypes());
  }

  public String resolvePolicy(String eventType) {
    String normalized = normalize(eventType);
    if (normalized.isBlank()) {
      return "optional";
    }
    if (disabledEventTypes.contains(normalized)) {
      return "audit-only";
    }
    if (requiredEventTypes.contains(normalized)) {
      return "required";
    }
    if (optionalEventTypes.contains(normalized)) {
      return "optional";
    }
    return "optional";
  }

  public boolean shouldAnchor(String eventType) {
    return !"audit-only".equals(resolvePolicy(eventType));
  }

  public List<ChainPolicyRuleResponse> listRules() {
    LinkedHashSet<String> ordered = new LinkedHashSet<>(DEFAULT_EVENT_ORDER);
    ordered.addAll(requiredEventTypes);
    ordered.addAll(optionalEventTypes);
    ordered.addAll(disabledEventTypes);

    return ordered.stream()
        .map(eventType -> new ChainPolicyRuleResponse(eventType, resolvePolicy(eventType)))
        .toList();
  }

  private Set<String> normalize(List<String> values) {
    if (values == null) {
      return Set.of();
    }
    return values.stream()
        .map(this::normalize)
        .filter(value -> !value.isBlank())
        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
  }
}
