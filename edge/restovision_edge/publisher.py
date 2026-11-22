"""Envoi des événements : console ou MQTT avec tampon local si le réseau coupe."""
from __future__ import annotations

import json
from collections import deque
from typing import Protocol

from .models import Event


class Publisher(Protocol):
    def publish(self, event: Event) -> None: ...
    def close(self) -> None: ...


class StdoutPublisher:
    def publish(self, event: Event) -> None:
        print(json.dumps(event.to_json(), ensure_ascii=False), flush=True)

    def close(self) -> None:
        pass


class MqttPublisher:
    """Publie en QoS 1. Les événements non envoyés restent en mémoire (10 000 max)
    et repartent dès que la connexion revient."""

    def __init__(self, host: str, port: int, topic: str, client=None, max_buffer: int = 10_000,
                 username: str | None = None, password: str | None = None):
        self.topic = topic
        self.buffer: deque[str] = deque(maxlen=max_buffer)
        if client is None:
            import paho.mqtt.client as mqtt  # import tardif : inutile pour les tests

            client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2)
            if username:
                client.username_pw_set(username, password)
            client.on_connect = lambda *_: self.flush()
            client.connect_async(host, port)
            client.loop_start()
        self.client = client

    def publish(self, event: Event) -> None:
        self.buffer.append(json.dumps(event.to_json(), ensure_ascii=False))
        self.flush()

    def flush(self) -> None:
        while self.buffer and self.client.is_connected():
            payload = self.buffer[0]
            info = self.client.publish(self.topic, payload, qos=1)
            if getattr(info, "rc", 0) != 0:
                return  # échec : on garde le message pour le prochain essai
            self.buffer.popleft()

    def close(self) -> None:
        self.flush()
        self.client.loop_stop()
        self.client.disconnect()
