"""Mise à jour à distance du boîtier.

Une version est un paquet signé publié sur le sujet MQTT `restovision/updates` (message conservé).
Le boîtier l'installe seulement si :
- sa signature Ed25519 correspond à la clé publique de sa configuration (la clé privée reste chez
  l'éditeur, hors du serveur : un serveur ou un broker compromis ne peut pas signer) ;
- son empreinte SHA-256 correspond au paquet reçu ;
- sa version est plus récente que celle qui tourne (pas de retour forcé vers une version ancienne) ;
- elle n'a pas déjà échoué sur ce boîtier.

Le paquet est installé à côté de l'ancien, puis le boîtier s'arrête avec le code 75 : le lanceur
(`launcher.py`) redémarre sur la nouvelle version, et revient à l'ancienne si elle ne tient pas.
"""
from __future__ import annotations

import base64
import hashlib
import io
import json
import os
import shutil
import tarfile
from pathlib import Path

UPDATE_TOPIC = "restovision/updates"
RESTART_CODE = 75  # code de sortie : « nouvelle version installée, redémarrer »
MAX_ARCHIVE_BYTES = 5_000_000


class RejectedUpdate(Exception):
    """Une version refusée. Le message est montré au gérant dans Réglages."""


def parse_version(version: str) -> tuple[int, ...]:
    try:
        return tuple(int(part) for part in version.strip().split("."))
    except ValueError as error:
        raise RejectedUpdate(f"numéro de version illisible : {version!r}") from error


def signed_bytes(version: str, sha256: str) -> bytes:
    """Ce qui est signé : la version ET l'empreinte, pour qu'on ne puisse pas changer l'une sans l'autre."""
    return f"restovision-edge\n{version}\n{sha256}".encode()


def verify(manifest: dict, public_key_b64: str) -> bytes:
    """Contrôle un manifeste ; renvoie le paquet (tar.gz) s'il est authentique, lève RejectedUpdate sinon."""
    from cryptography.exceptions import InvalidSignature
    from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PublicKey

    try:
        version, sha256 = str(manifest["version"]), str(manifest["sha256"])
        archive = base64.b64decode(manifest["archive"], validate=True)
        signature = base64.b64decode(manifest["signature"], validate=True)
    except (KeyError, ValueError, TypeError) as error:
        raise RejectedUpdate("manifeste incomplet") from error
    parse_version(version)
    if len(archive) > MAX_ARCHIVE_BYTES:
        raise RejectedUpdate("paquet trop gros")
    if hashlib.sha256(archive).hexdigest() != sha256:
        raise RejectedUpdate("empreinte du paquet incorrecte")
    try:
        Ed25519PublicKey.from_public_bytes(base64.b64decode(public_key_b64)).verify(signature, signed_bytes(version, sha256))
    except (InvalidSignature, ValueError) as error:
        raise RejectedUpdate("signature invalide") from error
    return archive


class VersionStore:
    """Les versions installées sous `root` : versions/<v>/, et les fichiers current, previous, good, bad/<v>."""

    def __init__(self, root: str | Path):
        self.root = Path(root)
        (self.root / "versions").mkdir(parents=True, exist_ok=True)
        (self.root / "bad").mkdir(exist_ok=True)

    def _read(self, name: str) -> str | None:
        try:
            return (self.root / name).read_text(encoding="utf-8").strip() or None
        except FileNotFoundError:
            return None

    def _write(self, name: str, value: str) -> None:
        tmp = self.root / f".{name}.tmp"
        tmp.write_text(value, encoding="utf-8")
        os.replace(tmp, self.root / name)   # remplacement atomique : jamais de fichier à moitié écrit

    @property
    def current(self) -> str | None:
        return self._read("current")

    @property
    def previous(self) -> str | None:
        return self._read("previous")

    @property
    def good(self) -> str | None:
        return self._read("good")

    def path(self, version: str) -> Path:
        return self.root / "versions" / version

    def is_bad(self, version: str) -> bool:
        return (self.root / "bad" / version).exists()

    def mark_bad(self, version: str, reason: str) -> None:
        (self.root / "bad" / version).write_text(reason, encoding="utf-8")

    def note_rollback(self, message: str) -> None:
        """Le lanceur laisse ce mot pour que le boîtier le signale à l'application."""
        self._write("rollback", message)

    @property
    def rollback_note(self) -> str | None:
        return self._read("rollback")

    def clear_rollback(self) -> None:
        (self.root / "rollback").unlink(missing_ok=True)

    def mark_good(self, version: str) -> None:
        self._write("good", version)

    def switch_to(self, version: str) -> None:
        if self.current and self.current != version:
            self._write("previous", self.current)
        self._write("current", version)

    def roll_back(self) -> str | None:
        """Revient à la dernière version qui a tenu ; renvoie la version retenue."""
        target = self.good if self.good and self.good != self.current else self.previous
        if target and self.path(target).exists():
            self._write("current", target)
            return target
        return None

    def install(self, version: str, archive: bytes) -> None:
        """Décompresse le paquet dans versions/<version>/ et vérifie qu'il annonce bien cette version."""
        final = self.path(version)
        staging = final.with_name(version + ".tmp")
        shutil.rmtree(staging, ignore_errors=True)
        staging.mkdir(parents=True)
        try:
            with tarfile.open(fileobj=io.BytesIO(archive), mode="r:gz") as tar:
                for member in tar.getmembers():
                    name = member.name
                    if not (member.isfile() or member.isdir()) or name.startswith(("/", "\\")) or ".." in Path(name).parts:
                        raise RejectedUpdate(f"chemin interdit dans le paquet : {name}")
                    if not name.startswith("restovision_edge"):
                        raise RejectedUpdate(f"fichier inattendu dans le paquet : {name}")
                tar.extractall(staging, filter="data")
            announced = _declared_version(staging / "restovision_edge" / "__init__.py")
            if announced != version:
                raise RejectedUpdate(f"le paquet annonce la version {announced}, pas {version}")
            shutil.rmtree(final, ignore_errors=True)
            os.replace(staging, final)
        except (tarfile.TarError, OSError) as error:
            shutil.rmtree(staging, ignore_errors=True)
            raise RejectedUpdate(f"paquet illisible : {error}") from error
        except RejectedUpdate:
            shutil.rmtree(staging, ignore_errors=True)
            raise


def _declared_version(init_file: Path) -> str | None:
    try:
        for line in init_file.read_text(encoding="utf-8").splitlines():
            if line.startswith("__version__"):
                return line.split("=", 1)[1].strip().strip("\"'")
    except FileNotFoundError:
        return None
    return None


class Updater:
    """Reçoit les manifestes publiés et installe ceux qui passent tous les contrôles."""

    def __init__(self, store: VersionStore, public_key_b64: str, running_version: str):
        self.store = store
        self.public_key = public_key_b64
        self.running = running_version
        self.restart_requested = False
        # Un retour en arrière du lanceur est signalé jusqu'à la prochaine mise à jour réussie.
        self.last_error: str | None = store.rollback_note

    def handle(self, payload: bytes) -> None:
        if not payload:
            return   # message conservé effacé : rien à installer
        try:
            manifest = json.loads(payload)
            version = str(manifest.get("version", ""))
            if parse_version(version) <= parse_version(self.running) or self.store.is_bad(version):
                return
            archive = verify(manifest, self.public_key)
            self.store.install(version, archive)
            self.store.switch_to(version)
            self.store.clear_rollback()
            self.last_error = None
            self.restart_requested = True
        except json.JSONDecodeError:
            self.last_error = "manifeste illisible"
        except RejectedUpdate as error:
            self.last_error = f"{error}"
