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

export interface Item {
  id: string;
  name: string;
  sku: string | null;
  gtin: string | null;
  squareVariationId: string | null;
  createdAt: string;
}

export interface NewItem {
  name: string;
  sku: string | null;
  gtin: string | null;
}

/** Body for logging a batch. `expiresOn` is the date printed on the package, yyyy-MM-dd. */
export interface NewBatch {
  itemId: string;
  locationId: string;
  expiresOn: string;
  quantity: number;
  unitCostCents: number | null;
}

export interface Batch {
  id: string;
  itemId: string;
  locationId: string;
  expiresOn: string;
  daysUntilExpiry: number;
  expired: boolean;
  quantityReceived: number;
  quantityRemaining: number;
  unitCostCents: number | null;
  status: BatchStatus;
  createdAt: string;
}

/** One waste record. `lossCents` is null when the batch had no unit cost (unknown, not zero). */
export interface WasteRecord {
  id: string;
  batchId: string;
  quantity: number;
  lossCents: number | null;
  recordedAt: string;
}

/** Answer to throwing units away: the new waste record and the batch as it is now. */
export interface WasteRecorded {
  waste: WasteRecord;
  batch: Batch;
}

/** Waste in one calendar month at the store. `month` is yyyy-MM in the store's own time zone. */
export interface WasteMonth {
  month: string;
  unitsWasted: number;
  knownLossCents: number;
  entriesWithUnknownLoss: number;
}

/** One item's waste over the report window. `itemName` is null only if the item is gone. */
export interface WasteTopItem {
  itemId: string;
  itemName: string | null;
  sku: string | null;
  unitsWasted: number;
  knownLossCents: number;
  entriesWithUnknownLoss: number;
}

/** Waste at one store over the last few months. Entries without a cost are counted, never priced as $0. */
export interface WasteReport {
  locationId: string;
  locationName: string;
  firstMonth: string;
  lastMonth: string;
  /** Every month in the window, oldest first, including months with no waste. */
  months: WasteMonth[];
  unitsWasted: number;
  knownLossCents: number;
  entriesWithUnknownLoss: number;
  topItems: WasteTopItem[];
}
