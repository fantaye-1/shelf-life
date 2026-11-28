import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { StoreLocation, WasteReport } from '../core/api.models';
import { CentsPipe } from '../core/format';
import { MerchantStore } from '../core/merchant.store';
import { ShelfApi, errorMessage } from '../core/shelf-api.service';
import { MonthBar, REPORT_MONTH_OPTIONS, monthBars, monthLabel, noWaste, shownLoss, unknownCostNote } from '../core/waste-report';

/** Dollars thrown away per month at one store, and the items that cost the most, so the owner can order smarter. */
@Component({
  selector: 'sl-waste-report',
  imports: [RouterLink, CentsPipe],
  templateUrl: './waste-report.component.html',
  styleUrl: './waste-report.component.css',
})
export class WasteReportComponent implements OnInit {
  private readonly api = inject(ShelfApi);
  protected readonly merchant = inject(MerchantStore);
  protected readonly monthOptions = REPORT_MONTH_OPTIONS;
  protected readonly monthLabel = monthLabel;
  protected readonly unknownCostNote = unknownCostNote;
  protected readonly shownLoss = shownLoss;

  readonly locations = signal<StoreLocation[]>([]);
  readonly locationsLoaded = signal(false);
  readonly locationId = signal<string | null>(null);
  readonly months = signal(6);
  readonly report = signal<WasteReport | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly bars = computed<MonthBar[]>(() => monthBars(this.report()?.months ?? []));
  readonly empty = computed(() => {
    const report = this.report();
    return report != null && noWaste(report);
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

  setMonths(value: string | number): void {
    const months = Number(value);
    if (!Number.isInteger(months) || months < 1 || months > 24) {
      return;
    }
    this.months.set(months);
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
    this.api.wasteReport(locationId, this.months()).subscribe({
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
