import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ApiService } from '../core/api.service';
import { Dish, ImportResult, Ingredient, Stock } from '../core/models';
import { SessionService } from '../core/session.service';

interface DraftLine {
  ingredientId: string;
  quantity: number;
}

/** Quantité lisible : « 2,5 », « 80 », sans zéros inutiles. */
const amount = (value: number) => value.toLocaleString('fr-FR', { maximumFractionDigits: 3 });

/** Stock du restaurant : ce qu'il reste, ce que chaque plat consomme, et les ventes importées de la caisse. */
@Component({
  selector: 'app-stock',
  template: `
    <div class="page-head">
      <div>
        <h1>Stock</h1>
        <p>{{ finding() }}</p>
      </div>
    </div>
    @if (error()) { <p class="error notice" role="alert">{{ error() }}</p> }

    <div class="layout">
      <section class="card">
        <div class="card-head">
          <h2>Ingrédients</h2>
          <span class="muted small">Corrigez la quantité à chaque livraison ou inventaire</span>
        </div>
        @if (stock(); as s) {
          <div class="scroll">
          <table>
            <thead>
              <tr><th>Ingrédient</th><th class="num">En stock</th><th class="num">Seuil</th><th></th><th></th></tr>
            </thead>
            <tbody>
              @for (item of s.ingredients; track item.id) {
                <tr>
                  <td>{{ item.name }}</td>
                  <td class="num">
                    <span class="field">
                      <input class="num" type="number" step="any" [value]="item.quantity" [disabled]="!canEdit"
                             (change)="setQuantity(item, $event)" [attr.aria-label]="'Quantité en stock : ' + item.name">
                      <span class="muted unit">{{ item.unit }}</span>
                    </span>
                  </td>
                  <td class="num">
                    <input class="num" type="number" min="0" step="any" [value]="item.threshold" [disabled]="!canEdit"
                           (change)="setThreshold(item, $event)" [attr.aria-label]="'Seuil de commande : ' + item.name">
                  </td>
                  <td>@if (item.low) { <span class="pill">{{ item.quantity < 0 ? 'Inventaire à refaire' : 'À commander' }}</span> }</td>
                  <td class="num">@if (canEdit) { <button class="quiet" (click)="removeIngredient(item)" [attr.aria-label]="'Supprimer ' + item.name">Supprimer</button> }</td>
                </tr>
              } @empty {
                <tr><td colspan="5"><p class="empty">Aucun ingrédient. Ajoutez le premier ci-dessous.</p></td></tr>
              }
            </tbody>
          </table>
          </div>
          @if (canEdit) {
            <form class="add" (submit)="addIngredient($event)">
              <input name="name" placeholder="Nouvel ingrédient" required maxlength="120" aria-label="Nom du nouvel ingrédient">
              <input name="unit" placeholder="Unité (kg, L, pièce)" required maxlength="20" aria-label="Unité">
              <input class="num" name="quantity" type="number" step="any" placeholder="En stock" required aria-label="Quantité en stock">
              <input class="num" name="threshold" type="number" min="0" step="any" placeholder="Seuil" required aria-label="Seuil de commande">
              <button type="submit">Ajouter</button>
            </form>
          }
        } @else if (!error()) {
          <p class="muted">Chargement du stock…</p>
        }
      </section>

      <div class="side">
        <section class="card">
          <div class="card-head">
            <h2>Ventes de la caisse</h2>
            @if (stock()?.lastImport; as last) { <span class="muted small num">Dernier import : {{ when(last.importedAt) }}</span> }
          </div>
          <p class="muted">Exportez les ventes de votre caisse en CSV, avec trois colonnes : <code>date</code>, <code>plat</code>, <code>quantite</code>. Chaque plat vendu retire sa recette du stock.</p>
          @if (canEdit) {
            <label class="pick" [class.busy]="importing()">
              {{ importing() ? 'Import en cours…' : 'Importer un fichier de ventes' }}
              <input type="file" accept=".csv,text/csv" [disabled]="importing()" (change)="importSales($event)">
            </label>
          } @else {
            <p class="muted small">Seul le gérant peut importer des ventes.</p>
          }
          @if (result(); as r) {
            <div class="result" role="status">
              <strong>{{ r.fileName }} : {{ amount(r.dishesSold) }} plats vendus sur {{ r.lines }} lignes.</strong>
              @if (r.usage.length) {
                <ul>
                  @for (use of r.usage; track use.ingredient) {
                    <li class="num">
                      <span>{{ use.ingredient }}</span>
                      <span class="muted">−{{ amount(use.used) }} {{ use.unit }}, reste {{ amount(use.remaining) }}</span>
                      @if (use.low) { <span class="pill">À commander</span> }
                    </li>
                  }
                </ul>
              }
              @if (r.unknownDishes.length) {
                <p class="warn">Plats sans recette, non retirés du stock : {{ unknownList(r) }}. Ajoutez leur recette, puis corrigez le stock à la main.</p>
              }
              @if (r.problems.length) {
                <p class="warn">{{ r.problems.length }} {{ r.problems.length > 1 ? 'lignes ignorées' : 'ligne ignorée' }} : ligne {{ r.problems[0].line }}, {{ r.problems[0].message }}</p>
              }
            </div>
          }
        </section>

        <section class="card">
          <div class="card-head">
            <h2>Recettes</h2>
            <span class="muted small">Le nom du plat doit être celui de la caisse</span>
          </div>
          <ul class="dishes">
            @for (dish of stock()?.dishes ?? []; track dish.id) {
              <li>
                <div>
                  <strong>{{ dish.name }}</strong>
                  <span class="muted small num">{{ recipe(dish) }}</span>
                </div>
                @if (canEdit) { <button class="quiet" (click)="removeDish(dish)" [attr.aria-label]="'Supprimer ' + dish.name">Supprimer</button> }
              </li>
            } @empty {
              <li class="empty">Aucune recette. Sans recette, une vente ne retire rien du stock.</li>
            }
          </ul>
          @if (canEdit && stock()?.ingredients?.length) {
            <form class="recipe" (submit)="addDish($event)">
              <input name="dish" placeholder="Nouveau plat" required maxlength="120" aria-label="Nom du nouveau plat">
              @for (line of draft(); track $index) {
                <div class="line num">
                  <span>{{ ingredientName(line.ingredientId) }}</span>
                  <span class="muted">{{ amount(line.quantity) }} {{ ingredientUnit(line.ingredientId) }}</span>
                  <button type="button" class="quiet" (click)="dropLine($index)">Retirer</button>
                </div>
              }
              <div class="line-add">
                <select #ingredient aria-label="Ingrédient de la recette">
                  @for (item of stock()!.ingredients; track item.id) { <option [value]="item.id">{{ item.name }} ({{ item.unit }})</option> }
                </select>
                <input #quantity class="num" type="number" min="0" step="any" placeholder="Quantité" aria-label="Quantité par plat">
                <button type="button" (click)="addLine(ingredient.value, quantity.value); quantity.value = ''">Ajouter à la recette</button>
              </div>
              <button type="submit" class="primary" [disabled]="!draft().length">Enregistrer le plat</button>
            </form>
          }
        </section>
      </div>
    </div>
  `,
  styles: `
    .notice { margin-bottom: 14px; }
    .layout { display: grid; grid-template-columns: minmax(0, 1.25fr) minmax(0, 1fr); gap: var(--gap); align-items: start; }
    .side { display: grid; gap: var(--gap); }
    /* Sur petit écran le tableau défile dans son cadre : la page ne défile jamais de côté. */
    .scroll { overflow-x: auto; }
    table { width: 100%; border-collapse: collapse; }
    th { text-align: left; font-size: 12px; font-weight: 400; color: var(--faint); height: 32px; padding: 0 8px 0 0; }
    td { height: 52px; padding: 0 8px 0 0; border-top: 1px solid var(--rule); }
    th.num, td.num { text-align: right; }
    td input { width: 92px; height: 36px; text-align: right; }
    .field { display: inline-flex; align-items: center; gap: 8px; }
    .unit { min-width: 38px; text-align: left; }
    .pill { display: inline-block; white-space: nowrap; height: 22px; line-height: 22px; padding: 0 9px; border-radius: 11px; background: var(--waiting-bg); color: var(--waiting); font-size: 12px; font-weight: 500; }
    .add { display: grid; grid-template-columns: minmax(0, 1.4fr) minmax(0, 1fr) 100px 90px auto; gap: 8px; margin-top: 16px; }
    .add input { height: 36px; }
    .pick { display: inline-flex; align-items: center; height: var(--control-height); padding: 0 16px; margin-top: 14px; border-radius: var(--radius-control); cursor: pointer; background: var(--text); color: #000; font-weight: 500; }
    .pick.busy { opacity: 0.6; cursor: default; }
    .pick input { display: none; }
    .pick:focus-within { outline: 2px solid var(--text); outline-offset: 2px; }
    .result { margin-top: 16px; padding-top: 16px; border-top: 1px solid var(--rule); display: grid; gap: 10px; }
    .result strong { font-weight: 500; }
    .result ul, .dishes { list-style: none; margin: 0; padding: 0; }
    .result li { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; min-height: 28px; }
    .result li span:first-child { min-width: 130px; }
    .warn { color: var(--waiting); }
    .dishes li:not(.empty) { display: flex; align-items: center; justify-content: space-between; gap: 12px; min-height: 56px; border-top: 1px solid var(--rule); }
    .dishes li:first-child { border-top: 0; }
    .dishes div { display: grid; min-width: 0; }
    .dishes strong { font-weight: 500; }
    .recipe { display: grid; gap: 8px; margin-top: 16px; padding-top: 16px; border-top: 1px solid var(--rule); }
    .recipe input, .recipe select { height: 36px; }
    .line { display: flex; align-items: center; gap: 12px; }
    .line span:first-child { flex: 1; }
    .line-add { display: grid; grid-template-columns: minmax(0, 1fr) 100px auto; gap: 8px; }
    .recipe > button { justify-self: start; }
    @media (max-width: 1180px) { .layout { grid-template-columns: minmax(0, 1fr); } }
    @media (max-width: 640px) {
      .add, .line-add { grid-template-columns: 1fr 1fr; }
      th:nth-child(3), td:nth-child(3), th:nth-child(5), td:nth-child(5) { display: none; }
      td input { width: 76px; }
    }
  `,
})
export class StockPage implements OnInit {
  private readonly api = inject(ApiService);
  readonly canEdit = inject(SessionService).session()?.role !== 'STAFF';
  readonly amount = amount;

