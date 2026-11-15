"""Assemble les analyseurs et leur fait traiter chaque image."""
from __future__ import annotations

from .analyzers import Analyzer
from .analyzers.queue import QueueAnalyzer
from .analyzers.tables import TableAnalyzer
from .config import EdgeConfig
from .models import Detection, Event


def build_analyzers(config: EdgeConfig) -> list[Analyzer]:
    analyzers: list[Analyzer] = []
    for zone in config.zones:
        if zone.type == "TABLE":
            analyzers.append(TableAnalyzer(config.restaurant_id, zone, config.groups, config.stable_seconds))
        elif zone.type == "QUEUE":
            analyzers.append(QueueAnalyzer(config.restaurant_id, zone, config.groups,
                                           config.seconds_per_customer, config.stable_seconds))
        else:
            raise ValueError(f"Type de zone inconnu : {zone.type}")
    # V2 / V3 : ajouter ici PlateWasteAnalyzer, HygieneAnalyzer.
    return analyzers


class Pipeline:
    def __init__(self, config: EdgeConfig):
        self.analyzers = build_analyzers(config)

    def process(self, detections: list[Detection], now: float) -> list[Event]:
        events: list[Event] = []
        for analyzer in self.analyzers:
            events.extend(analyzer.process(detections, now))
        return events
