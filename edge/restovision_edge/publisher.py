"""Envoi des événements : console ou MQTT avec tampon local si le réseau coupe."""
from __future__ import annotations

import json
from collections import deque
from typing import Protocol

from .models import Event


class Publisher(Protocol):
    def publish(self, event: Event) -> None: ...
    def publish_layout(self, event: Event) -> None: ...
    def pending(self) -> int: ...
    def close(self) -> None: ...


class StdoutPublisher:
    def publish(self, event: Event) -> None:
        print(json.dumps(event.to_json(), ensure_ascii=False), flush=True)

    publish_layout = publish

    def pending(self) -> int:
        return 0

    def close(self) -> None:
        pass


class MqttPublisher:
    """Publie en QoS 1. Les événements non envoyés restent en mémoire (10 000 max)
    et repartent dès que la connexion revient. Le plan des zones part sur un sujet à part,
    conservé par le broker (retain) : un backend qui se connecte plus tard le reçoit quand même."""

    def __init__(self, host: str, port: int, topic: str, client=None, max_buffer: int = 10_000,
                 username: str | None = None, password: str | None = None, tls: bool = False):
        self.topic = topic
        self.layout_topic = topic.rsplit("/", 1)[0] + "/layout"
        self.buffer: deque[tuple[str, str, bool]] = deque(maxlen=max_buffer)
        if client is None:
            import paho.mqtt.client as mqtt  # import tardif : inutile pour les tests

            client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2)
            client.on_connect = lambda *_: self._connected()
            if username:
                client.username_pw_set(username, password)
            if tls:
                client.tls_set()  # certificats de confiance du système
            client.connect_async(host, port)
            client.loop_start()
        self.client = client
        self.subscriptions: dict[str, object] = {}

    def subscribe(self, topic: str, callback) -> None:
        """Reçoit les messages d'un sujet ; l'abonnement est refait à chaque reconnexion."""
        self.subscriptions[topic] = callback
        self.client.message_callback_add(topic, lambda _client, _userdata, message: callback(message.payload))
        if self.client.is_connected():
            self.client.subscribe(topic, qos=1)

    def _connected(self) -> None:
        for topic in self.subscriptions:
            self.client.subscribe(topic, qos=1)
        self.flush()

    def publish(self, event: Event) -> None:
        self.buffer.append((self.topic, json.dumps(event.to_json(), ensure_ascii=False), False))
        self.flush()

    def publish_layout(self, event: Event) -> None:
        self.buffer.append((self.layout_topic, json.dumps(event.to_json(), ensure_ascii=False), True))
        self.flush()

    def pending(self) -> int:
        """Messages en attente d'envoi (réseau coupé)."""
        return len(self.buffer)

    def flush(self) -> None:
        while self.buffer and self.client.is_connected():
            topic, payload, retain = self.buffer[0]
            info = self.client.publish(topic, payload, qos=1, retain=retain)
            if getattr(info, "rc", 0) != 0:
                return  # échec : on garde le message pour le prochain essai
            self.buffer.popleft()

    def close(self) -> None:
        self.flush()
        self.client.loop_stop()
        self.client.disconnect()
