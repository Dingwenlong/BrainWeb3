from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Any
from urllib.parse import urlparse

try:
    import mne  # type: ignore
    import numpy as np
except ImportError:  # pragma: no cover - exercised via fallback paths
    mne = None
    np = None


REGIONS = [
    {"code": "LEFT_FRONTAL", "label": "Left Frontal", "electrodes": ["Fp1", "F3", "F7"]},
    {"code": "RIGHT_FRONTAL", "label": "Right Frontal", "electrodes": ["Fp2", "F4", "F8"]},
    {"code": "LEFT_PARIETAL", "label": "Left Parietal", "electrodes": ["P3", "CP3"]},
    {"code": "RIGHT_PARIETAL", "label": "Right Parietal", "electrodes": ["P4", "CP4"]},
    {"code": "LEFT_TEMPORAL", "label": "Left Temporal", "electrodes": ["T3", "T5"]},
    {"code": "RIGHT_TEMPORAL", "label": "Right Temporal", "electrodes": ["T4", "T6"]},
    {"code": "OCCIPITAL", "label": "Occipital", "electrodes": ["O1", "Oz", "O2"]},
    {"code": "CENTRAL_MOTOR", "label": "Central Motor", "electrodes": ["C3", "Cz", "C4"]},
]

BAND_RANGES: dict[str, tuple[float, float]] = {
    "delta": (0.5, 4.0),
    "theta": (4.0, 8.0),
    "alpha": (8.0, 13.0),
    "beta": (13.0, 30.0),
    "gamma": (30.0, 45.0),
}

MAX_FRAMES = 60


@dataclass
class BrainActivityResult:
    dataset_id: str
    sampling_rate: int
    band: str
    window_size: float
    step_size: float
    regions: list[dict[str, Any]]
    frames: list[dict[str, Any]]
    quality_flags: list[str]

    def to_dict(self) -> dict[str, Any]:
        return {
            "datasetId": self.dataset_id,
            "samplingRate": self.sampling_rate,
            "band": self.band,
            "windowSize": self.window_size,
            "stepSize": self.step_size,
            "regions": self.regions,
            "frames": self.frames,
            "qualityFlags": self.quality_flags,
        }


@dataclass
class EegMetadataResult:
    format: str
    sampling_rate: int
    channel_count: int
    sample_count: int
    duration_seconds: float
    quality_flags: list[str]

    def to_dict(self) -> dict[str, Any]:
        return {
            "format": self.format,
            "samplingRate": self.sampling_rate,
            "channelCount": self.channel_count,
            "sampleCount": self.sample_count,
            "durationSeconds": round(self.duration_seconds, 4),
            "qualityFlags": self.quality_flags,
        }


def analyze_brain_activity(
    dataset_id: str,
    band: str,
    window_size: float,
    step_size: float,
    source_uri: str | None = None,
    time_start: float | None = None,
    time_end: float | None = None,
) -> BrainActivityResult:
    normalized_band = band.lower()
    if normalized_band not in BAND_RANGES:
        raise ValueError(f"Unsupported band: {band}")
    if window_size <= 0:
        raise ValueError("windowSize must be greater than 0")
    if step_size <= 0:
        raise ValueError("stepSize must be greater than 0")
    if time_start is not None and time_start < 0:
        raise ValueError("timeStart must be greater than or equal to 0")
    if time_end is not None and time_end <= 0:
        raise ValueError("timeEnd must be greater than 0")
    if time_start is not None and time_end is not None and time_end <= time_start:
        raise ValueError("timeEnd must be greater than timeStart")

    if source_uri:
        try:
            return _analyze_from_source(
                dataset_id,
                normalized_band,
                window_size,
                step_size,
                source_uri,
                time_start,
                time_end,
            )
        except Exception as exc:  # pragma: no cover - covered via Flask-level integration
            fallback = _build_fallback_result(
                dataset_id,
                normalized_band,
                window_size,
                step_size,
                time_start,
                time_end,
            )
            fallback.quality_flags.extend(
                [
                    "analysis-fallback",
                    _compact_error_flag(type(exc).__name__),
                ]
            )
            return fallback

    fallback = _build_fallback_result(
        dataset_id,
        normalized_band,
        window_size,
        step_size,
        time_start,
        time_end,
    )
    fallback.quality_flags.append("source-unavailable")
    return fallback


