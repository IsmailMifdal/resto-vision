# Installer RestoVision dans un restaurant

Guide pour la personne qui installe le boîtier. Compter une demi-journée.

## Ce qu'il faut

- Des caméras IP déjà posées, avec leur adresse RTSP (`rtsp://utilisateur:motdepasse@adresse/flux`).
- Un boîtier sur le même réseau que les caméras : mini-PC avec carte graphique, ou Jetson Orin Nano.
  Sans carte graphique l'analyse fonctionne, à 3 images par seconde environ.
- Python 3.11 ou plus récent sur le boîtier.
- Le restaurant créé côté serveur, avec son code (`restaurantId`) et un compte MQTT pour le boîtier.

## 1. Installer le logiciel du boîtier

```bash
cd edge
pip install -r requirements.txt
cp config/edge.example.json config/edge.json
```

Dans `config/edge.json`, régler `restaurantId` et le bloc `mqtt` (adresse du serveur, `"tls": true`,
`username`). Le mot de passe se met dans la variable d'environnement `RESTOVISION_MQTT_PASSWORD`,
pas dans le fichier.

Dans `edge.json`, ajouter aussi la clé publique de l'éditeur, qui sert à vérifier les mises à jour :
`"update": { "publicKey": "<clé publique>" }`.

### Lancer le boîtier sous son lanceur

Le boîtier tourne sous un lanceur, qui le redémarre s'il s'arrête, installe les mises à jour et revient à la
version précédente si une nouvelle version plante :

```bash
python -m restovision_edge.launcher --root /opt/restovision -- \
    --config config/edge.json --source rtsp://... --publisher mqtt
```

Pour qu'il démarre avec la machine, en faire un service (Linux, `/etc/systemd/system/restovision.service`) :

```ini
[Unit]
Description=Boîtier RestoVision
After=network-online.target

[Service]
WorkingDirectory=/opt/restovision-install/edge
Environment=RESTOVISION_MQTT_PASSWORD=<mot de passe du boîtier>
ExecStart=/usr/bin/python3 -m restovision_edge.launcher --root /opt/restovision -- --config config/edge.json --source rtsp://... --publisher mqtt
Restart=always

[Install]
WantedBy=multi-user.target
```

puis `systemctl enable --now restovision`.

## 2. Dessiner les zones

1. Lancer le boîtier avec l'aperçu :

   ```bash
   python -m restovision_edge --config config/edge.json --source rtsp://... --preview-port 8090
   ```

2. Sur le boîtier, ouvrir l'application web, page **Zones**, puis « Depuis le boîtier ».
3. Dessiner chaque table **avec ses chaises**, et la file devant le comptoir.
4. « Télécharger edge.json », remplacer `config/edge.json`, relancer le boîtier.

Une personne compte pour une table quand le centre de sa silhouette est dans la zone : une zone trop serrée
autour du plateau ne voit pas les clients assis.

## 3. Régler les tables dressées

Si les tables sont dressées d'avance (verres, couverts), le faire salle vide et dressée :

```bash
python -m restovision_edge --config config/edge.json --source rtsp://... --calibrate 20
```

Recopier les valeurs `baselineDishes` affichées dans `edge.json`, ou les saisir dans la page Zones.
Sans ce réglage, une table dressée et libre est vue « à débarrasser ».

## 4. Vérifier

- Ouvrir http://127.0.0.1:8090 sur le boîtier : l'image annotée montre chaque zone et son état.
- Faire asseoir quelqu'un à une table : elle passe « en attente » en quelques secondes.
- Dans l'application, l'écran Salle affiche le plan et suit les changements.

## 5. Régler les alertes avec le gérant

Page **Réglages** : à partir de combien de temps prévenir l'équipe. Pour démarrer : 5 minutes pour des clients
non servis, 3 minutes pour une table à débarrasser, 5 personnes dans la file.

## Avant de partir

- Poser l'affichage d'information « vidéo » à l'entrée et faire signer l'accord au gérant.
- Vérifier que le boîtier redémarre seul après une coupure de courant.
- Noter l'angle de chaque caméra : si elle bouge, il faut redessiner les zones.

## Limites à connaître

- Le modèle de base ne reconnaît pas les assiettes, seulement verres, bols, couverts et bouteilles.
- Mauvais éclairage, contre-jour et clients cachés par un pilier donnent des erreurs : choisir la caméra qui
  voit le mieux les tables.
- La mise à jour à distance du boîtier n'existe pas encore.
