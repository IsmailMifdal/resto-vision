from restovision_edge.analyzers.queue import QueueAnalyzer
from restovision_edge.analyzers.tables import TableAnalyzer, classify
from restovision_edge.config import load_config
from restovision_edge.models import Detection, Event, TableState, Zone
from restovision_edge.pipeline import Pipeline
from restovision_edge.publisher import MqttPublisher
from restovision_edge.simulate import scenario
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

    def is_connected(self):
        return self.connected

    def publish(self, topic, payload, qos=0):
        self.sent.append((topic, payload))
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
