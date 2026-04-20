from importlib.util import module_from_spec, spec_from_file_location
from pathlib import Path
from unittest.mock import patch


SERVICE_ROOT = Path(__file__).resolve().parents[1]
SPEC = spec_from_file_location("eeg_service_app", SERVICE_ROOT / "app.py")
MODULE = module_from_spec(SPEC)
assert SPEC and SPEC.loader
SPEC.loader.exec_module(MODULE)
create_app = MODULE.create_app


def test_health_endpoint() -> None:
    app = create_app()
    client = app.test_client()

    response = client.get("/health")

    assert response.status_code == 200
    assert response.get_json()["status"] == "ok"


def test_brain_activity_response_shape() -> None:
    app = create_app()
    client = app.test_client()

    response = client.get("/api/v1/datasets/demo-dataset/brain-activity?band=beta")
    payload = response.get_json()

    assert response.status_code == 200
    assert payload["datasetId"] == "demo-dataset"
    assert payload["band"] == "beta"
    assert len(payload["regions"]) == 8
    assert len(payload["frames"]) == 6
    assert "fallback-data" in payload["qualityFlags"]


def test_brain_activity_supports_time_range_queries() -> None:
    app = create_app()
    client = app.test_client()

    response = client.get(
        "/api/v1/datasets/demo-dataset/brain-activity"
        "?band=alpha&timeStart=12&timeEnd=13.5&stepSize=0.5"
    )
    payload = response.get_json()

    assert response.status_code == 200
    assert payload["frames"][0]["timestamp"] == 12.0
    assert payload["frames"][-1]["timestamp"] <= 13.5


def test_brain_activity_uses_real_analysis_when_source_is_available() -> None:
    app = create_app()
    client = app.test_client()

    fake_result = {
        "datasetId": "demo-dataset",
        "samplingRate": 256,
        "band": "alpha",
        "windowSize": 2.0,
        "stepSize": 0.5,
        "regions": [{"code": "LEFT_FRONTAL", "label": "Left Frontal", "electrodes": ["Fp1"]}],
        "frames": [{"timestamp": 0.0, "intensities": {"LEFT_FRONTAL": 0.81}}],
        "qualityFlags": ["real-data", "derived-from-source"],
    }

    with patch.object(MODULE, "analyze_brain_activity") as analyze_brain_activity:
        analyze_brain_activity.return_value.to_dict.return_value = fake_result
        response = client.get(
            "/api/v1/datasets/demo-dataset/brain-activity"
            "?band=alpha&windowSize=2&stepSize=0.5&sourceUri=file:///tmp/demo.edf"
        )

    payload = response.get_json()

    assert response.status_code == 200
    assert payload["samplingRate"] == 256
    assert payload["frames"][0]["intensities"]["LEFT_FRONTAL"] == 0.81
    assert "real-data" in payload["qualityFlags"]


def test_metadata_endpoint_returns_generated_payload() -> None:
    app = create_app()
    client = app.test_client()

    fake_result = {
        "format": "EDF",
        "samplingRate": 256,
        "channelCount": 64,
        "sampleCount": 4096,
        "durationSeconds": 16.0,
        "qualityFlags": ["metadata-read"],
    }

    with patch.object(MODULE, "extract_eeg_metadata") as extract_eeg_metadata:
        extract_eeg_metadata.return_value.to_dict.return_value = fake_result
        response = client.get("/api/v1/eeg/metadata?sourceUri=file:///tmp/demo.edf")

    payload = response.get_json()

    assert response.status_code == 200
    assert payload["format"] == "EDF"
    assert payload["channelCount"] == 64
    assert "generatedAt" in payload
