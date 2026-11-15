"""Machine à états d'une table : vide, en attente, servie, à débarrasser."""
from __future__ import annotations

from ..models import Detection, Event, TableState, Zone
from ..zones import count_by_group, detections_in_zone


def classify(persons: int, dishes: int) -> TableState:
    if persons > 0:
        return TableState.SERVED if dishes > 0 else TableState.WAITING
    return TableState.TO_CLEAR if dishes > 0 else TableState.EMPTY


class TableAnalyzer:
    """Suit une table. Un changement n'est validé que s'il tient `stable_seconds`,
    pour ignorer un serveur qui passe ou une détection ratée sur quelques images."""

    def __init__(self, restaurant_id: str, zone: Zone, groups: dict[str, set[str]], stable_seconds: float = 5.0):
        self.restaurant_id = restaurant_id
        self.zone = zone
        self.groups = groups
        self.stable_seconds = stable_seconds
        self.state = TableState.EMPTY
        self.state_since: float | None = None
        self._candidate: TableState | None = None
        self._candidate_since = 0.0

    def process(self, detections: list[Detection], now: float) -> list[Event]:
        if self.state_since is None:
            self.state_since = now
        counts = count_by_group(detections_in_zone(self.zone, detections), self.groups)
        persons, dishes = counts.get("person", 0), counts.get("dish", 0)
        observed = classify(persons, dishes)

        if observed == self.state:
            self._candidate = None
            return []
        if observed != self._candidate:
            self._candidate, self._candidate_since = observed, now
            return []
        if now - self._candidate_since < self.stable_seconds:
            return []

        previous = self.state
        # L'état a réellement commencé quand on l'a vu pour la première fois.
        self.state, self.state_since, self._candidate = observed, self._candidate_since, None
        return [Event(
            type="TABLE_STATE",
            restaurant_id=self.restaurant_id,
            zone_id=self.zone.id,
            timestamp=self.state_since,
            data={"state": observed.value, "previousState": previous.value, "persons": persons, "dishes": dishes},
        )]
