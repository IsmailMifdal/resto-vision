"""File d'attente au comptoir : nombre de personnes et attente estimée."""
from __future__ import annotations

from ..models import Detection, Event, Zone
from ..zones import count_by_group, detections_in_zone


class QueueAnalyzer:
    def __init__(self, restaurant_id: str, zone: Zone, groups: dict[str, set[str]],
                 seconds_per_customer: float = 90.0, stable_seconds: float = 5.0):
        self.restaurant_id = restaurant_id
        self.zone = zone
        self.groups = {"person": groups.get("person", {"person"})}
        self.seconds_per_customer = seconds_per_customer
        self.stable_seconds = stable_seconds
        self.count = 0
        self._candidate: int | None = None
        self._candidate_since = 0.0

    def process(self, detections: list[Detection], now: float) -> list[Event]:
        observed = count_by_group(detections_in_zone(self.zone, detections), self.groups)["person"]
        if observed == self.count:
            self._candidate = None
            return []
        if observed != self._candidate:
            self._candidate, self._candidate_since = observed, now
            return []
        if now - self._candidate_since < self.stable_seconds:
            return []
        self.count, self._candidate = observed, None
        return [Event(
            type="QUEUE",
            restaurant_id=self.restaurant_id,
            zone_id=self.zone.id,
            timestamp=now,
            data={"persons": observed, "estimatedWaitSeconds": int(observed * self.seconds_per_customer)},
        )]
