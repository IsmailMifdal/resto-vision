"""Chargement de la configuration du boîtier."""
from __future__ import annotations

import json
from dataclasses import dataclass, field
from pathlib import Path

from .models import Zone


@dataclass
class EdgeConfig:
    restaurant_id: str
    zones: list[Zone]
    groups: dict[str, set[str]]
    stable_seconds: float = 5.0
    seconds_per_customer: float = 90.0
    mqtt_host: str = "localhost"
    mqtt_port: int = 1883
    topic_prefix: str = "restovision"
    weights: str = "yolo11n.pt"
    confidence: float = 0.35
    fps: float = 5.0
    extra: dict = field(default_factory=dict)

    @property
    def topic(self) -> str:
        return f"{self.topic_prefix}/{self.restaurant_id}/events"


def load_config(path: str | Path) -> EdgeConfig:
    raw = json.loads(Path(path).read_text(encoding="utf-8"))
    mqtt, model = raw.get("mqtt", {}), raw.get("model", {})
    zones = [Zone(z["id"], z["type"], tuple(tuple(p) for p in z["polygon"])) for z in raw["zones"]]
    return EdgeConfig(
        restaurant_id=raw["restaurantId"],
        zones=zones,
        groups={name: set(labels) for name, labels in raw.get("labels", {"person": ["person"]}).items()},
        stable_seconds=raw.get("stableSeconds", 5.0),
        seconds_per_customer=raw.get("queue", {}).get("secondsPerCustomer", 90.0),
        mqtt_host=mqtt.get("host", "localhost"),
        mqtt_port=mqtt.get("port", 1883),
        topic_prefix=mqtt.get("topicPrefix", "restovision"),
        weights=model.get("weights", "yolo11n.pt"),
        confidence=model.get("confidence", 0.35),
        fps=model.get("fps", 5.0),
    )
