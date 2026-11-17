"""Simulateur : rejoue un service de restaurant sans caméra ni GPU.

Permet de tester toute la chaîne (edge -> MQTT -> backend -> web) sur un PC.
"""
from __future__ import annotations

from .config import EdgeConfig
from .models import Detection, Zone


def _center(zone: Zone) -> tuple[float, float]:
    xs, ys = [p[0] for p in zone.polygon], [p[1] for p in zone.polygon]
    return sum(xs) / len(xs), sum(ys) / len(ys)


def _box(label: str, cx: float, cy: float, size: float = 20) -> Detection:
    return Detection(label, cx - size, cy - size, cx + size, cy + size)


def scenario(config: EdgeConfig, cycle_seconds: float = 240.0, step: float = 1.0, cycles: int = 0):
    """Chaque table suit le cycle : vide -> clients -> servis -> partis -> débarrassée.
    Les tables sont décalées entre elles. La file varie de 0 à 4 personnes.
    cycles = 0 : sans fin. Renvoie (détections, secondes écoulées)."""
    tables = [z for z in config.zones if z.type == "TABLE"]
    queues = [z for z in config.zones if z.type == "QUEUE"]
    person = sorted(config.groups["person"])[0]
    dish = sorted(config.groups.get("dish", {"cup"}))[0]
    food = sorted(config.groups.get("food", set()))
    meal = "pizza" if "pizza" in food else (food[0] if food else None)
    t = 0.0
    while cycles == 0 or t < cycle_seconds * cycles:
        detections: list[Detection] = []
        for i, zone in enumerate(tables):
            phase = ((t + i * cycle_seconds / max(1, len(tables))) % cycle_seconds) / cycle_seconds
            cx, cy = _center(zone)
            if 0.15 <= phase < 0.75:  # clients assis
                detections += [_box(person, cx - 30, cy), _box(person, cx + 30, cy)]
            if 0.40 <= phase < 0.90:  # vaisselle sur la table
                detections.append(_box(dish, cx, cy + 30, 8))
            if meal and 0.40 <= phase < 0.90:
                # Deux plats servis ; une fois sur trois, un plat revient à moitié plein.
                cycle = int((t + i * cycle_seconds / max(1, len(tables))) // cycle_seconds)
                left = 1 if (cycle + i) % 3 == 0 else 0
                served = 2 if phase < 0.70 else left
                detections += [_box(meal, cx - 20 + 40 * k, cy - 30, 10) for k in range(served)]
        for zone in queues:
            cx, cy = _center(zone)
            count = int(4 * abs(((t / cycle_seconds) * 2) % 2 - 1) + 0.5)
            detections += [_box(person, cx, cy - 60 + 30 * k) for k in range(count)]
        yield detections, t
        t += step
