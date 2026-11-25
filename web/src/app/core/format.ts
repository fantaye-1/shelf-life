import { Pipe, PipeTransform } from '@angular/core';

const USD = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' });

/** Integer cents as dollars; null means the cost is unknown and shows as a dash, never $0.00. */
export function formatCents(cents: number | null | undefined): string {
  return cents == null ? '—' : USD.format(cents / 100);
}

/** Plain words for how close a batch is to its date, judged by the store's own day. */
export function expiryLabel(daysUntilExpiry: number): string {
  if (daysUntilExpiry < -1) {
    return `Expired ${-daysUntilExpiry} days ago`;
  }
  if (daysUntilExpiry === -1) {
    return 'Expired yesterday';
  }
  if (daysUntilExpiry === 0) {
    return 'Expires today';
  }
  if (daysUntilExpiry === 1) {
    return 'Expires tomorrow';
  }
  return `Expires in ${daysUntilExpiry} days`;
}

@Pipe({ name: 'cents' })
export class CentsPipe implements PipeTransform {
  transform(cents: number | null | undefined): string {
    return formatCents(cents);
  }
}

@Pipe({ name: 'expiryLabel' })
export class ExpiryLabelPipe implements PipeTransform {
  transform(daysUntilExpiry: number): string {
    return expiryLabel(daysUntilExpiry);
  }
}
