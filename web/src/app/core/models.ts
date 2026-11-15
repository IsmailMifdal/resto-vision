export type TableState = 'EMPTY' | 'WAITING' | 'SERVED' | 'TO_CLEAR';
export type ZoneType = 'TABLE' | 'QUEUE';
export type AlertType = 'UNSERVED' | 'TO_CLEAR' | 'QUEUE_LENGTH';

export interface Zone {
  code: string;
  type: ZoneType;
  state: TableState | null;
  stateSince: string | null;
  persons: number;
  dishes: number;
  estimatedWaitSeconds: number | null;
}

export interface Alert {
  id: string;
  type: AlertType;
  zoneCode: string;
  message: string;
  createdAt: string;
  acknowledgedAt: string | null;
  resolvedAt: string | null;
}

export interface AlertRule {
  id: string;
  type: AlertType;
  threshold: number;
  enabled: boolean;
}

export interface Stats {
  tablesTotal: number;
  tablesOccupied: number;
  tablesToClear: number;
  occupancyRate: number;
  averageWaitSeconds: number | null;
  seatingsToday: number;
  queuePersons: number;
  queueWaitSeconds: number;
}

export interface Session {
  token: string;
  restaurantCode: string;
  restaurantName: string;
  role: string;
}

export const STATE_LABELS: Record<TableState, string> = {
  EMPTY: 'Libre',
  WAITING: 'En attente',
  SERVED: 'Servie',
  TO_CLEAR: 'À débarrasser',
};

export const RULE_LABELS: Record<AlertType, { label: string; unit: string }> = {
  UNSERVED: { label: 'Clients non servis', unit: 'secondes' },
  TO_CLEAR: { label: 'Table à débarrasser', unit: 'secondes' },
  QUEUE_LENGTH: { label: 'File d’attente', unit: 'personnes' },
};

/** Remplace ou ajoute une zone, liste triée par code. */
export function upsertZone(zones: Zone[], zone: Zone): Zone[] {
  return [...zones.filter((z) => z.code !== zone.code), zone].sort((a, b) =>
    a.code.localeCompare(b.code, 'fr', { numeric: true }),
  );
}

/** Une alerte résolue disparaît de la liste, les autres sont ajoutées ou mises à jour. */
export function upsertAlert(alerts: Alert[], alert: Alert): Alert[] {
  const others = alerts.filter((a) => a.id !== alert.id);
  return alert.resolvedAt ? others : [alert, ...others];
}

export function formatDuration(seconds: number): string {
  if (seconds < 60) return `${Math.max(0, Math.floor(seconds))} s`;
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes} min`;
  return `${Math.floor(minutes / 60)} h ${String(minutes % 60).padStart(2, '0')}`;
}
