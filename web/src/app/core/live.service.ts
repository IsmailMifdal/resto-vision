import { Injectable, inject, signal } from '@angular/core';
import { Client } from '@stomp/stompjs';
import { ApiService } from './api.service';
import { WS_URL } from './config';
import { Alert, Box, Zone, upsertAlert, upsertZone } from './models';
import { SessionService } from './session.service';

/** État en direct du restaurant : chargé une fois par l'API, puis tenu à jour par WebSocket. */
@Injectable({ providedIn: 'root' })
export class LiveService {
  private readonly api = inject(ApiService);
  private readonly sessions = inject(SessionService);
  private client: Client | null = null;
  private timer: ReturnType<typeof setInterval> | null = null;

  readonly zones = signal<Zone[]>([]);
  readonly alerts = signal<Alert[]>([]);
  /** null tant que la liste n'est pas chargée. */
  readonly boxes = signal<Box[] | null>(null);
  readonly connected = signal(false);
  readonly error = signal<string | null>(null);
  /** Horloge qui avance chaque seconde, pour afficher les durées. */
  readonly now = signal(Date.now());

  start(): void {
    const session = this.sessions.session();
    if (!session || this.client) return;
    this.timer = setInterval(() => this.now.set(Date.now()), 1000);
    this.reload();

    const base = `/topic/restaurants/${session.restaurantCode}`;
    this.client = new Client({
      brokerURL: WS_URL,
      connectHeaders: { Authorization: `Bearer ${session.token}` },
      reconnectDelay: 3000,
      onConnect: () => {
        this.connected.set(true);
        this.reload(); // rattrape ce qui a changé pendant une coupure
        this.client?.subscribe(`${base}/zones`, (message) =>
          this.zones.update((zones) => upsertZone(zones, JSON.parse(message.body) as Zone)),
        );
        this.client?.subscribe(`${base}/boxes`, (message) => {
          const box = JSON.parse(message.body) as Box;
          this.boxes.update((boxes) => [...(boxes ?? []).filter((b) => b.boxId !== box.boxId), box]
            .sort((a, b) => a.boxId.localeCompare(b.boxId)));
        });
        this.client?.subscribe(`${base}/alerts`, (message) =>
          this.alerts.update((alerts) => upsertAlert(alerts, JSON.parse(message.body) as Alert)),
        );
      },
      onWebSocketClose: () => this.connected.set(false),
    });
    this.client.activate();
  }

  stop(): void {
    void this.client?.deactivate();
    this.client = null;
    if (this.timer) clearInterval(this.timer);
    this.timer = null;
    this.connected.set(false);
    this.zones.set([]);
    this.alerts.set([]);
    this.boxes.set(null);
  }

  acknowledge(alert: Alert): void {
    this.api.acknowledge(alert.id).subscribe({
      next: (updated) => this.alerts.update((alerts) => upsertAlert(alerts, updated)),
      error: () => this.error.set('Impossible de prendre l’alerte en charge.'),
    });
  }

  private reload(): void {
    this.api.zones().subscribe({
      next: (zones) => {
        this.zones.set(zones.reduce(upsertZone, [] as Zone[]));
        this.error.set(null);
      },
      error: () => this.error.set('Backend injoignable. Vérifie qu’il tourne sur le port 8080.'),
    });
    this.api.alerts().subscribe({ next: (alerts) => this.alerts.set(alerts), error: () => undefined });
    this.api.boxes().subscribe({ next: (boxes) => this.boxes.set(boxes), error: () => undefined });
  }
}
