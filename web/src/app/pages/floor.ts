import { Component, computed, inject } from '@angular/core';
import { LiveService } from '../core/live.service';
import { STATE_LABELS, Zone, formatDuration } from '../core/models';

/** Écran des employés : plan des tables en direct et alertes. */
@Component({
  selector: 'app-floor',
  template: `
    <h1>Salle <span class="status" [class.on]="live.connected()">{{ live.connected() ? 'en direct' : 'hors ligne' }}</span></h1>
    @if (live.error()) { <p class="error">{{ live.error() }}</p> }

    @if (live.alerts().length) {
      <section class="alerts">
        @for (alert of live.alerts(); track alert.id) {
          <div class="alert" [class.ack]="alert.acknowledgedAt">
            <span>{{ alert.message }}</span>
            @if (alert.acknowledgedAt) {
              <span class="muted">pris en charge</span>
            } @else {
              <button (click)="live.acknowledge(alert)">Je m’en occupe</button>
            }
          </div>
        }
      </section>
    }

    <h2>Tables</h2>
    @if (tables().length) {
      <div class="grid">
        @for (table of tables(); track table.code) {
          <div class="table" [attr.data-state]="table.state">
            <strong>{{ table.code }}</strong>
            <span>{{ label(table) }}</span>
            <span class="since">{{ since(table) }}</span>
            <span class="people">{{ table.persons }} pers.</span>
          </div>
        }
      </div>
    } @else {
      <p class="muted">Aucune table pour l’instant. Lance le boîtier ou le simulateur : les tables apparaissent au premier événement.</p>
    }

    @if (queues().length) {
      <h2>File d’attente</h2>
      <div class="grid">
        @for (queue of queues(); track queue.code) {
          <div class="card">
            <strong>{{ queue.code }}</strong>
            <div class="big">{{ queue.persons }}</div>
            <span class="muted">personnes, attente estimée {{ wait(queue) }}</span>
          </div>
        }
      </div>
    }
  `,
  styles: `
    .status { font-size: 0.75rem; font-weight: 500; padding: 0.15rem 0.5rem; border-radius: 99px; background: var(--clear); color: var(--clear-ink); vertical-align: middle; }
    .status.on { background: var(--empty); color: var(--empty-ink); }
    .alerts { display: grid; gap: 0.5rem; }
    .alert { display: flex; justify-content: space-between; align-items: center; gap: 1rem; padding: 0.7rem 1rem; border-radius: 10px; background: var(--clear); color: var(--clear-ink); font-weight: 600; }
    .alert.ack { background: var(--surface); border: 1px solid var(--border); color: var(--text); font-weight: 400; }
    .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); gap: 0.75rem; }
    .table { display: grid; gap: 0.15rem; padding: 0.9rem; border-radius: 12px; background: var(--empty); color: var(--empty-ink); }
    .table[data-state='WAITING'] { background: var(--waiting); color: var(--waiting-ink); }
    .table[data-state='SERVED'] { background: var(--served); color: var(--served-ink); }
    .table[data-state='TO_CLEAR'] { background: var(--clear); color: var(--clear-ink); }
    .table strong { font-size: 1.3rem; }
    .since, .people { font-size: 0.85rem; opacity: 0.85; }
    .big { font-size: 2.2rem; font-weight: 700; }
  `,
})
export class FloorPage {
  readonly live = inject(LiveService);
  readonly tables = computed(() => this.live.zones().filter((z) => z.type === 'TABLE'));
  readonly queues = computed(() => this.live.zones().filter((z) => z.type === 'QUEUE'));

  label(table: Zone): string {
    return table.state ? STATE_LABELS[table.state] : '';
  }

  since(table: Zone): string {
    if (!table.stateSince) return '';
    return `depuis ${formatDuration((this.live.now() - Date.parse(table.stateSince)) / 1000)}`;
  }

  wait(queue: Zone): string {
    return formatDuration(queue.estimatedWaitSeconds ?? 0);
  }
}
