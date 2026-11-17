"""Mise à jour à distance : signature, versions, installation, retour en arrière."""
import base64
import io
import json
import tarfile
from pathlib import Path

import pytest

pytest.importorskip("cryptography")

from restovision_edge.launcher import Launcher, bootstrap  # noqa: E402
from restovision_edge.release import build, keygen  # noqa: E402
from restovision_edge.updater import RESTART_CODE, RejectedUpdate, Updater, VersionStore, verify  # noqa: E402

PACKAGE = Path(__file__).resolve().parents[1] / "restovision_edge"


def package_at(tmp_path: Path, version: str, extra: str = "") -> Path:
    """Une copie du paquet du boîtier qui annonce `version`."""
    import shutil

    target = tmp_path / f"src-{version}" / "restovision_edge"
    shutil.copytree(PACKAGE, target, ignore=shutil.ignore_patterns("__pycache__"))
    init = target / "__init__.py"
    text = init.read_text(encoding="utf-8")
    lines = [f'__version__ = "{version}"' if line.startswith("__version__") else line for line in text.splitlines()]
    init.write_text("\n".join(lines) + "\n" + extra, encoding="utf-8")
    return target


@pytest.fixture
def keys(tmp_path):
    public = keygen(tmp_path / "keys")
    return public, tmp_path / "keys" / "restovision-edge.key"


def test_a_signed_release_installs_and_asks_for_a_restart(tmp_path, keys):
    public, private = keys
    store = VersionStore(tmp_path / "box")
    updater = Updater(store, public, "0.2.0")
    manifest = build(package_at(tmp_path, "0.3.0"), private)

    updater.handle(json.dumps(manifest).encode())

    assert updater.last_error is None and updater.restart_requested
    assert store.current == "0.3.0"
    assert (store.path("0.3.0") / "restovision_edge" / "updater.py").exists()


def test_a_tampered_package_or_another_key_is_refused(tmp_path, keys):
    public, private = keys
    manifest = build(package_at(tmp_path, "0.3.0"), private)

    tampered = dict(manifest, archive=base64.b64encode(base64.b64decode(manifest["archive"]) + b"x").decode())
    with pytest.raises(RejectedUpdate, match="empreinte"):
        verify(tampered, public)

    import hashlib
    archive = base64.b64decode(tampered["archive"])
    resigned = dict(tampered, sha256=hashlib.sha256(archive).hexdigest())   # empreinte refaite, signature d'origine
    with pytest.raises(RejectedUpdate, match="signature"):
        verify(resigned, public)

    other_public = keygen(tmp_path / "other")
    with pytest.raises(RejectedUpdate, match="signature"):
        verify(manifest, other_public)

    relabelled = dict(manifest, version="9.9.9")   # changer le numéro casse la signature
    with pytest.raises(RejectedUpdate, match="signature"):
        verify(relabelled, public)


def test_older_versions_and_failed_versions_are_ignored(tmp_path, keys):
    public, private = keys
    store = VersionStore(tmp_path / "box")
    old = build(package_at(tmp_path, "0.1.9"), private)
    updater = Updater(store, public, "0.2.0")
    updater.handle(json.dumps(old).encode())
    assert store.current is None and not updater.restart_requested and updater.last_error is None

    store.mark_bad("0.3.0", "plantage")
    updater.handle(json.dumps(build(package_at(tmp_path, "0.3.0"), private)).encode())
    assert store.current is None and not updater.restart_requested


def test_a_package_that_escapes_its_folder_is_refused(tmp_path):
    buffer = io.BytesIO()
    with tarfile.open(fileobj=buffer, mode="w:gz") as tar:
        data = b"x"
        info = tarfile.TarInfo("../../etc/evil")
        info.size = len(data)
        tar.addfile(info, io.BytesIO(data))
    with pytest.raises(RejectedUpdate, match="chemin interdit"):
        VersionStore(tmp_path / "box").install("0.3.0", buffer.getvalue())


def test_a_mislabelled_package_is_refused(tmp_path, keys):
    _, private = keys
    manifest = build(package_at(tmp_path, "0.3.0"), private)
    with pytest.raises(RejectedUpdate, match="annonce la version 0.3.0"):
        VersionStore(tmp_path / "box").install("0.4.0", base64.b64decode(manifest["archive"]))


def test_the_launcher_restarts_on_update_and_rolls_back_a_version_that_crashes(tmp_path):
    store = VersionStore(tmp_path / "box")
    first = bootstrap(store)
    (store.path("0.9.0") / "restovision_edge").mkdir(parents=True)
    runs = []

    def run(version, _args):
        runs.append(version)
        if version == first and len(runs) == 1:
            store.switch_to("0.9.0")          # le boîtier vient d'installer 0.9.0
            return RESTART_CODE, 300.0
        if version == "0.9.0":
            return 1, 3.0                     # la nouvelle version plante au démarrage
        return 0, 500.0                       # l'ancienne tourne puis s'arrête normalement

    code = Launcher(store, [], run=run, sleep=lambda _s: None, log=lambda _m: None).loop(max_runs=6)
    assert code == 0
    assert runs == [first, "0.9.0", "0.9.0", first]
    assert store.current == first and store.is_bad("0.9.0")
    assert "0.9.0 écartée" in Updater(store, "", first).last_error


def test_the_launcher_runs_the_installed_version_not_the_code_in_the_current_folder(tmp_path, monkeypatch):
    """Les tests tournent depuis edge/, qui contient lui-même un dossier restovision_edge : un piège."""
    store = VersionStore(tmp_path / "box")
    package = store.path("7.7.7") / "restovision_edge"
    package.mkdir(parents=True)
    marker = tmp_path / "ran-from.txt"
    (package / "__init__.py").write_text('__version__ = "7.7.7"\n', encoding="utf-8")
    (package / "__main__.py").write_text(
        f"import pathlib, sys\npathlib.Path({str(marker)!r}).write_text(__file__ + '|' + ' '.join(sys.argv[1:]))\n",
        encoding="utf-8")
    monkeypatch.chdir(PACKAGE.parent)

    code, _ = Launcher(store, ["--config", "x.json"])._run("7.7.7", ["--config", "x.json"])

    ran_from, argv = marker.read_text().split("|")
    assert code == 0
    assert str(store.path("7.7.7")) in ran_from
    assert argv.startswith("--config x.json --update-root")
