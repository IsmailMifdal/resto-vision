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
| `edge/` | Détection, zones, état des tables, file d'attente, aperçu annoté, simulateur | V1 codée, 17 tests |
| `backend/` | API, ingestion MQTT, règles d'alerte, historique du service, JWT | V1 codée, 18 tests |
| `web/` | Plan de salle en direct, frise du service pour le gérant, réglages, dessin des zones | V1 codée |
| `infra/` + `docker-compose.yml` | PostgreSQL (TimescaleDB), Mosquitto, backend | prêt |

Fonctionnalités réservées, pas encore codées : stock (caisse), anti-gaspillage, prédiction, rapport IA,
alertes vocales, hygiène, multi-sites. Chaque module a déjà son fichier (voir la page « À venir » du web).

## Lancer la démo sans caméra

Le plus simple, avec Docker seulement :

```bash
docker compose --profile demo up --build     # puis http://localhost:8088
```

Le déroulé d'une présentation est dans [docs/DEMONSTRATION.md](docs/DEMONSTRATION.md), et l'installation
dans un restaurant dans [docs/INSTALLATION.md](docs/INSTALLATION.md).

Pour développer, lancer chaque partie à la main. Il faut Docker, Python 3.11+ et Node 22+.

```bash
# 1. Base de données, broker MQTT et backend
docker compose up --build db mqtt backend

# 2. Application web  ->  http://localhost:4200
cd web && npm install && npm start

# 3. Simulateur : rejoue un service de restaurant en temps réel
cd edge && pip install paho-mqtt
python -m restovision_edge --config config/edge.example.json --source simulate --publisher mqtt
```

