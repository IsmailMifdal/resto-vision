import { Component, computed, inject } from '@angular/core';
import { LiveService } from '../core/live.service';
import { Alert, STATE_LABELS, TableState, Zone, formatDuration } from '../core/models';

const STATES: TableState[] = ['EMPTY', 'WAITING', 'SERVED', 'TO_CLEAR'];
const ALERT_LABELS: Record<Alert['type'], string> = {
  UNSERVED: 'Non servis',
  TO_CLEAR: 'À débarrasser',
  QUEUE_LENGTH: 'File trop longue',
};

interface Placed {
  zone: Zone;
  points: string;
  /** Centre de la zone, en pourcentage du plan. */
  left: number;
  top: number;
  alert: boolean;
}

/** Écran de l'équipe : le plan de salle en direct et ce qu'il y a à faire maintenant. */
@Component({
  selector: 'app-floor',
  template: `
    <div class="page-head">
      <div>
        <h1>Salle</h1>
        <p>{{ finding() }}</p>
      </div>
    </div>
    @if (live.error()) { <p class="error notice">{{ live.error() }}</p> }

    <div class="layout">
      <section class="card room" aria-label="Plan de salle">
        <div class="card-head">
          <h2>Plan de salle</h2>
          <ul class="legend">
            @for (state of states; track state) {
              <li><span class="dot" [attr.data-state]="state"></span>{{ labels[state] }} <span class="num muted">{{ counts()[state] }}</span></li>
            }
          </ul>
        </div>

        @if (plan(); as p) {
          <div class="plan" [style.aspect-ratio]="p.width + ' / ' + p.height">
            <svg [attr.viewBox]="p.viewBox" preserveAspectRatio="none" aria-hidden="true">
              @for (item of p.items; track item.zone.code) {
                <polygon [attr.points]="item.points" [attr.data-type]="item.zone.type"
                         [attr.data-state]="item.zone.state" [class.alert]="item.alert" />
              }
            </svg>
            @for (item of p.items; track item.zone.code) {
              <div class="tag" [style.left.%]="item.left" [style.top.%]="item.top" [class.alert]="item.alert"
                   [attr.data-state]="item.zone.state">
                @if (item.zone.type === 'TABLE') {
                  <strong>{{ item.zone.code }}</strong>
                  <span class="state">{{ item.alert ? alertWord(item.zone) : label(item.zone) }}</span>
                  <span class="num meta">{{ elapsed(item.zone) }}@if (item.zone.persons) { · {{ item.zone.persons }} pers.}</span>
                } @else {
                  <strong>File</strong>
                  <span class="num people">{{ item.zone.persons }}</span>
                  <span class="num meta">{{ item.zone.persons ? 'attente ' + wait(item.zone) : 'personne' }}</span>
                }
              </div>
            }
          </div>
        }

        <!-- Sans plan (boîtier pas encore connecté) ou sur petit écran : une tuile par table. -->
        <div class="tiles" [class.only]="!plan()">
          @for (table of tables(); track table.code) {
            <div class="tile" [attr.data-state]="table.state" [class.alert]="hasAlert(table)">
              <strong>{{ table.code }}</strong>
              <span class="state">{{ hasAlert(table) ? alertWord(table) : label(table) }}</span>
              <span class="num meta">{{ since(table) }}@if (table.persons) { · {{ table.persons }} pers.}</span>
            </div>
          } @empty {
            <p class="empty">Aucune table pour l’instant. Lancez le boîtier ou le simulateur : les tables apparaissent au premier événement.</p>
          }
        </div>
      </section>

      <div class="side">
        <section class="card">
          <div class="card-head">
            <h2>À faire</h2>
            @if (live.alerts().length) { <span class="num muted">{{ live.alerts().length }}</span> }
          </div>
          <ul class="todo">
            @for (alert of todo(); track alert.id) {
              <li [class.done]="alert.acknowledgedAt">
                <span class="dot" [attr.data-state]="alert.acknowledgedAt ? 'SERVED' : 'ALERT'"></span>
                <div>
                  <strong>{{ alert.zoneCode }} · {{ alertLabels[alert.type] }}</strong>
                  <span class="num small faint">{{ alertDetail(alert) }}</span>
                </div>
                @if (alert.acknowledgedAt) {
                  <span class="small faint">Pris en charge</span>
                } @else {
                  <button [class.primary]="alert.id === firstOpen()" (click)="live.acknowledge(alert)">Je m’en occupe</button>
                }
              </li>
            } @empty {
              <li class="empty">Rien à signaler. Une table qui attend trop longtemps apparaît ici.</li>
            }
          </ul>
        </section>

        @for (queue of queues(); track queue.code) {
          <section class="card queue">
            <div class="card-head">
              <h2>File d’attente</h2>
              <span class="muted">{{ queue.code }}</span>
            </div>
            <div class="figure num">{{ queue.persons }} <span>{{ queue.persons > 1 ? 'personnes' : 'personne' }}</span></div>
            <p class="muted num">{{ queue.persons ? 'Attente estimée : ' + wait(queue) : 'Personne n’attend au comptoir.' }}</p>
          </section>
        }
      </div>
    </div>
  `,
  styles: `
    .notice { margin-bottom: 14px; }
    .layout { display: grid; grid-template-columns: minmax(0, 1fr) 340px; gap: var(--gap); align-items: start; }
    .side { display: grid; gap: var(--gap); }
    .legend { display: flex; flex-wrap: wrap; gap: 6px 18px; list-style: none; margin: 0; padding: 0; color: var(--muted); }
    .legend li { display: inline-flex; align-items: center; gap: 7px; white-space: nowrap; }

    /* Le plan reprend les contours dessinés sur l'image de la caméra. */
    .plan { position: relative; width: 100%; background: var(--ground); border-radius: 14px; overflow: hidden; }
    .plan svg { position: absolute; inset: 0; width: 100%; height: 100%; }
    polygon { fill: transparent; stroke: var(--free); stroke-width: 1.5; stroke-linejoin: round; vector-effect: non-scaling-stroke; transition: fill 300ms ease, stroke 300ms ease; }
    polygon[data-state='WAITING'] { fill: var(--waiting-bg); stroke: var(--waiting); }
    polygon[data-state='SERVED'] { fill: var(--served-bg); stroke: var(--served-bg); }
    polygon[data-state='TO_CLEAR'] { fill: var(--clear-bg); stroke: var(--clear); }
    polygon[data-type='QUEUE'] { fill: transparent; stroke: var(--control); stroke-dasharray: 5 5; }
    polygon.alert { fill: var(--alert-bg); stroke: var(--alert); stroke-width: 2.5; }

    .tag { position: absolute; transform: translate(-50%, -50%); display: grid; justify-items: center; text-align: center; line-height: 17px; white-space: nowrap; pointer-events: none; }
    .tag strong, .tile strong { font-size: 15px; font-weight: 600; }
    .state { font-size: 12px; color: var(--free); }
    [data-state='WAITING'] > .state { color: var(--waiting); }
    [data-state='SERVED'] > .state { color: var(--muted); }
    [data-state='TO_CLEAR'] > .state { color: var(--clear); }
    .alert > .state { color: var(--alert); font-weight: 600; }
    .meta { font-size: 12px; color: var(--faint); }
    .people { font-size: 24px; line-height: 30px; font-weight: 600; }

    .tiles { display: none; grid-template-columns: repeat(auto-fill, minmax(140px, 1fr)); gap: 8px; }
    .tiles.only { display: grid; }
    .tiles .empty { grid-column: 1 / -1; }
    .tile { display: grid; gap: 1px; padding: 12px 14px; border-radius: 12px; box-shadow: inset 0 0 0 1.5px var(--free); }
    .tile[data-state='WAITING'] { background: var(--waiting-bg); box-shadow: inset 0 0 0 1.5px var(--waiting); }
    .tile[data-state='SERVED'] { background: var(--served-bg); box-shadow: none; }
    .tile[data-state='TO_CLEAR'] { background: var(--clear-bg); box-shadow: inset 0 0 0 1.5px var(--clear); }
    .tile.alert { background: var(--alert-bg); box-shadow: inset 0 0 0 2.5px var(--alert); }

    .todo { list-style: none; margin: 0; padding: 0; display: grid; }
    .todo li:not(.empty) { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; align-items: center; gap: 12px; min-height: 60px; border-top: 1px solid var(--rule); }
    .todo li:first-child { border-top: 0; }
    .todo div { display: grid; }
    .todo strong { font-weight: 500; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .todo .done strong { color: var(--muted); font-weight: 400; }

    .figure { font-size: 32px; line-height: 38px; font-weight: 600; }
    .figure span { font-size: 14px; font-weight: 400; color: var(--muted); }

    /* Écran étroit : ce qu'il y a à faire passe avant le plan. */
    @media (max-width: 1180px) {
      .layout { grid-template-columns: minmax(0, 1fr); }
      .side { order: -1; }
    }
    @media (max-width: 760px) {
      .plan { display: none; }
      .tiles { display: grid; }
    }
  `,
})
export class FloorPage {
  readonly live = inject(LiveService);
  readonly states = STATES;
  readonly labels = STATE_LABELS;
  readonly alertLabels = ALERT_LABELS;

