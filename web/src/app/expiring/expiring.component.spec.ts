import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ExpiringReport, StoreLocation } from '../core/api.models';
import { merchantInterceptor } from '../core/merchant.interceptor';
import { MerchantStore } from '../core/merchant.store';
import { ExpiringComponent } from './expiring.component';

const MERCHANT = '3f2b8c1e-5d4a-4e6f-9a7b-1c2d3e4f5a6b';
const DAYTON: StoreLocation = {
  id: 'a1111111-1111-4111-8111-111111111111', name: 'Zmarket23 Dayton', timezone: 'America/New_York',
  squareLocationId: null, digestEmail: null, createdAt: '2026-11-01T12:00:00Z',
};
const KETTERING: StoreLocation = { ...DAYTON, id: 'b2222222-2222-4222-8222-222222222222', name: 'Kettering' };

function report(overrides: Partial<ExpiringReport> = {}): ExpiringReport {
  return {
    locationId: DAYTON.id,
    locationName: DAYTON.name,
    storeToday: '2026-11-25',
    withinDays: 7,
    counts: { EXPIRED: 1, TODAY: 0, NEXT_3_DAYS: 1, NEXT_7_DAYS: 0, LATER: 0 },
    knownValueAtRiskCents: 1234,
    batchesWithUnknownCost: 1,
    batches: [
      {
        batchId: 'c1', itemId: 'i1', itemName: 'Whole milk 1 gal', sku: null, expiresOn: '2026-11-23',
        daysUntilExpiry: -2, window: 'EXPIRED', quantityRemaining: 3, unitCostCents: 411,
        valueAtRiskCents: 1233, status: 'ACTIVE',
      },
      {
        batchId: 'c2', itemId: 'i2', itemName: 'Injera pack', sku: 'INJ-1', expiresOn: '2026-11-27',
        daysUntilExpiry: 2, window: 'NEXT_3_DAYS', quantityRemaining: 5, unitCostCents: null,
        valueAtRiskCents: null, status: 'MARKED_DOWN',
      },
    ],
    ...overrides,
  };
}

