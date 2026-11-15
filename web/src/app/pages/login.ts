import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { ApiService } from '../core/api.service';
import { SessionService } from '../core/session.service';

@Component({
  selector: 'app-login',
  template: `
    <form class="card login" (submit)="submit($event)">
      <h1>RestoVision</h1>
      <label>Email
        <input type="email" autocomplete="username" [value]="email()" (input)="email.set(value($event))" required>
      </label>
      <label>Mot de passe
        <input type="password" autocomplete="current-password" [value]="password()" (input)="password.set(value($event))" required>
      </label>
      @if (error()) { <p class="error">{{ error() }}</p> }
      <button class="primary" type="submit" [disabled]="loading()">Se connecter</button>
      <p class="muted">Compte de démonstration : demo&#64;restovision.local / demo1234</p>
    </form>
  `,
  styles: `
    .login { max-width: 360px; margin: 12vh auto 0; display: grid; gap: 0.9rem; }
    label { display: grid; gap: 0.3rem; font-size: 0.9rem; }
    p { margin: 0; font-size: 0.85rem; }
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
