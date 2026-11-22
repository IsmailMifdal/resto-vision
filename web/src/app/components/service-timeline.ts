import { Component, computed, input } from '@angular/core';
import { Day, STATE_LABELS, clockTime, formatWait } from '../core/models';

interface Block {
  state: string;
  left: number;
  width: number;
  longest: boolean;
  tip: string;
  flip: boolean;
}

/** La frise du service : une ligne par table, le temps en abscisse, la couleur dit l'état de la table.
 *  On y voit d'un regard où les clients ont attendu et quelles tables sont restées à débarrasser. */
@Component({
  selector: 'app-service-timeline',
  template: `
    @if (view(); as v) {
      <div class="frise" role="img" [attr.aria-label]="'Frise du service, ' + v.rows.length + ' tables, de ' + v.first + ' h à ' + v.last + ' h'">
        @for (row of v.rows; track row.code) {
          <div class="code num">{{ row.code }}</div>
          <div class="track">
            @for (hour of v.hours; track hour.label) { <i [style.left.%]="hour.left"></i> }
            @for (block of row.blocks; track $index) {
              <span class="block" [attr.data-state]="block.state" [class.longest]="block.longest"
                    [style.left.%]="block.left" [style.width.%]="block.width" tabindex="0">
                <span class="tip num" [class.flip]="block.flip">{{ block.tip }}</span>
              </span>
            }
            @if (v.now !== null) { <b [style.left.%]="v.now"></b> }
          </div>
        }
        <div></div>
        <div class="axis num" aria-hidden="true">
          @for (hour of v.hours; track hour.label) { <span [style.left.%]="hour.left">{{ hour.label }}</span> }
        </div>
      </div>
    } @else {
      <p class="empty">Aucun service enregistré ce jour-là. La frise se remplit dès que le boîtier voit des clients s’installer.</p>
    }
  `,
  styles: `
    .frise { display: grid; grid-template-columns: 44px minmax(0, 1fr); row-gap: 6px; column-gap: 8px; align-items: center; }
    .code { color: var(--muted); font-size: 12px; text-align: right; }
    .track { position: relative; height: 20px; }
    .track i { position: absolute; top: -3px; bottom: -3px; width: 1px; background: var(--rule); }
    .track b { position: absolute; top: -3px; bottom: -3px; width: 1px; background: var(--text); opacity: 0.6; }
    .block { position: absolute; top: 0; bottom: 0; min-width: 2px; border-radius: 4px; background: #3d3a36; box-shadow: 0 0 0 1px var(--surface); }
    .block[data-state='WAITING'] { background: var(--waiting); }
    .block[data-state='TO_CLEAR'] { background: var(--clear); }
    /* L'attente la plus longue de la journée est cerclée : c'est elle que nomme la phrase du titre. */
    .block.longest { box-shadow: 0 0 0 1px var(--surface), 0 0 0 2.5px var(--text); z-index: 1; }
    .block:hover, .block:focus-visible { filter: brightness(1.2); z-index: 2; outline: none; }
    .tip { display: none; position: absolute; bottom: calc(100% + 8px); left: 0; z-index: 4; background: var(--raised); color: var(--text); border-radius: 8px; padding: 6px 10px; font-size: 12px; line-height: 16px; white-space: nowrap; box-shadow: 0 6px 18px rgba(0, 0, 0, 0.5); pointer-events: none; }
    .tip.flip { left: auto; right: 0; }
    .block:hover .tip, .block:focus-visible .tip { display: block; }
    .axis { position: relative; height: 16px; margin-top: 2px; }
    .axis span { position: absolute; transform: translateX(-50%); font-size: 12px; line-height: 16px; color: var(--faint); white-space: nowrap; }
    .axis span:first-child { transform: none; }
    .axis span:last-child { transform: translateX(-100%); }
    @media (max-width: 640px) { .axis span:nth-child(even) { display: none; } }
  `,
})
export class ServiceTimeline {
  readonly day = input.required<Day>();
  /** Vrai pour la journée en cours : un trait marque « maintenant ». */
  readonly today = input(false);

  readonly view = computed(() => {
    const day = this.day();
    if (!day.hours.length) return null;
    const first = day.hours[0].hour;
    const last = day.hours[day.hours.length - 1].hour + 1;
    const start = Date.parse(day.from) + first * 3600_000;
    const span = (last - first) * 3600_000;
    const position = (instant: number) => Math.min(100, Math.max(0, ((instant - start) / span) * 100));

    const rows = day.tables.map((table) => ({
      code: table.code,
      blocks: table.segments.map((segment): Block => {
        const from = Date.parse(segment.start), to = Date.parse(segment.end);
        const left = position(from);
        const longest =
          segment.state === 'WAITING' && table.code === day.longestWaitTable && segment.start === day.longestWaitAt;
        return {
          state: segment.state,
          left,
          width: position(to) - left,
          longest,
          flip: left > 60,
          tip: `${table.code} · ${STATE_LABELS[segment.state]} · ${clockTime(day.from, segment.start)} – ${clockTime(day.from, segment.end)} · ${formatWait(Math.round((to - from) / 1000))}`,
        };
      }),
    }));
    const hours = Array.from({ length: last - first + 1 }, (_, i) => ({
      label: `${first + i} h`,
      left: (i / (last - first)) * 100,
    }));
    return { rows, hours, first, last, now: this.today() ? position(Date.parse(day.to)) : null };
  });
}
