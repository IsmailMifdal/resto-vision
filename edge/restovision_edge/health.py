"""Signal de vie du boîtier : le backend sait qu'il tourne, à quelle cadence, et depuis quand il se tait."""
from __future__ import annotations

import time
from typing import Callable

from . import __version__
from .models import Event

HEARTBEAT_PERIOD = 30.0  # secondes entre deux signaux de vie


class Heartbeat:
    """Compte les images analysées et produit un événement HEARTBEAT toutes les `period` secondes."""

    def __init__(self, restaurant_id: str, box_id: str, source: str, period: float = HEARTBEAT_PERIOD,
                 clock: Callable[[], float] = time.time):
        self.restaurant_id = restaurant_id
        self.box_id = box_id
        self.source = source
        self.period = period
        self.clock = clock
        self.started = clock()
        self.window_start = self.started
        self.frames = 0
        self.sent_once = False

    def frame(self) -> None:
        self.frames += 1

    def due(self, buffered: int = 0, update_error: str | None = None) -> Event | None:
        """L'événement à publier s'il est temps, sinon None. Le premier part tout de suite."""
        now = self.clock()
        elapsed = now - self.window_start
        if self.sent_once and elapsed < self.period:
            return None
        fps = round(self.frames / elapsed, 1) if elapsed > 0 else 0.0
        self.sent_once, self.window_start, self.frames = True, now, 0
        return Event(
            type="HEARTBEAT",
            restaurant_id=self.restaurant_id,
            zone_id="",
            timestamp=now,
            data={"boxId": self.box_id, "source": self.source, "fps": fps, "version": __version__,
                  "uptimeSeconds": int(now - self.started), "buffered": buffered, "updateError": update_error},
        )


def describe_source(source: str) -> str:
    """Le type de source, sans jamais l'adresse : une URL RTSP contient souvent un mot de passe."""
    if source == "simulate":
        return "simulateur"
    if source.lower().startswith("rtsp://"):
        return "caméra"
    return "vidéo"
