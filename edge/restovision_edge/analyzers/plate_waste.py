"""V2 - Anti-gaspillage : repérer les assiettes qui reviennent pleines.

À faire :
- entraîner un modèle sur des assiettes (COCO n'a pas de classe "assiette") avec
  un niveau de remplissage (vide, entamée, pleine) ;
- regarder l'assiette au moment où la table passe à TO_CLEAR ;
- émettre un événement PLATE_WASTE {zoneId, fillLevel}.
"""
from __future__ import annotations

from ..models import Detection, Event


class PlateWasteAnalyzer:
    def process(self, detections: list[Detection], now: float) -> list[Event]:
        return []  # pas encore implémenté