Le simulateur anime une salle de 8 tables et une file. Le restaurant de démonstration reçoit aussi une semaine
de services fictifs (`DEMO_HISTORY=false` pour s'en passer), pour que l'écran « Service » ait un historique.

Connexion : `demo@restovision.local` / `demo1234`. Les seuils du restaurant de démonstration sont courts
(30 s non servi, 20 s à débarrasser, 4 personnes dans la file) pour voir des alertes en quelques minutes.

Sans Docker, le backend tourne aussi sur une base en mémoire :
`cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=local` (il faut alors un broker MQTT local,
ou `MQTT_ENABLED=false`).

## Supervision des boîtiers

Chaque boîtier envoie un signal de vie toutes les 30 secondes : sa source (caméra, vidéo, simulateur), le nombre
d'images analysées par seconde, sa version et les messages en attente si le réseau est coupé. Sans signal pendant
90 secondes (`restovision.boxes.offline-after-seconds`), le boîtier est déclaré hors ligne : le bandeau de
l'application l'affiche en rouge et l'écran Salle prévient que l'état des tables n'est plus à jour. Le détail est
dans Réglages. Le nom d'un boîtier se règle avec `boxId` dans `edge.json` (par défaut, le nom de la machine).

## Retours en cuisine (anti-gaspillage)

Pendant le repas, le boîtier note le plus grand nombre d'aliments vus sur la table ; quand les clients partent,
il compte ceux qui restent et envoie un événement `PLATE_RETURN`. L'écran « Service » affiche la part des repas
qui ont laissé de la nourriture, comparée aux 7 jours précédents, et les tables concernées.

Limite : le modèle de base reconnaît des aliments (pizza, sandwich, gâteau, fruits, brocoli, carotte), pas les
assiettes. Un plat qu'il ne sait pas nommer n'est pas compté. Un modèle entraîné sur les assiettes du restaurant
pilote remplacera la liste `labels.food` de `edge.json`.

## Rapport du jour

L'écran « Service » affiche un rapport en phrases pour chaque journée. Les chiffres sont calculés par le
backend ; le rédacteur ne fait que les mettre en phrases.

- Sans réglage, le rapport est rédigé avec des phrases fixes.
- Avec la variable `ANTHROPIC_API_KEY`, le rapport d'une journée terminée est rédigé par Claude
  (`REPORT_MODEL` pour changer de modèle, `claude-opus-5-5` par défaut). Si l'appel échoue, le rapport
  revient aux phrases fixes.
- Le rapport d'une journée terminée est rédigé une fois puis gardé en base. Le gérant peut le faire rédiger
  à nouveau. La journée en cours est toujours rédigée sans IA.

## Stock

L'écran « Stock » suit les ingrédients à partir des ventes de la caisse. La caméra ne compte pas les plats :
ce serait peu fiable.

1. Saisir les ingrédients, avec leur quantité et un seuil de commande.
2. Saisir la recette de chaque plat. Le nom du plat doit être celui que la caisse écrit dans ses exports
   (majuscules et accents ne comptent pas).
3. Importer l'export de ventes de la caisse en CSV. Trois colonnes suffisent, dans n'importe quel ordre :
   `date`, `plat`, `quantite`. Séparateur `;` ou `,`. Un exemple : `infra/ventes-exemple.csv`.

Chaque plat vendu retire sa recette du stock. L'écran signale les ingrédients passés sous leur seuil, les plats
vendus sans recette et les lignes illisibles. Un même fichier ne peut pas être compté deux fois.

### Commande fournisseur

La carte « Commande à préparer » propose les quantités à commander pour tenir 3, 7 ou 14 jours. Pour chaque
ingrédient, la consommation par jour vient des ventes importées sur les 28 derniers jours ; la commande
couvre la durée choisie et laisse le stock au-dessus du seuil. Sans vente importée, seuls les ingrédients
sous leur seuil sont proposés. Les lignes sont regroupées par fournisseur et se copient en texte.

La proposition ne tient pas encore compte de l'affluence prévue : les ventes viennent de la caisse et
l'affluence des caméras, et il faut de vraies données pour relier les deux.

## Prévision d'affluence

L'écran « Service » affiche l'affluence attendue le lendemain, heure par heure. C'est la moyenne des 28 derniers
jours, où le même jour de la semaine compte trois fois plus. L'écran indique sur combien de jours elle repose.

Météo : si la position du restaurant est saisie dans Réglages, les jours passés dont la météo ressemble à celle
du lendemain (pluie ou non, à 5 °C près) comptent deux fois plus. Aucun coefficient n'est inventé : ce sont les
journées du restaurant qui disent comment il réagit au temps. La météo vient d'Open-Meteo (gratuit, sans clé) ;
seules les coordonnées lui sont envoyées. Sans position, ou si Open-Meteo ne répond pas, la prévision se fait
sur l'historique seul. Les réservations ne sont pas prises en compte.

## Avec une vraie vidéo

```bash
cd edge && pip install -r requirements.txt
cp config/edge.example.json config/edge.json     # puis dessiner les zones (coordonnées en pixels)
python -m restovision_edge --config config/edge.json --source ma_video.mp4 --publisher mqtt
# ou une caméra : --source rtsp://utilisateur:motdepasse@192.168.1.20/stream
```

### Dessiner les zones

Lancer le boîtier avec `--preview-port 8090`, puis ouvrir la page **Zones** de l'application web sur le même
poste. « Depuis le boîtier » affiche l'image de la caméra : on y dessine chaque table (avec ses chaises) et la
file d'attente, puis « Télécharger edge.json » donne le fichier à placer dans `edge/config/edge.json`.
Un `edge.json` existant peut être rouvert pour être corrigé. L'image reste dans le navigateur.

Options utiles avec une vidéo ou une caméra :

| Option | Effet |
| --- | --- |
| `--preview-port 8090` | Image annotée en direct (zones, états) sur http://127.0.0.1:8090, visible seulement depuis ce poste. `/frame.jpg` donne l'image brute |
| `--preview apercu.mp4` | Enregistre l'image annotée dans un fichier |
| `--realtime --loop` | Rejoue un fichier à vitesse réelle et sans fin, comme une caméra (démonstration) |
| `--calibrate 20` | Salle dressée et vide : mesure 20 s la vaisselle de chaque table et affiche les `baselineDishes` à recopier |

Réglages de `edge.json` à connaître :

- `model.imgsz` (1280 par défaut) : taille d'analyse. En 640 l'analyse est 3 fois plus rapide mais la vaisselle
  vue de loin n'est presque plus détectée.
- `zones[].baselineDishes` : vaisselle d'une table dressée et libre (verres, couverts). Elle n'est pas comptée,
  sinon une table dressée passerait « à débarrasser ».
- `stableSeconds` : durée de la fenêtre de lissage. Une personne doit être vue la moitié de ce temps pour
  compter (un serveur qui passe est ignoré), la vaisselle 30 % du temps.
- Dessiner chaque zone de table **avec ses chaises** : une personne compte si le centre de sa boîte est dans la zone.

Limite connue : le modèle YOLO de base ne connaît pas la classe « assiette ». Il repère verres, bols,
couverts et bouteilles. Il faudra l'entraîner sur des images du restaurant pilote.

## Tests

```bash
cd edge && python -m pytest -q tests
cd backend && mvn verify
cd web && npx ng build
```

## Mise en production

Lancer le backend avec le profil `prod` (`--spring.profiles.active=prod`). Il refuse de démarrer s'il manque
une de ces variables : `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET` (32 caractères au moins), `MQTT_URL`,
`MQTT_USER`, `MQTT_PASSWORD`, `CORS_ORIGINS`. Les données de démonstration sont désactivées.

- **Base** : le schéma est géré par Flyway (`backend/src/main/resources/db/migration`). Toute évolution passe
  par un nouveau fichier `V2__...sql`.
- **Écrans en direct** : le WebSocket exige le jeton de connexion, et un écran ne reçoit que son restaurant.
- **Broker MQTT** : `infra/mosquitto.prod.conf` et `infra/mosquitto.prod.acl` donnent un compte par boîtier,
  limité aux sujets de son restaurant, sur une liaison chiffrée. Côté boîtier, dans `edge.json` :
  `"mqtt": { "host": "...", "port": 8883, "tls": true, "username": "boitier-demo" }`, et le mot de passe dans
  la variable `RESTOVISION_MQTT_PASSWORD`.
- **RGPD** : affichage dans le restaurant, analyse d'impact, pas de reconnaissance faciale.

Reste à faire avant un vrai déploiement : servir l'application web en HTTPS derrière un proxy, et sauvegarder la base.
