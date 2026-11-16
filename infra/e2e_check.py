"""Test de bout en bout : le simulateur publie en MQTT, on vérifie ce que l'API du backend expose.

Prérequis : broker MQTT, backend sur :8080 et simulateur déjà lancés (voir .github/workflows/ci.yml).
"""
import json
import os
import sys
import time
import urllib.request

BASE = "http://localhost:8080/api"
LOGIN = {"email": os.environ.get("DEMO_EMAIL", "demo@restovision.local"),
         "password": os.environ.get("DEMO_PASSWORD", "demo1234")}


def call(path, token=None, body=None):
    request = urllib.request.Request(BASE + path, data=json.dumps(body).encode() if body else None)
    request.add_header("Content-Type", "application/json")
    if token:
        request.add_header("Authorization", "Bearer " + token)
    with urllib.request.urlopen(request, timeout=10) as response:
        return json.load(response)


def main():
    token = call("/auth/login", body=LOGIN)["token"]
    expected_zones, expected_alerts = {"T1", "T2", "Q1"}, {"UNSERVED", "TO_CLEAR"}
    zones, alerts, states, plans = set(), set(), set(), set()
    deadline = time.time() + 170
    while time.time() < deadline and not (expected_zones <= zones and expected_alerts <= alerts):
        for zone in call("/zones", token):
            zones.add(zone["code"])
            if zone["polygon"]:
                plans.add(zone["code"])
            if zone["state"]:
                states.add(zone["state"])
        for alert in call("/alerts", token):
            if alert["type"] not in alerts:
                print("alerte :", alert["message"], flush=True)
            alerts.add(alert["type"])
        time.sleep(2)
    stats = call("/stats/today", token)
    print("zones :", sorted(zones), "| états vus :", sorted(states), "| alertes vues :", sorted(alerts))
    print("stats :", stats)
    missing = (expected_zones - zones) | (expected_alerts - alerts) | (expected_zones - plans)
    if missing or stats["tablesTotal"] != 8:
        sys.exit(f"ÉCHEC, manquant : {sorted(missing)}")
    print("OK : la chaîne simulateur -> MQTT -> backend -> API fonctionne")


if __name__ == "__main__":
    main()
