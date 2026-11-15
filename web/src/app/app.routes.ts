import { Routes } from '@angular/router';
import { authGuard } from './core/auth';
import { DashboardPage } from './pages/dashboard';
import { FloorPage } from './pages/floor';
import { LoginPage } from './pages/login';
import { RoadmapPage } from './pages/roadmap';

export const routes: Routes = [
  { path: 'login', component: LoginPage },
  { path: 'salle', component: FloorPage, canActivate: [authGuard] },
  { path: 'gerant', component: DashboardPage, canActivate: [authGuard] },
  { path: 'a-venir', component: RoadmapPage, canActivate: [authGuard] },
  { path: '**', redirectTo: 'salle' },
];
