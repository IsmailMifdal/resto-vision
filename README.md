# RestoVision

Service d'IA branché sur les caméras d'un restaurant ou d'un snack : il suit l'état des tables et la file
d'attente, et prévient l'équipe quand des clients attendent trop longtemps.

> **🔗 Démo en ligne : [resto-vision.vercel.app](https://resto-vision.vercel.app)**
> Compte de démonstration : `demo@restovision.local` / `demo1234` (le plan de salle est animé par le simulateur).
>
> Réalisé par [Ismail Mifdal](https://ismail-mifdal.vercel.app).

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

## Sortie de secours encombrée

Une zone de type `EXIT` (dessinée avec « + Sortie de secours » dans la page Zones) est surveillée : si un objet
(chaise, valise, sac, banc, plante, vélo, table — `labels.obstacle` dans `edge.json`) y est vu au moins 80 % du
temps sur une dizaine de secondes, la sortie passe « encombrée ». Au-delà du seuil réglé dans Réglages (60 s par
défaut), l'équipe reçoit une alerte, comme pour une table. Une personne qui passe n'est jamais comptée.

Avant de vendre la fonction : la faire valider par un juriste et la mentionner dans l'affichage du restaurant.
Le sol mouillé n'est pas détecté : le modèle de base ne connaît pas cette situation.

## Comparaison multi-sites

Les restaurants qui partagent un `groupCode` forment un groupe. Le responsable de groupe (rôle `ADMIN`) voit
l'écran « Groupe » : un classement des sites sur une mesure au choix (attente, occupation, repas avec restes) et
un tableau de tous les indicateurs, sur les 7, 14 ou 28 dernières journées terminées. Un gérant (`MANAGER`) ne
voit que son restaurant. Démonstration : le compte `groupe@restovision.local` / `demo1234` voit trois sites.

## Alertes sans regarder l'écran

Sur l'écran Salle, chaque appareil de l'équipe peut activer :

- **Annonces vocales** : chaque nouvelle alerte est dite en français (« Table 4 : clients pas encore servis »),
  dans le haut-parleur ou l'oreillette reliée au téléphone, avec une vibration. Une alerte que personne n'a
  prise en charge est rappelée au bout de 2 minutes.
- **Notifications** : la même alerte en notification ; une montre connectée au téléphone la reprend.

Le réglage reste sur l'appareil et fonctionne quel que soit l'écran ouvert. Les alertes déjà présentes à
l'ouverture ne sont pas relues. Limites : l'application doit rester ouverte (pas d'envoi quand le navigateur est
fermé) ; sur iPhone, les notifications web demandent d'ajouter l'application à l'écran d'accueil ; les
notifications et le service worker exigent HTTPS, sauf sur `localhost`.

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
assiettes. Un plat qu'il ne sait pas nommer n'est pas compté. Le modèle entraîné sur les images du pilote
(« Entraîner sur les images du pilote ») reconnaît `plate_full`, `plate_partial` et `plate_empty` ; ses classes
remplacent la liste `labels.food` de `edge.json`.

## Mise à jour à distance des boîtiers

Une nouvelle version du boîtier se publie depuis le poste de l'éditeur ; chaque boîtier l'installe seul.

```bash
cd edge
python -m restovision_edge.release keygen --out ~/cles-restovision      # une seule fois
# augmenter __version__ dans restovision_edge/__init__.py, puis :
python -m restovision_edge.release build --key ~/cles-restovision/restovision-edge.key --out version.json
python -m restovision_edge.release publish --manifest version.json --host <broker> --port 8883 --tls \
    --username editeur --password <mot de passe>
```

Sécurité : chaque version est signée (Ed25519) avec une clé privée qui reste chez l'éditeur, hors du serveur. Le
boîtier ne connaît que la clé publique (`update.publicKey` dans `edge.json`) et refuse un paquet dont la signature
ou l'empreinte ne correspond pas, une version plus ancienne que la sienne, ou un fichier qui sortirait de son
dossier. Un serveur ou un broker piraté ne peut donc pas installer de code. La version arrive par la connexion MQTT
que le boîtier ouvre déjà (message conservé sur `restovision/updates`) : aucune connexion entrante.

Le boîtier tourne sous `restovision_edge.launcher` (voir `docs/INSTALLATION.md`) : la nouvelle version est installée
à côté de l'ancienne ; si elle plante deux fois au démarrage, le lanceur revient à la dernière version qui a tenu
2 minutes et ne la réessaie plus. Réglages affiche la version de chaque boîtier, la dernière publiée et le
dernier refus. `release publish --clear` retire la version publiée. Sans clé publique dans `edge.json`, le boîtier
ignore les mises à jour (c'est le cas du simulateur de démonstration).

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

### Envoi par e-mail

- **Rapport du jour** : envoyé chaque matin à 8 h aux gérants pour la veille (`MAIL_REPORT_CRON`), et sur
  demande depuis l'écran Service. Il faut un serveur d'envoi : `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`,
  `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`, et l'expéditeur `MAIL_FROM`. Sans serveur, rien ne part.
  Dans Docker, les e-mails vont dans Mailpit, une boîte de test locale : http://localhost:8025.
- **Bon de commande** : « Préparer l'e-mail » ouvre la messagerie du gérant avec la commande rédigée.
  Rien ne part sans lui.

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

### Modèle de détection

Le boîtier utilise **RF-DETR** (Roboflow) pré-entraîné sur COCO, avec le suivi **ByteTrack** de la bibliothèque
`trackers`. Les deux sont sous licence Apache 2.0, poids compris : ils peuvent être vendus dans un abonnement.
Dans `edge.json` : `"model": { "backend": "rfdetr", "size": "nano" }` (`small` ou `medium` : plus précis, plus
lent). `"weights": "chemin/checkpoint.pth"` charge un modèle entraîné sur les images du restaurant (voir
« Entraîner sur les images du pilote »).

`"backend": "yolo"` garde YOLO d'Ultralytics pour comparer. Attention : sa licence AGPL-3.0 couvre aussi les
modèles entraînés avec, et un produit commercial demande une licence Enterprise d'Ultralytics.

Mesuré sur 3 images de la vidéo de salle (processeur de portable, sans carte graphique) : RF-DETR Nano trouve
24 personnes et 7 objets de vaisselle en 0,4 s par image ; YOLO11n en 1280 px, 15 personnes et 10 objets en
0,4 s ; YOLO11n en 640 px, 12 personnes et 1 objet en 0,1 s.

Réglages de `edge.json` à connaître :

- `model.imgsz` (YOLO seulement, 1280 par défaut) : taille d'analyse. En 640 l'analyse est 3 fois plus rapide
  mais la vaisselle vue de loin n'est presque plus détectée.
- `zones[].baselineDishes` : vaisselle d'une table dressée et libre (verres, couverts). Elle n'est pas comptée,
  sinon une table dressée passerait « à débarrasser ».
- `stableSeconds` : durée de la fenêtre de lissage. Une personne doit être vue la moitié de ce temps pour
  compter (un serveur qui passe est ignoré), la vaisselle 30 % du temps.
- Dessiner chaque zone de table **avec ses chaises** : une personne compte si le centre de sa boîte est dans la zone.

### Entraîner sur les images du pilote

Le modèle COCO ne connaît pas les assiettes ni leur remplissage. Le paquet `edge/restovision_training` prépare un
modèle propre au restaurant, sur un poste de travail (il ne part jamais sur les boîtiers) :

```bash
cd edge
pip install -r requirements.txt -r requirements-training.txt

# 1. Étiquetage automatique d'enregistrements de la salle (une image toutes les 2 s)
python -m restovision_training.autolabel --config config/edge.json --video salle.mp4 --every 2 --out dataset

# 2. Relire les étiquettes (dataset/apercu/ montre chaque image annotée), corriger dans un outil COCO
#    (CVAT, Label Studio, Roboflow) les erreurs, puis :

# 3. Entraîner, de préférence sur une carte graphique (sur un processeur seul, compter des heures)
python -m restovision_training.train --dataset dataset --size nano --epochs 40 --out runs/pilote
```

Comment l'étiquetage automatique fonctionne :

- **RF-DETR** (COCO) donne les personnes, chaises, tables, verres, tasses ;
- **Grounding DINO** (détection à partir d'un texte) trouve les assiettes (« plate ») et les panneaux « sol
  mouillé » ; puis, dans chaque assiette recadrée et agrandie, la nourriture (« food. soup. pasta. salad… ») ;
- **SAM 2** découpe le contour exact de l'assiette et de la nourriture ; la part de l'assiette couverte donne la
  classe : `plate_full` (plus de 45 %), `plate_partial`, `plate_empty` (moins de 10 %) ;
- **anonymisation** : le haut de chaque personne est flouté dans les images du jeu, qui ne contient donc aucun
  visage. Ces images restent sur le poste du prestataire et sont à effacer à la fin du pilote.

Les images sont réparties 80 / 10 / 10 entre `train`, `valid` et `test`, toujours de la même façon pour un même
nom. Les étiquettes automatiques sont un point de départ : **il faut les relire** avant d'entraîner. À la fin,
`train` écrit `runs/pilote/edge-model.json` : le bloc `model` (avec `weights` vers `checkpoint_best_total.pth`)
et les groupes `labels` à recopier dans `edge.json`. Le modèle est ensuite livré aux boîtiers avec la mise à jour
à distance.

Le panneau « sol mouillé » est seulement étiqueté pour l'instant : l'alerte correspondante attend la validation
du pilote.

Vérifié sur la vidéo d'exemple : 8 images étiquetées (45 personnes, 15 assiettes dont 13 pleines), une époque
d'entraînement sur processeur, puis le boîtier charge le modèle et rend les classes `plate_*`. Un vrai modèle
demande quelques centaines d'images relues du restaurant.

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
