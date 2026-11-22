"""Étiquetage automatique des images du restaurant pilote, pour entraîner RF-DETR.

    python -m restovision_training.autolabel --config config/edge.json --video pilote.mp4 --every 5 --out dataset
    python -m restovision_training.autolabel --config config/edge.json --images photos/ --out dataset

Pour chaque image :
1. RF-DETR (poids COCO) étiquette les classes que le boîtier connaît déjà (personnes, vaisselle, obstacles) :
   le modèle réentraîné doit continuer à les reconnaître.
2. Grounding DINO cherche ce que COCO ne connaît pas, à partir d'une phrase : assiettes, panneau « sol glissant ».
3. SAM 2 découpe chaque assiette et la nourriture posée dessus ; la part couverte donne la classe :
   assiette pleine, entamée ou vide.
4. Le haut de chaque personne est flouté avant l'enregistrement : le jeu d'images est anonymisé.

Le résultat est un jeu au format COCO (dossiers train, valid, test) que `restovision_training.train` lit
directement. Ces étiquettes sont une première passe : les relire et les corriger (CVAT, Label Studio,
Roboflow importent ce format) avant d'entraîner. Les aperçus annotés sont dans `<sortie>/apercu/`.

Modèles (licence Apache 2.0) : RF-DETR, Grounding DINO (IDEA-Research/grounding-dino-tiny),
SAM 2.1 (facebook/sam2.1-hiera-tiny). Ils sont téléchargés au premier lancement.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from dataclasses import dataclass, field
from pathlib import Path

PLATE_CLASSES = ("plate_full", "plate_partial", "plate_empty")
WET_FLOOR = "wet_floor_sign"
# Ce que Grounding DINO cherche sur l'image entière.
PROMPTS = {"plate": "plate", "wet floor sign": WET_FLOOR}
# Ce qu'il cherche dans chaque assiette, recadrée et agrandie : vue de loin, la nourriture est trop petite.
FOOD_PROMPTS = ("food", "meal", "soup", "pasta", "salad", "meat", "fries", "dessert")
CROP_SIZE = 320
GROUNDING_MODEL = "IDEA-Research/grounding-dino-tiny"
SAM_MODEL = "facebook/sam2.1-hiera-tiny"
# Part de l'assiette couverte de nourriture : au-dessus, pleine ; en dessous d'EMPTY, vide. Valeurs de départ,
# à ajuster sur les images du pilote.
FULL_ABOVE = 0.45
EMPTY_BELOW = 0.10


def fill_class(ratio: float) -> str:
    """Classe d'une assiette selon la part couverte de nourriture."""
    if ratio >= FULL_ABOVE:
        return "plate_full"
    if ratio <= EMPTY_BELOW:
        return "plate_empty"
    return "plate_partial"


def split_of(name: str) -> str:
    """Répartition stable 80 / 10 / 10 : la même image va toujours dans le même dossier."""
    bucket = int(hashlib.sha256(name.encode()).hexdigest(), 16) % 10
    return "test" if bucket == 0 else "valid" if bucket == 1 else "train"


