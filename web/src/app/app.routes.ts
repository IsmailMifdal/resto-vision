import { Routes } from '@angular/router';
import { authGuard } from './core/auth';
import { DashboardPage } from './pages/dashboard';
import { FloorPage } from './pages/floor';
import { LoginPage } from './pages/login';
import { RoadmapPage } from './pages/roadmap';
import { SettingsPage } from './pages/settings';
import { StockPage } from './pages/stock';
import { ZonesPage } from './pages/zones';

export const routes: Routes = [
  { path: 'login', component: LoginPage },
  { path: 'salle', component: FloorPage, canActivate: [authGuard] },
  { path: 'service', component: DashboardPage, canActivate: [authGuard] },
  { path: 'stock', component: StockPage, canActivate: [authGuard] },
  { path: 'zones', component: ZonesPage, canActivate: [authGuard] },
  { path: 'reglages', component: SettingsPage, canActivate: [authGuard] },
  { path: 'a-venir', component: RoadmapPage, canActivate: [authGuard] },
  { path: 'gerant', redirectTo: 'service' },
  { path: '**', redirectTo: 'salle' },
];
