import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Batch, ExpiringReport, Item, NewBatch, NewItem, StoreLocation, WasteRecorded, WasteReport } from './api.models';

/** Thin typed client for the ShelfLife REST API (same origin; `ng serve` proxies /api to :8080). */
@Injectable({ providedIn: 'root' })
export class ShelfApi {
  private readonly http = inject(HttpClient);

  locations(): Observable<StoreLocation[]> {
    return this.http.get<StoreLocation[]>('/api/locations');
  }

  expiring(locationId: string, withinDays: number): Observable<ExpiringReport> {
    return this.http.get<ExpiringReport>('/api/expiring', {
      params: { locationId, withinDays: String(withinDays) },
    });
  }

  items(): Observable<Item[]> {
    return this.http.get<Item[]>('/api/items');
  }

  itemsByBarcode(gtin: string): Observable<Item[]> {
    return this.http.get<Item[]>('/api/items', { params: { gtin } });
  }

  createItem(item: NewItem): Observable<Item> {
    return this.http.post<Item>('/api/items', item);
  }

  logBatch(batch: NewBatch): Observable<Batch> {
    return this.http.post<Batch>('/api/batches', batch);
  }

  recordSale(batchId: string, quantity: number): Observable<Batch> {
    return this.http.post<Batch>(`/api/batches/${encodeURIComponent(batchId)}/sales`, { quantity });
  }

  recordWaste(batchId: string, quantity: number): Observable<WasteRecorded> {
    return this.http.post<WasteRecorded>(`/api/batches/${encodeURIComponent(batchId)}/waste`, { quantity });
  }

  markDown(batchId: string): Observable<Batch> {
    return this.http.post<Batch>(`/api/batches/${encodeURIComponent(batchId)}/mark-down`, null);
  }

  wasteReport(locationId: string, months: number): Observable<WasteReport> {
    return this.http.get<WasteReport>('/api/waste/report', {
      params: { locationId, months: String(months) },
    });
  }

  markSoldOut(batchId: string): Observable<Batch> {
    return this.http.post<Batch>(`/api/batches/${encodeURIComponent(batchId)}/sold-out`, null);
  }
}

/** A short message a store owner can act on, using the API's problem+json detail when there is one. */
export function errorMessage(err: unknown): string {
  if (err instanceof HttpErrorResponse) {
    if (err.status === 0) {
      return "Can't reach the ShelfLife server. Check the connection and try again.";
    }
    const detail = (err.error as { detail?: unknown } | null)?.detail;
    if (typeof detail === 'string' && detail.trim()) {
      return detail;
    }
    return `The server answered with an error (${err.status}).`;
  }
  return 'Something went wrong.';
}
