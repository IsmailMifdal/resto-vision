"""Lissage dans le temps : une détection ratée ou en trop sur quelques images ne doit rien changer."""
from __future__ import annotations

from collections import deque
from typing import Any


class Window:
    """Garde les observations des dernières `seconds` secondes.

    Chaque observation vaut jusqu'à la suivante. Avant la première, la valeur est 0.
    """

    def __init__(self, seconds: float):
        self.seconds = seconds
        self.samples: deque[tuple[float, Any]] = deque()

    def add(self, now: float, value: Any) -> None:
        self.samples.append((now, value))
        start = now - self.seconds
        # On garde une seule observation d'avant la fenêtre : elle donne la valeur au début de la fenêtre.
        while len(self.samples) >= 2 and self.samples[1][0] <= start:
            self.samples.popleft()

    def level(self, now: float, share: float) -> int:
        """Plus grande valeur atteinte ou dépassée pendant au moins `share` de la fenêtre.

        share = 0.5 : ce qu'on a vu la moitié du temps (ignore un passage bref).
        share = 0.3 : suffit de le voir 30 % du temps (pour les petits objets, souvent ratés).
        """
        if not self.samples:
            return 0
        if self.seconds <= 0:
            return self.samples[-1][1]
        start = now - self.seconds
        durations: dict[int, float] = {}
        for i, (t, value) in enumerate(self.samples):
            end = self.samples[i + 1][0] if i + 1 < len(self.samples) else now
            held = end - max(t, start)
            if held > 0:
                durations[value] = durations.get(value, 0.0) + held
        needed, total = share * self.seconds, 0.0
        for value in sorted(durations, reverse=True):
            total += durations[value]
            if total >= needed - 1e-9:
                return value
        return 0

    def first(self, value: Any, default: float) -> float:
        """Heure de la plus ancienne observation gardée qui vaut `value`."""
        return next((t for t, v in self.samples if v == value), default)
