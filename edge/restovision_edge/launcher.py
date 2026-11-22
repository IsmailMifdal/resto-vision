"""Lanceur du boîtier : démarre la version courante, la redémarre, et revient en arrière si elle ne tient pas.

    python -m restovision_edge.launcher --root /opt/restovision -- --config config/edge.json --source rtsp://...

Le lanceur lui-même n'est jamais remplacé par une mise à jour : il reste celui de l'installation, pour
pouvoir toujours revenir à une version qui marche.
"""
from __future__ import annotations

import argparse
import shutil
import subprocess
import sys
import time
from pathlib import Path
from typing import Callable

from . import __version__
from .updater import RESTART_CODE, VersionStore

STABLE_AFTER = 120.0   # secondes de fonctionnement pour considérer une version comme bonne
MAX_EARLY_CRASHES = 2  # plantages au démarrage avant de revenir à la version précédente
BACKOFF = 5.0          # pause avant de relancer après un plantage


def bootstrap(store: VersionStore) -> str:
    """Première installation : la version livrée avec le lanceur devient la version courante."""
    if store.current and store.path(store.current).exists():
        return store.current
    target = store.path(__version__) / "restovision_edge"
    if not target.exists():
        shutil.copytree(Path(__file__).parent, target, ignore=shutil.ignore_patterns("__pycache__", "*.pyc"))
    store.switch_to(__version__)
    store.mark_good(__version__)
    return __version__


class Launcher:
    def __init__(self, store: VersionStore, args: list[str],
                 run: Callable[[str, list[str]], tuple[int, float]] | None = None,
                 sleep: Callable[[float], None] = time.sleep, log: Callable[[str], None] = print):
        self.store = store
        self.args = args
        self.run = run or self._run
        self.sleep = sleep
        self.log = log

    def _run(self, version: str, args: list[str]) -> tuple[int, float]:
        """Lance la version et attend sa fin ; renvoie (code de sortie, durée en secondes).

        Le dossier de la version est mis en tête du chemin de recherche : `python -m` chercherait sinon
        d'abord dans le dossier courant, et pourrait charger un autre code que la version voulue.
        """
        start = (f"import sys, runpy; sys.path.insert(0, {str(self.store.path(version))!r}); "
                 "runpy.run_module('restovision_edge', run_name='__main__', alter_sys=True)")
        started = time.monotonic()
        code = subprocess.call([sys.executable, "-c", start, *args, "--update-root", str(self.store.root)])
        return code, time.monotonic() - started

    def loop(self, max_runs: int | None = None) -> int:
        crashes, runs = 0, 0
        while max_runs is None or runs < max_runs:
            runs += 1
            version = self.store.current or bootstrap(self.store)
            self.log(f"Lanceur : version {version}")
            code, lasted = self.run(version, self.args)
            if lasted >= STABLE_AFTER:
                self.store.mark_good(version)
                crashes = 0
            if code == RESTART_CODE:
                self.log("Lanceur : nouvelle version installée, redémarrage")
                crashes = 0
                continue
            if code == 0:
                return 0   # fin normale (une vidéo terminée, un arrêt demandé)
            crashes = crashes + 1 if lasted < STABLE_AFTER else 1
            self.log(f"Lanceur : arrêt inattendu (code {code}) après {lasted:.0f} s")
            if crashes >= MAX_EARLY_CRASHES and version != self.store.good:
                self.store.mark_bad(version, f"plantage au démarrage (code {code})")
                back = self.store.roll_back()
                self.store.note_rollback(f"version {version} écartée après des plantages au démarrage, retour à {back}")
                self.log(f"Lanceur : version {version} écartée, retour à {back}")
                crashes = 0
                continue
            self.sleep(BACKOFF)
        return 0


def main() -> None:
    parser = argparse.ArgumentParser(description="Lanceur du boîtier RestoVision")
    parser.add_argument("--root", required=True, help="dossier des versions installées")
    parser.add_argument("args", nargs=argparse.REMAINDER, help="arguments du boîtier, après --")
    options = parser.parse_args()
    args = options.args[1:] if options.args[:1] == ["--"] else options.args
    store = VersionStore(options.root)
    bootstrap(store)
    raise SystemExit(Launcher(store, args).loop())


if __name__ == "__main__":
    main()
