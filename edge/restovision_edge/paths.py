"""Chemins venus de la ligne de commande : ils doivent rester dans des dossiers autorisés.

Autorisés : le dossier courant, le dossier personnel, le dossier temporaire et /opt/restovision.
D'autres dossiers (par exemple une clé USB qui garde la clé de signature) s'ajoutent avec la variable
RESTOVISION_ALLOWED_DIRS, séparés par « ; » sous Windows et « : » ailleurs.
"""
from __future__ import annotations

import os
import tempfile
from pathlib import Path

ALLOWED_DIRS_VARIABLE = "RESTOVISION_ALLOWED_DIRS"


def allowed_roots() -> list[str]:
    roots = [os.getcwd(), str(Path.home()), tempfile.gettempdir(), "/opt/restovision"]
    roots += [entry for entry in os.environ.get(ALLOWED_DIRS_VARIABLE, "").split(os.pathsep) if entry.strip()]
    return [os.path.realpath(os.path.expanduser(root)) for root in roots]


def confined(path: str | os.PathLike) -> Path:
    """Le chemin, résolu (liens et « .. » compris), s'il est dans un dossier autorisé ; sinon ValueError."""
    resolved = os.path.realpath(os.path.expanduser(os.fspath(path)))
    for root in allowed_roots():
        try:
            inside = os.path.commonpath([root, resolved]) == root
        except ValueError:   # Windows : lecteurs différents
            inside = False
        if inside:
            return Path(resolved)
    raise ValueError(f"{path} est hors des dossiers autorisés ; l'ajouter à {ALLOWED_DIRS_VARIABLE} si besoin.")
