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
    { version: 'V2', title: 'Stock synchronisé avec la caisse', what: 'Le stock baisse à chaque vente, à partir des recettes.' },
    { version: 'V2', title: 'Anti-gaspillage', what: 'Repérer les assiettes qui reviennent pleines.' },
    { version: 'V2', title: 'Prédiction d’affluence', what: 'Prévoir le rush avec l’historique, la météo et le jour.' },
    { version: 'V2', title: 'Commande fournisseur', what: 'Un bon de commande préparé, à valider en un clic.' },
    { version: 'V2', title: 'Rapport quotidien', what: 'Le résumé du service, rédigé et envoyé au gérant.' },
    { version: 'V3', title: 'Alertes vocales', what: 'Sur oreillette ou montre, sans regarder un écran.' },
    { version: 'V3', title: 'Hygiène et sécurité', what: 'Sol mouillé, sortie de secours bloquée. Après validation juridique.' },
    { version: 'V3', title: 'Comparaison multi-sites', what: 'Les restaurants d’un même groupe côte à côte.' },
  ];
}