def blur_heads(image, person_boxes, share: float = 0.35):
    """Floute le haut de chaque personne (tête, visage) ; renvoie une copie."""
    import cv2

    out = image.copy()
    height, width = out.shape[:2]
    for x1, y1, x2, y2 in person_boxes:
        x1, x2 = max(0, int(x1)), min(width, int(x2))
        top, bottom = max(0, int(y1)), min(height, int(y1 + (y2 - y1) * share))
        if x2 - x1 < 2 or bottom - top < 2:
            continue
        region = out[top:bottom, x1:x2]
        kernel = max(15, (min(region.shape[:2]) // 2) | 1)
        out[top:bottom, x1:x2] = cv2.GaussianBlur(region, (kernel, kernel), 0)
    return out


@dataclass
class CocoDataset:
    """Écrit un jeu COCO à la manière d'un export Roboflow, le format que RF-DETR lit."""
    root: Path
    classes: list[str]
    splits: dict = field(default_factory=dict)

    def __post_init__(self):
        # Convention des exports Roboflow : la catégorie 0 nomme le jeu, les classes commencent à 1.
        self.categories = [{"id": 0, "name": "restovision", "supercategory": "none"}] + [
            {"id": i + 1, "name": name, "supercategory": "restovision"} for i, name in enumerate(self.classes)]
        self.class_id = {name: i + 1 for i, name in enumerate(self.classes)}

    def add(self, file_name: str, image, boxes: list[tuple[str, tuple[float, float, float, float]]]) -> str:
        import cv2

        split = split_of(file_name)
        data = self.splits.setdefault(split, {"images": [], "annotations": []})
        folder = self.root / split
        folder.mkdir(parents=True, exist_ok=True)
        cv2.imwrite(str(folder / file_name), image)
        image_id = len(data["images"])
        height, width = image.shape[:2]
        data["images"].append({"id": image_id, "file_name": file_name, "width": width, "height": height})
        for label, (x1, y1, x2, y2) in boxes:
            if label not in self.class_id:
                continue
            w, h = max(0.0, x2 - x1), max(0.0, y2 - y1)
            data["annotations"].append({"id": len(data["annotations"]), "image_id": image_id,
                                        "category_id": self.class_id[label], "bbox": [x1, y1, w, h],
                                        "area": w * h, "iscrowd": 0})
        return split

    def close(self) -> dict[str, int]:
        counts = {}
        for split in ("train", "valid", "test"):
            data = self.splits.get(split, {"images": [], "annotations": []})
            (self.root / split).mkdir(parents=True, exist_ok=True)
            (self.root / split / "_annotations.coco.json").write_text(json.dumps(
                {"info": {"description": "RestoVision, étiquetage automatique à relire"},
                 "categories": self.categories, **data}, ensure_ascii=False), encoding="utf-8")
            counts[split] = len(data["images"])
        return counts


class AutoLabeler:
    """Charge les trois modèles une fois, puis étiquette image par image."""

    def __init__(self, coco_labels: set[str], box_threshold: float = 0.35, text_threshold: float = 0.25,
                 use_sam: bool = True):
        import torch
        from rfdetr import RFDETRNano
        from transformers import AutoModelForZeroShotObjectDetection, AutoProcessor

        self.torch = torch
        self.coco_labels = coco_labels
        self.box_threshold, self.text_threshold = box_threshold, text_threshold
        self.rfdetr = RFDETRNano()
        self.dino_processor = AutoProcessor.from_pretrained(GROUNDING_MODEL)
        self.dino = AutoModelForZeroShotObjectDetection.from_pretrained(GROUNDING_MODEL).eval()
        self.sam = self.sam_processor = None
        if use_sam:
            from transformers import Sam2Model, Sam2Processor

            self.sam_processor = Sam2Processor.from_pretrained(SAM_MODEL)
            self.sam = Sam2Model.from_pretrained(SAM_MODEL).eval()

    def coco_boxes(self, rgb) -> list[tuple[str, tuple]]:
        detections = self.rfdetr.predict(rgb, threshold=0.4)
        return [(str(name), tuple(float(v) for v in box)) for box, name in zip(detections.xyxy, detections.data["class_name"])
                if str(name) in self.coco_labels]

    def grounded_boxes(self, pil) -> list[tuple[str, float, tuple]]:
        text = ". ".join(PROMPTS) + "."
        inputs = self.dino_processor(images=pil, text=text, return_tensors="pt")
        with self.torch.no_grad():
            outputs = self.dino(**inputs)
        result = self.dino_processor.post_process_grounded_object_detection(
            outputs, inputs.input_ids, threshold=self.box_threshold, text_threshold=self.text_threshold,
            target_sizes=[pil.size[::-1]])[0]
        labels = result.get("text_labels", result.get("labels"))
        found = []
        for box, score, label in zip(result["boxes"].tolist(), result["scores"].tolist(), labels):
            phrase = next((p for p in PROMPTS if p in str(label)), None)
            if phrase:
                found.append((phrase, float(score), tuple(box)))
        return found

    def food_on(self, rgb, plate, margin: float = 0.15) -> list[tuple]:
        """Nourriture dans une assiette : l'assiette est recadrée et agrandie, puis les boîtes sont ramenées à l'image."""
        from PIL import Image

        height, width = rgb.shape[:2]
        x1, y1, x2, y2 = plate
        mx, my = (x2 - x1) * margin, (y2 - y1) * margin
        cx1, cy1, cx2, cy2 = max(0, int(x1 - mx)), max(0, int(y1 - my)), min(width, int(x2 + mx)), min(height, int(y2 + my))
        if cx2 - cx1 < 4 or cy2 - cy1 < 4:
            return []
        crop = Image.fromarray(rgb[cy1:cy2, cx1:cx2]).resize((CROP_SIZE, CROP_SIZE))
        inputs = self.dino_processor(images=crop, text=". ".join(FOOD_PROMPTS) + ".", return_tensors="pt")
        with self.torch.no_grad():
            outputs = self.dino(**inputs)
        result = self.dino_processor.post_process_grounded_object_detection(
            outputs, inputs.input_ids, threshold=0.3, text_threshold=0.25, target_sizes=[(CROP_SIZE, CROP_SIZE)])[0]
        sx, sy = (cx2 - cx1) / CROP_SIZE, (cy2 - cy1) / CROP_SIZE
        return [(cx1 + bx1 * sx, cy1 + by1 * sy, cx1 + bx2 * sx, cy1 + by2 * sy)
                for bx1, by1, bx2, by2 in result["boxes"].tolist()]

    def masks(self, pil, boxes: list[tuple]) -> list:
        """Un masque par boîte (tableaux booléens à la taille de l'image)."""
        if not boxes or self.sam is None:
            return []
        inputs = self.sam_processor(images=pil, input_boxes=[[list(b) for b in boxes]], return_tensors="pt")
        with self.torch.no_grad():
            outputs = self.sam(**inputs, multimask_output=False)
        masks = self.sam_processor.post_process_masks(outputs.pred_masks.cpu(), inputs["original_sizes"])[0]
        return [m[0].numpy().astype(bool) for m in masks]

    def label(self, bgr) -> tuple[list[tuple[str, tuple]], list[tuple]]:
        """Renvoie (boîtes étiquetées, boîtes des personnes) pour une image BGR."""
        from PIL import Image

        rgb = bgr[:, :, ::-1].copy()
        pil = Image.fromarray(rgb)
        boxes = self.coco_boxes(rgb)
        persons = [box for label, box in boxes if label == "person"]
        grounded = self.grounded_boxes(pil)
        plates = [box for phrase, _, box in grounded if phrase == "plate"]
        foods = [food for plate in plates for food in self.food_on(rgb, plate)]
        boxes += [(WET_FLOOR, box) for phrase, _, box in grounded if phrase == "wet floor sign"]
        if plates:
            plate_masks = self.masks(pil, plates)
            food_masks = self.masks(pil, foods)
            for index, plate in enumerate(plates):
                boxes.append((fill_class(self.fill_ratio(plate, plate_masks, index, foods, food_masks)), plate))
        return boxes, persons

    @staticmethod
    def fill_ratio(plate, plate_masks, index, foods, food_masks) -> float:
        """Part de l'assiette couverte de nourriture : par les masques si SAM 2 tourne, sinon par les boîtes."""
        def inside(box):
            cx, cy = (box[0] + box[2]) / 2, (box[1] + box[3]) / 2
            return plate[0] <= cx <= plate[2] and plate[1] <= cy <= plate[3]

        on_plate = [i for i, food in enumerate(foods) if inside(food)]
        if plate_masks and food_masks:
            plate_mask = plate_masks[index]
            area = plate_mask.sum()
            if area == 0:
                return 0.0
            covered = plate_mask.copy() & False
            for i in on_plate:
                covered |= food_masks[i] & plate_mask
            return float(covered.sum() / area)
        plate_area = max(1.0, (plate[2] - plate[0]) * (plate[3] - plate[1]))
        food_area = sum((foods[i][2] - foods[i][0]) * (foods[i][3] - foods[i][1]) for i in on_plate)
        return min(1.0, food_area / plate_area)


def images_from(video: str | None, folder: str | None, every: float):
    """(nom, image BGR) pour chaque image à étiqueter : une toutes les `every` secondes d'une vidéo, ou un dossier."""
    import cv2

    if folder:
        for path in sorted(Path(folder).iterdir()):
            if path.suffix.lower() in (".jpg", ".jpeg", ".png"):
                image = cv2.imread(str(path))
                if image is not None:
                    yield path.stem + ".jpg", image
        return
    capture = cv2.VideoCapture(video)
    fps = capture.get(cv2.CAP_PROP_FPS) or 25.0
    step, index, stem = max(1, round(fps * every)), 0, Path(video).stem
    while capture.grab():
        if index % step == 0:
            ok, image = capture.retrieve()
            if ok:
                yield f"{stem}-{index:07d}.jpg", image
        index += 1
    capture.release()


def draw_preview(image, boxes):
    import cv2

    out = image.copy()
    colours = {"person": (0, 200, 0), "plate_full": (0, 0, 230), "plate_partial": (0, 160, 255),
               "plate_empty": (200, 200, 0), WET_FLOOR: (255, 0, 255)}
    for label, (x1, y1, x2, y2) in boxes:
        colour = colours.get(label, (255, 255, 255))
        cv2.rectangle(out, (int(x1), int(y1)), (int(x2), int(y2)), colour, 2)
        cv2.putText(out, label, (int(x1), max(12, int(y1) - 4)), cv2.FONT_HERSHEY_SIMPLEX, 0.45, colour, 1)
    return out


def main() -> None:
    import cv2

    from restovision_edge.config import load_config

    parser = argparse.ArgumentParser(description="Étiquetage automatique des images du restaurant")
    parser.add_argument("--config", required=True, help="edge.json du restaurant (ses groupes de classes)")
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument("--video")
    source.add_argument("--images", help="dossier d'images JPEG ou PNG")
    parser.add_argument("--every", type=float, default=5.0, help="vidéo : une image toutes les N secondes")
    parser.add_argument("--out", required=True, type=Path)
    parser.add_argument("--box-threshold", type=float, default=0.35)
    parser.add_argument("--no-sam", action="store_true", help="sans SAM 2 : remplissage estimé par les boîtes")
    parser.add_argument("--no-blur", action="store_true", help="ne pas flouter les personnes (déconseillé)")
    parser.add_argument("--limit", type=int, default=0, help="arrêter après N images (essai)")
    options = parser.parse_args()

    config = load_config(options.config)
    coco_labels = set().union(*config.groups.values())
    classes = sorted(coco_labels) + list(PLATE_CLASSES) + [WET_FLOOR]
    labeler = AutoLabeler(coco_labels, box_threshold=options.box_threshold, use_sam=not options.no_sam)
    dataset = CocoDataset(options.out, classes)
    preview = options.out / "apercu"
    preview.mkdir(parents=True, exist_ok=True)
    totals: dict[str, int] = {}
    for count, (name, image) in enumerate(images_from(options.video, options.images, options.every), start=1):
        boxes, persons = labeler.label(image)
        if not options.no_blur:
            image = blur_heads(image, persons)
        dataset.add(name, image, boxes)
        cv2.imwrite(str(preview / name), draw_preview(image, boxes))
        for label, _ in boxes:
            totals[label] = totals.get(label, 0) + 1
        print(f"{name} : {len(boxes)} étiquettes", flush=True)
        if options.limit and count >= options.limit:
            break
    counts = dataset.close()
    print(f"Jeu écrit dans {options.out} : {counts}. Étiquettes : {dict(sorted(totals.items()))}")
    print("À relire avant d'entraîner : les aperçus annotés sont dans", preview)


if __name__ == "__main__":
    main()