  readonly stock = signal<Stock | null>(null);
  readonly error = signal<string | null>(null);
  readonly result = signal<ImportResult | null>(null);
  readonly importing = signal(false);
  /** Recette en cours de saisie. */
  readonly draft = signal<DraftLine[]>([]);

  /** Ce qu'il faut commander, en une phrase. */
  readonly finding = computed(() => {
    const stock = this.stock();
    if (!stock) return '';
    if (!stock.ingredients.length) return 'Aucun ingrédient pour l’instant.';
    const low = stock.ingredients.filter((item) => item.low);
    if (!low.length) return `Rien à commander : les ${stock.ingredients.length} ingrédients sont au-dessus de leur seuil.`;
    const names = low.map((item) => item.name).join(', ');
    return low.length === 1 ? `1 ingrédient à commander : ${names}.` : `${low.length} ingrédients à commander : ${names}.`;
  });

  ngOnInit(): void {
    this.reload();
  }

  setQuantity(item: Ingredient, event: Event): void {
    const value = Number((event.target as HTMLInputElement).value);
    if (Number.isFinite(value)) this.saveIngredient({ ...item, quantity: value });
  }

  setThreshold(item: Ingredient, event: Event): void {
    const value = Number((event.target as HTMLInputElement).value);
    if (Number.isFinite(value) && value >= 0) this.saveIngredient({ ...item, threshold: value });
  }

