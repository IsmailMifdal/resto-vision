import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { ApiService } from '../core/api.service';
import { AlertRule, RULE_LABELS, Stats, formatDuration } from '../core/models';
import { SessionService } from '../core/session.service';

/** Écran du gérant : indicateurs du jour et réglage des alertes. */
@Component({
  selector: 'app-dashboard',
  template: `
    <h1>Aujourd’hui</h1>
    @if (error()) { <p class="error">{{ error() }}</p> }
    @if (stats(); as s) {
      <div class="kpis">
        <div class="card"><span class="muted">Attente avant service</span><div class="big">{{ s.averageWaitSeconds === null ? '–' : duration(s.averageWaitSeconds) }}</div></div>
        <div class="card"><span class="muted">Occupation</span><div class="big">{{ percent(s.occupancyRate) }}</div><span class="muted">{{ s.tablesOccupied }} / {{ s.tablesTotal }} tables</span></div>
        <div class="card"><span class="muted">Tables installées</span><div class="big">{{ s.seatingsToday }}</div></div>
        <div class="card"><span class="muted">À débarrasser</span><div class="big">{{ s.tablesToClear }}</div></div>
        <div class="card"><span class="muted">File d’attente</span><div class="big">{{ s.queuePersons }}</div><span class="muted">attente {{ duration(s.queueWaitSeconds) }}</span></div>
      </div>
    }

    <h2>Règles d’alerte</h2>
    <div class="card rules">
      @for (rule of rules(); track rule.id) {
        <div class="rule">
          <label><input type="checkbox" [checked]="rule.enabled" [disabled]="!canEdit" (change)="toggle(rule, $event)"> {{ labels[rule.type].label }}</label>
          <span>
            <input type="number" min="1" [value]="rule.threshold" [disabled]="!canEdit" (change)="threshold(rule, $event)">
            <span class="muted">{{ labels[rule.type].unit }}</span>
          </span>
        </div>
      }
      @if (!canEdit) { <p class="muted">Seul le gérant peut modifier les règles.</p> }
    </div>
  `,
  styles: `
    .kpis { display: grid; grid-template-columns: repeat(auto-fill, minmax(170px, 1fr)); gap: 0.75rem; }
    .big { font-size: 1.9rem; font-weight: 700; }
    .rules { display: grid; gap: 0.7rem; }
    .rule { display: flex; justify-content: space-between; align-items: center; gap: 1rem; flex-wrap: wrap; }
    .rule input[type='number'] { width: 90px; }
    .rule input[type='checkbox'] { width: auto; }
    p { margin: 0; font-size: 0.85rem; }
  `,
})
export class DashboardPage implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);
  private timer: ReturnType<typeof setInterval> | null = null;

  readonly labels = RULE_LABELS;
  readonly canEdit = inject(SessionService).session()?.role !== 'STAFF';
  readonly stats = signal<Stats | null>(null);
  readonly rules = signal<AlertRule[]>([]);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.loadStats();
    this.timer = setInterval(() => this.loadStats(), 10_000);
    this.api.rules().subscribe({ next: (rules) => this.rules.set(rules), error: () => undefined });
  }

  ngOnDestroy(): void {
    if (this.timer) clearInterval(this.timer);
  }

  duration(seconds: number): string {
    return formatDuration(seconds);
  }

  percent(rate: number): string {
    return `${Math.round(rate * 100)} %`;
  }

  toggle(rule: AlertRule, event: Event): void {
    this.save({ ...rule, enabled: (event.target as HTMLInputElement).checked });
  }

  threshold(rule: AlertRule, event: Event): void {
    const value = Math.floor(Number((event.target as HTMLInputElement).value));
    if (value >= 1) this.save({ ...rule, threshold: value });
  }

  private save(rule: AlertRule): void {
    this.api.updateRule(rule).subscribe({
      next: (saved) => {
        this.rules.update((rules) => rules.map((r) => (r.id === saved.id ? saved : r)));
        this.error.set(null);
      },
      error: () => this.error.set('La règle n’a pas pu être enregistrée.'),
    });
  }

  private loadStats(): void {
    this.api.stats().subscribe({
      next: (stats) => {
        this.stats.set(stats);
        this.error.set(null);
      },
      error: () => this.error.set('Statistiques indisponibles.'),
    });
  }
}
