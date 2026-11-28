import { WasteMonth, WasteReport } from './api.models';
import { monthBars, monthLabel, noWaste, shownLoss, unknownCostNote } from './waste-report';

function month(m: string, knownLossCents: number, unitsWasted = 1, entriesWithUnknownLoss = 0): WasteMonth {
  return { month: m, unitsWasted, knownLossCents, entriesWithUnknownLoss };
}

describe('monthLabel', () => {
  it('names the store month without going through Date', () => {
    expect(monthLabel('2026-01')).toBe('Jan 2026');
    expect(monthLabel('2026-10')).toBe('Oct 2026');
    expect(monthLabel('2025-12')).toBe('Dec 2025');
  });

  it('leaves anything unexpected as it came', () => {
    expect(monthLabel('2026-13')).toBe('2026-13');
    expect(monthLabel('oops')).toBe('oops');
  });
});

describe('monthBars', () => {
  it('puts the newest month first and scales bars to the worst month', () => {
    const bars = monthBars([month('2026-09', 500), month('2026-10', 2000), month('2026-11', 1000)]);
    expect(bars.map((b) => b.month)).toEqual(['2026-11', '2026-10', '2026-09']);
    expect(bars.map((b) => b.percent)).toEqual([50, 100, 25]);
    expect(bars[0].label).toBe('Nov 2026');
  });

  it('draws no bars when no month has a known dollar loss', () => {
    const bars = monthBars([month('2026-10', 0, 0), month('2026-11', 0, 4, 2)]);
    expect(bars.map((b) => b.percent)).toEqual([0, 0]);
    expect(bars[0].entriesWithUnknownLoss).toBe(2);
  });

  it('copes with an empty list', () => {
    expect(monthBars([])).toEqual([]);
  });
});

describe('noWaste', () => {
  const base: WasteReport = {
    locationId: 'l', locationName: 'Store', firstMonth: '2026-06', lastMonth: '2026-11', months: [],
    unitsWasted: 0, knownLossCents: 0, entriesWithUnknownLoss: 0, topItems: [],
  };

  it('is true only when nothing was thrown away', () => {
    expect(noWaste(base)).toBeTrue();
    expect(noWaste({ ...base, unitsWasted: 2 })).toBeFalse();
    expect(noWaste({ ...base, entriesWithUnknownLoss: 1 })).toBeFalse();
  });
});

describe('unknownCostNote', () => {
  it('says how many entries have no cost', () => {
    expect(unknownCostNote(0)).toBeNull();
    expect(unknownCostNote(1)).toBe('+ 1 entry without a cost (not counted in dollars)');
    expect(unknownCostNote(3)).toBe('+ 3 entries without a cost (not counted in dollars)');
  });
});

describe('shownLoss', () => {
  it('shows a dash instead of $0.00 when every entry had no cost', () => {
    expect(shownLoss(0, 2)).toBeNull();
  });

  it('shows the known loss otherwise, including a real zero', () => {
    expect(shownLoss(0, 0)).toBe(0);
    expect(shownLoss(822, 1)).toBe(822);
  });
});