  addIngredient(event: Event): void {
    event.preventDefault();
    const form = event.target as HTMLFormElement;
    const data = new FormData(form);
    this.api
      .addIngredient({
        name: String(data.get('name')),
        unit: String(data.get('unit')),
        quantity: Number(data.get('quantity')),
        threshold: Number(data.get('threshold')),
      })
      .subscribe({ next: () => { form.reset(); this.reload(); }, error: (e) => this.fail(e, 'L’ingrédient n’a pas pu être ajouté.') });
  }

  removeIngredient(item: Ingredient): void {
    this.api.deleteIngredient(item.id).subscribe({ next: () => this.reload(), error: (e) => this.fail(e, 'L’ingrédient n’a pas pu être supprimé.') });
  }

  addLine(ingredientId: string, quantity: string): void {
    const value = Number(quantity);
    if (!ingredientId || !(value > 0)) return;
    this.draft.update((lines) => [...lines.filter((line) => line.ingredientId !== ingredientId), { ingredientId, quantity: value }]);
  }

  dropLine(index: number): void {
    this.draft.update((lines) => lines.filter((_, i) => i !== index));
  }

  addDish(event: Event): void {
    event.preventDefault();
    const form = event.target as HTMLFormElement;
    const name = String(new FormData(form).get('dish'));
    this.api.addDish({ name, lines: this.draft() }).subscribe({
      next: () => { form.reset(); this.draft.set([]); this.reload(); },
      error: (e) => this.fail(e, 'Le plat n’a pas pu être enregistré.'),
    });
  }

