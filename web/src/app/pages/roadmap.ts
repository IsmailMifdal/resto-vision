import { Component } from '@angular/core';

/** Fonctionnalités prévues : chaque ligne correspond à un module déjà réservé dans le code. */
@Component({
  selector: 'app-roadmap',
  template: `
    <h1>À venir</h1>
    <div class="list">
      @for (item of items; track item.title) {
        <div class="card">
          <span class="tag">{{ item.version }}</span>
          <strong>{{ item.title }}</strong>
          <p class="muted">{{ item.where }}</p>
        </div>
      }
    </div>
  `,
  styles: `
    .list { display: grid; gap: 0.6rem; }
    .tag { font-size: 0.75rem; padding: 0.1rem 0.5rem; border-radius: 99px; background: var(--served); color: var(--served-ink); margin-right: 0.5rem; }
    p { margin: 0.3rem 0 0; font-size: 0.85rem; }
  `,
})
export class RoadmapPage {
  readonly items = [
    { version: 'V2', title: 'Stock synchronisé avec la caisse', where: 'backend : stock/PosConnector' },
    { version: 'V2', title: 'Anti-gaspillage', where: 'edge : analyzers/plate_waste.py' },
    { version: 'V2', title: 'Prédiction d’affluence et commande fournisseur', where: 'backend : forecast/ForecastService' },
    { version: 'V2', title: 'Rapport quotidien généré par IA', where: 'backend : report/DailyReportService' },
    { version: 'V3', title: 'Alertes vocales', where: 'web : à brancher sur LiveService.alerts' },
    { version: 'V3', title: 'Hygiène et sécurité', where: 'edge : analyzers/hygiene.py (validation juridique d’abord)' },
    { version: 'V3', title: 'Comparaison multi-sites', where: 'backend : multisite/GroupComparisonService' },
  ];
}
