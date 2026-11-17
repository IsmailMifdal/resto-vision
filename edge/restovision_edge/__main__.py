"""Point d'entrée : python -m restovision_edge --config config/edge.example.json --source simulate"""
from __future__ import annotations

import argparse
import time

from .config import load_config
from .health import Heartbeat, describe_source
from .pipeline import Pipeline, layout_event
from .publisher import MqttPublisher, StdoutPublisher


LAYOUT_PERIOD = 300.0  # secondes entre deux envois du plan des zones


def main() -> None:
    parser = argparse.ArgumentParser(description="RestoVision edge")
    parser.add_argument("--config", required=True)
    parser.add_argument("--source", default="simulate", help="'simulate', un fichier vidéo ou une URL RTSP")
    parser.add_argument("--publisher", choices=["stdout", "mqtt"], default="stdout")
    parser.add_argument("--speed", type=float, default=1.0,
                        help="simulateur : 1 = temps réel (requis pour tester les alertes), 0 = sans pause")
    parser.add_argument("--cycles", type=int, default=0, help="simulateur : nombre de cycles (0 = sans fin)")
    parser.add_argument("--cycle-seconds", type=float, default=240.0, help="simulateur : durée d'un cycle de table")
    parser.add_argument("--realtime", action="store_true",
                        help="fichier vidéo : le rejouer à vitesse réelle, comme une caméra (pour une démonstration)")
    parser.add_argument("--loop", action="store_true", help="fichier vidéo : le rejouer sans fin")
    parser.add_argument("--preview", metavar="FICHIER.mp4", help="enregistrer l'image annotée (zones et états)")
    parser.add_argument("--preview-port", type=int, metavar="PORT",
                        help="voir l'image annotée en direct sur http://127.0.0.1:PORT (reste sur ce poste)")
    parser.add_argument("--update-root", metavar="DOSSIER",
                        help="dossier des versions (donné par le lanceur) : active la mise à jour à distance")
    parser.add_argument("--calibrate", type=float, metavar="SECONDES", default=0,
                        help="salle dressée et vide : mesurer la vaisselle de chaque table, puis s'arrêter")
    args = parser.parse_args()

    config = load_config(args.config)
    if args.calibrate:
        calibrate(config, args.source, args.calibrate)
        return
    pipeline = Pipeline(config)
    publisher = (MqttPublisher(config.mqtt_host, config.mqtt_port, config.topic, username=config.mqtt_username,
                               password=config.mqtt_password, tls=config.mqtt_tls)
                 if args.publisher == "mqtt" else StdoutPublisher())
    views = []
    layout_sent = 0.0
    heartbeat = Heartbeat(config.restaurant_id, config.box_id, describe_source(args.source))
    updater = None
    if args.update_root and config.update_public_key and isinstance(publisher, MqttPublisher):
        from . import __version__
        from .updater import UPDATE_TOPIC, Updater, VersionStore

        updater = Updater(VersionStore(args.update_root), config.update_public_key, __version__)
        publisher.subscribe(UPDATE_TOPIC, updater.handle)

    def beat() -> bool:
        """Signal de vie ; renvoie vrai quand une nouvelle version est installée et qu'il faut redémarrer."""
        heartbeat.frame()
        event = heartbeat.due(publisher.pending(), updater.last_error if updater else None)
        if event:
            publisher.publish(event)
        return bool(updater and updater.restart_requested)

    restart = False

    def announce_layout() -> None:
        # Le broker garde ce message pour tout backend qui se connecte. On le renvoie toutes les 5 minutes
        # au cas où le broker aurait redémarré.
        nonlocal layout_sent
        if time.time() - layout_sent >= LAYOUT_PERIOD:
            layout_sent = time.time()
            publisher.publish_layout(layout_event(config, layout_sent))

    try:
        if args.source == "simulate":
            from .simulate import scenario

            start = time.time()
            for detections, elapsed in scenario(config, cycle_seconds=args.cycle_seconds, cycles=args.cycles):
                # En temps réel, on horodate avec l'heure réelle pour que les alertes du backend soient justes.
                now = time.time() if args.speed == 1 else start + elapsed
                announce_layout()
                for event in pipeline.process(detections, now):
                    publisher.publish(event)
                if beat():
                    restart = True
                    break
                if args.speed > 0:
                    time.sleep(1.0 / args.speed)
        else:
            from .detector import YoloDetector, frames

            if args.preview or args.preview_port:
                from .preview import LiveView, VideoFile, draw

                if args.preview:
                    views.append(VideoFile(args.preview, config.fps))
                if args.preview_port:
                    views.append(LiveView(args.preview_port))
                    print(f"Aperçu en direct : http://127.0.0.1:{args.preview_port}", flush=True)
            labels = set().union(*config.groups.values())
            detector = YoloDetector(config.weights, config.confidence, labels, config.imgsz)
            for frame, now in frames(args.source, config.fps, realtime=args.realtime, loop=args.loop):
                detections = detector.detect(frame)
                announce_layout()
                for event in pipeline.process(detections, now):
                    publisher.publish(event)
                if beat():
                    restart = True
                    break
                if views:
                    image = draw(frame, detections, pipeline.analyzers)
                    for view in views:
                        view.show(image, frame)
    except KeyboardInterrupt:
        pass
    finally:
        publisher.close()
        for view in views:
            view.close()
    if restart:
        from .updater import RESTART_CODE

        print("Nouvelle version installée : redémarrage par le lanceur.", flush=True)
        raise SystemExit(RESTART_CODE)


def calibrate(config, source: str, seconds: float) -> None:
    """Mesure la vaisselle visible sur chaque table quand la salle est dressée et vide."""
    from .analyzers.tables import DISH_SHARE
    from .detector import YoloDetector, frames
    from .smoothing import Window
    from .zones import count_by_group, detections_in_zone

    tables = [z for z in config.zones if z.type == "TABLE"]
    windows = {z.id: Window(seconds) for z in tables}
    detector = YoloDetector(config.weights, config.confidence, set().union(*config.groups.values()), config.imgsz)
    start = now = None
    for frame, now in frames(source, config.fps):
        start = now if start is None else start
        detections = detector.detect(frame)
        for zone in tables:
            windows[zone.id].add(now, count_by_group(detections_in_zone(zone, detections), config.groups).get("dish", 0))
        if now - start >= seconds:
            break
    if start is None:
        raise SystemExit("Aucune image lue.")
    print('Valeurs à recopier dans la configuration ("baselineDishes" de chaque table) :')
    for zone in tables:
        window = windows[zone.id]
        window.seconds = max(now - start, 1e-6)
        print(f'  {zone.id} : "baselineDishes": {window.level(now, DISH_SHARE)}')


if __name__ == "__main__":
    main()
