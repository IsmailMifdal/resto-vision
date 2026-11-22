"""Sortie de secours : signaler quand des objets l'encombrent.

Seuls des objets sont comptés (chaise, valise, sac, banc, plante, vélo, table) : une personne qui passe
devant la sortie n'est jamais suivie ni comptée. La sortie est déclarée encombrée quand un objet y est vu
au moins 80 % du temps pendant la fenêtre, pour ignorer un chariot qui passe.
"""
from __future__ import annotations

from ..models import Detection, Event, Zone
from ..smoothing import Window
from ..zones import count_by_group, detections_in_zone

BLOCKED_SHARE = 0.8


class ExitAnalyzer:
    def __init__(self, restaurant_id: str, zone: Zone, obstacle_labels: set[str], window_seconds: float = 10.0):
        self.restaurant_id = restaurant_id
        self.zone = zone
        self.groups = {"obstacle": obstacle_labels}
        self.window_seconds = window_seconds
        self.blocked = False
        self.obstacles = 0
        self._seen = Window(window_seconds)
        self._started: float | None = None

    def process(self, detections: list[Detection], now: float) -> list[Event]:
        if self._started is None:
            self._started = now
        self._seen.add(now, count_by_group(detections_in_zone(self.zone, detections), self.groups)["obstacle"])
        if now - self._started < self.window_seconds:
            return []
        obstacles = self._seen.level(now, BLOCKED_SHARE)
        blocked = obstacles > 0
        if blocked == self.blocked:
            self.obstacles = obstacles if blocked else 0
            return []
        self.blocked, self.obstacles = blocked, obstacles
        return [Event(
            type="EXIT",
            restaurant_id=self.restaurant_id,
            zone_id=self.zone.id,
            timestamp=now,
            data={"blocked": blocked, "obstacles": obstacles},
        )]