def extract_eeg_metadata(source_uri: str) -> EegMetadataResult:
    if not source_uri:
        raise ValueError("sourceUri is required")

    if mne is None:
        raise RuntimeError("mne runtime is not installed")

    raw = _load_raw(source_uri, preload=False)
    sampling_rate = int(round(float(raw.info["sfreq"])))
    sample_count = int(raw.n_times)
    duration_seconds = sample_count / sampling_rate if sampling_rate > 0 else 0.0
    parsed = urlparse(source_uri)
    path = _resolve_local_path(source_uri) if parsed.scheme not in {"http", "https"} else Path(parsed.path)
    format_name = path.suffix[1:].upper() if path.suffix else "EDF"

    quality_flags = ["metadata-read"]
    if raw.info.get("bads"):
        quality_flags.append("contains-bad-channels")
    if sampling_rate < 128:
        quality_flags.append("low-sampling-rate")

    return EegMetadataResult(
        format=format_name,
        sampling_rate=sampling_rate,
        channel_count=len(raw.ch_names),
        sample_count=sample_count,
        duration_seconds=duration_seconds,
        quality_flags=quality_flags,
    )


def _analyze_from_source(
    dataset_id: str,
    band: str,
    window_size: float,
    step_size: float,
    source_uri: str,
    time_start: float | None,
    time_end: float | None,
) -> BrainActivityResult:
    if mne is None or np is None:
        raise RuntimeError("mne runtime is not installed")

    raw = _load_raw(source_uri, preload=True)
    sampling_rate = int(round(float(raw.info["sfreq"])))
    data, channel_names = raw.get_data(return_times=False), list(raw.ch_names)

    frames = _compute_frames(
        data,
        channel_names,
        sampling_rate,
        band,
        window_size,
        step_size,
        time_start,
        time_end,
    )
    quality_flags = ["real-data", "derived-from-source"]

    if raw.info.get("bads"):
        quality_flags.append("contains-bad-channels")
    if sampling_rate < 128:
        quality_flags.append("low-sampling-rate")
    if len(frames) >= MAX_FRAMES:
        quality_flags.append("frame-window-capped")

    return BrainActivityResult(
        dataset_id=dataset_id,
        sampling_rate=sampling_rate,
        band=band,
        window_size=window_size,
        step_size=step_size,
        regions=REGIONS,
        frames=frames,
        quality_flags=quality_flags,
    )


def _load_raw(source_uri: str, preload: bool):
    parsed = urlparse(source_uri)
    if parsed.scheme in {"http", "https"}:
        return mne.io.read_raw_edf(source_uri, preload=preload, verbose="ERROR")

    path = _resolve_local_path(source_uri)
    suffix = path.suffix.lower()
    if suffix == ".bdf":
        return mne.io.read_raw_bdf(str(path), preload=preload, verbose="ERROR")
    if suffix == ".gdf":
        return mne.io.read_raw_gdf(str(path), preload=preload, verbose="ERROR")
    return mne.io.read_raw_edf(str(path), preload=preload, verbose="ERROR")


def _resolve_local_path(source_uri: str) -> Path:
    if len(source_uri) >= 2 and source_uri[1] == ":":
        return Path(source_uri)

    parsed = urlparse(source_uri)
    if parsed.scheme == "file":
        local_path = parsed.path
        if local_path.startswith("/") and len(local_path) >= 3 and local_path[2] == ":":
            local_path = local_path[1:]
        return Path(local_path)
    if parsed.scheme:
        raise ValueError(f"Unsupported local source URI: {source_uri}")
    return Path(source_uri)


