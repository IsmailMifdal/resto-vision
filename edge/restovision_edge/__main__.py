"""Point d'entrée : python -m restovision_edge --config config/edge.example.json --source simulate"""
from __future__ import annotations

import argparse
import time

from .config import load_config
from .pipeline import Pipeline
from .publisher import MqttPublisher, StdoutPublisher


def main() -> None:
    parser = argparse.ArgumentParser(description="RestoVision edge")
    parser.add_argument("--config", required=True)
    parser.add_argument("--source", default="simulate", help="'simulate', un fichier vidéo ou une URL RTSP")
    parser.add_argument("--publisher", choices=["stdout", "mqtt"], default="stdout")
    parser.add_argument("--speed", type=float, default=1.0,
                        help="simulateur : 1 = temps réel (requis pour tester les alertes), 0 = sans pause")
    parser.add_argument("--cycles", type=int, default=0, help="simulateur : nombre de cycles (0 = sans fin)")
    parser.add_argument("--cycle-seconds", type=float, default=240.0, help="simulateur : durée d'un cycle de table")
    args = parser.parse_args()

    config = load_config(args.config)
    pipeline = Pipeline(config)
    publisher = (MqttPublisher(config.mqtt_host, config.mqtt_port, config.topic)
                 if args.publisher == "mqtt" else StdoutPublisher())
    try:
        if args.source == "simulate":
            from .simulate import scenario

            start = time.time()
            for detections, elapsed in scenario(config, cycle_seconds=args.cycle_seconds, cycles=args.cycles):
                # En temps réel, on horodate avec l'heure réelle pour que les alertes du backend soient justes.
                now = time.time() if args.speed == 1 else start + elapsed
                for event in pipeline.process(detections, now):
                    publisher.publish(event)
                if args.speed > 0:
                    time.sleep(1.0 / args.speed)
        else:
            from .detector import YoloDetector, frames

            labels = set().union(*config.groups.values())
            detector = YoloDetector(config.weights, config.confidence, labels)
            for frame, now in frames(args.source, config.fps):
                for event in pipeline.process(detector.detect(frame), now):
                    publisher.publish(event)
    except KeyboardInterrupt:
        pass
    finally:
        publisher.close()


if __name__ == "__main__":
    main()
