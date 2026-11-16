import { Component, computed, input, output } from '@angular/core';

export interface Bar {
  /** Texte sous la barre. */
  label: string;
  /** null : pas de mesure sur cette période (aucune barre n'est dessinée). */
  value: number | null;
  /** Ligne de l'infobulle, par exemple « 12 h – 13 h ». */
  title: string;
}

/** Échelle ronde : 4 graduations au plus, la plus haute au-dessus du maximum. */
export function niceScale(maximum: number, steps: number[]): { max: number; ticks: number[] } {
  const largest = steps[steps.length - 1];
  const step = steps.find((s) => maximum <= s * 4) ?? largest * Math.ceil(maximum / (largest * 4));
  const count = Math.max(2, Math.min(4, Math.ceil(maximum / step)));
  return { max: step * count, ticks: Array.from({ length: count + 1 }, (_, i) => i * step) };
}

/** Barres verticales : une seule série, une seule couleur, l'échelle à gauche, la valeur au survol. */
@Component({
  selector: 'app-bar-chart',
  template: `
    <div class="chart" [class.pickable]="pickable()">
      <div class="scale" aria-hidden="true">
        @for (tick of scale().ticks; track tick) {
          <span class="num" [style.bottom.%]="(tick / scale().max) * 100">{{ format()(tick) }}</span>
        }
      </div>
      <div class="plot">
        @for (tick of scale().ticks; track tick) {
          <i [style.bottom.%]="(tick / scale().max) * 100" [class.base]="tick === 0"></i>
        }
        <div class="bars" role="list">
          @for (bar of bars(); track $index) {
            <button type="button" class="col" role="listitem" [class.selected]="$index === selected()"
                    [disabled]="!pickable()" (click)="pick.emit($index)"
                    [attr.aria-label]="bar.title + ' : ' + (bar.value === null ? 'pas de mesure' : format()(bar.value))">
              @if (bar.value !== null && bar.value > 0) {
                <span class="bar" [attr.data-tone]="tone()" [style.height.%]="(bar.value / scale().max) * 100"></span>
              }
              <span class="tip num" [class.left]="$index > bars().length / 2">
                <span class="muted">{{ bar.title }}</span>
                <strong>{{ bar.value === null ? 'Pas de mesure' : format()(bar.value) }}</strong>
              </span>
            </button>
          }
        </div>
      </div>
      <div class="labels num" aria-hidden="true">
        @for (bar of bars(); track $index) {
          <span [class.selected]="$index === selected()">{{ bars().length > 8 && $index % 2 ? '' : bar.label }}</span>
        }
      </div>
    </div>
  `,
  styles: `
    .chart { display: grid; grid-template-columns: 44px minmax(0, 1fr); grid-template-rows: 150px auto; column-gap: 8px; row-gap: 8px; }
    .scale { position: relative; }
    .scale span { position: absolute; right: 0; transform: translateY(50%); font-size: 12px; line-height: 14px; color: var(--faint); white-space: nowrap; }
    .plot { position: relative; }
    .plot i { position: absolute; left: 0; right: 0; height: 1px; background: var(--rule); }
    .plot i.base { background: var(--control); }
    .bars { position: absolute; inset: 0; display: flex; align-items: stretch; gap: 2px; }
    .col { flex: 1; min-width: 0; height: 100%; padding: 0; border-radius: 0; background: transparent; position: relative; display: flex; align-items: flex-end; justify-content: center; cursor: default; transform: none !important; }
    .pickable .col { cursor: pointer; }
    .col:hover:not(:disabled), .col:hover { background: rgba(255, 255, 255, 0.04); }
    .col:disabled { opacity: 1; }
    .bar { width: min(100%, 28px); border-radius: 4px 4px 0 0; background: #e6e2dc; }
    .bar[data-tone='waiting'] { background: var(--waiting); }
    /* Avec un jour choisi, les autres barres s'effacent : la couleur suit la sélection, pas le rang. */
    .pickable .col:not(.selected) .bar { opacity: 0.4; }
    .tip { display: none; position: absolute; bottom: calc(100% - 26px); left: 50%; z-index: 3; background: var(--raised); border-radius: 8px; padding: 6px 10px; white-space: nowrap; text-align: left; font-size: 12px; line-height: 16px; box-shadow: 0 6px 18px rgba(0, 0, 0, 0.5); pointer-events: none; }
    .tip.left { left: auto; right: 50%; }
    .tip span, .tip strong { display: block; font-weight: 500; }
    .col:hover .tip, .col:focus-visible .tip { display: block; }
    .labels { grid-column: 2; display: flex; gap: 2px; }
    .labels span { flex: 1; min-width: 0; text-align: center; font-size: 12px; line-height: 14px; color: var(--faint); white-space: nowrap; }
    .labels span.selected { color: var(--text); }
  `,
})
export class BarChart {
  readonly bars = input.required<Bar[]>();
  /** Écrit une valeur avec son unité : « 6 min », « 75 % ». */
  readonly format = input.required<(value: number) => string>();
  /** Pas possibles entre deux graduations, du plus petit au plus grand. */
  readonly steps = input.required<number[]>();
  readonly tone = input<'ink' | 'waiting'>('ink');
  /** Plafond imposé (1 pour un taux) ; sinon l'échelle suit le maximum des barres. */
  readonly ceiling = input<number | null>(null);
  readonly selected = input<number | null>(null);
  readonly pickable = input(false);
  readonly pick = output<number>();

  readonly scale = computed(() => {
    const ceiling = this.ceiling();
    if (ceiling !== null) return niceScale(ceiling, this.steps());
    // 5 % de marge : la plus haute barre ne touche jamais le haut du tracé.
    const top = Math.max(0, ...this.bars().map((bar) => bar.value ?? 0)) * 1.05;
    return niceScale(top || this.steps()[0], this.steps());
  });
}
