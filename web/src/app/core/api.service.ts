import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_URL } from './config';
import { Alert, AlertRule, Day, DaySummary, Report, Session, Stats, Zone } from './models';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  login(email: string, password: string): Observable<Session> {
    return this.http.post<Session>(`${API_URL}/auth/login`, { email, password });
  }
  zones(): Observable<Zone[]> {
    return this.http.get<Zone[]>(`${API_URL}/zones`);
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
  report(date: string): Observable<Report> {
    return this.http.get<Report>(`${API_URL}/reports/daily`, { params: { date } });
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
