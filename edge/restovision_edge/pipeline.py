"""Assemble les analyseurs et leur fait traiter chaque image."""
from __future__ import annotations

from .analyzers import Analyzer
from .analyzers.exit import ExitAnalyzer
from .analyzers.plate_waste import PlateWasteAnalyzer
from .analyzers.queue import QueueAnalyzer
from .analyzers.tables import TableAnalyzer
from .config import EdgeConfig
from .models import Detection, Event


def build_analyzers(config: EdgeConfig) -> list[Analyzer]:
    analyzers: list[Analyzer] = []
    for zone in config.zones:
        if zone.type == "TABLE":
            table = TableAnalyzer(config.restaurant_id, zone, config.groups, config.stable_seconds)
            analyzers.append(table)
            if config.groups.get("food"):
                analyzers.append(PlateWasteAnalyzer(table, config.groups["food"]))  # après sa table
        elif zone.type == "QUEUE":
            analyzers.append(QueueAnalyzer(config.restaurant_id, zone, config.groups,
                                           config.seconds_per_customer, config.stable_seconds))
        elif zone.type == "EXIT":
            analyzers.append(ExitAnalyzer(config.restaurant_id, zone, config.groups.get("obstacle", set()),
                                          max(10.0, config.stable_seconds * 2)))
        else:
            raise ValueError(f"Type de zone inconnu : {zone.type}")
    # V3 : ajouter ici HygieneAnalyzer.
    return analyzers


class Pipeline:
    def __init__(self, config: EdgeConfig):
        self.analyzers = build_analyzers(config)

    def process(self, detections: list[Detection], now: float) -> list[Event]:
        events: list[Event] = []
        for analyzer in self.analyzers:
            events.extend(analyzer.process(detections, now))
        return events


def layout_event(config: EdgeConfig, now: float) -> Event:
    """Le contour de toutes les zones, pour que l'application dessine le plan de salle. Aucune image."""
    return Event(
        type="LAYOUT",
        restaurant_id=config.restaurant_id,
        zone_id="",
        timestamp=now,
        data={"zones": [{"id": zone.id, "type": zone.type, "polygon": [list(point) for point in zone.polygon]}
                        for zone in config.zones]},
    )
