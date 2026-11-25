import { Component, ElementRef, OnInit, computed, inject, signal, viewChild } from '@angular/core';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { Item, StoreLocation } from '../core/api.models';
import { barcodeDigits, isValidGtin, searchItems, storeToday, validateBatchEntry } from '../core/batch-entry';
import { CentsPipe } from '../core/format';
import { MerchantStore } from '../core/merchant.store';
import { ShelfApi, errorMessage } from '../core/shelf-api.service';

interface LoggedBatch {
  itemName: string;
  quantity: number;
  expiresOn: string;
  unitCostCents: number | null;
}

/** Phone-first screen for logging a delivery: pick or scan an item, then expiry date and quantity. */
@Component({
  selector: 'sl-batch-entry',
  imports: [RouterLink, CentsPipe],
  templateUrl: './batch-entry.component.html',
  styleUrl: './batch-entry.component.css',
})
export class BatchEntryComponent implements OnInit {
  private readonly api = inject(ShelfApi);
  protected readonly merchant = inject(MerchantStore);
  private readonly searchBox = viewChild<ElementRef<HTMLInputElement>>('searchInput');

  readonly locations = signal<StoreLocation[]>([]);
  readonly items = signal<Item[]>([]);
  readonly loaded = signal(false);
  readonly locationId = signal<string | null>(null);

  readonly query = signal('');
  readonly barcodeMatches = signal<Item[] | null>(null);
  readonly selectedItem = signal<Item | null>(null);

  readonly expiresOn = signal('');
  readonly quantity = signal('');
  readonly unitCost = signal('');

  readonly saving = signal(false);
  readonly errors = signal<string[]>([]);
  readonly lastLogged = signal<LoggedBatch | null>(null);

  /** Only the newest barcode lookup may update the list. */
  private lookupSeq = 0;

  readonly barcode = computed(() => barcodeDigits(this.query()));
  readonly barcodeInvalid = computed(() => {
    const code = this.barcode();
    return code !== null && !isValidGtin(code);
  });

  readonly results = computed<Item[]>(() => {
    if (this.barcode() !== null) {
      return this.barcodeMatches() ?? [];
    }
    return this.query().trim() === '' ? [] : searchItems(this.items(), this.query());
  });

  readonly location = computed(() => this.locations().find((l) => l.id === this.locationId()) ?? null);

  /** A past date is allowed (stock found already expired) but is called out before saving. */
  readonly alreadyExpired = computed(() => {
    const loc = this.location();
    const date = this.expiresOn();
    return loc !== null && /^\d{4}-\d{2}-\d{2}$/.test(date) && date < storeToday(loc.timezone);
  });

  ngOnInit(): void {
    if (!this.merchant.merchantId()) {
      return;
    }
    forkJoin({ locations: this.api.locations(), items: this.api.items() }).subscribe({
      next: ({ locations, items }) => {
        this.locations.set(locations);
        this.items.set(items);
        this.locationId.set(locations[0]?.id ?? null);
        this.loaded.set(true);
      },
      error: (err) => this.errors.set([errorMessage(err)]),
    });
  }

  selectLocation(id: string): void {
    this.locationId.set(id);
  }

  search(value: string): void {
    this.query.set(value);
    this.selectedItem.set(null);
    this.barcodeMatches.set(null);
    const code = barcodeDigits(value);
    if (code === null || !isValidGtin(code)) {
      return;
    }
    const seq = ++this.lookupSeq;
    this.api.itemsByBarcode(code).subscribe({
      next: (matches) => {
        if (seq !== this.lookupSeq) {
          return;
        }
        this.barcodeMatches.set(matches);
        if (matches.length === 1) {
          // A scan that finds exactly one item goes straight to the date and quantity.
          this.choose(matches[0]);
        }
      },
      error: (err) => {
        if (seq === this.lookupSeq) {
          this.barcodeMatches.set([]);
          this.errors.set([errorMessage(err)]);
        }
      },
    });
  }

  choose(item: Item): void {
    this.selectedItem.set(item);
    this.errors.set([]);
  }

  changeItem(): void {
    this.selectedItem.set(null);
    this.focusSearch();
  }

  /** Adds the searched name (or scanned barcode) as a new item and picks it. */
  addItem(name: string): void {
    const trimmed = name.trim();
    if (!trimmed) {
      this.errors.set(['Give the new item a name.']);
      return;
    }
    const code = this.barcode();
    this.saving.set(true);
    this.api.createItem({ name: trimmed, sku: null, gtin: code }).subscribe({
      next: (item) => {
        this.items.update((items) => [...items, item]);
        this.saving.set(false);
        this.choose(item);
      },
      error: (err) => {
        this.saving.set(false);
        this.errors.set([errorMessage(err)]);
      },
    });
  }

  submit(): void {
    if (this.saving()) {
      return;
    }
    const item = this.selectedItem();
    const result = validateBatchEntry({
      itemId: item?.id ?? null,
      locationId: this.locationId(),
      expiresOn: this.expiresOn(),
      quantity: this.quantity(),
      unitCost: this.unitCost(),
    });
    if (!result.ok) {
      this.errors.set(result.errors);
      return;
    }
    this.saving.set(true);
    this.errors.set([]);
    this.api.logBatch(result.batch).subscribe({
      next: (batch) => {
        this.saving.set(false);
        this.lastLogged.set({
          itemName: item!.name,
          quantity: batch.quantityReceived,
          expiresOn: batch.expiresOn,
          unitCostCents: batch.unitCostCents,
        });
        // Ready for the next box from the same delivery: keep the store, clear the rest.
        this.query.set('');
        this.barcodeMatches.set(null);
        this.selectedItem.set(null);
        this.expiresOn.set('');
        this.quantity.set('');
        this.unitCost.set('');
        this.focusSearch();
      },
      error: (err) => {
        this.saving.set(false);
        this.errors.set([errorMessage(err)]);
      },
    });
  }

  private focusSearch(): void {
    queueMicrotask(() => this.searchBox()?.nativeElement.focus());
  }
}
