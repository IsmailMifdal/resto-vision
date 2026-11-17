import { Component } from '@angular/core';

/** Fonctionnalités prévues : ce que le service apportera, et à quelle version. */
@Component({
  selector: 'app-roadmap',
  template: `
    <div class="page-head">
      <div>
        <h1>À venir</h1>
        <p>Ces fonctions réutilisent les données déjà collectées, sans nouveau matériel.</p>
      </div>
    </div>
    <section class="card">
      <ul>
        @for (item of items; track item.title) {
          <li>
            <div>
              <strong>{{ item.title }}</strong>
              <span class="muted">{{ item.what }}</span>
            </div>
            <span class="version">{{ item.version }}</span>
          </li>
        }
      </ul>
    </section>
  `,
  styles: `
    .card { max-width: 760px; padding: 6px var(--pad); }
    ul { list-style: none; margin: 0; padding: 0; }
    li { display: flex; align-items: center; justify-content: space-between; gap: 16px; min-height: 64px; padding: 10px 0; border-top: 1px solid var(--rule); }
    li:first-child { border-top: 0; }
    li div { display: grid; }
    strong { font-weight: 500; }
    .version { flex: none; white-space: nowrap; height: 22px; padding: 0 9px; border-radius: 11px; background: var(--raised); font-size: 12px; line-height: 22px; font-weight: 500; }
  `,
})
export class RoadmapPage {
  readonly items = [
    { version: 'V2', title: 'Caisse connectée en direct', what: 'Le stock, aujourd’hui mis à jour par un fichier de ventes, suivra la caisse vente après vente.' },
    { version: 'V2', title: 'Anti-gaspillage par plat', what: 'Reconnaître chaque assiette et son niveau de remplissage, avec un modèle entraîné sur les images du pilote.' },
    { version: 'V2', title: 'Prévision avec météo et réservations', what: 'La prévision du lendemain, déjà visible dans Service, affinée par la météo et les réservations.' },
    { version: 'V2', title: 'Commande envoyée au fournisseur', what: 'Le bon de commande, déjà préparé dans Stock, envoyé directement et ajusté à l’affluence prévue.' },
    { version: 'V2', title: 'Rapport envoyé chaque soir', what: 'Le rapport du jour, déjà visible dans Service, envoyé par e-mail au gérant.' },
    { version: 'V3', title: 'Alertes vocales', what: 'Sur oreillette ou montre, sans regarder un écran.' },
    { version: 'V3', title: 'Hygiène et sécurité', what: 'Sol mouillé, sortie de secours bloquée. Après validation juridique.' },
    { version: 'V3', title: 'Comparaison multi-sites', what: 'Les restaurants d’un même groupe côte à côte.' },
  ];
}
