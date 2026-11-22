"""Outils d'entraînement : la logique, sans télécharger de modèle."""
import json

import pytest

np = pytest.importorskip("numpy")

cv2 = pytest.importorskip("cv2")

from restovision_training.autolabel import AutoLabeler, CocoDataset, blur_heads, fill_class, split_of  # noqa: E402
from restovision_training.train import check_dataset, suggested_labels  # noqa: E402


def test_fill_level_classes():
    assert fill_class(0.8) == "plate_full"
    assert fill_class(0.3) == "plate_partial"
    assert fill_class(0.05) == "plate_empty"


def test_the_split_is_stable_and_roughly_80_10_10():
    names = [f"image-{i}.jpg" for i in range(1000)]
    splits = [split_of(n) for n in names]
    assert splits == [split_of(n) for n in names]
    assert 700 < splits.count("train") < 900 and 50 < splits.count("valid") < 150


def test_only_the_top_of_each_person_is_blurred():
    image = np.zeros((200, 200, 3), dtype=np.uint8)
    image[::2, ::2] = 255   # motif fin : le flou le fait disparaître
    out = blur_heads(image, [(50, 20, 150, 180)])
    head, legs, outside = out[25:70, 60:140], out[150:175, 60:140], out[20:70, 0:40]
    assert head.std() < image[25:70, 60:140].std() / 2
    assert np.array_equal(legs, image[150:175, 60:140]) and np.array_equal(outside, image[20:70, 0:40])
    assert np.array_equal(blur_heads(image, []), image)


def test_fill_ratio_by_masks_and_by_boxes():
    plate = (0, 0, 10, 10)
    plate_mask = np.zeros((10, 10), bool)
    plate_mask[:, :] = True
    food_mask = np.zeros((10, 10), bool)
    food_mask[:5, :] = True
    assert AutoLabeler.fill_ratio(plate, [plate_mask], 0, [(0, 0, 10, 5)], [food_mask]) == 0.5
    assert AutoLabeler.fill_ratio(plate, [], 0, [(0, 0, 10, 5)], []) == 0.5           # sans SAM 2
    assert AutoLabeler.fill_ratio(plate, [], 0, [(50, 50, 60, 60)], []) == 0.0        # nourriture hors de l'assiette


def test_the_dataset_is_written_in_the_format_rf_detr_reads(tmp_path):
    dataset = CocoDataset(tmp_path, ["person", "plate_full"])
    image = np.zeros((40, 60, 3), dtype=np.uint8)
    for i in range(40):
        dataset.add(f"img-{i}.jpg", image, [("person", (1, 2, 11, 22)), ("plate_full", (5, 5, 15, 15)), ("car", (0, 0, 1, 1))])
    counts = dataset.close()
    assert sum(counts.values()) == 40 and counts["train"] > 0

    data = json.loads((tmp_path / "train" / "_annotations.coco.json").read_text(encoding="utf-8"))
    assert data["categories"][0] == {"id": 0, "name": "restovision", "supercategory": "none"}
    assert [c["name"] for c in data["categories"][1:]] == ["person", "plate_full"]
    first = data["annotations"][0]
    assert first["bbox"] == [1, 2, 10, 20] and first["category_id"] == 1
    assert {a["category_id"] for a in data["annotations"]} == {1, 2}                  # la voiture est ignorée
    assert (tmp_path / "train" / data["images"][0]["file_name"]).exists()


def test_check_dataset_and_suggested_labels(tmp_path):
    with pytest.raises(SystemExit):
        check_dataset(tmp_path)
    dataset = CocoDataset(tmp_path, ["chair", "cup", "person", "plate_empty", "plate_full", "plate_partial"])
    image = np.zeros((10, 10, 3), dtype=np.uint8)
    for i in range(60):
        dataset.add(f"i{i}.jpg", image, [])
    dataset.close()
    classes = check_dataset(tmp_path)
    labels = suggested_labels(classes)
    assert labels["food"] == ["plate_full", "plate_partial"]
    assert "plate_empty" in labels["dish"] and "cup" in labels["dish"] and labels["obstacle"] == ["chair"]
