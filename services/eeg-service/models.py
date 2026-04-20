from __future__ import annotations

from datetime import datetime, timezone
from typing import Any


def with_generated_at(payload: dict[str, Any]) -> dict[str, Any]:
    payload["generatedAt"] = datetime.now(timezone.utc).isoformat()
    return payload
