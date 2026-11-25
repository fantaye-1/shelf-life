/** Shapes returned by the ShelfLife API. Money is always integer cents. */

export interface StoreLocation {
  id: string;
  name: string;
  timezone: string;
  squareLocationId: string | null;
  digestEmail: string | null;
  createdAt: string;
}

export type ExpiryWindow = 'EXPIRED' | 'TODAY' | 'NEXT_3_DAYS' | 'NEXT_7_DAYS' | 'LATER';

export type BatchStatus = 'ACTIVE' | 'MARKED_DOWN' | 'SOLD_OUT' | 'WASTED';

export interface ExpiringBatch {
  batchId: string;
  itemId: string;
  itemName: string;
  sku: string | null;
  /** The store's calendar date, ISO yyyy-MM-dd. */
  expiresOn: string;
  daysUntilExpiry: number;
  window: ExpiryWindow;
  quantityRemaining: number;
  unitCostCents: number | null;
  valueAtRiskCents: number | null;
  status: BatchStatus;
}

export interface ExpiringReport {
  locationId: string;
  locationName: string;
  storeToday: string;
  withinDays: number;
  counts: Record<ExpiryWindow, number>;
  knownValueAtRiskCents: number;
  batchesWithUnknownCost: number;
  batches: ExpiringBatch[];
}
