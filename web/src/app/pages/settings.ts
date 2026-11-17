import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { LiveService } from '../core/live.service';
import { AlertRule, AlertType, Box, RestaurantSettings, formatDuration } from '../core/models';
import { SessionService } from '../core/session.service';

const RULES: Record<AlertType, { title: string; unit: string; sentence: (threshold: string) => string }> = {
  UNSERVED: {
    title: 'Clients non servis',
    unit: 'secondes',
    sentence: (t) => `L’équipe est prévenue quand des clients sont assis depuis ${t} sans rien sur la table.`,
  },
  TO_CLEAR: {
    title: 'Table à débarrasser',
    unit: 'secondes',
    sentence: (t) => `L’équipe est prévenue quand une table reste ${t} avec de la vaisselle après le départ des clients.`,
  },
  EXIT_BLOCKED: {
    title: 'Sortie de secours encombrée',
    unit: 'secondes',
    sentence: (t) => `L’équipe est prévenue quand un objet encombre une sortie de secours depuis ${t}.`,
  },
  QUEUE_LENGTH: {
    title: 'File d’attente',
    unit: 'personnes',
    sentence: (t) => `L’équipe est prévenue quand ${t} attendent au comptoir.`,
  },
};

/** Réglages du restaurant : à partir de quand l'équipe est prévenue. */
@Component({
  selector: 'app-settings',
  imports: [RouterLink],
  template: `
    <div class="page-head">
      <div>
        <h1>Réglages</h1>
        <p>{{ session?.restaurantName }}</p>
      </div>
    </div>
    @if (error()) { <p class="error notice">{{ error() }}</p> }

    <div class="section">
      <div class="about">
        <h2>Alertes</h2>
        <p class="muted">À partir de quand une table ou la file demande l’attention de l’équipe. Chaque réglage est enregistré dès qu’il change.</p>
        @if (!canEdit) { <p class="muted">Seul le gérant peut modifier ces réglages.</p> }
      </div>
      <div class="card panel">
        @for (rule of rules(); track rule.id) {
          <div class="rule">
            <label class="switch">
              <input type="checkbox" [checked]="rule.enabled" [disabled]="!canEdit" (change)="toggle(rule, $event)">
              <span>{{ info[rule.type].title }}</span>
            </label>
            <div class="threshold">
              <input class="num" type="number" min="1" [value]="rule.threshold" [disabled]="!canEdit || !rule.enabled"
                     (change)="threshold(rule, $event)" [attr.aria-label]="'Seuil : ' + info[rule.type].title">
              <span class="muted">{{ info[rule.type].unit }}</span>
            </div>
            <p class="muted small">{{ rule.enabled ? info[rule.type].sentence(readable(rule)) : 'Alerte désactivée.' }}</p>
          </div>
        } @empty {
          <p class="muted">Chargement des règles…</p>
        }
        @if (saved()) { <p class="faint small" role="status">Enregistré à {{ saved() }}</p> }
      </div>
    </div>

    <div class="section">
      <div class="about">
        <h2>Position du restaurant</h2>
        <p class="muted">Sert à la météo de la prévision d’affluence. Les coordonnées sont envoyées à Open-Meteo, un service météo gratuit ; rien d’autre ne l’est.</p>
      </div>
      <form class="card panel location" (submit)="saveLocation($event)">
        <div class="pair">
          <label>Latitude <input class="num" name="latitude" type="number" step="any" min="-90" max="90" [value]="place()?.latitude ?? ''" [disabled]="!canEdit" placeholder="ex. 48.8566"></label>
          <label>Longitude <input class="num" name="longitude" type="number" step="any" min="-180" max="180" [value]="place()?.longitude ?? ''" [disabled]="!canEdit" placeholder="ex. 2.3522"></label>
        </div>
        <p class="muted small">{{ place()?.latitude !== null && place()?.latitude !== undefined ? 'Météo prise en compte dans la prévision.' : 'Aucune position : la prévision se fait sans météo.' }} Laissez les deux champs vides pour ne plus envoyer la position.</p>
        @if (canEdit) { <button type="submit">Enregistrer la position</button> }
      </form>
    </div>

    <div class="section">
      <div class="about">
        <h2>Boîtiers</h2>
        <p class="muted">Le boîtier analyse la vidéo dans le restaurant et envoie un signal toutes les 30 secondes. Sans signal pendant 1 min 30, il est signalé hors ligne.</p>
      </div>
      <div class="card panel">
        @for (box of live.boxes() ?? []; track box.boxId) {
          <div class="box">
            <span class="dot" [attr.data-state]="box.online ? 'EMPTY' : 'ALERT'"></span>
            <div>
              <strong>{{ box.boxId }}</strong>
              <span class="muted small num">{{ boxDetail(box) }}</span>
            </div>
            <span class="num" [class.error]="!box.online">{{ box.online ? 'En ligne' : 'Hors ligne' }}</span>
            @if (updateNote(box); as note) { <p class="update small" [class.error]="box.updateError">{{ note }}</p> }
          </div>
        } @empty {
          <p class="muted">Aucun boîtier n’a encore donné signe de vie. Lancez-le avec <code>--publisher mqtt</code>.</p>
        }
      </div>
    </div>

    <div class="section">
      <div class="about">
        <h2>Caméra et zones</h2>
        <p class="muted">Les tables et la file sont dessinées sur l’image de la caméra. Le plan de salle les reprend telles quelles.</p>
      </div>
      <div class="card panel">
        <p>Pour ajouter une table ou corriger un contour, ouvrez l’outil de dessin depuis le poste du boîtier.</p>
        <a class="button" routerLink="/zones">Dessiner les zones</a>
      </div>
    </div>
  `,
  styles: `
    .notice { margin-bottom: 14px; }
    .section { display: grid; grid-template-columns: minmax(220px, 380px) minmax(0, 640px); gap: 32px; padding: 28px 0; border-top: 1px solid var(--border); }
    .section:first-of-type { border-top: 0; padding-top: 4px; }
    .about { display: grid; gap: 8px; align-content: start; }
    .panel { display: grid; gap: 4px; justify-items: start; }
    .panel > p { margin-bottom: 12px; }
    .rule { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 6px 16px; align-items: center; width: 100%; padding: 14px 0; border-top: 1px solid var(--rule); }
    .rule:first-child { border-top: 0; padding-top: 0; }
    .rule p { grid-column: 1 / -1; }
    .switch { display: inline-flex; align-items: center; gap: 12px; font-weight: 500; cursor: pointer; }
    /* Interrupteur : gris éteint, blanc allumé. */
    .switch input { appearance: none; width: 34px; height: 20px; border-radius: 10px; background: var(--control); border: 0; padding: 0; position: relative; cursor: pointer; flex: none; transition: background-color 120ms ease; }
    .switch input::after { content: ''; position: absolute; top: 3px; left: 3px; width: 14px; height: 14px; border-radius: 50%; background: var(--ground); transition: transform 120ms ease; }
    .switch input:checked { background: var(--text); }
    .switch input:checked::after { transform: translateX(14px); }
    .switch input:disabled { opacity: 0.5; cursor: default; }
    .location .pair { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; width: 100%; }
    .location label { display: grid; gap: 8px; font-weight: 500; }
    .location button { margin-top: 8px; }
    .box { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; align-items: center; gap: 12px; width: 100%; min-height: 56px; border-top: 1px solid var(--rule); }
    .box:first-child { border-top: 0; }
    .box div { display: grid; }
    .box .update { grid-column: 2 / -1; margin: -6px 0 10px; color: var(--muted); }
    .box .update.error { color: var(--alert); }
    .box strong { font-weight: 500; }
    .threshold { display: inline-flex; align-items: center; gap: 8px; }
    .threshold input { width: 84px; text-align: right; }
    .threshold input:disabled { opacity: 0.5; }
    @media (max-width: 800px) { .section { grid-template-columns: 1fr; gap: 16px; } }
  `,
})
export class SettingsPage implements OnInit {
  private readonly api = inject(ApiService);
  readonly session = inject(SessionService).session();
  readonly canEdit = this.session?.role !== 'STAFF';
  readonly info = RULES;

