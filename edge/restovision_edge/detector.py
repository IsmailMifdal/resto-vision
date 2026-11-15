"""Détection et suivi avec YOLO (Ultralytics). Importé seulement avec une vraie source vidéo."""
from __future__ import annotations

from .models import Detection


class YoloDetector:
    def __init__(self, weights: str, confidence: float, labels: set[str]):
        from ultralytics import YOLO

        self.model = YOLO(weights)
        self.confidence = confidence
        # Ne garder que les classes utiles accélère le traitement.
        self.class_ids = [i for i, name in self.model.names.items() if name in labels]

    def detect(self, frame) -> list[Detection]:
        result = self.model.track(frame, persist=True, tracker="bytetrack.yaml",
                                  conf=self.confidence, classes=self.class_ids, verbose=False)[0]
        detections: list[Detection] = []
        for box in result.boxes:
            x1, y1, x2, y2 = box.xyxy[0].tolist()
            detections.append(Detection(
                label=self.model.names[int(box.cls[0])],
                x1=x1, y1=y1, x2=x2, y2=y2,
                confidence=float(box.conf[0]),
                track_id=int(box.id[0]) if box.id is not None else None,
            ))
        return detections


def frames(source: str, fps: float):
    """Lit un flux RTSP ou un fichier et renvoie (image, horodatage) à la cadence voulue."""
    import time

    import cv2

    capture = cv2.VideoCapture(source)
    if not capture.isOpened():
        raise RuntimeError(f"Impossible d'ouvrir la source vidéo : {source}")
    is_file = not source.lower().startswith(("rtsp://", "http://", "https://"))
    source_fps = capture.get(cv2.CAP_PROP_FPS) or 25.0
    step = max(1, round(source_fps / fps))
    index = 0
    start = time.time()
    try:
        while True:
            ok, frame = capture.read()
            if not ok:
                break
            if index % step == 0:
                # Fichier : temps de la vidéo. Flux en direct : heure réelle.
                now = start + index / source_fps if is_file else time.time()
                yield frame, now
            index += 1
    finally:
        capture.release()
