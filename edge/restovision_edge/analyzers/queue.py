"""File d'attente au comptoir : nombre de personnes et attente estimée."""
from __future__ import annotations

from ..models import Detection, Event, Zone
from ..smoothing import Window
from ..zones import count_by_group, detections_in_zone


class QueueAnalyzer:
    """Compte les personnes dans la zone. Le nombre retenu est celui vu pendant
    au moins la moitié des `stable_seconds` dernières secondes."""

    def __init__(self, restaurant_id: str, zone: Zone, groups: dict[str, set[str]],
                 seconds_per_customer: float = 90.0, stable_seconds: float = 5.0):
        self.restaurant_id = restaurant_id
        self.zone = zone
        self.groups = {"person": groups.get("person", {"person"})}
        self.seconds_per_customer = seconds_per_customer
        self.stable_seconds = stable_seconds
        self.count = 0
        self._started: float | None = None
        self._counts = Window(stable_seconds)

    def process(self, detections: list[Detection], now: float) -> list[Event]:
        self._counts.add(now, count_by_group(detections_in_zone(self.zone, detections), self.groups)["person"])
        if self._started is None:
            self._started = now
        observed = self._counts.level(now, 0.5)
        if observed == self.count or now - self._started < self.stable_seconds:
            return []
        self.count = observed
        return [Event(
            type="QUEUE",
            restaurant_id=self.restaurant_id,
            zone_id=self.zone.id,
            timestamp=now,
            data={"persons": observed, "estimatedWaitSeconds": int(observed * self.seconds_per_customer)},
        )]
