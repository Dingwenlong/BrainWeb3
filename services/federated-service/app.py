from __future__ import annotations

from datetime import datetime, timezone
from typing import Any

from flask import Flask, jsonify, request


def create_app() -> Flask:
    app = Flask(__name__)

    @app.get("/health")
    def health() -> Any:
        return jsonify({"service": "federated-service", "status": "ok"})

    @app.post("/api/v1/trainings/preview")
    def preview_training() -> Any:
        payload = request.get_json(silent=True) or {}
        dataset_ids = payload.get("datasetIds", [])

        return jsonify(
            {
                "name": payload.get("name", "bootstrap-training"),
                "rounds": payload.get("rounds", 3),
                "participants": len(dataset_ids),
                "status": "preview-ready",
                "nextAction": "Bind authorized datasets and FATE party topology.",
                "generatedAt": datetime.now(timezone.utc).isoformat(),
            }
        )

    return app


app = create_app()


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=8102, debug=True)
