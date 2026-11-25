import { Item } from './api.models';
import {
  barcodeDigits, isValidGtin, parseDollarsToCents, searchItems, storeToday, validateBatchEntry,
} from './batch-entry';

function item(name: string, sku: string | null = null): Item {
  return { id: name, name, sku, gtin: null, squareVariationId: null, createdAt: '2026-11-01T00:00:00Z' };
}

describe('parseDollarsToCents', () => {
  it('reads common ways of typing a price as exact cents', () => {
    expect(parseDollarsToCents('3')).toBe(300);
    expect(parseDollarsToCents('3.5')).toBe(350);
    expect(parseDollarsToCents('$2.49')).toBe(249);
    expect(parseDollarsToCents(' 0.07 ')).toBe(7);
    expect(parseDollarsToCents('.99')).toBe(99);
    expect(parseDollarsToCents('1,299.00')).toBe(129900);
    expect(parseDollarsToCents('4.10')).toBe(410);
    expect(parseDollarsToCents('0')).toBe(0);
  });

  it('treats blank as no cost given', () => {
    expect(parseDollarsToCents('')).toBeNull();
    expect(parseDollarsToCents('   ')).toBeNull();
  });

  it('rejects anything that is not a price', () => {
    for (const bad of ['abc', '-1', '1.234', '1.2.3', '.', '$', '1e3', '12345678']) {
      expect(parseDollarsToCents(bad)).withContext(bad).toBeUndefined();
    }
  });
});

describe('barcodes', () => {
  it('pulls the digits out of a scanned or typed code', () => {
    expect(barcodeDigits('0 12345 67890 5')).toBe('012345678905');
    expect(barcodeDigits('4006381-333931')).toBe('4006381333931');
    expect(barcodeDigits('milk')).toBeNull();
    expect(barcodeDigits('1234567')).toBeNull();
    expect(barcodeDigits('123456789012345')).toBeNull();
  });

  it('checks the GTIN check digit', () => {
    expect(isValidGtin('012345678905')).toBeTrue(); // UPC-A
    expect(isValidGtin('4006381333931')).toBeTrue(); // EAN-13
    expect(isValidGtin('96385074')).toBeTrue(); // EAN-8
    expect(isValidGtin('10012345678902')).toBeTrue(); // GTIN-14
    expect(isValidGtin('012345678904')).toBeFalse();
    expect(isValidGtin('1234567890')).toBeFalse(); // 10 digits is no GTIN
  });
});

describe('storeToday', () => {
  it("uses the store's own calendar day", () => {
    const lateEvening = new Date('2026-11-26T03:30:00Z'); // 10:30 PM on Nov 25 in Dayton
    expect(storeToday('America/New_York', lateEvening)).toBe('2026-11-25');
    expect(storeToday('UTC', lateEvening)).toBe('2026-11-26');
  });
});

describe('searchItems', () => {
  const items = [item('Whole milk 1 gal', 'MLK-1'), item('Chocolate milk'), item('Injera pack', 'INJ-1'), item('Milk bread')];

  it('matches name or SKU, names starting with the text first', () => {
    expect(searchItems(items, 'milk').map((i) => i.name)).toEqual(['Milk bread', 'Chocolate milk', 'Whole milk 1 gal']);
    expect(searchItems(items, 'inj-').map((i) => i.name)).toEqual(['Injera pack']);
    expect(searchItems(items, 'rice')).toEqual([]);
  });

  it('caps the list', () => {
    expect(searchItems(items, '', 2).length).toBe(2);
  });
});

describe('validateBatchEntry', () => {
  const good = { itemId: 'i1', locationId: 'l1', expiresOn: '2026-12-01', quantity: '12', unitCost: '2.49' };

  it('builds the API body with integer cents', () => {
    expect(validateBatchEntry(good)).toEqual({
      ok: true,
      batch: { itemId: 'i1', locationId: 'l1', expiresOn: '2026-12-01', quantity: 12, unitCostCents: 249 },
    });
  });

  it('sends a blank cost as unknown, not zero', () => {
    const result = validateBatchEntry({ ...good, unitCost: '' });
    expect(result.ok && result.batch.unitCostCents).toBeNull();
  });

  it('lists every problem at once', () => {
    const result = validateBatchEntry({ itemId: null, locationId: null, expiresOn: '2026-02-30', quantity: '0', unitCost: 'x' });
    expect(result.ok).toBeFalse();
    expect(result.ok ? [] : result.errors).toEqual([
      'Pick a store.',
      'Pick an item.',
      'Enter the expiry date printed on the package.',
      'Quantity must be a whole number of at least 1.',
      'Unit cost must be a dollar amount like 2.49, or left blank.',
    ]);
  });

  it('rejects fractional, negative and oversized quantities', () => {
    for (const quantity of ['1.5', '-3', '', 'ten', '2147483648']) {
      expect(validateBatchEntry({ ...good, quantity }).ok).withContext(quantity).toBeFalse();
    }
  });
});
