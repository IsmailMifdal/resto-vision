import pytest

from restovision_edge.analyzers.queue import QueueAnalyzer
from restovision_edge.analyzers.tables import TableAnalyzer, classify
from restovision_edge.config import load_config
from restovision_edge.models import Detection, Event, TableState, Zone
from restovision_edge.pipeline import Pipeline, layout_event
from restovision_edge.publisher import MqttPublisher
from restovision_edge.simulate import scenario
from restovision_edge.smoothing import Window
from restovision_edge.zones import point_in_polygon

SQUARE = ((0, 0), (100, 0), (100, 100), (0, 100))
GROUPS = {"person": {"person"}, "dish": {"cup"}}
ZONE = Zone("T1", "TABLE", SQUARE)


def person(x=50, y=50):
    return Detection("person", x - 5, y - 5, x + 5, y + 5)


def cup(x=50, y=60):
    return Detection("cup", x - 2, y - 2, x + 2, y + 2)


def test_point_in_polygon():
    assert point_in_polygon((50, 50), SQUARE)
    assert not point_in_polygon((150, 50), SQUARE)
    assert not point_in_polygon((50, -1), SQUARE)


def test_classify():
    assert classify(0, 0) is TableState.EMPTY
    assert classify(2, 0) is TableState.WAITING
    assert classify(2, 1) is TableState.SERVED
    assert classify(0, 1) is TableState.TO_CLEAR


def test_table_full_cycle():
    table = TableAnalyzer("demo", ZONE, GROUPS, stable_seconds=5)
    seen = []
    timeline = [(0, []), (10, [person()]), (16, [person()]), (100, [person(), cup()]),
                (106, [person(), cup()]), (200, [cup()]), (206, [cup()]), (300, []), (306, [])]
    for now, detections in timeline:
        seen += [e.data["state"] for e in table.process(detections, now)]
    assert seen == ["WAITING", "SERVED", "TO_CLEAR", "EMPTY"]


def test_table_state_starts_when_first_seen():
    table = TableAnalyzer("demo", ZONE, GROUPS, stable_seconds=5)
    table.process([], 0)
    table.process([person()], 10)
    (event,) = table.process([person()], 15)
    assert event.timestamp == 10
    assert event.data["previousState"] == "EMPTY"


def test_table_ignores_short_flicker():
    table = TableAnalyzer("demo", ZONE, GROUPS, stable_seconds=5)
    events = []
    # Un serveur traverse la zone pendant 2 secondes : pas de changement d'état.
    for now, detections in [(0, []), (1, [person()]), (3, []), (10, [])]:
        events += table.process(detections, now)
    assert events == []
    assert table.state is TableState.EMPTY


def test_person_outside_zone_is_ignored():
    table = TableAnalyzer("demo", ZONE, GROUPS, stable_seconds=0)
    assert table.process([person(500, 500)], 0) == []
    assert table.process([person(500, 500)], 1) == []


def test_window_level_ignores_what_is_seen_too_briefly():
    window = Window(10)
    for now, value in [(0, 0), (2, 3), (4, 0), (10, 0)]:  # 3 personnes pendant 2 s sur 10
        window.add(now, value)
    assert window.level(10, 0.5) == 0
    assert window.level(10, 0.2) == 3


def test_table_stays_served_when_dishes_are_missed_on_most_frames():
    table = TableAnalyzer("demo", ZONE, GROUPS, stable_seconds=5)
    states = []
    for tenth in range(0, 600):  # 60 s à 10 images/s, le verre n'est vu que 4 images sur 10
        now = tenth / 10
        detections = [person()] + ([cup()] if tenth % 10 < 4 else [])
        states += [e.data["state"] for e in table.process(detections, now)]
    assert states[-1] == "SERVED"
    assert states.count("SERVED") == 1  # pas d'aller-retour entre « servie » et « en attente »


def test_set_table_is_not_to_clear():
    """Une table dressée (2 verres posés d'avance) est libre, pas à débarrasser."""
    zone = Zone("T1", "TABLE", SQUARE, baseline_dishes=2)
    table = TableAnalyzer("demo", zone, GROUPS, stable_seconds=5)
    glasses = [cup(30, 60), cup(70, 60)]
    seen = []
    timeline = [(0, glasses), (10, glasses), (20, glasses + [person()]), (30, glasses + [person()]),
                (40, glasses + [person(), cup(50, 40)]), (50, glasses + [person(), cup(50, 40)])]
    for now, detections in timeline:
        seen += [e.data["state"] for e in table.process(detections, now)]
    assert seen == ["WAITING", "SERVED"]


def test_config_reads_baseline_and_image_size(tmp_path):
    path = tmp_path / "edge.json"
    path.write_text('{"restaurantId": "r", "model": {"imgsz": 960}, "zones": '
                    '[{"id": "T1", "type": "TABLE", "baselineDishes": 3, "polygon": [[0, 0], [1, 0], [1, 1]]}]}')
    config = load_config(path)
    assert config.imgsz == 960 and config.zones[0].baseline_dishes == 3


