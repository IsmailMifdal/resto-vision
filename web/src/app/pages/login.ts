import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { ApiService } from '../core/api.service';
import { SessionService } from '../core/session.service';

@Component({
  selector: 'app-login',
  template: `
    <div class="stage">
      <form class="card login" (submit)="submit($event)">
        <div class="brand">
          <svg viewBox="0 0 28 28" width="32" height="32" aria-hidden="true">
            <path d="M3 9V5a2 2 0 0 1 2-2h4M19 3h4a2 2 0 0 1 2 2v4M25 19v4a2 2 0 0 1-2 2h-4M9 25H5a2 2 0 0 1-2-2v-4" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
            <circle cx="14" cy="14" r="5" fill="var(--free)"/>
          </svg>
          <h1>RestoVision</h1>
        </div>
        <p class="muted">La salle en direct, vue par vos caméras.</p>
        <label>Email
          <input type="email" autocomplete="username" [value]="email()" (input)="email.set(value($event))" required>
        </label>
        <label>Mot de passe
          <input type="password" autocomplete="current-password" [value]="password()" (input)="password.set(value($event))" required>
        </label>
        @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
        <button class="primary" type="submit" [disabled]="loading()">Se connecter</button>
        <p class="faint small">Compte de démonstration : demo&#64;restovision.local / demo1234</p>
      </form>
    </div>
  `,
  styles: `
    .stage { min-height: 100vh; display: grid; place-items: center; padding: 16px; }
    .login { width: 100%; max-width: 380px; display: grid; gap: 14px; padding: 28px; }
    .brand { display: flex; align-items: center; gap: 12px; }
    label { display: grid; gap: 8px; font-weight: 500; }
    button { height: 40px; margin-top: 4px; }
  `,
})
export class LoginPage {
  private readonly api = inject(ApiService);
  private readonly sessions = inject(SessionService);
  private readonly router = inject(Router);

  readonly email = signal('demo@restovision.local');
  readonly password = signal('');
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  value(event: Event): string {
    return (event.target as HTMLInputElement).value;
  }

  submit(event: Event): void {
    event.preventDefault();
    this.loading.set(true);
    this.error.set(null);
    this.api.login(this.email(), this.password()).subscribe({
      next: (session) => {
        this.sessions.set(session);
        void this.router.navigateByUrl('/salle');
      },
      error: (response) => {
        this.loading.set(false);
        this.error.set(response.status === 401 ? 'Email ou mot de passe incorrect.' : 'Backend injoignable.');
      },
    });
  }
}
