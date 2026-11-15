# RestoVision

Service d'IA branché sur les caméras d'un restaurant ou d'un snack : il suit l'état des tables et la file
d'attente, et prévient l'équipe quand des clients attendent trop longtemps.

**Principe : la vidéo est analysée sur place. Seuls des événements (texte) partent vers le cloud.**

```
caméras IP -> edge (Python, YOLO) --MQTT--> backend (Spring Boot) --WebSocket--> web (Angular)
```

## Contenu

| Dossier | Rôle | État |
| --- | --- | --- |
| `edge/` | Détection, zones, état des tables, file d'attente, simulateur | V1 codée, 10 tests |
| `backend/` | API, ingestion MQTT, règles d'alerte, statistiques, JWT | V1 codée, tests lancés par la CI |
| `web/` | Écran salle en direct, dashboard gérant | V1 codée |
| `infra/` + `docker-compose.yml` | PostgreSQL (TimescaleDB), Mosquitto, backend | prêt |

Fonctionnalités réservées, pas encore codées : stock (caisse), anti-gaspillage, prédiction, rapport IA,
alertes vocales, hygiène, multi-sites. Chaque module a déjà son fichier (voir la page « À venir » du web).

## Lancer la démo sans caméra

Il faut Docker, Python 3.11+ et Node 22+.

```bash
# 1. Base de données, broker MQTT et backend
docker compose up --build

# 2. Application web  ->  http://localhost:4200
cd web && npm install && npm start

# 3. Simulateur : rejoue un service de restaurant en temps réel
cd edge && pip install paho-mqtt
python -m restovision_edge --config config/edge.example.json --source simulate --publisher mqtt
```

Connexion : `demo@restovision.local` / `demo1234`. Les seuils du restaurant de démonstration sont courts
(30 s non servi, 20 s à débarrasser, 4 personnes dans la file) pour voir des alertes en quelques minutes.

Sans Docker, le backend tourne aussi sur une base en mémoire :
`cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=local` (il faut alors un broker MQTT local,
ou `MQTT_ENABLED=false`).

## Avec une vraie vidéo

```bash
cd edge && pip install -r requirements.txt
cp config/edge.example.json config/edge.json     # puis dessiner les zones (coordonnées en pixels)
python -m restovision_edge --config config/edge.json --source ma_video.mp4 --publisher mqtt
# ou une caméra : --source rtsp://utilisateur:motdepasse@192.168.1.20/stream
```

Limite connue : le modèle YOLO de base ne connaît pas la classe « assiette ». Il repère verres, bols,
couverts et bouteilles. Il faudra l'entraîner sur des images du restaurant pilote.

## Tests

```bash
cd edge && python -m pytest -q tests
cd backend && mvn verify
cd web && npx ng build
```

## Avant la production

- Remplacer `ddl-auto: update` par des migrations Flyway.
- Vérifier le JWT à la connexion WebSocket (`WebSocketConfig`).
- Identifiants et TLS par boîtier sur le broker MQTT (`infra/mosquitto.conf` accepte tout le monde).
- Définir `JWT_SECRET` et `DEMO_DATA=false`.
- RGPD : affichage dans le restaurant, analyse d'impact, pas de reconnaissance faciale.