def test_preview_draws_zones_on_a_copy():
    np = pytest.importorskip("numpy")
    pytest.importorskip("cv2")
    from restovision_edge.preview import draw

    config = load_config("config/edge.example.json")
    frame = np.zeros((720, 1280, 3), dtype=np.uint8)
    image = draw(frame, [person(200, 400)], Pipeline(config).analyzers)
    assert image.shape == frame.shape and image.any() and not frame.any()


def test_queue_count_and_wait():
    queue = QueueAnalyzer("demo", Zone("Q1", "QUEUE", SQUARE), GROUPS, seconds_per_customer=90, stable_seconds=5)
    people = [person(50, 20), person(50, 50), person(50, 80)]
    assert queue.process(people, 0) == []
    (event,) = queue.process(people, 5)
    assert event.data == {"persons": 3, "estimatedWaitSeconds": 270}


def test_event_json_shape():
    event = Event("TABLE_STATE", "demo", "T1", 0.0, {"state": "WAITING"})
    assert event.to_json() == {"type": "TABLE_STATE", "restaurantId": "demo", "zoneId": "T1",
                               "timestamp": "1970-01-01T00:00:00Z", "state": "WAITING"}


def test_simulator_drives_every_table_through_the_cycle():
    config = load_config("config/edge.example.json")
    pipeline = Pipeline(config)
    states: dict[str, list[str]] = {}
    queue_events = 0
    for detections, elapsed in scenario(config, cycles=2):
        for event in pipeline.process(detections, 1000.0 + elapsed):
            if event.type == "TABLE_STATE":
                states.setdefault(event.zone_id, []).append(event.data["state"])
            else:
                queue_events += 1
    for zone_id in ("T1", "T2"):
        assert {"WAITING", "SERVED", "TO_CLEAR", "EMPTY"} <= set(states[zone_id])
    assert queue_events > 0


class FakeClient:
    def __init__(self):
        self.connected = False
        self.sent = []
        self.retained = []

    def is_connected(self):
        return self.connected

    def publish(self, topic, payload, qos=0, retain=False):
        self.sent.append((topic, payload))
        self.retained.append(retain)
        return type("Info", (), {"rc": 0})()

    def loop_stop(self):
        pass

    def disconnect(self):
        pass


def test_mqtt_buffers_while_offline_then_flushes_in_order():
    client = FakeClient()
    publisher = MqttPublisher("h", 1, "restovision/demo/events", client=client)
    publisher.publish(Event("QUEUE", "demo", "Q1", 1.0, {"persons": 1}))
    publisher.publish(Event("QUEUE", "demo", "Q1", 2.0, {"persons": 2}))
    assert client.sent == [] and len(publisher.buffer) == 2
    client.connected = True
    publisher.flush()
    assert len(client.sent) == 2 and not publisher.buffer
    assert '"persons": 1' in client.sent[0][1] and '"persons": 2' in client.sent[1][1]


def test_layout_event_describes_every_zone_without_any_image():
    config = load_config("config/edge.example.json")
    message = layout_event(config, 0.0).to_json()
    assert message["type"] == "LAYOUT" and message["restaurantId"] == "demo"
    assert [z["id"] for z in message["zones"]] == [z.id for z in config.zones]
    assert message["zones"][0] == {"id": "Q1", "type": "QUEUE", "polygon": [[60, 90], [250, 90], [250, 630], [60, 630]]}
    assert set(message) == {"type", "restaurantId", "zoneId", "timestamp", "zones"}


def test_layout_is_retained_on_its_own_topic():
    client = FakeClient()
    client.connected = True
    publisher = MqttPublisher("h", 1, "restovision/demo/events", client=client)
    publisher.publish(Event("QUEUE", "demo", "Q1", 1.0, {"persons": 1}))
    publisher.publish_layout(layout_event(load_config("config/edge.example.json"), 2.0))
    assert [topic for topic, _ in client.sent] == ["restovision/demo/events", "restovision/demo/layout"]
    assert client.retained == [False, True]


def test_heartbeat_reports_rate_and_waits_for_its_period():
    from restovision_edge.health import Heartbeat

    clock = [1000.0]
    beat = Heartbeat("demo", "salle-1", "caméra", period=30, clock=lambda: clock[0])
    first = beat.due()
    assert first.type == "HEARTBEAT" and first.data["boxId"] == "salle-1"   # le premier part tout de suite
    for _ in range(150):
        beat.frame()
    clock[0] += 10
    assert beat.due() is None
    clock[0] += 20
    event = beat.due(buffered=3)
    assert event.data["fps"] == 5.0 and event.data["uptimeSeconds"] == 30 and event.data["buffered"] == 3


def test_source_is_described_without_its_address():
    from restovision_edge.health import describe_source

    assert describe_source("rtsp://admin:secret@192.168.1.20/flux") == "caméra"
    assert describe_source("simulate") == "simulateur"
    assert describe_source("samples/salle.mp4") == "vidéo"