  readonly tables = computed(() => this.live.zones().filter((z) => z.type === 'TABLE'));
  readonly queues = computed(() => this.live.zones().filter((z) => z.type === 'QUEUE'));
  readonly counts = computed(() => {
    const counts: Record<TableState, number> = { EMPTY: 0, WAITING: 0, SERVED: 0, TO_CLEAR: 0 };
    for (const table of this.tables()) if (table.state) counts[table.state]++;
    return counts;
  });
  /** Zones dont une alerte n'a pas encore été prise en charge. */
  private readonly alerting = computed(
    () => new Set(this.live.alerts().filter((a) => !a.acknowledgedAt).map((a) => a.zoneCode)),
  );
  /** L'alerte la plus ancienne non prise en charge : c'est elle qui porte le bouton principal. */
  readonly firstOpen = computed(() => {
    const open = this.live.alerts().filter((a) => !a.acknowledgedAt);
    return open.length ? open.reduce((a, b) => (a.createdAt <= b.createdAt ? a : b)).id : null;
  });

  /** Le plan : chaque zone à sa place dans l'image de la caméra. Null si aucun contour n'est connu. */
  readonly plan = computed(() => {
    const placed = this.live.zones().filter((zone) => zone.polygon && zone.polygon.length >= 3);
    if (!placed.length) return null;
    const xs = placed.flatMap((zone) => zone.polygon!.map((p) => p[0]));
    const ys = placed.flatMap((zone) => zone.polygon!.map((p) => p[1]));
    const pad = 28;
    const minX = Math.min(...xs) - pad, minY = Math.min(...ys) - pad;
    const width = Math.max(...xs) + pad - minX, height = Math.max(...ys) + pad - minY;
    const items: Placed[] = placed.map((zone) => {
      const px = zone.polygon!.map((p) => p[0]), py = zone.polygon!.map((p) => p[1]);
      return {
        zone,
        points: zone.polygon!.map((p) => p.join(',')).join(' '),
        left: (((Math.min(...px) + Math.max(...px)) / 2 - minX) / width) * 100,
        top: (((Math.min(...py) + Math.max(...py)) / 2 - minY) / height) * 100,
        alert: this.alerting().has(zone.code),
      };
    });
    return { viewBox: `${minX} ${minY} ${width} ${height}`, width, height, items };
  });

