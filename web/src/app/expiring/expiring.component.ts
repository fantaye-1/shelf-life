import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ExpiringBatch, ExpiringReport, ExpiryWindow, StoreLocation } from '../core/api.models';
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
}
