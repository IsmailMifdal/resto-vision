"""Entraîner RF-DETR sur le jeu d'images du restaurant (produit par `autolabel`, puis relu).

    python -m restovision_training.train --dataset dataset --size nano --epochs 40 --out runs/pilote

À lancer de préférence sur une machine avec carte graphique (Jetson, poste avec GPU, ou service en ligne) :
sur un processeur seul, compter des heures. À la fin, l'outil indique quoi mettre dans edge.json.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path

SIZES = {"nano": "RFDETRNano", "small": "RFDETRSmall", "medium": "RFDETRMedium"}


def check_dataset(root: Path) -> list[str]:
    """Vérifie le jeu avant d'entraîner ; renvoie ses classes."""
    classes = None
    for split in ("train", "valid"):
        annotations = root / split / "_annotations.coco.json"
        if not annotations.exists():
            raise SystemExit(f"Il manque {annotations} : lancer d'abord restovision_training.autolabel.")
        data = json.loads(annotations.read_text(encoding="utf-8"))
        if not data["images"]:
            raise SystemExit(f"Le dossier {split} ne contient aucune image : il en faut dans train et valid.")
        names = [c["name"] for c in sorted(data["categories"], key=lambda c: c["id"]) if c["supercategory"] != "none"]
        if classes is not None and names != classes:
            raise SystemExit("Les classes de train et de valid ne sont pas les mêmes.")
        classes = names
    return classes


def suggested_labels(classes: list[str]) -> dict:
    """Les groupes de classes à mettre dans edge.json pour le modèle entraîné."""
    plates = [c for c in classes if c.startswith("plate_")]
    return {
        "person": ["person"],
        "dish": [c for c in classes if c in {"cup", "bowl", "wine glass", "bottle", "fork", "knife", "spoon",
                                              "pizza", "sandwich", "cake"}] + plates,
        # Anti-gaspillage : une assiette encore pleine ou entamée au départ des clients revient en cuisine.
        "food": [c for c in plates if c != "plate_empty"],
        "obstacle": [c for c in classes if c in {"chair", "suitcase", "backpack", "handbag", "bench", "potted plant",
                                                  "bicycle", "dining table"}],
    }


def main() -> None:
    parser = argparse.ArgumentParser(description="Entraîner RF-DETR sur les images du restaurant")
    parser.add_argument("--dataset", required=True, type=Path)
    parser.add_argument("--size", choices=SIZES, default="nano")
    parser.add_argument("--epochs", type=int, default=40)
    parser.add_argument("--batch-size", type=int, default=4)
    parser.add_argument("--grad-accum", type=int, default=4, help="cumul de gradients (lot effectif = batch x cumul)")
    parser.add_argument("--out", required=True, type=Path)
    options = parser.parse_args()

    classes = check_dataset(options.dataset)
    import rfdetr

    model = getattr(rfdetr, SIZES[options.size])()
    model.train(dataset_dir=str(options.dataset), epochs=options.epochs, batch_size=options.batch_size,
                grad_accum_steps=options.grad_accum, output_dir=str(options.out), early_stopping=True,
                tensorboard=False)
    # checkpoint_best_total.pth : le meilleur modèle, allégé pour l'inférence (c'est lui qui part sur le boîtier).
    best = options.out / "checkpoint_best_total.pth"
    weights = str(best if best.exists() else next(iter(sorted(options.out.glob("*.pth"))), best))
    snippet = {"model": {"backend": "rfdetr", "size": options.size, "weights": weights, "confidence": 0.35, "fps": 5},
               "labels": suggested_labels(classes)}
    (options.out / "edge-model.json").write_text(json.dumps(snippet, indent=2, ensure_ascii=False), encoding="utf-8")
    print("Entraînement terminé. À recopier dans edge.json :")
    print(json.dumps(snippet, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
