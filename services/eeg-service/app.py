from __future__ import annotations

import sys
from pathlib import Path
from typing import Any

from flask import Flask, jsonify, request

SERVICE_ROOT = Path(__file__).resolve().parent
if str(SERVICE_ROOT) not in sys.path:
    sys.path.insert(0, str(SERVICE_ROOT))

from brain_activity import analyze_brain_activity, extract_eeg_metadata
from models import with_generated_at

def create_app() -> Flask:
    app = Flask(__name__)

    @app.get("/health")
    def health() -> Any:
        return jsonify({"service": "eeg-service", "status": "ok"})

    @app.get("/api/v1/datasets/<dataset_id>/brain-activity")
    def get_brain_activity(dataset_id: str) -> Any:
        band = request.args.get("band", "alpha").lower()
        window_size = float(request.args.get("windowSize", 2))
        step_size = float(request.args.get("stepSize", 0.5))
        source_uri = request.args.get("sourceUri")
        time_start = request.args.get("timeStart", type=float)
        time_end = request.args.get("timeEnd", type=float)
        result = analyze_brain_activity(
            dataset_id,
            band,
            window_size,
            step_size,
            source_uri=source_uri,
            time_start=time_start,
            time_end=time_end,
        )
        return jsonify(with_generated_at(result.to_dict()))

    @app.get("/api/v1/eeg/metadata")
    def get_eeg_metadata() -> Any:
        source_uri = request.args.get("sourceUri", "")
        result = extract_eeg_metadata(source_uri)
        return jsonify(with_generated_at(result.to_dict()))

    return app


app = create_app()


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=8101, debug=True)
