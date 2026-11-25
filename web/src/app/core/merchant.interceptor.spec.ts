import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { merchantInterceptor } from './merchant.interceptor';
import { MerchantStore } from './merchant.store';

const ID = '3f2b8c1e-5d4a-4e6f-9a7b-1c2d3e4f5a6b';

describe('merchantInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([merchantInterceptor])), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    backend.verify();
    localStorage.clear();
  });

  it('sends the store id to our API', () => {
    TestBed.inject(MerchantStore).set(ID);
    http.get('/api/items').subscribe();
    expect(backend.expectOne('/api/items').request.headers.get('X-Merchant-Id')).toBe(ID);
  });

  it('never sends the store id to another host', () => {
    TestBed.inject(MerchantStore).set(ID);
    http.get('https://example.com/api/items').subscribe();
    expect(backend.expectOne('https://example.com/api/items').request.headers.has('X-Merchant-Id')).toBeFalse();
  });

  it('sends no header when no store is set', () => {
    http.get('/api/items').subscribe();
    expect(backend.expectOne('/api/items').request.headers.has('X-Merchant-Id')).toBeFalse();
  });
});
