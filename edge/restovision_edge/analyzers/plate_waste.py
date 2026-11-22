"""Anti-gaspillage : ce qui reste dans les assiettes quand les clients partent.

Le modèle de base ne reconnaît pas les assiettes, mais il reconnaît des aliments (pizza, sandwich,
gâteau, fruits, brocoli, carotte). Pendant le repas, on note le plus grand nombre d'aliments vus sur la
table ; au départ des clients, on compte ceux qui restent. Le rapport des deux donne une estimation
de ce qui revient en cuisine.

Limite : un plat que le modèle ne sait pas nommer (un steak, une salade composée) n'est pas compté.
Un modèle entraîné sur les assiettes du restaurant pilote remplacera la liste d'aliments.
"""
from __future__ import annotations

from ..models import Detection, Event, TableState
from ..smoothing import Window
from ..zones import count_by_group, detections_in_zone
from .tables import DISH_SHARE, TableAnalyzer


class PlateWasteAnalyzer:
    """Suit une table à côté de son TableAnalyzer, qui doit traiter chaque image avant lui.

    Les restes sont comptés pendant les `stable_seconds` qui suivent le départ des clients, quand plus
    personne ne mange : la fin du repas ne compte pas comme un reste.
    """

    def __init__(self, table: TableAnalyzer, food_labels: set[str]):
        self.table = table
        self.groups = {"food": food_labels}
        self._food = Window(table.stable_seconds)
        self._previous = table.state
        self.served_peak = 0
        self._left_since: float | None = None  # départ des clients, en attente de la mesure

    def process(self, detections: list[Detection], now: float) -> list[Event]:
        seen = count_by_group(detections_in_zone(self.table.zone, detections), self.groups)["food"]
        self._food.add(now, seen)
        food = self._food.level(now, DISH_SHARE)
        state, previous = self.table.state, self._previous
        self._previous = state

        if state is TableState.SERVED:
            self.served_peak = max(self.served_peak, food)
            self._left_since = None
            return []
        if previous is TableState.SERVED and state is TableState.TO_CLEAR and self.served_peak > 0:
            self._left_since = now
        if self._left_since is None:
            self.served_peak = 0
            return []
        waited = now - self._left_since
        if state is TableState.TO_CLEAR and waited < self.table.stable_seconds:
            return []
        # Fenêtre entièrement après le départ, ou table débarrassée plus tôt : on mesure maintenant.
        left = food if waited >= self.table.stable_seconds else seen
        event = Event(
            type="PLATE_RETURN",
            restaurant_id=self.table.restaurant_id,
            zone_id=self.table.zone.id,
            timestamp=self._left_since,
            data={"servedFood": self.served_peak, "leftFood": min(left, self.served_peak)},
        )
        self.served_peak, self._left_since = 0, None
        return [event]
