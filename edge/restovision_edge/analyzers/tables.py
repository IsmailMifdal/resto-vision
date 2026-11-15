"""Machine à états d'une table : vide, en attente, servie, à débarrasser."""
from __future__ import annotations

from ..models import Detection, Event, TableState, Zone
from ..smoothing import Window
from ..zones import count_by_group, detections_in_zone

# Part du temps où il faut voir quelque chose pour y croire.
PERSON_SHARE = 0.5  # un serveur qui passe moins de la moitié de la fenêtre est ignoré
DISH_SHARE = 0.3    # la vaisselle est petite et souvent ratée : 30 % du temps suffit


def classify(persons: int, dishes: int) -> TableState:
    if persons > 0:
        return TableState.SERVED if dishes > 0 else TableState.WAITING
    return TableState.TO_CLEAR if dishes > 0 else TableState.EMPTY


class TableAnalyzer:
    """Suit une table sur une fenêtre de `stable_seconds` : une détection ratée ou
    un serveur qui passe ne changent pas l'état.

    `zone.baseline_dishes` est la vaisselle d'une table dressée et libre (verres, couverts) :
    seule la vaisselle en plus compte comme « servie » ou « à débarrasser ».
    """

    def __init__(self, restaurant_id: str, zone: Zone, groups: dict[str, set[str]], stable_seconds: float = 5.0):
        self.restaurant_id = restaurant_id
        self.zone = zone
        self.groups = groups
        self.stable_seconds = stable_seconds
        self.state = TableState.EMPTY
        self.state_since: float | None = None
        self._started = 0.0
        self.persons = 0
        self.dishes = 0
        self._persons = Window(stable_seconds)
        self._dishes = Window(stable_seconds)
        self._raw = Window(stable_seconds)

    def process(self, detections: list[Detection], now: float) -> list[Event]:
        if self.state_since is None:
            self.state_since = self._started = now
        counts = count_by_group(detections_in_zone(self.zone, detections), self.groups)
        seen_persons, seen_dishes = counts.get("person", 0), counts.get("dish", 0)
        self._persons.add(now, seen_persons)
        self._dishes.add(now, seen_dishes)
        self._raw.add(now, classify(seen_persons, self._extra(seen_dishes)))

        self.persons = self._persons.level(now, PERSON_SHARE)
        self.dishes = self._extra(self._dishes.level(now, DISH_SHARE))
        observed = classify(self.persons, self.dishes)
        # Au démarrage, on attend d'avoir une fenêtre complète avant de se prononcer.
        if observed == self.state or now - self._started < self.stable_seconds:
            return []

        previous = self.state
        # L'état a réellement commencé quand on l'a vu pour la première fois.
        self.state = observed
        self.state_since = max(self.state_since, self._raw.first(observed, default=now))
        return [Event(
            type="TABLE_STATE",
            restaurant_id=self.restaurant_id,
            zone_id=self.zone.id,
            timestamp=self.state_since,
            data={"state": observed.value, "previousState": previous.value,
                  "persons": self.persons, "dishes": self.dishes},
        )]

    def _extra(self, dishes: int) -> int:
        return max(0, dishes - self.zone.baseline_dishes)
