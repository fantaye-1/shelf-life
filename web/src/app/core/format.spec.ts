import { expiryLabel, formatCents } from './format';

describe('formatCents', () => {
  it('shows integer cents as dollars', () => {
    expect(formatCents(0)).toBe('$0.00');
    expect(formatCents(289)).toBe('$2.89');
    expect(formatCents(123456)).toBe('$1,234.56');
  });

  it('shows an unknown cost as a dash, not $0.00', () => {
    expect(formatCents(null)).toBe('—');
    expect(formatCents(undefined)).toBe('—');
  });
});

describe('expiryLabel', () => {
  it('words each distance from the store day', () => {
    expect(expiryLabel(-3)).toBe('Expired 3 days ago');
    expect(expiryLabel(-1)).toBe('Expired yesterday');
    expect(expiryLabel(0)).toBe('Expires today');
    expect(expiryLabel(1)).toBe('Expires tomorrow');
    expect(expiryLabel(5)).toBe('Expires in 5 days');
  });
});
