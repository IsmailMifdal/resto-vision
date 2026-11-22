"""Détection et suivi. Importé seulement avec une vraie source vidéo.

Deux moteurs :
- RF-DETR (Roboflow, licence Apache 2.0, poids compris), suivi par ByteTrack (bibliothèque `trackers`,
  Apache 2.0). C'est le moteur par défaut : il peut être vendu dans un abonnement sans condition particulière.
- YOLO d'Ultralytics, gardé pour comparer. Sa licence AGPL-3.0 couvre aussi les modèles entraînés avec :
  un produit commercial demande une licence Enterprise d'Ultralytics.
"""
from __future__ import annotations

from typing import Protocol

from .models import Detection

RFDETR_SIZES = {"nano": "RFDETRNano", "small": "RFDETRSmall", "medium": "RFDETRMedium"}


class Detector(Protocol):
    def detect(self, frame, now: float | None = None) -> list[Detection]: ...


def make_detector(config) -> Detector:
    """Le moteur choisi dans la configuration (`model.backend`)."""
    labels = set().union(*config.groups.values())
    if config.backend == "yolo":
        return YoloDetector(config.weights or "yolo11n.pt", config.confidence, labels, config.imgsz)
    if config.backend == "rfdetr":
        return RfDetrDetector(config.model_size, config.weights, config.confidence, labels, config.fps)
    raise ValueError(f"Moteur de détection inconnu : {config.backend!r} (rfdetr ou yolo)")


class RfDetrDetector:
    """RF-DETR pré-entraîné sur COCO (ou un modèle entraîné sur les images du restaurant) + ByteTrack."""

    def __init__(self, size: str = "nano", weights: str | None = None, confidence: float = 0.35,
                 labels: set[str] | None = None, fps: float = 5.0, predictor=None, tracker=None):
        self.confidence = confidence
        self.labels = labels
        self.predictor = predictor or self._load(size, weights)
        if tracker is None:
            from trackers import ByteTrackTracker

            # Une personne suivie reste reconnue 6 secondes après avoir été masquée (un serveur passe devant).
            tracker = ByteTrackTracker(frame_rate=fps, lost_track_buffer=max(1, int(fps * 6)))
        self.tracker = tracker

    @staticmethod
    def _load(size: str, weights: str | None):
        import rfdetr

        if weights:
            # Modèle entraîné sur les images du restaurant. Chargement sûr : un fichier qui demanderait
            # d'exécuter du code est refusé (trust_checkpoint reste à False).
            model = rfdetr.RFDETR.from_checkpoint(weights)
        else:
            if size not in RFDETR_SIZES:
                raise ValueError(f"Taille RF-DETR inconnue : {size!r} ({', '.join(RFDETR_SIZES)})")
            model = getattr(rfdetr, RFDETR_SIZES[size])()   # poids COCO officiels, téléchargés une fois
        try:
            return model.inference()   # prédicteur optimisé (plus rapide sur carte graphique)
        except Exception:
            return model

    def detect(self, frame, now: float | None = None) -> list[Detection]:
        detections = self.predictor.predict(frame[:, :, ::-1].copy(), threshold=self.confidence)  # BGR -> RVB
        names = detections.data.get("class_name")
        if names is None:
            names = [self.predictor.class_names[int(c)] for c in detections.class_id]
        if self.labels is not None and len(detections):
            detections = detections[[str(name) in self.labels for name in names]]
        tracked = self.tracker.update(detections, timestamp=now)
        names = tracked.data.get("class_name", [])
        ids = tracked.tracker_id if tracked.tracker_id is not None else [-1] * len(tracked)
        confidences = tracked.confidence if tracked.confidence is not None else [1.0] * len(tracked)
        return [
            Detection(label=str(name), x1=float(x1), y1=float(y1), x2=float(x2), y2=float(y2),
                      confidence=float(confidence), track_id=None if int(track) < 0 else int(track))
            for (x1, y1, x2, y2), name, confidence, track in zip(tracked.xyxy, names, confidences, ids)
        ]


class YoloDetector:
    """Ultralytics YOLO (AGPL-3.0 : voir l'en-tête de ce fichier avant tout usage commercial)."""

    def __init__(self, weights: str, confidence: float, labels: set[str], imgsz: int = 1280):
        from ultralytics import YOLO

        self.model = YOLO(weights)
        self.confidence = confidence
        self.imgsz = imgsz
        # Ne garder que les classes utiles accélère le traitement.
        self.class_ids = [i for i, name in self.model.names.items() if name in labels]

    def detect(self, frame, now: float | None = None) -> list[Detection]:
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
