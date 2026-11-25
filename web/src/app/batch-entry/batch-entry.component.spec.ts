import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Batch, Item, StoreLocation } from '../core/api.models';
import { merchantInterceptor } from '../core/merchant.interceptor';
import { MerchantStore } from '../core/merchant.store';
import { BatchEntryComponent } from './batch-entry.component';

const MERCHANT = '3f2b8c1e-5d4a-4e6f-9a7b-1c2d3e4f5a6b';
const DAYTON: StoreLocation = {
  id: 'a1111111-1111-4111-8111-111111111111', name: 'Zmarket23 Dayton', timezone: 'America/New_York',
  squareLocationId: null, digestEmail: null, createdAt: '2026-11-01T12:00:00Z',
};
const KETTERING: StoreLocation = { ...DAYTON, id: 'b2222222-2222-4222-8222-222222222222', name: 'Kettering' };
const MILK: Item = {
  id: 'd1111111-1111-4111-8111-111111111111', name: 'Whole milk 1 gal', sku: 'MLK-1', gtin: '012345678905',
  squareVariationId: null, createdAt: '2026-11-01T12:00:00Z',
};
const INJERA: Item = { ...MILK, id: 'd2222222-2222-4222-8222-222222222222', name: 'Injera pack', sku: null, gtin: null };

function batch(overrides: Partial<Batch> = {}): Batch {
  return {
    id: 'e1', itemId: MILK.id, locationId: DAYTON.id, expiresOn: '2026-12-01', daysUntilExpiry: 6,
    expired: false, quantityReceived: 12, quantityRemaining: 12, unitCostCents: 249, status: 'ACTIVE',
    createdAt: '2026-11-25T12:00:00Z', ...overrides,
  };
}

describe('BatchEntryComponent', () => {
  let backend: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      imports: [BatchEntryComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([merchantInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    backend.verify();
    localStorage.clear();
  });

  function start(locations: StoreLocation[] = [DAYTON], items: Item[] = [MILK, INJERA]): ComponentFixture<BatchEntryComponent> {
    TestBed.inject(MerchantStore).set(MERCHANT);
    const fixture = TestBed.createComponent(BatchEntryComponent);
    fixture.detectChanges();
    const loc = backend.expectOne('/api/locations');
    expect(loc.request.headers.get('X-Merchant-Id')).toBe(MERCHANT);
    loc.flush(locations);
    backend.expectOne((r) => r.url === '/api/items' && !r.params.has('gtin')).flush(items);
    fixture.detectChanges();
    return fixture;
  }

  function text(fixture: ComponentFixture<BatchEntryComponent>): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  function fill(c: BatchEntryComponent, expiresOn: string, quantity: string, unitCost: string): void {
    c.expiresOn.set(expiresOn);
    c.quantity.set(quantity);
    c.unitCost.set(unitCost);
  }

  it('asks for the store id before calling the API', () => {
    const fixture = TestBed.createComponent(BatchEntryComponent);
    fixture.detectChanges();
    expect(text(fixture)).toContain('Settings');
    backend.expectNone('/api/locations');
  });

  it('searches items by name and logs a batch with the cost in cents', () => {
    const fixture = start();
    const c = fixture.componentInstance;
    c.search('inj');
    fixture.detectChanges();
    expect(text(fixture)).toContain('Injera pack');
    expect(text(fixture)).not.toContain('Whole milk');

    c.choose(INJERA);
    fill(c, '2026-12-01', '12', '$2.49');
    c.submit();
    const req = backend.expectOne('/api/batches');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      itemId: INJERA.id, locationId: DAYTON.id, expiresOn: '2026-12-01', quantity: 12, unitCostCents: 249,
    });
    req.flush(batch({ itemId: INJERA.id }));
    fixture.detectChanges();

    expect(text(fixture)).toContain('Logged 12 × Injera pack, expires 2026-12-01');
    expect(text(fixture)).toContain('$2.49');
    // Ready for the next entry at the same store.
    expect(c.selectedItem()).toBeNull();
    expect(c.quantity()).toBe('');
    expect(c.locationId()).toBe(DAYTON.id);
  });

  it('picks the item straight away when a scanned barcode has one match', () => {
    const fixture = start();
    const c = fixture.componentInstance;
    c.search('0 12345 67890 5');
    backend.expectOne((r) => r.url === '/api/items' && r.params.get('gtin') === '012345678905').flush([MILK]);
    fixture.detectChanges();
    expect(c.selectedItem()).toEqual(MILK);
    expect(text(fixture)).toContain('Whole milk 1 gal');
  });

  it("doesn't look up a barcode with a wrong check digit", () => {
    const fixture = start();
    fixture.componentInstance.search('012345678904');
    fixture.detectChanges();
    backend.expectNone((r) => r.params.has('gtin'));
    expect(text(fixture)).toContain("check digit doesn't match");
  });

  it('adds an unknown scanned barcode as a new item', () => {
    const fixture = start();
    const c = fixture.componentInstance;
    c.search('4006381333931');
    backend.expectOne((r) => r.params.get('gtin') === '4006381333931').flush([]);
    fixture.detectChanges();
    expect(text(fixture)).toContain('Add item with this barcode');

    c.addItem('  Berbere 1 lb ');
    const req = backend.expectOne((r) => r.url === '/api/items' && r.method === 'POST');
    expect(req.request.body).toEqual({ name: 'Berbere 1 lb', sku: null, gtin: '4006381333931' });
    const created: Item = { ...INJERA, id: 'd3', name: 'Berbere 1 lb', gtin: '4006381333931' };
    req.flush(created);
    expect(c.selectedItem()).toEqual(created);
    expect(c.items()).toContain(created);
  });

  it('shows every form problem and sends nothing', () => {
    const fixture = start();
    const c = fixture.componentInstance;
    fill(c, '', '0', 'abc');
    c.submit();
    fixture.detectChanges();
    const page = text(fixture);
    expect(page).toContain('Pick an item.');
    expect(page).toContain('Enter the expiry date');
    expect(page).toContain('Quantity must be a whole number');
    expect(page).toContain('Unit cost must be a dollar amount');
    backend.expectNone('/api/batches');
  });

  it('logs at the store picked in the store list', () => {
    const fixture = start([DAYTON, KETTERING]);
    const c = fixture.componentInstance;
    c.selectLocation(KETTERING.id);
    c.choose(MILK);
    fill(c, '2026-12-01', '3', '');
    c.submit();
    const req = backend.expectOne('/api/batches');
    expect(req.request.body.locationId).toBe(KETTERING.id);
    expect(req.request.body.unitCostCents).toBeNull();
    req.flush(batch({ locationId: KETTERING.id, quantityReceived: 3, unitCostCents: null }));
  });

  it('warns about a date that has already passed but still allows it', () => {
    const fixture = start();
    const c = fixture.componentInstance;
    c.expiresOn.set('2020-01-01');
    fixture.detectChanges();
    expect(text(fixture)).toContain('already passed at Zmarket23 Dayton');
  });

  it('keeps the form and shows the server error detail when saving fails', () => {
    const fixture = start();
    const c = fixture.componentInstance;
    c.choose(MILK);
    fill(c, '2026-12-01', '5', '1');
    c.submit();
    backend.expectOne('/api/batches').flush(
      { title: 'Not Found', detail: 'Item not found' },
      { status: 404, statusText: 'Not Found' },
    );
    fixture.detectChanges();
    expect(text(fixture)).toContain('Item not found');
    expect(c.selectedItem()).toEqual(MILK);
    expect(c.quantity()).toBe('5');
    expect(c.saving()).toBeFalse();
  });
});
