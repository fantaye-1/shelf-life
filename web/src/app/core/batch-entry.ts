import { Item, NewBatch } from './api.models';

/** Largest whole quantity the API accepts in one batch (a Java int). */
export const MAX_QUANTITY = 2_147_483_647;

/**
 * Turns a typed price like "3", "3.5", "$3.49" or "1,299.00" into integer cents without floating
 * point. Blank means "no cost given" (null); anything else that isn't a price is `undefined`.
 */
export function parseDollarsToCents(input: string): number | null | undefined {
  const raw = input.trim();
  if (raw === '') {
    return null;
  }
  const text = raw.replace(/^\$\s*/, '').replace(/,/g, '');
  const match = /^(\d{0,7})(?:\.(\d{0,2}))?$/.exec(text);
  if (!match || (match[1] === '' && !match[2])) {
    return undefined;
  }
  const dollars = match[1] === '' ? 0 : parseInt(match[1], 10);
  const cents = parseInt((match[2] ?? '').padEnd(2, '0'), 10);
  return dollars * 100 + cents;
}

/** The digits of a scanned or typed barcode (spaces and dashes removed), or null if it isn't one. */
export function barcodeDigits(input: string): string | null {
  const digits = input.replace(/[\s-]/g, '');
  return /^\d{8,14}$/.test(digits) ? digits : null;
}

/** GTIN-8/12/13/14 check-digit test, the same rule the API applies. */
export function isValidGtin(digits: string): boolean {
  if (!/^\d+$/.test(digits) || ![8, 12, 13, 14].includes(digits.length)) {
    return false;
  }
  let sum = 0;
  const payload = digits.slice(0, -1);
  for (let i = 0; i < payload.length; i++) {
    // Weights run 3, 1, 3, 1 ... from the digit next to the check digit.
    const weight = (payload.length - i) % 2 === 1 ? 3 : 1;
    sum += Number(payload[i]) * weight;
  }
  return (10 - (sum % 10)) % 10 === Number(digits[digits.length - 1]);
}

/** The store's own calendar date (yyyy-MM-dd) at `now`, so "already expired" matches the shelf. */
export function storeToday(timezone: string, now: Date = new Date()): string {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: timezone, year: 'numeric', month: '2-digit', day: '2-digit',
  }).formatToParts(now);
  const part = (type: string) => parts.find((p) => p.type === type)?.value ?? '';
  return `${part('year')}-${part('month')}-${part('day')}`;
}

/** Items whose name or SKU contains the search text, names that start with it first, then A–Z. */
export function searchItems(items: readonly Item[], query: string, limit = 20): Item[] {
  const q = query.trim().toLowerCase();
  const byName = (a: Item, b: Item) => a.name.localeCompare(b.name);
  if (q === '') {
    return [...items].sort(byName).slice(0, limit);
  }
  const rank = (item: Item) => (item.name.toLowerCase().startsWith(q) ? 0 : 1);
  return items
    .filter((item) => item.name.toLowerCase().includes(q) || (item.sku ?? '').toLowerCase().includes(q))
    .sort((a, b) => rank(a) - rank(b) || byName(a, b))
    .slice(0, limit);
}

export interface BatchEntryInput {
  itemId: string | null;
  locationId: string | null;
  expiresOn: string;
  quantity: string;
  unitCost: string;
}

export type BatchEntryResult = { ok: true; batch: NewBatch } | { ok: false; errors: string[] };

function isRealDate(value: string): boolean {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    return false;
  }
  const [y, m, d] = value.split('-').map(Number);
  const date = new Date(Date.UTC(y, m - 1, d));
  return date.getUTCFullYear() === y && date.getUTCMonth() === m - 1 && date.getUTCDate() === d;
}

/** Checks the batch form and builds the API body, with messages a store clerk can act on. */
export function validateBatchEntry(input: BatchEntryInput): BatchEntryResult {
  const errors: string[] = [];
  if (!input.locationId) {
    errors.push('Pick a store.');
  }
  if (!input.itemId) {
    errors.push('Pick an item.');
  }
  if (!isRealDate(input.expiresOn)) {
    errors.push('Enter the expiry date printed on the package.');
  }
  const qtyText = input.quantity.trim();
  const quantity = /^\d+$/.test(qtyText) ? Number(qtyText) : NaN;
  if (!Number.isSafeInteger(quantity) || quantity < 1 || quantity > MAX_QUANTITY) {
    errors.push('Quantity must be a whole number of at least 1.');
  }
  const unitCostCents = parseDollarsToCents(input.unitCost);
  if (unitCostCents === undefined) {
    errors.push('Unit cost must be a dollar amount like 2.49, or left blank.');
  }
  if (errors.length > 0) {
    return { ok: false, errors };
  }
  return {
    ok: true,
    batch: {
      itemId: input.itemId!,
      locationId: input.locationId!,
      expiresOn: input.expiresOn,
      quantity,
      unitCostCents: unitCostCents ?? null,
    },
  };
}
