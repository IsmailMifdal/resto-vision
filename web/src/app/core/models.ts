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
  /** Contour de la zone dans l'image de la caméra, ou null tant que le boîtier ne l'a pas envoyé. */
  polygon: [number, number][] | null;
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

/** Un état tenu par une table. Les périodes « libre » ne sont pas listées. */
export interface Segment {
  state: TableState;
  start: string;
  end: string;
}

export interface HourStats {
  /** Heure du restaurant : 12 = de 12 h à 13 h. */
  hour: number;
  occupancyRate: number;
  averageWaitSeconds: number | null;
  seatings: number;
}

/** Une journée de service. `from` est minuit, heure du restaurant. */
export interface Day {
  date: string;
  from: string;
  to: string;
  tablesTotal: number;
  tables: { code: string; segments: Segment[] }[];
  hours: HourStats[];
  averageWaitSeconds: number | null;
  longestWaitSeconds: number | null;
  longestWaitTable: string | null;
  longestWaitAt: string | null;
  seatings: number;
  averageToClearSeconds: number | null;
  peakOccupancyRate: number;
}

export interface DaySummary {
  date: string;
  averageWaitSeconds: number | null;
  longestWaitSeconds: number | null;
  seatings: number;
  peakOccupancyRate: number;
  averageToClearSeconds: number | null;
}

export interface Ingredient {
  id: string;
  name: string;
  unit: string;
  quantity: number;
  /** Sous ce seuil, l'ingrédient est à commander. */
  threshold: number;
  low: boolean;
  updatedAt: string;
  supplier: string | null;
}

export interface Dish {
  id: string;
  name: string;
  lines: { ingredientId: string; ingredient: string; unit: string; quantity: number }[];
}

export interface Stock {
  ingredients: Ingredient[];
  dishes: Dish[];
  lastImport: { fileName: string; importedAt: string; lines: number } | null;
}

/** Proposition de commande fournisseur. */
export interface Order {
  /** Nombre de jours que la commande doit permettre de tenir. */
  coverDays: number;
  /** Jours de ventes importées utilisés pour estimer la consommation. */
  salesDays: number;
  lines: {
    ingredient: string;
    unit: string;
    supplier: string | null;
    quantity: number;
    /** null : aucune vente connue pour cet ingrédient. */
    dailyUse: number | null;
    daysLeft: number | null;
    toOrder: number;
  }[];
}

/** Bilan d'un import de ventes de la caisse. */
export interface ImportResult {
  fileName: string;
  lines: number;
  dishesSold: number;
  usage: { ingredient: string; unit: string; used: number; remaining: number; low: boolean }[];
  /** Plats vendus sans recette : ils n'ont rien retiré du stock. */
  unknownDishes: { name: string; quantity: number }[];
  problems: { line: number; message: string }[];
}

/** Affluence attendue un jour à venir, à partir de l'historique. */
export interface Forecast {
  date: string;
  /** Jours d'historique utilisés, dont `sameWeekdayDays` du même jour de la semaine. */
  basedOnDays: number;
  sameWeekdayDays: number;
  hours: { hour: number; expectedSeatings: number; expectedOccupancyRate: number }[];
  peakHour: number | null;
  expectedSeatings: number;
}

/** Le rapport d'une journée, en phrases. */
export interface Report {
  date: string;
  text: string;
  /** AI : rédigé par un modèle de langage. TEMPLATE : phrases toutes faites. */
  source: 'AI' | 'TEMPLATE';
  generatedAt: string;
  /** Journée en cours : les chiffres bougent encore. */
  partial: boolean;
  /** Une clé d'API est configurée côté serveur. */
  aiAvailable: boolean;
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

/** Durée d'un indicateur : « 6 min 20 », ou un tiret quand il n'y a rien à mesurer. */
export function formatWait(seconds: number | null): string {
  if (seconds === null) return '–';
  if (seconds < 60) return `${seconds} s`;
  const rest = seconds % 60;
  return rest ? `${Math.floor(seconds / 60)} min ${String(rest).padStart(2, '0')}` : `${seconds / 60} min`;
}

/** Heure du restaurant (« 12:52 ») d'un instant, à partir du minuit de la journée renvoyé par le serveur. */
export function clockTime(dayStart: string, instant: string): string {
  const minutes = Math.floor((Date.parse(instant) - Date.parse(dayStart)) / 60000);
  return `${String(Math.floor(minutes / 60) % 24).padStart(2, '0')}:${String(minutes % 60).padStart(2, '0')}`;
}
