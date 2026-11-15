import { Injectable, signal } from '@angular/core';
import { Session } from './models';

const KEY = 'restovision.session';

/** Session de l'utilisateur connecté, gardée dans le navigateur. */
@Injectable({ providedIn: 'root' })
export class SessionService {
  readonly session = signal<Session | null>(this.read());

  set(session: Session | null): void {
    this.session.set(session);
    try {
      if (session) localStorage.setItem(KEY, JSON.stringify(session));
      else localStorage.removeItem(KEY);
    } catch {
      // stockage indisponible : la session reste en mémoire
    }
  }

  private read(): Session | null {
    try {
      const raw = localStorage.getItem(KEY);
      return raw ? (JSON.parse(raw) as Session) : null;
    } catch {
      return null;
    }
  }
}
