import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_URL } from './config';
import {
  Alert, AlertRule, Box, Day, DaySummary, Dish, Forecast, Group, ImportResult, Ingredient, Order, Report, RestaurantSettings, Session, Stats, Stock, WasteDay, Zone,
} from './models';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  login(email: string, password: string): Observable<Session> {
    return this.http.post<Session>(`${API_URL}/auth/login`, { email, password });
  }
  zones(): Observable<Zone[]> {
    return this.http.get<Zone[]>(`${API_URL}/zones`);
  }
  boxes(): Observable<Box[]> {
    return this.http.get<Box[]>(`${API_URL}/boxes`);
  }
  alerts(): Observable<Alert[]> {
    return this.http.get<Alert[]>(`${API_URL}/alerts`);
  }
  acknowledge(id: string): Observable<Alert> {
    return this.http.post<Alert>(`${API_URL}/alerts/${id}/ack`, {});
  }
  rules(): Observable<AlertRule[]> {
    return this.http.get<AlertRule[]>(`${API_URL}/alert-rules`);
  }
  updateRule(rule: AlertRule): Observable<AlertRule> {
    return this.http.put<AlertRule>(`${API_URL}/alert-rules/${rule.id}`, {
      threshold: rule.threshold,
      enabled: rule.enabled,
    });
  }
  stats(): Observable<Stats> {
    return this.http.get<Stats>(`${API_URL}/stats/today`);
  }
  /** Une journée de service table par table. `date` au format AAAA-MM-JJ. */
  day(date: string): Observable<Day> {
    return this.http.get<Day>(`${API_URL}/stats/day`, { params: { date } });
  }
  stock(): Observable<Stock> {
    return this.http.get<Stock>(`${API_URL}/stock`);
  }
  /** Proposition de commande pour tenir `days` jours. */
  order(days: number): Observable<Order> {
    return this.http.get<Order>(`${API_URL}/stock/order`, { params: { days } });
  }
  addIngredient(item: Pick<Ingredient, 'name' | 'unit' | 'quantity' | 'threshold' | 'supplier'>): Observable<Ingredient> {
    return this.http.post<Ingredient>(`${API_URL}/stock/ingredients`, item);
  }
  updateIngredient(item: Ingredient): Observable<Ingredient> {
    const { name, unit, quantity, threshold, supplier } = item;
    return this.http.put<Ingredient>(`${API_URL}/stock/ingredients/${item.id}`, { name, unit, quantity, threshold, supplier });
  }
  deleteIngredient(id: string): Observable<void> {
    return this.http.delete<void>(`${API_URL}/stock/ingredients/${id}`);
  }
  addDish(dish: { name: string; lines: { ingredientId: string; quantity: number }[] }): Observable<Dish> {
    return this.http.post<Dish>(`${API_URL}/stock/dishes`, dish);
  }
  deleteDish(id: string): Observable<void> {
    return this.http.delete<void>(`${API_URL}/stock/dishes/${id}`);
  }
  /** Envoie un export de ventes de la caisse (CSV). */
  importSales(file: File): Observable<ImportResult> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<ImportResult>(`${API_URL}/stock/sales`, form);
  }
  /** Les restaurants du groupe côte à côte. Réservé au responsable de groupe. */
  group(days: number): Observable<Group> {
    return this.http.get<Group>(`${API_URL}/group`, { params: { days } });
  }
  restaurant(): Observable<RestaurantSettings> {
    return this.http.get<RestaurantSettings>(`${API_URL}/restaurant`);
  }
  updateLocation(latitude: number | null, longitude: number | null): Observable<RestaurantSettings> {
    return this.http.put<RestaurantSettings>(`${API_URL}/restaurant/location`, { latitude, longitude });
  }
  /** Affluence attendue demain. */
  forecast(): Observable<Forecast> {
    return this.http.get<Forecast>(`${API_URL}/forecast`);
  }
  waste(date: string): Observable<WasteDay> {
    return this.http.get<WasteDay>(`${API_URL}/waste`, { params: { date } });
  }
  report(date: string): Observable<Report> {
    return this.http.get<Report>(`${API_URL}/reports/daily`, { params: { date } });
  }
  /** Vrai si le serveur sait envoyer des e-mails. */
  mailAvailable(): Observable<{ available: boolean }> {
    return this.http.get<{ available: boolean }>(`${API_URL}/mail`);
  }
  /** Envoie le rapport d'un jour terminé aux gérants. */
  sendReport(date: string): Observable<{ sentTo: string[] }> {
    return this.http.post<{ sentTo: string[] }>(`${API_URL}/reports/daily/send`, null, { params: { date } });
  }
  /** Fait rédiger à nouveau le rapport d'un jour terminé. */
  rewriteReport(date: string): Observable<Report> {
    return this.http.post<Report>(`${API_URL}/reports/daily`, null, { params: { date } });
  }
  /** Résumé des derniers jours, du plus ancien au plus récent, aujourd'hui compris. */
  days(count: number): Observable<DaySummary[]> {
    return this.http.get<DaySummary[]>(`${API_URL}/stats/days`, { params: { count } });
  }
}
