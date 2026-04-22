package com.brainweb3.backend.config;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class SensitiveTextSanitizer {

  private static final String REDACTED = "[REDACTED]";
  private static final List<Pattern> QUOTED_FIELD_PATTERNS = List.of(
      Pattern.compile("(?i)(\"(?:password|secret|token|accessToken|refreshToken|resetToken|jwt|authorization)\"\\s*:\\s*\")([^\"]+)(\")"),
      Pattern.compile("(?i)('(?:password|secret|token|accessToken|refreshToken|resetToken|jwt|authorization)'\\s*:\\s*')([^']+)(')")
  );
  private static final Pattern QUERY_PARAMETER_PATTERN = Pattern.compile(
      "(?i)([?&](?:password|secret|token|access_token|refresh_token|reset_token|jwt)=)([^&\\s]+)"
  );
  private static final Pattern KEY_VALUE_PATTERN = Pattern.compile(
      "(?i)(\\b(?:password|secret|accessToken|refreshToken|resetToken|jwt|authorization|token)\\b\\s*[:=]\\s*)([^,;\\s]+)"
  );
  private static final Pattern BEARER_PATTERN = Pattern.compile(
      "(?i)(\\bBearer\\s+)([A-Za-z0-9._~+/=-]+)"
  );
  private static final Pattern JWT_PATTERN = Pattern.compile(
      "\\beyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\b"
  );

  public String sanitize(String value) {
    if (value == null || value.isBlank()) {
      return value;
    }

    String sanitized = value;
    for (Pattern pattern : QUOTED_FIELD_PATTERNS) {
      sanitized = replaceAll(sanitized, pattern, matcher -> matcher.group(1) + REDACTED + matcher.group(3));
    }
    sanitized = replaceAll(
        sanitized,
        QUERY_PARAMETER_PATTERN,
        matcher -> matcher.group(1) + REDACTED
    );
    sanitized = replaceAll(
        sanitized,
        KEY_VALUE_PATTERN,
        matcher -> matcher.group(1) + REDACTED
    );
    sanitized = replaceAll(
        sanitized,
        BEARER_PATTERN,
        matcher -> matcher.group(1) + REDACTED
    );
    sanitized = replaceAll(sanitized, JWT_PATTERN, matcher -> REDACTED);
    return sanitized;
  }

  private String replaceAll(String value, Pattern pattern, Replacement replacement) {
    Matcher matcher = pattern.matcher(value);
    if (!matcher.find()) {
      return value;
    }

    StringBuffer buffer = new StringBuffer();
    do {
      matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement.apply(matcher)));
    } while (matcher.find());
    matcher.appendTail(buffer);
    return buffer.toString();
  }

  @FunctionalInterface
  private interface Replacement {
    String apply(Matcher matcher);
  }
}
