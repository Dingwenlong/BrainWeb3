from importlib.util import module_from_spec, spec_from_file_location
from pathlib import Path


SERVICE_ROOT = Path(__file__).resolve().parents[1]
SPEC = spec_from_file_location("federated_service_app", SERVICE_ROOT / "app.py")
MODULE = module_from_spec(SPEC)
assert SPEC and SPEC.loader
SPEC.loader.exec_module(MODULE)
create_app = MODULE.create_app


def test_health_endpoint() -> None:
    app = create_app()
    client = app.test_client()

    response = client.get("/health")

    assert response.status_code == 200
    assert response.get_json()["service"] == "federated-service"


def test_training_preview() -> None:
    app = create_app()
    client = app.test_client()

    response = client.post(
        "/api/v1/trainings/preview",
        json={"name": "fate-demo", "datasetIds": ["a", "b", "c"], "rounds": 5},
    )
    payload = response.get_json()

    assert response.status_code == 200
    assert payload["participants"] == 3
    assert payload["status"] == "preview-ready"