def _compute_frames(
    data,
    channel_names: list[str],
    sampling_rate: int,
    band: str,
    window_size: float,
    step_size: float,
    time_start: float | None,
    time_end: float | None,
) -> list[dict[str, Any]]:
    band_low, band_high = BAND_RANGES[band]
    normalized_names = {name.upper(): index for index, name in enumerate(channel_names)}
    window_samples = max(1, int(round(window_size * sampling_rate)))
    step_samples = max(1, int(round(step_size * sampling_rate)))
    total_samples = data.shape[1]
    selected_start = min(total_samples, max(0, int(round((time_start or 0) * sampling_rate))))
    selected_end = total_samples if time_end is None else min(total_samples, int(round(time_end * sampling_rate)))

    if selected_end <= selected_start:
        return []

    data = data[:, selected_start:selected_end]
    total_samples = data.shape[1]
    max_start = max(total_samples - window_samples, 0)

    if total_samples <= window_samples:
        starts = [0]
    else:
        starts = list(range(0, max_start + 1, step_samples))
        if starts[-1] != max_start:
            starts.append(max_start)

    if len(starts) > MAX_FRAMES:
        starts = starts[:MAX_FRAMES]

    frames: list[dict[str, Any]] = []
    for start in starts:
        end = min(start + window_samples, total_samples)
        window = data[:, start:end]
        intensities: dict[str, float] = {}
        for region in REGIONS:
            values = []
            for electrode in region["electrodes"]:
                channel_index = normalized_names.get(electrode.upper())
                if channel_index is not None:
                    values.append(_band_power(window[channel_index], sampling_rate, band_low, band_high))
            intensities[region["code"]] = round(_normalize_power(values), 4)

        frames.append(
            {
                "timestamp": round((selected_start + start) / sampling_rate, 2),
                "intensities": intensities,
            }
        )

    return frames


def _band_power(channel_data, sampling_rate: int, low: float, high: float) -> float:
    if channel_data.size == 0:
        return 0.0

    spectrum = np.fft.rfft(channel_data)
    frequencies = np.fft.rfftfreq(channel_data.size, d=1.0 / sampling_rate)
    mask = (frequencies >= low) & (frequencies < high)
    if not np.any(mask):
        return 0.0

    power = np.abs(spectrum[mask]) ** 2
    return float(np.mean(power))


def _normalize_power(values: list[float]) -> float:
    if not values:
        return 0.0
    average = sum(values) / len(values)
    normalized = average / (average + 25.0)
    return max(0.0, min(normalized, 1.0))


def _build_fallback_result(
    dataset_id: str,
    band: str,
    window_size: float,
    step_size: float,
    time_start: float | None,
    time_end: float | None,
) -> BrainActivityResult:
    start_seconds = time_start or 0.0
    requested_end = time_end if time_end is not None else start_seconds + step_size * 5
    frames = []
    for index in range(6):
        timestamp = start_seconds + index * step_size
        if timestamp > requested_end:
            break
        frames.append(
            {
                "timestamp": round(timestamp, 2),
                "intensities": {
                    "LEFT_FRONTAL": round(0.44 + index * 0.07, 2),
                    "RIGHT_FRONTAL": round(0.39 + index * 0.06, 2),
                    "LEFT_PARIETAL": round(0.36 + index * 0.05, 2),
                    "RIGHT_PARIETAL": round(0.33 + index * 0.05, 2),
                    "LEFT_TEMPORAL": round(0.42 + index * 0.04, 2),
                    "RIGHT_TEMPORAL": round(0.37 + index * 0.05, 2),
                    "OCCIPITAL": round(0.31 + index * 0.08, 2),
                    "CENTRAL_MOTOR": round(0.47 + index * 0.06, 2),
                },
            }
        )

    if not frames:
        frames.append(
            {
                "timestamp": round(start_seconds, 2),
                "intensities": {
                    "LEFT_FRONTAL": 0.44,
                    "RIGHT_FRONTAL": 0.39,
                    "LEFT_PARIETAL": 0.36,
                    "RIGHT_PARIETAL": 0.33,
                    "LEFT_TEMPORAL": 0.42,
                    "RIGHT_TEMPORAL": 0.37,
                    "OCCIPITAL": 0.31,
                    "CENTRAL_MOTOR": 0.47,
                },
            }
        )

    return BrainActivityResult(
        dataset_id=dataset_id,
        sampling_rate=160,
        band=band,
        window_size=window_size,
        step_size=step_size,
        regions=REGIONS,
        frames=frames,
        quality_flags=["fallback-data"],
    )


def _compact_error_flag(name: str) -> str:
    return f"fallback-{name.lower()}"
