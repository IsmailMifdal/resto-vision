# Présenter RestoVision

Déroulé d'une démonstration de 10 minutes devant un gérant, avec la salle simulée. Aucune caméra n'est nécessaire.

## Avant le rendez-vous

1. Lancer la démo 5 minutes avant, le temps que tout démarre :

   ```bash
   docker compose --profile demo up --build
   ```

2. Ouvrir http://localhost:8088 et se connecter : `demo@restovision.local` / `demo1234`.
3. Vérifier le bandeau du haut : il doit afficher « En direct » et le nombre de tables occupées.
4. Ouvrir http://localhost:8025 dans un autre onglet : c'est la boîte de test où arrivent les e-mails
   envoyés pendant la démonstration (rapport du jour). Rien ne part vraiment.

À savoir : la salle simulée fait vivre chaque table en 4 minutes au lieu d'une heure, et les alertes de
démonstration se déclenchent au bout de 20 à 30 secondes. C'est voulu, pour voir des alertes pendant le rendez-vous.

## Le déroulé

| Minute | Écran | Ce qu'on montre | Ce qu'on dit |
| --- | --- | --- | --- |
| 0 à 1 | Connexion | La page d'accueil | « Vos caméras existent déjà. On y branche un boîtier, et voici ce que voit votre équipe. » |
| 1 à 4 | Salle | Le plan : chaque table à sa place, sa couleur, sa durée | « Vert, la table est libre. Ambre, des clients attendent. Bleu, il faut débarrasser. Rouge, ça dure trop. » |
| 4 à 5 | Salle | Attendre une alerte, cliquer « Je m'en occupe » | « Le serveur le plus proche prend l'alerte. Les autres voient que c'est pris en charge. » |
| 5 à 8 | Service | Choisir samedi : la frise, l'attente la plus longue cerclée | « Samedi midi, la table 4 a attendu 17 minutes. Vous voyez où et quand le service a coincé. » |
| 8 à 9 | Réglages | Changer un seuil | « C'est vous qui décidez à partir de quand l'équipe est prévenue. » |
| 9 à 10 | À venir | La liste | « Avec les mêmes caméras : le stock, la prévision d'affluence, le rapport du soir. » |

Montrer l'écran Salle sur une tablette ou un téléphone si possible : c'est là que l'équipe le verra.

## Les questions qui reviennent

- **« Vous filmez mes clients ? »** Les images restent dans le restaurant, sur le boîtier. Seul du texte en sort :
  « table 4, en attente depuis 6 minutes ». Il n'y a pas de reconnaissance faciale.
- **« Et mes salariés ? »** Le service suit des tables, pas des personnes. Un affichage dans le restaurant
  informe clients et équipe, comme pour toute caméra.
- **« Il faut changer mes caméras ? »** Non, si ce sont des caméras IP. On ajoute un boîtier.
- **« Ça reconnaît les assiettes ? »** Pas encore. Aujourd'hui le service repère verres, bols, couverts et
  bouteilles. Les assiettes demandent un apprentissage sur des images de votre salle, prévu pendant le pilote.
- **« C'est fiable ? »** La chaîne complète fonctionne et a été testée sur des vidéos de salle. Elle n'a pas
  encore tourné dans un vrai restaurant : c'est l'objet du pilote.

## Ce qu'il ne faut pas promettre

- Les chiffres de l'écran Service sont fictifs. Ils montrent ce que l'écran affichera, pas un résultat mesuré.
- La détection n'a pas été réglée sur une vraie salle. Les fausses alertes se corrigent pendant le pilote.
- Le stock, la prévision et le rapport ne sont pas encore développés.

## Après la démonstration

`Ctrl+C` dans le terminal, puis `docker compose --profile demo down`.
Ajouter `-v` pour repartir d'une base vide la prochaine fois.