  readonly live = inject(LiveService);
  readonly rules = signal<AlertRule[]>([]);
  readonly place = signal<RestaurantSettings | null>(null);
  readonly error = signal<string | null>(null);
  /** Heure du dernier enregistrement réussi, pour le confirmer sans bouton. */
  readonly saved = signal<string | null>(null);

  ngOnInit(): void {
    this.api.restaurant().subscribe({ next: (place) => this.place.set(place), error: () => undefined });
    this.api.rules().subscribe({
      next: (rules) => this.rules.set(rules),
      error: () => this.error.set('Règles indisponibles. Vérifiez que le backend tourne.'),
    });
  }

  saveLocation(event: Event): void {
    event.preventDefault();
    const data = new FormData(event.target as HTMLFormElement);
    const read = (name: string) => (String(data.get(name) ?? '').trim() === '' ? null : Number(data.get(name)));
    this.api.updateLocation(read('latitude'), read('longitude')).subscribe({
      next: (place) => {
        this.place.set(place);
        this.saved.set(new Date().toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit', second: '2-digit' }));
        this.error.set(null);
      },
      error: (e) => this.error.set(e.error?.message || 'La position n’a pas pu être enregistrée.'),
    });
  }

  /** Où en est le boîtier par rapport à la dernière version publiée. */
  updateNote(box: Box): string | null {
    if (box.updateError) return `Mise à jour refusée : ${box.updateError}. Le boîtier garde sa version.`;
    if (!box.latestVersion || !box.version) return null;
    const newer = box.latestVersion.split('.').map(Number);
    const own = box.version.split('.').map(Number);
    const behind = newer.some((n, i) => n !== (own[i] ?? 0)) && newer.join('.') > own.join('.');
    return behind ? `Version ${box.latestVersion} publiée : installation au prochain signal du boîtier.` : 'À jour.';
  }

  /** « caméra · 4,8 images/s · version 0.2.0 · vu à 14:02 », ou depuis quand il se tait. */
  boxDetail(box: Box): string {
    const seen = new Date(box.lastSeen).toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' });
    if (!box.online) return `Dernier signal à ${seen}`;
    const parts = [box.source, box.fps === null ? null : `${box.fps.toLocaleString('fr-FR')} images/s`,
      box.version ? `version ${box.version}` : null, box.buffered ? `${box.buffered} messages en attente` : null];
    return parts.filter(Boolean).join(' · ');
  }

  /** Le seuil en clair : « 5 min », « 4 personnes ». */
  readable(rule: AlertRule): string {
    if (rule.type === 'QUEUE_LENGTH') return `${rule.threshold} ${rule.threshold > 1 ? 'personnes' : 'personne'}`;
    return formatDuration(rule.threshold);
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
        this.saved.set(new Date().toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit', second: '2-digit' }));
        this.error.set(null);
      },
      error: () => this.error.set('La règle n’a pas pu être enregistrée.'),
    });
  }
}
