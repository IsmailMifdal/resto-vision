"""Géométrie : quel objet est dans quelle zone."""
from __future__ import annotations

from .models import Detection, Zone


def point_in_polygon(point: tuple[float, float], polygon: tuple[tuple[float, float], ...]) -> bool:
    """Algorithme du lancer de rayon."""
    x, y = point
    inside = False
    n = len(polygon)
    for i in range(n):
        x1, y1 = polygon[i]
        x2, y2 = polygon[(i + 1) % n]
        if (y1 > y) != (y2 > y):
            x_cross = x1 + (y - y1) * (x2 - x1) / (y2 - y1)
            if x < x_cross:
                inside = not inside
    return inside


def detections_in_zone(zone: Zone, detections: list[Detection]) -> list[Detection]:
    return [d for d in detections if point_in_polygon(d.anchor, zone.polygon)]


def count_by_group(detections: list[Detection], groups: dict[str, set[str]]) -> dict[str, int]:
    """Compte les détections par groupe de labels (ex. person, dish)."""
    counts = {name: 0 for name in groups}
    for d in detections:
        for name, labels in groups.items():
            if d.label in labels:
                counts[name] += 1
    return counts