  removeDish(dish: Dish): void {
    this.api.deleteDish(dish.id).subscribe({ next: () => this.reload(), error: (e) => this.fail(e, 'Le plat n’a pas pu être supprimé.') });
  }

  importSales(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    this.importing.set(true);
    this.api.importSales(file).subscribe({
      next: (result) => { this.result.set(result); this.importing.set(false); this.error.set(null); this.reload(); },
      error: (e) => { this.importing.set(false); this.result.set(null); this.fail(e, 'Le fichier n’a pas pu être importé.'); },
    });
    input.value = ''; // permet de choisir à nouveau le même fichier après une erreur
  }

  recipe(dish: Dish): string {
    if (!dish.lines.length) return 'Recette vide : une vente ne retire rien du stock.';
    return dish.lines.map((line) => `${amount(line.quantity)} ${line.unit} ${line.ingredient.toLowerCase()}`).join(' · ');
  }

  unknownList(result: ImportResult): string {
    return result.unknownDishes.map((dish) => `${dish.name} (${amount(dish.quantity)})`).join(', ');
  }

  ingredientName(id: string): string {
    return this.stock()?.ingredients.find((item) => item.id === id)?.name ?? '';
  }

  ingredientUnit(id: string): string {
    return this.stock()?.ingredients.find((item) => item.id === id)?.unit ?? '';
  }

  when(instant: string): string {
    return new Date(instant).toLocaleString('fr-FR', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' });
  }

  private saveIngredient(item: Ingredient): void {
    this.api.updateIngredient(item).subscribe({ next: () => this.reload(), error: (e) => this.fail(e, 'La modification n’a pas pu être enregistrée.') });
  }

  private reload(): void {
    this.api.stock().subscribe({
      next: (stock) => this.stock.set(stock),
      error: () => this.error.set('Stock indisponible. Vérifiez que le backend tourne.'),
    });
  }

  /** Le backend explique ses refus en clair (« Ce fichier a déjà été importé… ») : on affiche sa phrase. */
  private fail(response: { error?: { detail?: string; message?: string } }, fallback: string): void {
    this.error.set(response.error?.detail || response.error?.message || fallback);
    this.reload();
  }
}
