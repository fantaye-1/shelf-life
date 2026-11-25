import { Injectable, signal } from '@angular/core';

const STORAGE_KEY = 'shelflife.merchantId';
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/**
 * Which store this browser works for. Temporary until app login (M8): the id is pasted on the
 * Settings screen, kept in this browser only, and sent as the X-Merchant-Id header.
 */
@Injectable({ providedIn: 'root' })
export class MerchantStore {
  private readonly id = signal<string | null>(readStored());
  readonly merchantId = this.id.asReadonly();

  /** Saves the id if it looks like one; returns false (and changes nothing) otherwise. */
  set(value: string): boolean {
    const id = value.trim().toLowerCase();
    if (!UUID.test(id)) {
      return false;
    }
    this.id.set(id);
    store(id);
    return true;
  }

  clear(): void {
    this.id.set(null);
    store(null);
  }
}

function readStored(): string | null {
  try {
    const value = localStorage.getItem(STORAGE_KEY);
    return value && UUID.test(value) ? value : null;
  } catch {
    return null;
  }
}

function store(value: string | null): void {
  try {
    if (value) {
      localStorage.setItem(STORAGE_KEY, value);
    } else {
      localStorage.removeItem(STORAGE_KEY);
    }
  } catch {
    // Private mode or blocked storage: the id still works until the page is reloaded.
  }
}
