"""Types partagés par tout le module edge."""
from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime, timezone
from enum import Enum
from typing import Any


class TableState(str, Enum):
    EMPTY = "EMPTY"          # personne, rien sur la table
    WAITING = "WAITING"      # clients assis, pas encore servis
    SERVED = "SERVED"        # clients assis, vaisselle sur la table
    TO_CLEAR = "TO_CLEAR"    # clients partis, vaisselle restante


@dataclass(frozen=True)
class Detection:
    """Un objet détecté dans une image."""
    label: str
    x1: float
    y1: float
    x2: float
    y2: float
    confidence: float = 1.0
    track_id: int | None = None

    @property
    def anchor(self) -> tuple[float, float]:
        """Point utilisé pour savoir dans quelle zone se trouve l'objet (centre de la boîte)."""
        return ((self.x1 + self.x2) / 2, (self.y1 + self.y2) / 2)


@dataclass(frozen=True)
class Zone:
    id: str
    type: str  # "TABLE" ou "QUEUE"
    polygon: tuple[tuple[float, float], ...]
    baseline_dishes: int = 0  # vaisselle d'une table dressée et libre, à ne pas compter


@dataclass
class Event:
    """Événement envoyé au cloud. Ne contient jamais d'image."""
    type: str
    restaurant_id: str
    zone_id: str
    timestamp: float  # secondes epoch
    data: dict[str, Any] = field(default_factory=dict)

    def to_json(self) -> dict[str, Any]:
        iso = datetime.fromtimestamp(self.timestamp, tz=timezone.utc).isoformat().replace("+00:00", "Z")
        return {
            "type": self.type,
            "restaurantId": self.restaurant_id,
            "zoneId": self.zone_id,
            "timestamp": iso,
            **self.data,
        }
