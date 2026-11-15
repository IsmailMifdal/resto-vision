import { Component, ElementRef, OnDestroy, computed, inject, signal, viewChild } from '@angular/core';
import {
  EdgeConfig,
  EdgeZone,
  Point,
  defaultEdgeConfig,
  nextZoneId,
  parseEdgeConfig,
  serializeEdgeConfig,
  zoneProblems,
} from '../core/edge-config';
import { ZoneType } from '../core/models';
import { SessionService } from '../core/session.service';

/** Outil d'installation : dessiner les tables et la file sur une image de la caméra,
 *  puis enregistrer le edge.json du boîtier. L'image ne quitte pas le navigateur. */
@Component({
  selector: 'app-zones',
  host: { '(document:keydown.escape)': 'cancelDraft()' },
  template: `
    <h1>Zones de la caméra</h1>
    <p class="muted intro">
      Dessinez chaque table <strong>avec ses chaises</strong>, et la file d’attente devant le comptoir.
      L’image reste dans ce navigateur : elle n’est envoyée nulle part.
    </p>

    <div class="card sources">
      <div>
        <strong>1. Image de la caméra</strong>
        <div class="row">
          <input type="url" [value]="boxUrl()" (change)="boxUrl.set(text($event))" aria-label="Adresse de l’image du boîtier">
          <button (click)="loadFromBox()">Depuis le boîtier</button>
          <label class="pick">ou un fichier<input type="file" accept="image/*" (change)="openImage($event)"></label>
        </div>
        <span class="muted small">Le boîtier doit tourner avec <code>--preview-port 8090</code>, sur ce poste.</span>
      </div>
      <div>
        <strong>2. Configuration existante</strong><span class="muted">&nbsp;(facultatif)</span>
        <div class="row">
          <label class="pick">Ouvrir un edge.json<input type="file" accept=".json,application/json" (change)="openConfig($event)"></label>
          @if (configName()) { <span class="muted">{{ configName() }}</span> }
        </div>
      </div>
    </div>
    @if (error()) { <p class="error">{{ error() }}</p> }

    @if (imageUrl(); as url) {
      <div class="layout">
        <div>
          <div class="tools">
            <button [class.primary]="tool() === 'TABLE'" (click)="startDraft('TABLE')">+ Table</button>
            <button [class.primary]="tool() === 'QUEUE'" (click)="startDraft('QUEUE')">+ File d’attente</button>
            @if (tool()) {
              <button (click)="finishDraft()" [disabled]="draft().length < 3">Terminer la zone</button>
              <button (click)="undoPoint()" [disabled]="!draft().length">Retirer le dernier point</button>
              <button (click)="cancelDraft()">Annuler</button>
            }
          </div>
          <p class="muted small hint">
            @if (tool()) {
              Cliquez sur l’image pour poser les coins ({{ draft().length }} posés, 3 au minimum).
              Cliquez sur le premier point pour fermer la zone.
            } @else {
              Cliquez sur une zone pour la sélectionner, puis faites glisser ses coins pour l’ajuster.
            }
          </p>
          <div class="stage">
            <img [src]="url" (load)="imageLoaded($event)" (error)="imageFailed()" alt="Image de la caméra">
            @if (size(); as s) {
              <svg #overlay [attr.viewBox]="'0 0 ' + s.width + ' ' + s.height" [class.drawing]="tool()"
                   (click)="stageClick($event)" (pointermove)="drag($event)" (pointerup)="endDrag()" (pointerleave)="endDrag()">
                @for (zone of zones(); track zi; let zi = $index) {
                  <polygon [attr.points]="points(zone.polygon)" [class]="zone.type === 'TABLE' ? 'table' : 'queue'"
                           [class.selected]="zi === selected()" (click)="select(zi, $event)" />
                  <text [attr.x]="zone.polygon[0][0] + unit()" [attr.y]="zone.polygon[0][1] + unit() * 3.4"
                        [attr.font-size]="unit() * 3">{{ zone.id }}</text>
                  @if (zi === selected() && !tool()) {
                    @for (point of zone.polygon; track pi; let pi = $index) {
                      <circle class="handle" [attr.cx]="point[0]" [attr.cy]="point[1]" [attr.r]="unit()"
                              (pointerdown)="startDrag(pi, $event)" (click)="handleClick($event)" />
                    }
                  }
                }
                @if (draft().length) {
                  <polyline class="draft" [attr.points]="points(draft())" />
                  @for (point of draft(); track pi; let pi = $index) {
                    <circle class="handle" [class.closer]="pi === 0 && draft().length >= 3" [attr.cx]="point[0]" [attr.cy]="point[1]"
                            [attr.r]="unit()" (click)="draftPointClick(pi, $event)" />
                  }
                }
              </svg>
            }
          </div>
        </div>

        <aside>
          <h2>Zones ({{ zones().length }})</h2>
          @for (zone of zones(); track zi; let zi = $index) {
            <div class="card zone" [class.selected]="zi === selected()" (click)="selected.set(zi)">
              <div class="row">
                <input [value]="zone.id" (change)="patch(zi, { id: text($event) })" aria-label="Nom de la zone">
                <select [value]="zone.type" (change)="patch(zi, { type: type($event) })" aria-label="Type de zone">
                  <option value="TABLE">Table</option>
                  <option value="QUEUE">File</option>
                </select>
                <button (click)="remove(zi, $event)" [attr.aria-label]="'Supprimer ' + zone.id">Supprimer</button>
              </div>
              @if (zone.type === 'TABLE') {
                <label class="baseline">
                  <span>Vaisselle quand la table est dressée et libre</span>
                  <input type="number" min="0" [value]="zone.baselineDishes" (change)="patch(zi, { baselineDishes: count($event) })">
                </label>
              }
            </div>
          } @empty {
            <p class="muted">Aucune zone. Commencez par « + Table ».</p>
          }

          @for (problem of problems(); track problem) { <p class="error">{{ problem }}</p> }
          <button class="primary save" (click)="download()" [disabled]="!zones().length || problems().length > 0">
            Télécharger edge.json
          </button>
          <p class="muted small">
            Placez ce fichier dans <code>edge/config/edge.json</code> sur le boîtier, puis relancez-le.
          </p>
          @if (zones().length) {
            <details>
              <summary>Voir le fichier</summary>
              <pre>{{ json() }}</pre>
            </details>
          }
        </aside>
      </div>
    }
  `,
  styles: `
    .intro { margin: -0.5rem 0 1rem; }
    .small { font-size: 0.85rem; }
    .sources { display: grid; gap: 1rem; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); }
    .row { display: flex; gap: 0.5rem; align-items: center; flex-wrap: wrap; margin: 0.4rem 0; }
    .row input[type='url'] { flex: 1; min-width: 200px; }
    .pick { border: 1px solid var(--border); border-radius: 8px; padding: 0.45rem 0.9rem; cursor: pointer; background: var(--surface); white-space: nowrap; }
    .pick input { display: none; }
    .pick:focus-within { outline: 2px solid var(--accent); }
    code { background: var(--bg); padding: 0.05rem 0.3rem; border-radius: 4px; }
    .layout { display: grid; grid-template-columns: minmax(0, 1fr) 320px; gap: 1rem; margin-top: 1rem; align-items: start; }
    @media (max-width: 800px) { .layout { grid-template-columns: 1fr; } }
    .tools { display: flex; gap: 0.5rem; flex-wrap: wrap; }
    .hint { margin: 0.5rem 0; }
    .stage { position: relative; line-height: 0; border-radius: 12px; overflow: hidden; border: 1px solid var(--border); }
    .stage img { width: 100%; height: auto; }
    svg { position: absolute; inset: 0; width: 100%; height: 100%; touch-action: none; }
    svg.drawing { cursor: crosshair; }
    polygon { stroke-width: 2; vector-effect: non-scaling-stroke; cursor: pointer; }
    polygon.table { fill: rgba(31, 111, 67, 0.25); stroke: #1f6f43; }
    polygon.queue { fill: rgba(125, 60, 150, 0.25); stroke: #7d3c96; }
    polygon.selected { stroke-width: 4; fill-opacity: 0.6; }
    svg.drawing polygon { pointer-events: none; }
    text { fill: #fff; font-weight: 700; paint-order: stroke; stroke: rgba(0, 0, 0, 0.75); stroke-width: 3px; vector-effect: non-scaling-stroke; pointer-events: none; }
    .draft { fill: none; stroke: #fff; stroke-width: 2; stroke-dasharray: 6 4; vector-effect: non-scaling-stroke; pointer-events: none; }
    .handle { fill: #fff; stroke: #1f2328; stroke-width: 2; vector-effect: non-scaling-stroke; cursor: grab; }
    .handle.closer { fill: var(--accent); cursor: pointer; }
    aside h2 { margin-top: 0; }
    .zone { margin-bottom: 0.6rem; padding: 0.7rem; cursor: pointer; }
    .zone.selected { border-color: var(--accent); box-shadow: 0 0 0 1px var(--accent); }
    .zone .row { margin: 0; flex-wrap: nowrap; }
    .zone .row input { min-width: 0; }
    select { font: inherit; border: 1px solid var(--border); border-radius: 8px; padding: 0.5rem; background: var(--surface); }
    .baseline { display: flex; align-items: center; justify-content: space-between; gap: 0.5rem; margin-top: 0.5rem; font-size: 0.85rem; color: var(--muted); }
    .baseline input { width: 70px; flex: none; }
    .save { width: 100%; margin-top: 0.5rem; }
    pre { background: var(--surface); border: 1px solid var(--border); border-radius: 8px; padding: 0.7rem; font-size: 0.75rem; overflow: auto; max-height: 320px; }
    p { margin: 0.4rem 0; }
  `,
})
export class ZonesPage implements OnDestroy {
  private readonly overlay = viewChild<ElementRef<SVGSVGElement>>('overlay');
  private config: EdgeConfig = defaultEdgeConfig(inject(SessionService).session()?.restaurantCode ?? 'demo');
  private objectUrl: string | null = null;
  private dragging: number | null = null;
  private justDragged = false;

