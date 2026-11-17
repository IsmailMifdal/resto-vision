import { Component, computed, effect, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AnnouncerService } from './core/announcer.service';
import { LiveService } from './core/live.service';
import { SessionService } from './core/session.service';

const ROLES: Record<string, string> = { MANAGER: 'Gérant', STAFF: 'Équipe', ADMIN: 'Administrateur' };

@Component({
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  selector: 'app-root',
  styleUrl: './app.css',
  templateUrl: './app.html',
})
export class App {
  private readonly router = inject(Router);
  protected readonly live = inject(LiveService);
  /** Démarré ici pour annoncer les alertes quel que soit l'écran ouvert. */
  private readonly announcer = inject(AnnouncerService);
  protected readonly sessions = inject(SessionService);

  /** L'état de la salle en une ligne, visible depuis tous les écrans. */
  protected readonly summary = computed(() => {
    const zones = this.live.zones();
    const tables = zones.filter((zone) => zone.type === 'TABLE');
    if (!tables.length) return null;
    return {
      tables: tables.length,
      occupied: tables.filter((t) => t.state === 'WAITING' || t.state === 'SERVED').length,
      waiting: tables.filter((t) => t.state === 'WAITING').length,
      toClear: tables.filter((t) => t.state === 'TO_CLEAR').length,
      queue: zones.filter((zone) => zone.type === 'QUEUE').reduce((sum, queue) => sum + queue.persons, 0),
    };
  });
  /** Le boîtier silencieux le plus ancien : tant qu'il se tait, l'état des tables affiché n'est plus à jour. */
  protected readonly silentBox = computed(() => {
    const offline = (this.live.boxes() ?? []).filter((box) => !box.online);
    if (!offline.length) return null;
    const oldest = offline.reduce((a, b) => (a.lastSeen <= b.lastSeen ? a : b));
    const minutes = Math.max(1, Math.floor((this.live.now() - Date.parse(oldest.lastSeen)) / 60000));
    return minutes < 60 ? `${minutes} min` : `${Math.floor(minutes / 60)} h ${String(minutes % 60).padStart(2, '0')}`;
  });
  protected readonly openAlerts = computed(() => this.live.alerts().filter((alert) => !alert.acknowledgedAt).length);
  protected readonly clock = computed(() =>
    new Date(this.live.now()).toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' }),
  );

  constructor() {
    // La connexion en direct suit la session : ouverte à la connexion, fermée à la déconnexion.
    effect(() => {
      if (this.sessions.session()) this.live.start();
      else this.live.stop();
    });
  }

  protected role(role: string): string {
    return ROLES[role] ?? role;
  }

  protected logout(): void {
    this.sessions.set(null);
    void this.router.navigateByUrl('/login');
  }
}
