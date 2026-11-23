"""Les chemins de la ligne de commande restent dans les dossiers autorisés."""
import os

import pytest

from restovision_edge.paths import ALLOWED_DIRS_VARIABLE, confined


def test_paths_inside_allowed_folders_are_accepted(tmp_path, monkeypatch):
    monkeypatch.chdir(tmp_path)
    assert confined("cles/restovision-edge.key") == (tmp_path / "cles" / "restovision-edge.key").resolve()
    assert confined(tmp_path / "a" / ".." / "b") == (tmp_path / "b").resolve()


def test_paths_outside_are_refused_unless_added(tmp_path, monkeypatch):
    outside = "Z:\cles" if os.name == "nt" else "/srv/cles-restovision"
    monkeypatch.delenv(ALLOWED_DIRS_VARIABLE, raising=False)
    with pytest.raises(ValueError):
        confined(outside)
    monkeypatch.setenv(ALLOWED_DIRS_VARIABLE, outside)
    assert str(confined(os.path.join(outside, "restovision-edge.key"))).endswith("restovision-edge.key")