  readonly boxUrl = signal('http://127.0.0.1:8090/frame.jpg');
  readonly imageUrl = signal<string | null>(null);
  readonly size = signal<{ width: number; height: number } | null>(null);
  readonly configName = signal<string | null>(null);
  readonly error = signal<string | null>(null);
  readonly zones = signal<EdgeZone[]>([]);
  readonly selected = signal<number | null>(null);
  /** Type de la zone en cours de dessin, ou null. */
  readonly tool = signal<ZoneType | null>(null);
  readonly draft = signal<Point[]>([]);

  /** Taille des poignées et du texte, proportionnelle à l'image. */
  readonly unit = computed(() => (this.size()?.width ?? 1280) / 110);
  readonly problems = computed(() => zoneProblems(this.zones()));
  readonly json = computed(() => serializeEdgeConfig(this.config, this.zones()));

  ngOnDestroy(): void {
    this.releaseImage();
  }

  // --- Sources ---

  loadFromBox(): void {
    const url = this.boxUrl().trim();
    if (!url) return;
    this.showImage(url + (url.includes('?') ? '&' : '?') + 't=' + Date.now());
  }

  openImage(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) return;
    this.releaseImage();
    this.objectUrl = URL.createObjectURL(file);
    this.showImage(this.objectUrl);
  }

  async openConfig(event: Event): Promise<void> {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) return;
    try {
      const { config, zones } = parseEdgeConfig(await file.text());
      this.config = config;
      this.zones.set(zones);
      this.selected.set(null);
      this.cancelDraft();
      this.configName.set(`${file.name} : ${zones.length} zone${zones.length > 1 ? 's' : ''}`);
      this.error.set(null);
    } catch (e) {
      this.error.set(e instanceof Error ? e.message : 'Fichier illisible.');
    }
  }

  imageLoaded(event: Event): void {
    const image = event.target as HTMLImageElement;
    this.size.set({ width: image.naturalWidth, height: image.naturalHeight });
    this.error.set(null);
  }

  imageFailed(): void {
    this.imageUrl.set(null);
    this.error.set('Image introuvable. Vérifiez que le boîtier tourne avec --preview-port, ou choisissez un fichier.');
  }

  // --- Dessin ---

  startDraft(type: ZoneType): void {
    this.tool.set(type);
    this.draft.set([]);
    this.selected.set(null);
  }

  cancelDraft(): void {
    this.tool.set(null);
    this.draft.set([]);
  }

  undoPoint(): void {
    this.draft.update((points) => points.slice(0, -1));
  }

  finishDraft(): void {
    const type = this.tool();
    if (!type || this.draft().length < 3) return;
    const zone: EdgeZone = { id: nextZoneId(this.zones(), type), type, baselineDishes: 0, polygon: this.draft() };
    this.zones.update((zones) => [...zones, zone]);
    this.selected.set(this.zones().length - 1);
    this.cancelDraft();
  }

  stageClick(event: MouseEvent): void {
    if (this.justDragged) {
      this.justDragged = false;
      return;
    }
    if (this.tool()) this.draft.update((points) => [...points, this.toImage(event)]);
    else this.selected.set(null);
  }

  draftPointClick(index: number, event: MouseEvent): void {
    if (index === 0 && this.draft().length >= 3) {
      event.stopPropagation();
      this.finishDraft();
    }
  }

  select(index: number, event: MouseEvent): void {
    if (this.tool()) return; // en dessin, le clic pose un point
    event.stopPropagation();
    this.selected.set(index);
  }

  // --- Ajustement des coins ---

  startDrag(pointIndex: number, event: PointerEvent): void {
    event.preventDefault();
    this.dragging = pointIndex;
  }

  drag(event: PointerEvent): void {
    const zoneIndex = this.selected();
    if (this.dragging === null || zoneIndex === null) return;
    const point = this.toImage(event);
    const pointIndex = this.dragging;
    this.zones.update((zones) =>
      zones.map((zone, i) =>
        i === zoneIndex ? { ...zone, polygon: zone.polygon.map((p, j) => (j === pointIndex ? point : p)) } : zone,
      ),
    );
  }

  endDrag(): void {
    if (this.dragging !== null) this.justDragged = true;
    this.dragging = null;
  }

  handleClick(event: MouseEvent): void {
    event.stopPropagation();
    this.justDragged = false;
  }

  // --- Liste ---

  patch(index: number, change: Partial<EdgeZone>): void {
    this.zones.update((zones) => zones.map((zone, i) => (i === index ? { ...zone, ...change } : zone)));
  }

  remove(index: number, event: MouseEvent): void {
    event.stopPropagation();
    this.zones.update((zones) => zones.filter((_, i) => i !== index));
    this.selected.set(null);
  }

  download(): void {
    const link = document.createElement('a');
    link.href = URL.createObjectURL(new Blob([this.json()], { type: 'application/json' }));
    link.download = 'edge.json';
    link.click();
    URL.revokeObjectURL(link.href);
  }

  // --- Aides du gabarit ---

  points(polygon: Point[]): string {
    return polygon.map((p) => p.join(',')).join(' ');
  }

  text(event: Event): string {
    return (event.target as HTMLInputElement).value;
  }

  type(event: Event): ZoneType {
    return (event.target as HTMLSelectElement).value as ZoneType;
  }

  count(event: Event): number {
    return Math.max(0, Math.floor(Number((event.target as HTMLInputElement).value)) || 0);
  }

  private showImage(url: string): void {
    this.size.set(null);
    this.error.set(null);
    this.imageUrl.set(url);
  }

  private releaseImage(): void {
    if (this.objectUrl) URL.revokeObjectURL(this.objectUrl);
    this.objectUrl = null;
  }

  /** Position du pointeur en pixels de l'image d'origine, quelle que soit la taille affichée. */
  private toImage(event: MouseEvent): Point {
    const size = this.size();
    const rect = this.overlay()?.nativeElement.getBoundingClientRect();
    if (!size || !rect) return [0, 0];
    const clamp = (value: number, max: number) => Math.min(max, Math.max(0, Math.round(value)));
    return [
      clamp(((event.clientX - rect.left) * size.width) / rect.width, size.width),
      clamp(((event.clientY - rect.top) * size.height) / rect.height, size.height),
    ];
  }
}
