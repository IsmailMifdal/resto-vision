import { Injectable, effect, inject, signal } from '@angular/core';
import { LiveService } from './live.service';
import { Alert } from './models';

const KEY = 'restovision.announcer';
/** Une alerte que personne n'a prise en charge est annoncée de nouveau après ce délai. */
const REMIND_AFTER_MS = 120_000;

interface Prefs {
  voice: boolean;
  notify: boolean;
}

/**
 * Alertes sans regarder l'écran : annonce vocale (dans l'oreillette reliée au téléphone), vibration,
 * et notification que la montre reprend. Réglé sur chaque appareil, rien n'est envoyé au serveur.
 */
@Injectable({ providedIn: 'root' })
export class AnnouncerService {
  private readonly live = inject(LiveService);
  /** Dernière annonce de chaque alerte encore ouverte. */
  private readonly announced = new Map<string, number>();
  private primed = false;

  readonly voice = signal(false);
  readonly notify = signal(false);
  readonly voiceSupported = typeof window !== 'undefined' && 'speechSynthesis' in window;
  readonly notifySupported = typeof window !== 'undefined' && 'Notification' in window;

  constructor() {
    const prefs = this.read();
    this.voice.set(prefs.voice && this.voiceSupported);
    this.notify.set(prefs.notify && this.notifySupported && Notification.permission === 'granted');

    effect(() => this.check(this.live.alerts()));
    // Le temps passe aussi sans nouvelle alerte : les rappels suivent l'horloge en direct.
    effect(() => {
      this.live.now();
      this.check(this.live.alerts());
    });
  }

  /** À appeler depuis un clic : les navigateurs ne parlent qu'après un geste de la personne. */
  toggleVoice(): void {
    const on = !this.voice();
    this.voice.set(on);
    this.save();
    if (on) this.speak('Annonces vocales activées.');
    else window.speechSynthesis?.cancel();
  }

  async toggleNotify(): Promise<void> {
    if (this.notify()) {
      this.notify.set(false);
      this.save();
      return;
    }
    const permission = await Notification.requestPermission();
    this.notify.set(permission === 'granted');
    this.save();
    if (permission === 'granted') void this.show('RestoVision', 'Les alertes de la salle arriveront ici.', 'restovision-test');
  }

  /** Ce qui est dit pour une alerte : court, sans abréviation, compréhensible dans le bruit d'une salle. */
  static sentence(alert: Alert, persons?: number): string {
    const table = /^T(\d+)$/i.exec(alert.zoneCode)?.[1];
    const name = table ? `Table ${table}` : alert.zoneCode;
    switch (alert.type) {
      case 'UNSERVED':
        return `${name} : clients pas encore servis.`;
      case 'TO_CLEAR':
        return `${name} à débarrasser.`;
      case 'QUEUE_LENGTH':
        return persons ? `File d'attente : ${persons} personnes.` : 'File d’attente trop longue.';
      case 'EXIT_BLOCKED':
        return 'Sortie de secours encombrée.';
    }
  }

  private check(alerts: Alert[]): void {
    const open = alerts.filter((alert) => !alert.acknowledgedAt);
    const openIds = new Set(open.map((alert) => alert.id));
    for (const id of [...this.announced.keys()]) if (!openIds.has(id)) this.announced.delete(id);
    // Au chargement, les alertes déjà là ne sont pas annoncées toutes d'un coup : seulement les suivantes.
    if (!this.primed) {
      if (this.live.connected()) {
        this.primed = true;
        open.forEach((alert) => this.announced.set(alert.id, Date.now()));
      }
      return;
    }
    const now = Date.now();
    for (const alert of open) {
      const last = this.announced.get(alert.id);
      if (last !== undefined && now - last < REMIND_AFTER_MS) continue;
      this.announced.set(alert.id, now);
      this.announce(alert, last !== undefined);
    }
  }

  private announce(alert: Alert, reminder: boolean): void {
    const persons = this.live.zones().find((zone) => zone.code === alert.zoneCode)?.persons;
    const text = (reminder ? 'Rappel. ' : '') + AnnouncerService.sentence(alert, persons);
    if (this.voice()) this.speak(text);
    if (this.voice() || this.notify()) navigator.vibrate?.([200, 100, 200]);
    if (this.notify()) void this.show(reminder ? 'Rappel' : 'Alerte en salle', text, alert.id);
  }

  private speak(text: string): void {
    const synth = window.speechSynthesis;
    if (!synth) return;
    const utterance = new SpeechSynthesisUtterance(text);
    utterance.lang = 'fr-FR';
    utterance.rate = 1.05;
    const french = synth.getVoices().find((voice) => voice.lang?.toLowerCase().startsWith('fr'));
    if (french) utterance.voice = french;
    synth.speak(utterance);
  }

  /** Sur téléphone, une notification passe par le service worker ; sur ordinateur, directement. */
  private async show(title: string, body: string, tag: string): Promise<void> {
    try {
      const registration = await navigator.serviceWorker?.getRegistration();
      if (registration) {
        await registration.showNotification(title, { body, tag, icon: 'favicon.ico' });
        return;
      }
      new Notification(title, { body, tag, icon: 'favicon.ico' });
    } catch {
      // Notifications refusées par l'appareil : la voix et l'écran restent.
    }
  }

  private read(): Prefs {
    try {
      return { voice: false, notify: false, ...JSON.parse(localStorage.getItem(KEY) ?? '{}') };
    } catch {
      return { voice: false, notify: false };
    }
  }

  private save(): void {
    try {
      localStorage.setItem(KEY, JSON.stringify({ voice: this.voice(), notify: this.notify() }));
    } catch {
      // stockage indisponible : le réglage vaut pour cette visite
    }
  }
}
