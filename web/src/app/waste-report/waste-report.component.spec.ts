import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { StoreLocation, WasteReport } from '../core/api.models';
import { merchantInterceptor } from '../core/merchant.interceptor';
import { MerchantStore } from '../core/merchant.store';
import { WasteReportComponent } from './waste-report.component';

const MERCHANT = '3f2b8c1e-5d4a-4e6f-9a7b-1c2d3e4f5a6b';
const DAYTON: StoreLocation = {
  id: 'a1111111-1111-4111-8111-111111111111', name: 'Zmarket23 Dayton', timezone: 'America/New_York',
  squareLocationId: null, digestEmail: null, createdAt: '2026-11-01T12:00:00Z',
};
const KETTERING: StoreLocation = { ...DAYTON, id: 'b2222222-2222-4222-8222-222222222222', name: 'Kettering' };

function report(overrides: Partial<WasteReport> = {}): WasteReport {
  return {
    locationId: DAYTON.id,
    locationName: DAYTON.name,
    firstMonth: '2026-09',
    lastMonth: '2026-11',
    months: [
      { month: '2026-09', unitsWasted: 0, knownLossCents: 0, entriesWithUnknownLoss: 0 },
      { month: '2026-10', unitsWasted: 6, knownLossCents: 2466, entriesWithUnknownLoss: 0 },
      { month: '2026-11', unitsWasted: 4, knownLossCents: 822, entriesWithUnknownLoss: 1 },
    ],
    unitsWasted: 10,
    knownLossCents: 3288,
    entriesWithUnknownLoss: 1,
    topItems: [
      { itemId: 'i1', itemName: 'Whole milk 1 gal', sku: null, unitsWasted: 8, knownLossCents: 3288, entriesWithUnknownLoss: 0 },
      { itemId: 'i2', itemName: 'Injera pack', sku: 'INJ-1', unitsWasted: 2, knownLossCents: 0, entriesWithUnknownLoss: 1 },
    ],
    ...overrides,
  };
}

describe('WasteReportComponent', () => {
  let backend: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      imports: [WasteReportComponent],
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

  function start(): ComponentFixture<WasteReportComponent> {
    TestBed.inject(MerchantStore).set(MERCHANT);
    const fixture = TestBed.createComponent(WasteReportComponent);
    fixture.detectChanges();
    return fixture;
  }

  function expectReport(locationId: string, months: string): TestRequest {
    const req = backend.expectOne((r) => r.url === '/api/waste/report');
    expect(req.request.params.get('locationId')).toBe(locationId);
    expect(req.request.params.get('months')).toBe(months);
    expect(req.request.headers.get('X-Merchant-Id')).toBe(MERCHANT);
    return req;
  }

  function el(fixture: ComponentFixture<WasteReportComponent>): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function text(fixture: ComponentFixture<WasteReportComponent>): string {
    return el(fixture).textContent ?? '';
  }

  it('asks for the store id before calling the API', () => {
    const fixture = TestBed.createComponent(WasteReportComponent);
    fixture.detectChanges();
    expect(text(fixture)).toContain('Settings');
    backend.expectNone('/api/locations');
  });

  it('shows the dollar total, months newest first and the most wasted items', () => {
    const fixture = start();
    backend.expectOne('/api/locations').flush([DAYTON]);
    expectReport(DAYTON.id, '6').flush(report());
    fixture.detectChanges();

    const page = text(fixture);
    expect(page).toContain('$32.88');
    expect(page).toContain('Sep 2026 to Nov 2026');
    expect(page).toContain('+ 1 entry without a cost (not counted in dollars)');
    expect(page).toMatch(/10\s*units wasted/);

    const months = Array.from(el(fixture).querySelectorAll('.months .label')).map((e) => e.textContent?.trim());
    expect(months).toEqual(['Nov 2026', 'Oct 2026', 'Sep 2026']);
    const widths = Array.from(el(fixture).querySelectorAll<HTMLElement>('.bar span')).map((e) => e.style.width);
    expect(widths).toEqual(['33%', '100%', '0%']);

    const items = Array.from(el(fixture).querySelectorAll('.items .label')).map((e) => e.textContent?.trim());
    expect(items).toEqual(['Whole milk 1 gal', 'Injera pack']);
    const itemAmounts = Array.from(el(fixture).querySelectorAll('.items .amount')).map((e) => e.textContent?.trim());
    expect(itemAmounts).toEqual(['$32.88', '—']);
    expect(page).toContain('SKU INJ-1');
  });

  it('says so when nothing was thrown away', () => {
    const fixture = start();
    backend.expectOne('/api/locations').flush([DAYTON]);
    expectReport(DAYTON.id, '6').flush(report({
      months: [{ month: '2026-11', unitsWasted: 0, knownLossCents: 0, entriesWithUnknownLoss: 0 }],
      unitsWasted: 0, knownLossCents: 0, entriesWithUnknownLoss: 0, topItems: [],
    }));
    fixture.detectChanges();
    expect(text(fixture)).toContain('Nothing was thrown away in this period.');
    expect(el(fixture).querySelector('.months')).toBeNull();
  });

  it('names an item that has been removed', () => {
    const fixture = start();
    backend.expectOne('/api/locations').flush([DAYTON]);
    expectReport(DAYTON.id, '6').flush(report({
      topItems: [{ itemId: 'gone', itemName: null, sku: null, unitsWasted: 1, knownLossCents: 100, entriesWithUnknownLoss: 0 }],
    }));
    fixture.detectChanges();
    expect(text(fixture)).toContain('Removed item');
  });

  it('reloads when the store or the period changes, and ignores bad periods', () => {
    const fixture = start();
    backend.expectOne('/api/locations').flush([DAYTON, KETTERING]);
    expectReport(DAYTON.id, '6').flush(report());

    fixture.componentInstance.selectLocation(KETTERING.id);
    expectReport(KETTERING.id, '6').flush(report({ locationId: KETTERING.id, locationName: 'Kettering' }));

    fixture.componentInstance.setMonths('12');
    expectReport(KETTERING.id, '12').flush(report({ locationId: KETTERING.id, locationName: 'Kettering' }));

    fixture.componentInstance.setMonths('25');
    fixture.componentInstance.setMonths('0');
    fixture.componentInstance.setMonths('abc');
    backend.expectNone('/api/waste/report');
    expect(fixture.componentInstance.months()).toBe(12);
  });

  it('ignores a slow answer that arrives after a newer one', () => {
    const fixture = start();
    backend.expectOne('/api/locations').flush([DAYTON, KETTERING]);
    const slow = expectReport(DAYTON.id, '6');
    fixture.componentInstance.selectLocation(KETTERING.id);
    expectReport(KETTERING.id, '6').flush(report({ locationId: KETTERING.id, locationName: 'Kettering' }));
    slow.flush(report());
    expect(fixture.componentInstance.report()?.locationName).toBe('Kettering');
  });

  it('shows the server error detail', () => {
    const fixture = start();
    backend.expectOne('/api/locations').flush([DAYTON]);
    expectReport(DAYTON.id, '6').flush(
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
