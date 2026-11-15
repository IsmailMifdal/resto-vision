"""Un analyseur reçoit les détections d'une image et renvoie des événements.

Pour ajouter une fonctionnalité (gaspillage, hygiène...), créer un analyseur ici
et l'enregistrer dans pipeline.build_analyzers.
"""
from __future__ import annotations

from typing import Protocol

from ..models import Detection, Event


class Analyzer(Protocol):
    def process(self, detections: list[Detection], now: float) -> list[Event]: ...