describe('ExpiringComponent', () => {
  let backend: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      imports: [ExpiringComponent],
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

  function start(): ComponentFixture<ExpiringComponent> {
    TestBed.inject(MerchantStore).set(MERCHANT);
    const fixture = TestBed.createComponent(ExpiringComponent);
    fixture.detectChanges();
    return fixture;
  }

  function expectReport(locationId: string, withinDays: string): TestRequest {
    const req = backend.expectOne((r) => r.url === '/api/expiring');
    expect(req.request.params.get('locationId')).toBe(locationId);
    expect(req.request.params.get('withinDays')).toBe(withinDays);
    return req;
  }

  function text(fixture: ComponentFixture<ExpiringComponent>): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  it('asks for the store id before calling the API', () => {
    const fixture = TestBed.createComponent(ExpiringComponent);
    fixture.detectChanges();
    expect(text(fixture)).toContain('Settings');
    backend.expectNone('/api/locations');
  });

  it('loads the first store and shows batches grouped by urgency with money at risk', () => {
    const fixture = start();
    const locations = backend.expectOne('/api/locations');
    expect(locations.request.headers.get('X-Merchant-Id')).toBe(MERCHANT);
    locations.flush([DAYTON]);
    expectReport(DAYTON.id, '7').flush(report());
    fixture.detectChanges();

    const headings = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('h2'))
      .map((h) => h.textContent?.trim());
    expect(headings).toEqual(['Expired: pull from the shelf 1', 'Next 3 days 1']);
    const page = text(fixture);
    expect(page).toContain('Whole milk 1 gal');
    expect(page).toContain('Expired 2 days ago');
    expect(page).toContain('$12.34');
    expect(page).toContain('1 batch without a cost');
    expect(page).toContain('marked down');
    expect(page).toContain('Expires in 2 days');
  });

  it('reloads when the store or the look-ahead changes', () => {
    const fixture = start();
    backend.expectOne('/api/locations').flush([DAYTON, KETTERING]);
    expectReport(DAYTON.id, '7').flush(report());

    fixture.componentInstance.selectLocation(KETTERING.id);
    expectReport(KETTERING.id, '7').flush(report({ locationId: KETTERING.id, locationName: 'Kettering' }));

    fixture.componentInstance.setWithinDays('14');
    expectReport(KETTERING.id, '14').flush(report({ withinDays: 14, batches: [] }));
    fixture.detectChanges();
    expect(text(fixture)).toContain('Nothing expires in the next 14 days.');
  });

  it('ignores a slow answer that arrives after a newer one', () => {
    const fixture = start();
    backend.expectOne('/api/locations').flush([DAYTON, KETTERING]);
    const slow = expectReport(DAYTON.id, '7');
    fixture.componentInstance.selectLocation(KETTERING.id);
    expectReport(KETTERING.id, '7').flush(report({ locationId: KETTERING.id, locationName: 'Kettering' }));
    slow.flush(report());
    expect(fixture.componentInstance.report()?.locationName).toBe('Kettering');
  });

  it('shows the server error detail', () => {
    const fixture = start();
    backend.expectOne('/api/locations').flush([DAYTON]);
    expectReport(DAYTON.id, '7').flush(
      { title: 'Not Found', detail: 'Location not found' },
      { status: 404, statusText: 'Not Found' },
    );
    fixture.detectChanges();
    expect(text(fixture)).toContain('Location not found');
  });

  it('says when the store has no locations yet', () => {
    const fixture = start();
    backend.expectOne('/api/locations').flush([]);
    fixture.detectChanges();
    expect(text(fixture)).toContain('No store locations yet');
  });

  describe('actions on a batch', () => {
    function loaded(): ComponentFixture<ExpiringComponent> {
      const fixture = start();
      backend.expectOne('/api/locations').flush([DAYTON]);
      expectReport(DAYTON.id, '7').flush(report());
      fixture.detectChanges();
      return fixture;
    }

    function el(fixture: ComponentFixture<ExpiringComponent>): HTMLElement {
      return fixture.nativeElement as HTMLElement;
    }

    function button(fixture: ComponentFixture<ExpiringComponent>, label: string): HTMLButtonElement {
      const found = Array.from(el(fixture).querySelectorAll('button')).find((b) => b.textContent?.trim() === label);
      if (!found) {
        throw new Error(`No button "${label}"`);
      }
      return found;
    }

    function openFirst(fixture: ComponentFixture<ExpiringComponent>): HTMLInputElement {
      button(fixture, 'Update').click();
      fixture.detectChanges();
      return el(fixture).querySelector('.actions input') as HTMLInputElement;
    }

    function typeQuantity(fixture: ComponentFixture<ExpiringComponent>, input: HTMLInputElement, value: string): void {
      input.value = value;
      input.dispatchEvent(new Event('input'));
      fixture.detectChanges();
    }

    it('opens one panel with "how many" preset to what is left', () => {
      const fixture = loaded();
      const input = openFirst(fixture);
      expect(input.value).toBe('3');
      expect(el(fixture).querySelectorAll('.actions').length).toBe(1);
      expect(button(fixture, 'Close').getAttribute('aria-expanded')).toBe('true');
    });

    it('records units thrown away, shows the dollar loss and reloads the list', () => {
      const fixture = loaded();
      typeQuantity(fixture, openFirst(fixture), '2');
      button(fixture, 'Thrown away').click();

      const req = backend.expectOne('/api/batches/c1/waste');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ quantity: 2 });
      expect(req.request.headers.get('X-Merchant-Id')).toBe(MERCHANT);
      req.flush({ waste: { id: 'w1', batchId: 'c1', quantity: 2, lossCents: 822, recordedAt: '2026-11-25T20:00:00Z' } });

      expectReport(DAYTON.id, '7').flush(report());
      fixture.detectChanges();
      expect(text(fixture)).toContain('Recorded 2 × Whole milk 1 gal thrown away: $8.22 lost.');
      expect(el(fixture).querySelector('.actions')).toBeNull();
    });

    it('records a sale', () => {
      const fixture = loaded();
      typeQuantity(fixture, openFirst(fixture), '1');
      button(fixture, 'Sold').click();
      const req = backend.expectOne('/api/batches/c1/sales');
      expect(req.request.body).toEqual({ quantity: 1 });
      req.flush({});
      expectReport(DAYTON.id, '7').flush(report());
      fixture.detectChanges();
      expect(text(fixture)).toContain('Recorded 1 × Whole milk 1 gal sold.');
    });

    it('marks down and sells out without a quantity', () => {
      const fixture = loaded();
      openFirst(fixture);
      button(fixture, 'Marked down').click();
      const markDown = backend.expectOne('/api/batches/c1/mark-down');
      expect(markDown.request.method).toBe('POST');
      markDown.flush({});
      expectReport(DAYTON.id, '7').flush(report());
      fixture.detectChanges();

      openFirst(fixture);
      button(fixture, 'Shelf empty: sold out').click();
      backend.expectOne('/api/batches/c1/sold-out').flush({});
      expectReport(DAYTON.id, '7').flush(report());
      fixture.detectChanges();
      expect(text(fixture)).toContain('Whole milk 1 gal is sold out.');
    });

    it('hides "Marked down" on a batch that already is', () => {
      const fixture = loaded();
      const updates = Array.from(el(fixture).querySelectorAll('button')).filter((b) => b.textContent?.trim() === 'Update');
      updates[1].click();
      fixture.detectChanges();
      expect(() => button(fixture, 'Marked down')).toThrow();
      expect(button(fixture, 'Shelf empty: sold out')).toBeTruthy();
    });

    it('catches a bad quantity before calling the API', () => {
      const fixture = loaded();
      typeQuantity(fixture, openFirst(fixture), '4');
      button(fixture, 'Sold').click();
      fixture.detectChanges();
      expect(text(fixture)).toContain('Only 3 left in this batch.');
      backend.expectNone('/api/batches/c1/sales');
    });

    it('keeps the panel open with the server error', () => {
      const fixture = loaded();
      openFirst(fixture);
      button(fixture, 'Thrown away').click();
      backend.expectOne('/api/batches/c1/waste').flush(
        { title: 'Conflict', detail: 'Batch is SOLD_OUT and can no longer change' },
        { status: 409, statusText: 'Conflict' },
      );
      fixture.detectChanges();
      expect(el(fixture).querySelector('.actions')?.textContent).toContain('can no longer change');
      expect(fixture.componentInstance.actionBusy()).toBeFalse();
    });
  });
});
