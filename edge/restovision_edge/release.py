"""Outil de l'éditeur : créer la clé, signer une version, la publier.

    python -m restovision_edge.release keygen --out cles/
    python -m restovision_edge.release build --key cles/restovision-edge.key --out version.json
    python -m restovision_edge.release publish --manifest version.json --host broker --port 8883 --tls ...

La clé privée ne doit jamais aller sur le serveur ni sur un boîtier : elle reste chez l'éditeur, hors ligne.
Les boîtiers reçoivent seulement la clé publique, dans `update.publicKey` de leur edge.json.
"""
from __future__ import annotations

import argparse
import base64
import hashlib
import io
import json
import tarfile
from pathlib import Path

from .paths import confined
from .updater import MAX_ARCHIVE_BYTES, UPDATE_TOPIC, _declared_version, parse_version, signed_bytes


def keygen(out: Path) -> str:
    from cryptography.hazmat.primitives import serialization
    from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey

    out = confined(out)
    out.mkdir(parents=True, exist_ok=True)
    private = Ed25519PrivateKey.generate()
    key_file = confined(out / "restovision-edge.key")
    public_file = confined(out / "restovision-edge.pub")
    if key_file.exists():
        raise SystemExit(f"{key_file} existe déjà : refus de l'écraser.")
    # NOSONAR ci-dessous : chemins vérifiés par confined() (dossiers autorisés), que SonarCloud ne reconnaît pas.
    key_file.write_bytes(private.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8,  # NOSONAR
                                               serialization.NoEncryption()))
    public = base64.b64encode(private.public_key().public_bytes(serialization.Encoding.Raw,
                                                                 serialization.PublicFormat.Raw)).decode()
    public_file.write_text(public + "\n", encoding="utf-8")  # NOSONAR
    return public


def build(package: Path, key_file: Path) -> dict:
    """Le manifeste d'une version : le paquet, son empreinte et la signature."""
    from cryptography.hazmat.primitives import serialization

    key_file = confined(key_file)
    version = _declared_version(package / "__init__.py")
    if not version:
        raise SystemExit("__version__ introuvable dans le paquet.")
    parse_version(version)
    buffer = io.BytesIO()
    with tarfile.open(fileobj=buffer, mode="w:gz") as tar:
        for path in sorted(package.rglob("*")):
            if "__pycache__" in path.parts or path.suffix == ".pyc":
                continue
            tar.add(path, arcname=str(Path("restovision_edge") / path.relative_to(package)), recursive=False)
    archive = buffer.getvalue()
    if len(archive) > MAX_ARCHIVE_BYTES:
        raise SystemExit("Paquet trop gros.")
    sha256 = hashlib.sha256(archive).hexdigest()
    private = serialization.load_pem_private_key(key_file.read_bytes(), password=None)
    return {
        "version": version,
        "sha256": sha256,
        "signature": base64.b64encode(private.sign(signed_bytes(version, sha256))).decode(),
        "archive": base64.b64encode(archive).decode(),
    }


def publish(manifest: str, host: str, port: int, username: str | None, password: str | None, tls: bool) -> None:
    """Publie le manifeste, conservé par le broker : un boîtier qui se connecte plus tard le reçoit aussi."""
    import paho.mqtt.client as mqtt

    client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2)
    if username:
        client.username_pw_set(username, password)
    if tls:
        client.tls_set()
    client.connect(host, port)
    client.loop_start()
    client.publish(UPDATE_TOPIC, manifest, qos=1, retain=True).wait_for_publish(timeout=30)
    client.loop_stop()
    client.disconnect()


def main() -> None:
    parser = argparse.ArgumentParser(description="Signer et publier une version du boîtier")
    commands = parser.add_subparsers(dest="command", required=True)
    gen = commands.add_parser("keygen", help="créer la paire de clés de signature")
    gen.add_argument("--out", required=True, type=Path)
    make = commands.add_parser("build", help="signer la version du paquet")
    make.add_argument("--key", required=True, type=Path)
    make.add_argument("--package", type=Path, help="paquet à signer ; par défaut, celui-ci")
    make.add_argument("--out", required=True, type=Path)
    send = commands.add_parser("publish", help="publier une version sur le broker")
    send.add_argument("--manifest", type=Path, help="manifeste à publier ; absent avec --clear")
    send.add_argument("--clear", action="store_true", help="retirer la version publiée")
    send.add_argument("--host", default="localhost")
    send.add_argument("--port", type=int, default=1883)
    send.add_argument("--username")
    send.add_argument("--password")
    send.add_argument("--tls", action="store_true")
    options = parser.parse_args()
    try:   # chemins de la ligne de commande : seulement dans les dossiers autorisés (voir paths.py)
        for name in ("out", "key", "package", "manifest"):
            if getattr(options, name, None) is not None:
                setattr(options, name, confined(getattr(options, name)))
    except ValueError as error:
        raise SystemExit(str(error)) from error

    if options.command == "keygen":
        public = keygen(options.out)
        print(f"Clé privée : {options.out / 'restovision-edge.key'} (à garder hors ligne)")
        print(f'Clé publique, à mettre dans edge.json : "update": {{ "publicKey": "{public}" }}')
    elif options.command == "build":
        manifest = build(options.package or Path(__file__).parent, options.key)
        options.out.write_text(json.dumps(manifest), encoding="utf-8")
        print(f"Version {manifest['version']} signée : {options.out} ({len(manifest['archive']) // 1024} Ko)")
    else:
        payload = "" if options.clear else options.manifest.read_text(encoding="utf-8")
        publish(payload, options.host, options.port, options.username, options.password, options.tls)
        print("Version retirée." if options.clear else "Version publiée.")


if __name__ == "__main__":
    main()
