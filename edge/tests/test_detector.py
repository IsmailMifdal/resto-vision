"""Le détecteur RF-DETR : filtrage des classes et suivi, avec un faux modèle (pas de téléchargement)."""
import numpy as np
import pytest

sv = pytest.importorskip("supervision")
pytest.importorskip("trackers")

from restovision_edge.config import load_config  # noqa: E402
from restovision_edge.detector import RfDetrDetector  # noqa: E402


class FakePredictor:
    """Rend les mêmes boîtes à chaque image : deux personnes, un verre, une voiture vue par la fenêtre."""
    class_names = ["person", "wine glass", "car"]

    def predict(self, image, threshold):
        assert image.shape[2] == 3 and threshold == 0.35
        detections = sv.Detections(
            xyxy=np.array([[10, 10, 50, 90], [100, 10, 140, 90], [60, 40, 70, 60], [300, 0, 400, 40]], dtype=float),
            confidence=np.array([0.9, 0.85, 0.6, 0.8]),
            class_id=np.array([1, 1, 46, 3]),
        )
        detections.data["class_name"] = np.array(["person", "person", "wine glass", "car"])
        return detections


def test_only_useful_classes_are_kept_and_people_get_a_stable_id():
    detector = RfDetrDetector(confidence=0.35, labels={"person", "wine glass"}, fps=5, predictor=FakePredictor())
    frame = np.zeros((480, 640, 3), dtype=np.uint8)
    seen = [detector.detect(frame, now=t / 5) for t in range(4)]

    last = seen[-1]
    assert sorted(d.label for d in last) == ["person", "person", "wine glass"]   # la voiture est écartée
    ids = {d.track_id for d in last if d.label == "person"}
    assert None not in ids and len(ids) == 2
    first_ids = {d.track_id for d in seen[1] if d.label == "person" and d.track_id is not None}
    assert first_ids <= ids or not first_ids                                    # les identifiants ne changent pas


def test_the_example_configuration_uses_rf_detr_by_default():
    config = load_config("config/edge.example.json")
    assert config.backend == "rfdetr" and config.model_size == "nano" and config.weights is None


def test_an_older_configuration_with_yolo_weights_stays_on_yolo(tmp_path):
    path = tmp_path / "edge.json"
    path.write_text('{"restaurantId": "r", "model": {"weights": "yolo11n.pt"}, "zones": []}')
    assert load_config(path).backend == "yolo"
