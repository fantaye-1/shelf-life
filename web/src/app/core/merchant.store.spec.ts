import { TestBed } from '@angular/core/testing';
import { MerchantStore } from './merchant.store';

const ID = '3f2b8c1e-5d4a-4e6f-9a7b-1c2d3e4f5a6b';

describe('MerchantStore', () => {
  beforeEach(() => localStorage.clear());
  afterEach(() => localStorage.clear());

  it('starts empty', () => {
    expect(TestBed.inject(MerchantStore).merchantId()).toBeNull();
  });

  it('saves a valid id, trimmed and lower-cased, and keeps it for the next visit', () => {
    const store = TestBed.inject(MerchantStore);
    expect(store.set(`  ${ID.toUpperCase()} `)).toBeTrue();
    expect(store.merchantId()).toBe(ID);
    expect(localStorage.getItem('shelflife.merchantId')).toBe(ID);
  });

  it('rejects text that is not an id and keeps the old one', () => {
    const store = TestBed.inject(MerchantStore);
    store.set(ID);
    expect(store.set('zmarket23')).toBeFalse();
    expect(store.merchantId()).toBe(ID);
  });

  it('reads the saved id on start and ignores junk', () => {
    localStorage.setItem('shelflife.merchantId', ID);
    expect(new MerchantStore().merchantId()).toBe(ID);
    localStorage.setItem('shelflife.merchantId', 'junk');
    expect(new MerchantStore().merchantId()).toBeNull();
  });

  it('forgets the id', () => {
    const store = TestBed.inject(MerchantStore);
    store.set(ID);
    store.clear();
    expect(store.merchantId()).toBeNull();
    expect(localStorage.getItem('shelflife.merchantId')).toBeNull();
  });
});
