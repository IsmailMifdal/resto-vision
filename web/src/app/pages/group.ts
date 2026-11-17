import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ApiService } from '../core/api.service';
import { Group, Site, formatWait } from '../core/models';

type Measure = 'wait' | 'occupancy' | 'leftovers';

interface MeasureInfo {
  label: string;
  /** Vrai quand une valeur basse est meilleure. */
  lowerIsBetter: boolean;
  value: (site: Site) => number | null;
  format: (value: number) => string;
}

const MEASURES: Record<Measure, MeasureInfo> = {
  wait: { label: 'Attente moyenne', lowerIsBetter: true, value: (s) => s.averageWaitSeconds, format: (v) => formatWait(v) },
  occupancy: { label: 'Pic d’occupation', lowerIsBetter: false, value: (s) => s.peakOccupancyRate, format: (v) => `${Math.round(v * 100)} %` },
  leftovers: { label: 'Repas avec restes', lowerIsBetter: true, value: (s) => s.leftoverShare, format: (v) => `${v} %` },
};

/** Écran du responsable de groupe : les restaurants côte à côte sur les derniers jours. */
@Component({
  selector: 'app-group',
  template: `
    <div class="page-head">
      <div>
        <h1>Groupe</h1>
        <p>{{ finding() }}</p>
      </div>
      <div class="days" role="tablist" aria-label="Période">
        @for (option of [7, 14, 28]; track option) {
          <button role="tab" [attr.aria-selected]="days() === option" [class.on]="days() === option" (click)="load(option)">{{ option }} jours</button>
        }
      </div>
    </div>
    @if (error()) { <p class="error notice">{{ error() }}</p> }

    @if (group(); as g) {
      <section class="card">
        <div class="card-head">
          <h2>Classement</h2>
          <div class="days" role="tablist" aria-label="Mesure comparée">
            @for (key of measureKeys; track key) {
              <button role="tab" [attr.aria-selected]="measure() === key" [class.on]="measure() === key" (click)="measure.set(key)">{{ measures[key].label }}</button>
            }
          </div>
        </div>
        <ul class="ranking">
          @for (row of ranking(); track row.site.code; let first = $first) {
            <li [class.mine]="row.site.mine">
              <span class="name">{{ row.site.name }}@if (row.site.mine) { <span class="faint small">Votre site</span> }</span>
              <span class="track">
                @if (row.value !== null) { <span class="bar" [class.best]="first && ranking().length > 1" [style.width.%]="row.width"></span> }
              </span>
              <strong class="num">{{ row.value === null ? '–' : measures[measure()].format(row.value) }}</strong>
            </li>
          }
        </ul>
        <p class="muted small">{{ measures[measure()].lowerIsBetter ? 'Du meilleur au moins bon : plus la valeur est basse, mieux c’est.' : 'Du plus rempli au moins rempli.' }} Journées terminées du {{ date(g.from) }} au {{ date(g.to) }}.</p>
      </section>

      <section class="card">
        <div class="card-head"><h2>Tous les indicateurs</h2></div>
        <div class="scroll">
          <table>
            <thead>
              <tr>
                <th>Restaurant</th><th class="num">Tablées</th><th class="num">Attente moyenne</th><th class="num">Attente la plus longue</th>
                <th class="num">Débarrassage</th><th class="num">Pic d’occupation</th><th class="num">Repas avec restes</th><th>Boîtiers</th>
              </tr>
            </thead>
            <tbody>
              @for (site of g.sites; track site.code) {
                <tr [class.mine]="site.mine">
                  <td>{{ site.name }}</td>
                  <td class="num">{{ site.seatings }}</td>
                  <td class="num">{{ wait(site.averageWaitSeconds) }}</td>
                  <td class="num">{{ wait(site.longestWaitSeconds) }}</td>
                  <td class="num">{{ wait(site.averageToClearSeconds) }}</td>
                  <td class="num">{{ site.seatings ? percent(site.peakOccupancyRate) : '–' }}</td>
                  <td class="num">{{ site.leftoverShare === null ? '–' : site.leftoverShare + ' %' }}</td>
                  <td>{{ boxes(site) }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      </section>
    } @else if (!error()) {
      <section class="card"><p class="muted">Chargement du groupe…</p></section>
    }
  `,
  styles: `
    .notice { margin-bottom: 14px; }
    .days { display: flex; gap: 4px; flex-wrap: wrap; }
    .days button { background: transparent; color: var(--muted); padding: 0 12px; height: 32px; }
    .days button:hover { color: var(--text); background: var(--surface); }
    .days button.on { background: var(--raised); color: var(--text); }
    .card + .card { margin-top: var(--gap); }
    .ranking { list-style: none; margin: 0 0 12px; padding: 0; display: grid; gap: 4px; }
    .ranking li { display: grid; grid-template-columns: minmax(160px, 240px) minmax(0, 1fr) 90px; align-items: center; gap: 16px; min-height: 40px; }
    .name { display: grid; line-height: 18px; min-width: 0; }
    .name > * { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .mine .name { font-weight: 600; }
    .track { height: 10px; }
    .bar { display: block; height: 10px; border-radius: 0 4px 4px 0; background: #6b6762; }
    .bar.best { background: var(--free); }
    .mine .bar:not(.best) { background: #e6e2dc; }
    .ranking strong { text-align: right; font-weight: 600; }
    .scroll { overflow-x: auto; }
    table { width: 100%; border-collapse: collapse; }
    th { text-align: left; font-size: 12px; font-weight: 400; color: var(--faint); height: 32px; padding: 0 12px 0 0; white-space: nowrap; }
    td { height: 48px; padding: 0 12px 0 0; border-top: 1px solid var(--rule); white-space: nowrap; }
    th.num, td.num { text-align: right; }
    tr.mine td:first-child { font-weight: 600; }
    @media (max-width: 640px) { .ranking li { grid-template-columns: minmax(0, 1fr) 70px; } .track { grid-column: 1 / -1; grid-row: 2; } }
  `,
})
export class GroupPage implements OnInit {
  private readonly api = inject(ApiService);
  readonly measures = MEASURES;
  readonly measureKeys = Object.keys(MEASURES) as Measure[];

