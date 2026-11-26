import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Observable, map } from 'rxjs';
import { ExpiringBatch, ExpiringReport, ExpiryWindow, StoreLocation } from '../core/api.models';
import { BatchAction, actionDoneMessage, parseActionQuantity } from '../core/batch-actions';
import { CentsPipe, ExpiryLabelPipe } from '../core/format';
import { MerchantStore } from '../core/merchant.store';
import { ShelfApi, errorMessage } from '../core/shelf-api.service';

interface WindowGroup {
  window: ExpiryWindow;
  title: string;
  batches: ExpiringBatch[];
}

const WINDOW_TITLES: ReadonlyArray<[ExpiryWindow, string]> = [
  ['EXPIRED', 'Expired: pull from the shelf'],
  ['TODAY', 'Expires today'],
  ['NEXT_3_DAYS', 'Next 3 days'],
  ['NEXT_7_DAYS', 'Next 7 days'],
  ['LATER', 'Later'],
];

export const LOOK_AHEAD_OPTIONS = [3, 7, 14, 30] as const;

/** The expiring-soon list for one store: what to pull, mark down or sell first. */
@Component({
  selector: 'sl-expiring',
  imports: [RouterLink, CentsPipe, ExpiryLabelPipe],
  templateUrl: './expiring.component.html',
  styleUrl: './expiring.component.css',
})
export class ExpiringComponent implements OnInit {
  private readonly api = inject(ShelfApi);
  protected readonly merchant = inject(MerchantStore);
  protected readonly lookAheadOptions = LOOK_AHEAD_OPTIONS;

  readonly locations = signal<StoreLocation[]>([]);
  readonly locationId = signal<string | null>(null);
  readonly withinDays = signal(7);
  readonly report = signal<ExpiringReport | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly locationsLoaded = signal(false);

  /** The batch whose action panel is open (one at a time), and that panel's state. */
  readonly openBatchId = signal<string | null>(null);
  readonly actionQuantity = signal('');
  readonly actionBusy = signal(false);
  readonly actionError = signal<string | null>(null);
  /** Confirmation of the last recorded action, shown above the list. */
  readonly done = signal<string | null>(null);

  readonly groups = computed<WindowGroup[]>(() => {
    const report = this.report();
    if (!report) {
      return [];
    }
    return WINDOW_TITLES.map(([window, title]) => ({
      window,
      title,
      batches: report.batches.filter((b) => b.window === window),
    })).filter((group) => group.batches.length > 0);
  });

  /** Only the newest request may update the screen, so a slow old answer can't overwrite it. */
  private requestSeq = 0;

  ngOnInit(): void {
    if (!this.merchant.merchantId()) {
      return;
    }
    this.api.locations().subscribe({
      next: (locations) => {
        this.locations.set(locations);
        this.locationsLoaded.set(true);
        if (locations.length > 0) {
          this.selectLocation(locations[0].id);
        }
      },
      error: (err) => this.error.set(errorMessage(err)),
    });
  }

  selectLocation(id: string): void {
    this.locationId.set(id);
    this.closeActions();
    this.done.set(null);
    this.load();
  }

  setWithinDays(value: string | number): void {
    const days = Number(value);
    if (!Number.isInteger(days) || days < 0 || days > 30) {
      return;
    }
    this.withinDays.set(days);
    this.load();
  }

  load(): void {
    const locationId = this.locationId();
    if (!locationId) {
      return;
    }
    const seq = ++this.requestSeq;
    this.loading.set(true);
    this.error.set(null);
    this.api.expiring(locationId, this.withinDays()).subscribe({
      next: (report) => {
        if (seq === this.requestSeq) {
          this.report.set(report);
          this.loading.set(false);
        }
      },
      error: (err) => {
        if (seq === this.requestSeq) {
          this.report.set(null);
          this.error.set(errorMessage(err));
          this.loading.set(false);
        }
      },
    });
  }

  /** Opens the action panel for a batch, with "how many" preset to everything left. */
  toggleActions(batch: ExpiringBatch): void {
    if (this.openBatchId() === batch.batchId) {
      this.closeActions();
      return;
    }
    this.openBatchId.set(batch.batchId);
    this.actionQuantity.set(String(batch.quantityRemaining));
    this.actionError.set(null);
  }

  closeActions(): void {
    this.openBatchId.set(null);
    this.actionError.set(null);
  }

  /** Records an action, then reloads the list so totals and groups match the server. */
  act(batch: ExpiringBatch, action: BatchAction): void {
    if (this.actionBusy()) {
      return;
    }
    let quantity = 0;
    if (action === 'sold' || action === 'wasted') {
      const parsed = parseActionQuantity(this.actionQuantity(), batch.quantityRemaining);
      if (!parsed.ok) {
        this.actionError.set(parsed.error);
        return;
      }
      quantity = parsed.quantity;
    }
    this.actionBusy.set(true);
    this.actionError.set(null);
    this.request(batch.batchId, action, quantity).subscribe({
      next: (lossCents) => {
        this.actionBusy.set(false);
        this.done.set(actionDoneMessage(action, batch.itemName, quantity, lossCents));
        this.closeActions();
        this.load();
      },
      error: (err) => {
        this.actionBusy.set(false);
        this.actionError.set(errorMessage(err));
      },
    });
  }

  /** Calls the API for one action; emits the dollar loss for waste, null otherwise. */
  private request(batchId: string, action: BatchAction, quantity: number): Observable<number | null> {
    switch (action) {
      case 'sold':
        return this.api.recordSale(batchId, quantity).pipe(map(() => null));
      case 'wasted':
        return this.api.recordWaste(batchId, quantity).pipe(map((r) => r.waste.lossCents));
      case 'markDown':
        return this.api.markDown(batchId).pipe(map(() => null));
      case 'soldOut':
        return this.api.markSoldOut(batchId).pipe(map(() => null));
    }
  }
}
