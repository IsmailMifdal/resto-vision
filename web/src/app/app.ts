import { Component, effect, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { LiveService } from './core/live.service';
import { SessionService } from './core/session.service';

@Component({
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  selector: 'app-root',
  styleUrl: './app.css',
  templateUrl: './app.html',
})
export class App {
  private readonly router = inject(Router);
  private readonly live = inject(LiveService);
  protected readonly sessions = inject(SessionService);

  constructor() {
    // La connexion en direct suit la session : ouverte à la connexion, fermée à la déconnexion.
    effect(() => {
      if (this.sessions.session()) this.live.start();
      else this.live.stop();
    });
  }

  protected logout(): void {
    this.sessions.set(null);
    void this.router.navigateByUrl('/login');
  }
}