  readonly group = signal<Group | null>(null);
  readonly days = signal(7);
  readonly measure = signal<Measure>('wait');
  readonly error = signal<string | null>(null);

  /** Les sites triés du meilleur au moins bon sur la mesure choisie ; les sites sans mesure à la fin. */
  readonly ranking = computed(() => {
    const info = MEASURES[this.measure()];
    const rows = (this.group()?.sites ?? []).map((site) => ({ site, value: info.value(site) }));
    const measured = rows.filter((row) => row.value !== null) as { site: Site; value: number }[];
    measured.sort((a, b) => (info.lowerIsBetter ? a.value - b.value : b.value - a.value));
    const max = Math.max(...measured.map((row) => row.value), 0) || 1;
    return [
      ...measured.map((row) => ({ ...row, width: (row.value / max) * 100 })),
      ...rows.filter((row) => row.value === null).map((row) => ({ ...row, width: 0 })),
    ];
  });

  /** L'écart qui compte, en une phrase : le site qui fait le plus attendre, comparé au groupe. */
  readonly finding = computed(() => {
    const group = this.group();
    if (!group) return '';
    const waits = group.sites.filter((site) => site.averageWaitSeconds !== null && site.seatings > 0);
    if (waits.length < 2) return `${group.sites.length} restaurants dans le groupe. Pas assez de services mesurés pour les comparer.`;
    const total = waits.reduce((sum, site) => sum + site.seatings, 0);
    const average = Math.round(waits.reduce((sum, site) => sum + site.averageWaitSeconds! * site.seatings, 0) / total);
    const slowest = waits.reduce((a, b) => (a.averageWaitSeconds! >= b.averageWaitSeconds! ? a : b));
    const gap = slowest.averageWaitSeconds! - average;
    return `Sur ${group.days} jours, ${slowest.name} fait attendre ses clients ${formatWait(slowest.averageWaitSeconds)} avant le service, soit ${formatWait(gap)} de plus que la moyenne du groupe (${formatWait(average)}).`;
  });

  ngOnInit(): void {
    this.load(7);
  }

  load(days: number): void {
    this.days.set(days);
    this.api.group(days).subscribe({
      next: (group) => {
        this.group.set(group);
        this.error.set(null);
      },
      error: (e) => this.error.set(e.error?.message || 'Comparaison indisponible.'),
    });
  }

  wait(seconds: number | null): string {
    return formatWait(seconds);
  }

  percent(rate: number): string {
    return `${Math.round(rate * 100)} %`;
  }

  date(value: string): string {
    return new Date(value + 'T12:00:00').toLocaleDateString('fr-FR', { day: 'numeric', month: 'long' });
  }

  boxes(site: Site): string {
    if (!site.boxesTotal) return 'Aucun';
    return site.boxesOnline === site.boxesTotal ? `${site.boxesTotal} en ligne` : `${site.boxesOnline} sur ${site.boxesTotal} en ligne`;
  }
}
