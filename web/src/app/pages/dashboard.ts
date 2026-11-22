import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { Bar, BarChart } from '../components/bar-chart';
import { ServiceTimeline } from '../components/service-timeline';
import { ApiService } from '../core/api.service';
import { Day, DaySummary, Forecast, Report, WasteDay, clockTime, formatWait } from '../core/models';
import { SessionService } from '../core/session.service';

const minutes = (seconds: number) =>
  seconds >= 7200 ? `${Math.round(seconds / 360) / 10} h` : `${Math.round(seconds / 60)} min`;
const percent = (rate: number) => `${Math.round(rate * 100)} %`;
const MINUTE_STEPS = [60, 120, 300, 600, 900, 1800];

/** Écran du gérant : comment s'est passé le service, table par table et heure par heure. */
@Component({
  selector: 'app-dashboard',
  imports: [BarChart, ServiceTimeline],
  template: `
    <div class="page-head">
      <div>
        <h1>Service</h1>
        <p>{{ finding() }}</p>
      </div>
      <div class="days" role="tablist" aria-label="Jour affiché">
        @for (item of days(); track item.date) {
          <button role="tab" [attr.aria-selected]="item.date === selected()" [class.on]="item.date === selected()"
                  (click)="select(item.date)">{{ dayLabel(item.date) }}</button>
        }
      </div>
    </div>
    @if (error()) { <p class="error notice">{{ error() }}</p> }

    @if (day(); as d) {
      <section class="card kpis" aria-label="Indicateurs du jour">
        <div class="lead">
          <span class="muted">Attente moyenne avant service</span>
          <strong class="num">{{ wait(d.averageWaitSeconds) }}</strong>
          <span class="faint small num">{{ weekAverage() }}</span>
        </div>
        <div>
          <span class="muted">Attente la plus longue</span>
          <strong class="num">{{ wait(d.longestWaitSeconds) }}</strong>
          <span class="faint small num">{{ d.longestWaitTable ? 'Table ' + d.longestWaitTable + ' à ' + time(d, d.longestWaitAt!) : 'Aucune attente mesurée' }}</span>
        </div>
        <div>
          <span class="muted">Tables installées</span>
          <strong class="num">{{ d.seatings }}</strong>
          <span class="faint small num">sur {{ d.tablesTotal }} tables</span>
        </div>
        <div>
          <span class="muted">Débarrassage moyen</span>
          <strong class="num">{{ wait(d.averageToClearSeconds) }}</strong>
          <span class="faint small">entre le départ et la table propre</span>
        </div>
        <div>
          <span class="muted">Pic d’occupation</span>
          <strong class="num">{{ d.hours.length ? percent(d.peakOccupancyRate) : '–' }}</strong>
          <span class="faint small num">{{ peakTables(d) }}</span>
        </div>
      </section>

      @if (report(); as r) {
        <section class="card report">
          <div class="card-head">
            <h2>Le rapport du jour</h2>
            <span class="muted small">{{ reportNote(r) }}</span>
          </div>
          <p>{{ r.text }}</p>
          @if (canRewrite && !r.partial) {
            <div class="actions">
              <button (click)="rewrite()" [disabled]="rewriting()">{{ rewriting() ? 'Rédaction…' : 'Rédiger à nouveau' }}</button>
              @if (mailAvailable()) {
                <button (click)="sendReport()" [disabled]="sending()">{{ sending() ? 'Envoi…' : 'Envoyer par e-mail' }}</button>
              }
              @if (sent()) { <span class="muted small" role="status">{{ sent() }}</span> }
            </div>
          }
        </section>
      }

      @if (waste(); as w) {
        <section class="card waste">
          <div class="card-head">
            <h2>Retours en cuisine</h2>
            <span class="muted small">Aliments reconnus : pizza, sandwich, gâteau, fruits, légumes</span>
          </div>
          @if (w.meals) {
            <div class="waste-row">
              <div class="figure">
                <strong class="num">{{ w.leftoverShare }} %</strong>
                <span class="muted small num">des repas ont laissé de la nourriture ({{ w.withLeftovers }} sur {{ w.meals }})</span>
                @if (w.previousWeekShare !== null) { <span class="faint small num">Moyenne des 7 jours précédents : {{ w.previousWeekShare }} %</span> }
              </div>
              <ul>
                @for (item of w.leftovers.slice(0, 6); track $index) {
                  <li class="num"><span>Table {{ item.table }}</span><span class="muted">{{ time(d, item.at) }}</span><span>{{ item.leftFood }} sur {{ item.servedFood }} plats</span></li>
                }
                @if (w.leftovers.length > 6) { <li class="muted small">et {{ w.leftovers.length - 6 }} autres</li> }
              </ul>
            </div>
          } @else {
            <p class="empty">Aucun repas observé ce jour-là : la mesure commence quand les clients quittent une table servie.</p>
          }
        </section>
      }

      <section class="card">
        <div class="card-head">
          <h2>Le service, table par table</h2>
          <ul class="legend">
            <li><span class="swatch" data-state="WAITING"></span>En attente d’être servie</li>
            <li><span class="swatch"></span>Servie</li>
            <li><span class="swatch" data-state="TO_CLEAR"></span>À débarrasser</li>
            @if (d.longestWaitTable) { <li><span class="swatch ring" data-state="WAITING"></span>Attente la plus longue</li> }
          </ul>
        </div>
        <app-service-timeline [day]="d" [today]="isToday()" />
      </section>

      <div class="row">
        <section class="card">
          <div class="card-head"><h2>Occupation par heure</h2><span class="muted small">part des tables occupées</span></div>
          @if (d.hours.length) {
            <app-bar-chart [bars]="occupancy()" [format]="percent" [steps]="[0.25]" [ceiling]="1" />
          } @else { <p class="empty">Pas de service ce jour-là.</p> }
        </section>
        <section class="card">
          <div class="card-head"><h2>Attente par heure</h2><span class="muted small">moyenne avant service</span></div>
          @if (d.hours.length) {
            <app-bar-chart [bars]="waits()" [format]="minutes" [steps]="minuteSteps" tone="waiting" />
          } @else { <p class="empty">Pas de service ce jour-là.</p> }
        </section>
        <section class="card">
          <div class="card-head"><h2>Attente moyenne, 7 jours</h2><span class="muted small">choisir un jour</span></div>
          <app-bar-chart [bars]="week()" [format]="minutes" [steps]="minuteSteps" tone="waiting"
                         [pickable]="true" [selected]="selectedIndex()" (pick)="select(days()[$event].date)" />
        </section>
      </div>
    } @else if (!error()) {
      <section class="card"><p class="muted">Chargement du service…</p></section>
    }

    @if (forecast(); as f) {
      <section class="card forecast">
        <div class="card-head">
          <h2>Prévision pour demain</h2>
          <span class="muted small">{{ forecastBasis(f) }}</span>
        </div>
        @if (f.hours.length) {
          <p>{{ forecastSentence(f) }}</p>
          <app-bar-chart [bars]="forecastBars()" [format]="percent" [steps]="[0.25]" [ceiling]="1" tone="forecast" />
        } @else {
          <p class="empty">Pas encore assez d’historique pour prévoir. La prévision apparaît après une première journée de service.</p>
        }
      </section>
    }
  `,
  styles: `
    .notice { margin-bottom: 14px; }
    .days { display: flex; gap: 4px; flex-wrap: wrap; }
    .days button { background: transparent; color: var(--muted); padding: 0 12px; height: 32px; }
    .days button:hover { color: var(--text); background: var(--surface); }
    .days button.on { background: var(--raised); color: var(--text); }

    /* Un seul bloc pour les indicateurs, séparés par un filet. Le premier répond à la question de l'écran. */
    .kpis { display: grid; grid-template-columns: 1.25fr repeat(4, 1fr); padding: 0; margin-bottom: var(--gap); }
    .kpis > div { display: grid; gap: 2px; align-content: start; padding: 18px var(--pad); border-left: 1px solid var(--rule); min-width: 0; }
    .kpis > div:first-child { border-left: 0; }
    .kpis strong { font-size: 24px; line-height: 32px; font-weight: 600; }
    .kpis .lead strong { font-size: 32px; line-height: 38px; }
    .kpis .small { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }

    .forecast { margin-top: var(--gap); }
    .waste { margin-bottom: var(--gap); }
    .waste-row { display: grid; grid-template-columns: minmax(220px, 300px) minmax(0, 1fr); gap: 32px; align-items: start; }
    .waste .figure { display: grid; gap: 2px; }
    .waste .figure strong { font-size: 24px; line-height: 32px; font-weight: 600; }
    .waste ul { list-style: none; margin: 0; padding: 0; columns: 2; column-gap: 32px; }
    .waste li { display: grid; grid-template-columns: 70px 52px auto; gap: 10px; min-height: 30px; align-items: center; break-inside: avoid; }
    @media (max-width: 760px) { .waste-row { grid-template-columns: 1fr; gap: 16px; } .waste ul { columns: 1; } }
    .forecast app-bar-chart { display: block; max-width: 680px; }
    .forecast > p:not(.empty) { margin-bottom: 20px; max-width: 86ch; }
    .report { margin-bottom: var(--gap); }
    .report p { max-width: 86ch; font-size: 16px; line-height: 26px; }
    .report .actions { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-top: 16px; }

    .legend { display: flex; flex-wrap: wrap; gap: 6px 18px; list-style: none; margin: 0; padding: 0; color: var(--muted); font-size: 12px; }
    .legend li { display: inline-flex; align-items: center; gap: 7px; white-space: nowrap; }
    .swatch { width: 18px; height: 10px; border-radius: 3px; background: #3d3a36; }
    .swatch[data-state='WAITING'] { background: var(--waiting); }
    .swatch[data-state='TO_CLEAR'] { background: var(--clear); }
    .swatch.ring { box-shadow: 0 0 0 1px var(--surface), 0 0 0 2.5px var(--text); margin: 0 2px; }

    .row { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: var(--gap); margin-top: var(--gap); }
    .row .card-head { margin-bottom: 20px; }

    @media (max-width: 1180px) {
      .kpis { grid-template-columns: repeat(2, 1fr); }
      .kpis .lead { grid-column: 1 / -1; }
      .kpis > div { border-left: 0; border-top: 1px solid var(--rule); }
      .kpis > div:nth-child(odd):not(.lead) { border-left: 1px solid var(--rule); }
      .kpis .lead { border-top: 0; }
      .row { grid-template-columns: 1fr; }
    }
  `,
})
export class DashboardPage implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);
  private timer: ReturnType<typeof setInterval> | null = null;

  readonly percent = percent;
  readonly minutes = minutes;
  readonly minuteSteps = MINUTE_STEPS;

  readonly days = signal<DaySummary[]>([]);
  readonly selected = signal<string | null>(null);
  readonly day = signal<Day | null>(null);
  readonly report = signal<Report | null>(null);
  readonly waste = signal<WasteDay | null>(null);
  readonly forecast = signal<Forecast | null>(null);
  readonly rewriting = signal(false);
  readonly mailAvailable = signal(false);
  readonly sending = signal(false);
  readonly sent = signal<string | null>(null);
  readonly canRewrite = inject(SessionService).session()?.role !== 'STAFF';
  readonly error = signal<string | null>(null);

  readonly selectedIndex = computed(() => {
    const index = this.days().findIndex((item) => item.date === this.selected());
    return index < 0 ? null : index;
  });
  readonly isToday = computed(() => {
    const days = this.days();
    return days.length > 0 && days[days.length - 1].date === this.selected();
  });

  /** La phrase du titre : le jour, puis ce qui a le plus pesé sur le service. */
  readonly finding = computed(() => {
    const day = this.day();
    if (!day) return '';
    const date = new Date(day.date + 'T12:00:00').toLocaleDateString('fr-FR', { weekday: 'long', day: 'numeric', month: 'long' });
    const title = this.isToday() ? `Aujourd’hui, ${date}` : date.charAt(0).toUpperCase() + date.slice(1);
    if (!day.hours.length) return `${title}. Aucun service enregistré.`;
    if (day.longestWaitSeconds === null) return `${title}. Aucune attente mesurée pour l’instant.`;
    return `${title}. Attente la plus longue : ${formatWait(day.longestWaitSeconds)}, table ${day.longestWaitTable} à ${clockTime(day.from, day.longestWaitAt!)}.`;
  });

  /** La moyenne des 7 jours, pour situer le jour affiché. */
  readonly weekAverage = computed(() => {
    const measured = this.days().map((item) => item.averageWaitSeconds).filter((value): value is number => value !== null);
    if (!measured.length) return 'Pas encore de moyenne sur 7 jours';
    return `Moyenne des 7 jours : ${formatWait(Math.round(measured.reduce((a, b) => a + b, 0) / measured.length))}`;
  });

  readonly occupancy = computed<Bar[]>(() =>
    (this.day()?.hours ?? []).map((hour) => ({
      label: `${hour.hour} h`,
      value: hour.occupancyRate,
      title: `${hour.hour} h – ${hour.hour + 1} h`,
    })),
  );
  readonly waits = computed<Bar[]>(() =>
    (this.day()?.hours ?? []).map((hour) => ({
      label: `${hour.hour} h`,
      value: hour.averageWaitSeconds,
      title: `Clients installés entre ${hour.hour} h et ${hour.hour + 1} h`,
    })),
  );
  readonly forecastBars = computed<Bar[]>(() =>
    (this.forecast()?.hours ?? []).map((hour) => ({
      label: `${hour.hour} h`,
      value: hour.expectedOccupancyRate,
      title: `${hour.hour} h – ${hour.hour + 1} h · environ ${Math.round(hour.expectedSeatings)} tablées`,
    })),
  );
  readonly week = computed<Bar[]>(() =>
    this.days().map((item) => ({
      label: this.dayLabel(item.date, true),
      value: item.averageWaitSeconds,
      title: `${this.dayLabel(item.date)} · ${item.seatings} tables installées`,
    })),
  );

  ngOnInit(): void {
    this.loadWeek(true);
    this.api.mailAvailable().subscribe({ next: (mail) => this.mailAvailable.set(mail.available), error: () => undefined });
    this.api.forecast().subscribe({ next: (forecast) => this.forecast.set(forecast), error: () => undefined });
    // La journée en cours avance : on la relit toutes les 30 secondes.
    this.timer = setInterval(() => {
      if (this.isToday()) this.loadWeek(false);
    }, 30_000);
  }

  ngOnDestroy(): void {
    if (this.timer) clearInterval(this.timer);
  }

  select(date: string): void {
    this.selected.set(date);
    this.sent.set(null);
    this.loadDay(date);
  }

  forecastSentence(forecast: Forecast): string {
    const day = new Date(forecast.date + 'T12:00:00').toLocaleDateString('fr-FR', { weekday: 'long', day: 'numeric', month: 'long' });
    const peak = forecast.hours.find((hour) => hour.hour === forecast.peakHour);
    const busiest = peak ? `, avec un pic vers ${peak.hour} h (${percent(peak.expectedOccupancyRate)} des tables occupées)` : '';
    const weather = forecast.weather
      ? ` Météo : ${forecast.weather.rainy ? 'pluie' : 'sec'}, ${Math.round(forecast.weather.maxTemperature)} °C au plus chaud.`
      : '';
    return `${day.charAt(0).toUpperCase() + day.slice(1)} : environ ${forecast.expectedSeatings} tablées attendues${busiest}.${weather}`;
  }

  /** Sur quoi repose la prévision, pour qu'on sache quel crédit lui donner. */
  forecastBasis(forecast: Forecast): string {
    if (!forecast.basedOnDays) return 'Estimation';
    const days = `${forecast.basedOnDays} ${forecast.basedOnDays > 1 ? 'jours' : 'jour'} d’historique`;
    const same = forecast.sameWeekdayDays ? `, dont ${forecast.sameWeekdayDays} même jour de la semaine` : '';
    const weather = forecast.weather
      ? forecast.similarWeatherDays
        ? ` Les ${forecast.similarWeatherDays} jours à la météo semblable comptent double.`
        : ' Aucun jour passé à la météo semblable.'
      : ' Sans météo : position du restaurant à saisir dans Réglages.';
    return `Estimation sur ${days}${same}.${weather} Sans réservations.`;
  }

  /** D'où vient le texte, pour que le gérant sache ce qu'il lit. */
  reportNote(report: Report): string {
    if (report.partial) return 'Journée en cours, mis à jour à chaque lecture';
    if (report.source === 'AI') return 'Rédigé par IA à partir des chiffres ci-dessous';
    return report.aiAvailable ? 'Rédigé sans IA' : 'Rédigé sans IA : aucune clé d’API configurée';
  }

  sendReport(): void {
    const date = this.selected();
    if (!date) return;
    this.sending.set(true);
    this.sent.set(null);
    this.api.sendReport(date).subscribe({
      next: (result) => {
        this.sending.set(false);
        this.sent.set(result.sentTo.length ? `Envoyé à ${result.sentTo.join(', ')}` : 'Aucun gérant à qui l’envoyer.');
      },
      error: (e) => {
        this.sending.set(false);
        this.error.set(e.error?.message || 'Le rapport n’a pas pu être envoyé.');
      },
    });
  }

  rewrite(): void {
    const date = this.selected();
    if (!date) return;
    this.rewriting.set(true);
    this.api.rewriteReport(date).subscribe({
      next: (report) => {
        if (this.selected() === date) this.report.set(report);
        this.rewriting.set(false);
      },
      error: () => {
        this.rewriting.set(false);
        this.error.set('Le rapport n’a pas pu être rédigé à nouveau.');
      },
    });
  }

  wait(seconds: number | null): string {
    return formatWait(seconds);
  }

  time(day: Day, instant: string): string {
    return clockTime(day.from, instant);
  }

  peakTables(day: Day): string {
    if (!day.hours.length) return 'Aucune table occupée';
    const tables = Math.round(day.peakOccupancyRate * day.tablesTotal);
    return `${tables} ${tables > 1 ? 'tables' : 'table'} sur ${day.tablesTotal} en même temps`;
  }

  /** « sam. 14 », ou « Aujourd’hui » pour le dernier jour. `short` : sans le nom du jour courant. */
  dayLabel(date: string, short = false): string {
    const days = this.days();
    if (days.length && days[days.length - 1].date === date) return short ? 'Auj.' : 'Aujourd’hui';
    const parsed = new Date(date + 'T12:00:00');
    const weekday = parsed.toLocaleDateString('fr-FR', { weekday: 'short' });
    return short ? weekday : `${weekday} ${parsed.getDate()}`;
  }

  private loadWeek(first: boolean): void {
    this.api.days(7).subscribe({
      next: (days) => {
        this.days.set(days);
        this.error.set(null);
        if (first && days.length) {
          // On ouvre sur aujourd'hui s'il y a déjà eu un service, sinon sur le dernier jour qui en a un.
          const withService = [...days].reverse().find((item) => item.seatings > 0);
          this.select((withService ?? days[days.length - 1]).date);
        } else if (this.selected()) {
          this.loadDay(this.selected()!);
        }
      },
      error: () => this.error.set('Historique indisponible. Vérifiez que le backend tourne.'),
    });
  }

  private loadDay(date: string): void {
    this.api.waste(date).subscribe({
      next: (waste) => {
        if (this.selected() === date) this.waste.set(waste);
      },
      error: () => this.waste.set(null),
    });
    this.api.report(date).subscribe({
      next: (report) => {
        if (this.selected() === date) this.report.set(report);
      },
      error: () => this.report.set(null),
    });
    this.api.day(date).subscribe({
      next: (day) => {
        if (this.selected() === date) this.day.set(day);
        this.error.set(null);
      },
      error: () => this.error.set('Cette journée n’a pas pu être chargée.'),
    });
  }
}
