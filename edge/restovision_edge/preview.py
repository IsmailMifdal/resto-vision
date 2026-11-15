"""Aperçu local : l'image de la caméra avec les zones et leur état.

Sert à régler l'installation et à faire une démonstration. L'aperçu reste sur le boîtier
(fichier local ou page servie sur 127.0.0.1) : aucune image ne part vers le cloud.
"""
from __future__ import annotations

import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

from .analyzers.queue import QueueAnalyzer
from .analyzers.tables import TableAnalyzer
from .models import Detection, TableState

# Couleurs BGR, les mêmes que dans l'application web. Texte sans accents : OpenCV ne les dessine pas.
STATES = {
    TableState.EMPTY: ("LIBRE", (90, 160, 60)),
    TableState.WAITING: ("EN ATTENTE", (0, 150, 230)),
    TableState.SERVED: ("SERVIE", (200, 110, 40)),
    TableState.TO_CLEAR: ("A DEBARRASSER", (50, 50, 210)),
}
QUEUE_COLOR = (160, 60, 140)


def draw(frame, detections: list[Detection], analyzers: list) -> "object":
    """Renvoie une copie de l'image avec les détections, les zones et leur état."""
    import cv2
    import numpy as np

    image = frame.copy()
    for d in detections:
        cv2.rectangle(image, (int(d.x1), int(d.y1)), (int(d.x2), int(d.y2)), (255, 255, 255), 1)
    for analyzer in analyzers:
        if isinstance(analyzer, TableAnalyzer):
            name, color = STATES[analyzer.state]
            text = f"{analyzer.zone.id} {name}" + (f" {analyzer.persons} pers." if analyzer.persons else "")
        elif isinstance(analyzer, QueueAnalyzer):
            color, text = QUEUE_COLOR, f"{analyzer.zone.id} FILE {analyzer.count} pers."
        else:
            continue
        points = np.array(analyzer.zone.polygon, dtype=np.int32)
        overlay = image.copy()
        cv2.fillPoly(overlay, [points], color)
        cv2.addWeighted(overlay, 0.25, image, 0.75, 0, image)
        cv2.polylines(image, [points], True, color, 2)
        x, y = int(points[:, 0].min()), int(points[:, 1].min())
        (width, height), _ = cv2.getTextSize(text, cv2.FONT_HERSHEY_SIMPLEX, 0.55, 2)
        y = max(y, height + 8)
        cv2.rectangle(image, (x, y - height - 8), (x + width + 8, y), color, -1)
        cv2.putText(image, text, (x + 4, y - 5), cv2.FONT_HERSHEY_SIMPLEX, 0.55, (255, 255, 255), 2)
    return image


class VideoFile:
    """Enregistre l'aperçu dans un fichier .mp4."""

    def __init__(self, path: str, fps: float):
        self.path, self.fps, self.writer = path, fps, None

    def show(self, image) -> None:
        import cv2

        if self.writer is None:
            height, width = image.shape[:2]
            self.writer = cv2.VideoWriter(self.path, cv2.VideoWriter_fourcc(*"mp4v"), self.fps, (width, height))
        self.writer.write(image)

    def close(self) -> None:
        if self.writer is not None:
            self.writer.release()


PAGE = b"""<!doctype html><meta charset="utf-8"><title>RestoVision - apercu</title>
<body style="margin:0;background:#111"><img src="/stream.mjpg" style="width:100%;height:100vh;object-fit:contain">"""


class LiveView:
    """Sert l'aperçu en direct sur http://127.0.0.1:<port> (flux MJPEG et /snapshot.jpg)."""

    def __init__(self, port: int, host: str = "127.0.0.1"):
        self.jpeg: bytes | None = None
        self.changed = threading.Condition()
        view = self

        class Handler(BaseHTTPRequestHandler):
            def do_GET(self):  # noqa: N802 (nom imposé par http.server)
                if self.path == "/":
                    self._send(200, "text/html", PAGE)
                elif self.path.startswith("/snapshot.jpg"):
                    if view.jpeg is None:
                        self._send(503, "text/plain", b"pas encore d'image")
                    else:
                        self._send(200, "image/jpeg", view.jpeg)
                elif self.path.startswith("/stream.mjpg"):
                    self.send_response(200)
                    self.send_header("Content-Type", "multipart/x-mixed-replace; boundary=frame")
                    self.end_headers()
                    try:
                        while True:
                            with view.changed:
                                view.changed.wait(timeout=5)
                                jpeg = view.jpeg
                            if jpeg is None:
                                continue
                            self.wfile.write(b"--frame\r\nContent-Type: image/jpeg\r\nContent-Length: "
                                             + str(len(jpeg)).encode() + b"\r\n\r\n" + jpeg + b"\r\n")
                    except OSError:
                        pass  # le navigateur a fermé la page
                else:
                    self._send(404, "text/plain", b"introuvable")

            def _send(self, status: int, content_type: str, body: bytes) -> None:
                self.send_response(status)
                self.send_header("Content-Type", content_type)
                self.send_header("Content-Length", str(len(body)))
                self.send_header("Cache-Control", "no-store")
                self.end_headers()
                self.wfile.write(body)

            def log_message(self, *_):
                pass

        self.server = ThreadingHTTPServer((host, port), Handler)
        self.server.daemon_threads = True
        threading.Thread(target=self.server.serve_forever, daemon=True).start()

    def show(self, image) -> None:
        import cv2

        ok, encoded = cv2.imencode(".jpg", image, [cv2.IMWRITE_JPEG_QUALITY, 80])
        if ok:
            with self.changed:
                self.jpeg = encoded.tobytes()
                self.changed.notify_all()

    def close(self) -> None:
        self.server.shutdown()
        self.server.server_close()