  /** Ce qui demande de l'attention en premier, en une phrase. */
  readonly finding = computed(() => {
    const tables = this.tables();
    if (!tables.length) return 'En attente du boîtier : aucune table reçue pour l’instant.';
    const open = this.live.alerts().filter((a) => !a.acknowledgedAt);
    if (open.length) {
      const zones = [...new Set(open.map((a) => a.zoneCode))].join(', ');
      return open.length === 1 ? `1 alerte à traiter : ${zones}.` : `${open.length} alertes à traiter : ${zones}.`;
    }
    const waiting = tables.filter((t) => t.state === 'WAITING' && t.stateSince);
    if (waiting.length) {
      const oldest = waiting.reduce((a, b) => (a.stateSince! <= b.stateSince! ? a : b));
      const noun = waiting.length === 1 ? '1 table attend d’être servie' : `${waiting.length} tables attendent d’être servies`;
      return `${noun}, la plus ancienne (${oldest.code}) ${this.since(oldest)}.`;
    }
    const counts = this.counts();
    if (counts.TO_CLEAR) return counts.TO_CLEAR === 1 ? '1 table à débarrasser.' : `${counts.TO_CLEAR} tables à débarrasser.`;
    return `Rien à signaler : ${counts.SERVED} ${counts.SERVED > 1 ? 'tables servies' : 'table servie'}, ${counts.EMPTY} ${counts.EMPTY > 1 ? 'libres' : 'libre'}.`;
  });

  /** À traiter d'abord, de la plus ancienne à la plus récente ; puis ce qui est déjà pris en charge. */
  readonly todo = computed(() =>
    [...this.live.alerts()].sort(
      (a, b) => Number(!!a.acknowledgedAt) - Number(!!b.acknowledgedAt) || a.createdAt.localeCompare(b.createdAt),
    ),
  );

  hasAlert(zone: Zone): boolean {
    return this.alerting().has(zone.code);
  }

  label(table: Zone): string {
    return table.state ? STATE_LABELS[table.state] : '';
  }

  /** Mot affiché sur une table en alerte : ce qui ne va pas, pas seulement son état. */
  alertWord(table: Zone): string {
    return table.state === 'TO_CLEAR' ? 'À débarrasser' : 'Non servis';
  }

  since(table: Zone): string {
    if (!table.stateSince) return '';
    return `depuis ${formatDuration((this.live.now() - Date.parse(table.stateSince)) / 1000)}`;
  }

  elapsed(table: Zone): string {
    return table.stateSince ? formatDuration((this.live.now() - Date.parse(table.stateSince)) / 1000) : '';
  }

  wait(queue: Zone): string {
    return formatDuration(queue.estimatedWaitSeconds ?? 0);
  }

  /** Durée en direct de ce que l'alerte signale. */
  alertDetail(alert: Alert): string {
    const zone = this.live.zones().find((z) => z.code === alert.zoneCode);
    if (alert.type === 'QUEUE_LENGTH') return zone ? `${zone.persons} personnes au comptoir` : '';
    const start = zone?.stateSince ?? alert.createdAt;
    return `depuis ${formatDuration((this.live.now() - Date.parse(start)) / 1000)}`;
  }
}
