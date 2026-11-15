"""V3 - Hygiène et sécurité : sol mouillé, sortie de secours bloquée.

Attention : cette fonctionnalité filme des salariés. À valider avec un juriste
(règles CNIL sur la vidéosurveillance au travail) avant toute mise en service.
"""
from __future__ import annotations

from ..models import Detection, Event


class HygieneAnalyzer:
    def process(self, detections: list[Detection], now: float) -> list[Event]:
        return []  # pas encore implémenté
