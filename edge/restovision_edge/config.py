"""Chargement de la configuration du boîtier."""
from __future__ import annotations

import json
import os
import socket
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
    mqtt_username: str | None = None
    mqtt_password: str | None = None
    mqtt_tls: bool = False
    box_id: str = "boitier"
    # Clé publique de l'éditeur (Ed25519, base64). Absente : pas de mise à jour à distance.
    update_public_key: str | None = None
    # Moteur de détection : "rfdetr" (Apache 2.0, par défaut) ou "yolo" (AGPL-3.0, pour comparer).
    backend: str = "rfdetr"
    model_size: str = "nano"   # RF-DETR : nano, small ou medium
    weights: str | None = None   # modèle entraîné sur le restaurant ; vide = poids COCO officiels
    confidence: float = 0.35
    fps: float = 5.0
    imgsz: int = 1280  # YOLO seulement : 640 est rapide mais rate la vaisselle vue de loin
    extra: dict = field(default_factory=dict)

    @property
    def topic(self) -> str:
        return f"{self.topic_prefix}/{self.restaurant_id}/events"


def load_config(path: str | Path) -> EdgeConfig:
    raw = json.loads(Path(path).read_text(encoding="utf-8"))
    mqtt, model = raw.get("mqtt", {}), raw.get("model", {})
    zones = [Zone(z["id"], z["type"], tuple(tuple(p) for p in z["polygon"]), int(z.get("baselineDishes", 0)))
             for z in raw["zones"]]
    return EdgeConfig(
        restaurant_id=raw["restaurantId"],
        # Nom du boîtier dans l'application. Par défaut, le nom de la machine.
        box_id=raw.get("boxId") or os.environ.get("RESTOVISION_BOX_ID") or socket.gethostname(),
        zones=zones,
        groups={name: set(labels) for name, labels in raw.get("labels", {"person": ["person"]}).items()},
        stable_seconds=raw.get("stableSeconds", 5.0),
        seconds_per_customer=raw.get("queue", {}).get("secondsPerCustomer", 90.0),
        # RESTOVISION_MQTT_HOST remplace l'hôte du fichier (utile dans Docker).
        mqtt_host=os.environ.get("RESTOVISION_MQTT_HOST") or mqtt.get("host", "localhost"),
        mqtt_port=mqtt.get("port", 1883),
        topic_prefix=mqtt.get("topicPrefix", "restovision"),
        # Le mot de passe peut rester hors du fichier : variable RESTOVISION_MQTT_PASSWORD.
        mqtt_username=mqtt.get("username"),
        mqtt_password=mqtt.get("password") or os.environ.get("RESTOVISION_MQTT_PASSWORD"),
        mqtt_tls=bool(mqtt.get("tls", False)),
        update_public_key=(raw.get("update") or {}).get("publicKey"),
        # Une configuration plus ancienne sans moteur mais avec des poids YOLO reste sur YOLO.
        backend=model.get("backend") or ("yolo" if str(model.get("weights", "")).lower().startswith("yolo") else "rfdetr"),
        model_size=model.get("size", "nano"),
        weights=model.get("weights"),
        confidence=model.get("confidence", 0.35),
        fps=model.get("fps", 5.0),
        imgsz=model.get("imgsz", 1280),
    )
