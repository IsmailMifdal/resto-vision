import { ZoneType } from './models';

/** Lecture et écriture du fichier de configuration du boîtier (edge.json). */

export type Point = [number, number];

export interface EdgeZone {
  id: string;
  type: ZoneType;
  /** Vaisselle d'une table dressée et libre, à ne pas compter. */
  baselineDishes: number;
  polygon: Point[];
}

export type EdgeConfig = Record<string, unknown>;

export function defaultEdgeConfig(restaurantId: string): EdgeConfig {
  return {
    restaurantId,
    mqtt: { host: 'localhost', port: 1883, topicPrefix: 'restovision' },
    model: { weights: 'yolo11n.pt', confidence: 0.35, fps: 5, imgsz: 1280 },
    labels: {
      person: ['person'],
      dish: ['cup', 'bowl', 'wine glass', 'bottle', 'fork', 'knife', 'spoon', 'pizza', 'sandwich', 'cake'],
    },
    stableSeconds: 5,
    queue: { secondsPerCustomer: 90 },
  };
}

/** Lit un edge.json. Lève une erreur lisible si le fichier n'est pas utilisable. */
export function parseEdgeConfig(text: string): { config: EdgeConfig; zones: EdgeZone[] } {
  let raw: unknown;
  try {
    raw = JSON.parse(text);
  } catch {
    throw new Error('Ce fichier n’est pas un JSON valide.');
  }
  if (typeof raw !== 'object' || raw === null || Array.isArray(raw)) {
    throw new Error('Ce fichier n’est pas une configuration de boîtier.');
  }
  const { zones, ...config } = raw as { zones?: unknown } & EdgeConfig;
  if (zones !== undefined && !Array.isArray(zones)) throw new Error('Le champ « zones » doit être une liste.');
  return { config, zones: (zones ?? []).map((zone, index) => readZone(zone, index)) };
}

function readZone(raw: unknown, index: number): EdgeZone {
  const zone = raw as Partial<Record<keyof EdgeZone, unknown>>;
  const polygon = Array.isArray(zone?.polygon) ? zone.polygon : [];
  const valid =
    typeof zone?.id === 'string' &&
    (zone.type === 'TABLE' || zone.type === 'QUEUE' || zone.type === 'EXIT') &&
    polygon.length >= 3 &&
    polygon.every((p) => Array.isArray(p) && p.length === 2 && p.every((n) => typeof n === 'number'));
  if (!valid) throw new Error(`La zone n° ${index + 1} est incomplète (id, type ou polygone).`);
  return {
    id: zone.id as string,
    type: zone.type as ZoneType,
    baselineDishes: Math.max(0, Math.floor(Number(zone.baselineDishes ?? 0)) || 0),
    polygon: polygon.map((p) => [p[0], p[1]] as Point),
  };
}

/** Écrit le edge.json : les réglages d'origine sont gardés, seules les zones sont remplacées. */
export function serializeEdgeConfig(config: EdgeConfig, zones: EdgeZone[]): string {
  const out = {
    ...config,
    zones: zones.map((zone) => ({
      id: zone.id.trim(),
      type: zone.type,
      ...(zone.type === 'TABLE' ? { baselineDishes: zone.baselineDishes } : {}),
      polygon: zone.polygon,
    })),
  };
  // Un point par ligne plutôt que trois : le fichier reste relisible.
  return JSON.stringify(out, null, 2).replace(/\[\s+(-?\d+),\s+(-?\d+)\s+\]/g, '[$1, $2]') + '\n';
}

/** Premier nom libre : T1, T2… pour une table, Q1, Q2… pour une file. */
export function nextZoneId(zones: EdgeZone[], type: ZoneType): string {
  const prefix = type === 'TABLE' ? 'T' : type === 'QUEUE' ? 'Q' : 'S';
  const used = new Set(zones.map((zone) => zone.id.trim()));
  let n = 1;
  while (used.has(prefix + n)) n++;
  return prefix + n;
}

/** Ce qui empêche d'enregistrer, en clair. Liste vide si tout va bien. */
export function zoneProblems(zones: EdgeZone[]): string[] {
  const problems: string[] = [];
  const ids = zones.map((zone) => zone.id.trim());
  if (ids.some((id) => !id)) problems.push('Chaque zone doit avoir un nom.');
  const duplicates = [...new Set(ids.filter((id, i) => id && ids.indexOf(id) !== i))];
  if (duplicates.length) problems.push(`Nom utilisé deux fois : ${duplicates.join(', ')}.`);
  return problems;
}
