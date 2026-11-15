"""Détection et suivi avec YOLO (Ultralytics). Importé seulement avec une vraie source vidéo."""
from __future__ import annotations

from .models import Detection


class YoloDetector:
    def __init__(self, weights: str, confidence: float, labels: set[str], imgsz: int = 1280):
        from ultralytics import YOLO

        self.model = YOLO(weights)
        self.confidence = confidence
        self.imgsz = imgsz
        # Ne garder que les classes utiles accélère le traitement.
        self.class_ids = [i for i, name in self.model.names.items() if name in labels]

    def detect(self, frame) -> list[Detection]:
        result = self.model.track(frame, persist=True, tracker="bytetrack.yaml", imgsz=self.imgsz,
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


def is_file(source: str) -> bool:
    return not source.lower().startswith(("rtsp://", "http://", "https://"))


def frames(source: str, fps: float, realtime: bool = False, loop: bool = False):
    """Lit un flux RTSP ou un fichier et renvoie (image, horodatage) à la cadence voulue.

    Fichier : l'horodatage suit le temps de la vidéo. Avec `realtime`, le fichier est rejoué
    à vitesse réelle comme une caméra (les images en retard sont sautées). `loop` le rejoue sans fin.
    """
    import time

    import cv2

    file = is_file(source)
    clock = time.time()  # horloge de la vidéo, pour les fichiers lus plus vite que le temps réel
    while True:
        capture = cv2.VideoCapture(source)
        if not capture.isOpened():
            raise RuntimeError(f"Impossible d'ouvrir la source vidéo : {source}")
        source_fps = capture.get(cv2.CAP_PROP_FPS) or 25.0
        step = max(1, round(source_fps / fps))
        index, next_index, start = 0, 0, time.time()
        try:
            while capture.grab():
                if index >= next_index:
                    ok, frame = capture.retrieve()
                    if not ok:
                        break
                    if not file:
                        now = time.time()
                    elif realtime:
                        time.sleep(max(0.0, start + index / source_fps - time.time()))
                        now = time.time()
                    else:
                        now = clock + index / source_fps
                    yield frame, now
                    next_index = index + step
                    if file and realtime:  # si l'analyse a pris du retard, on saute les images manquées
                        next_index = max(next_index, int((time.time() - start) * source_fps))
                index += 1
        finally:
            capture.release()
        if not (file and loop):
            return
        clock += index / source_fps
