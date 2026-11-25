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
});
