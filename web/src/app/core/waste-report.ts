import { WasteMonth, WasteReport } from './api.models';

const MONTH_NAMES = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

/** Report lengths the screen offers, in calendar months (the API allows 1 to 24). */
export const REPORT_MONTH_OPTIONS = [3, 6, 12, 24] as const;

/**
 * "2026-10" as "Oct 2026". Parsed by hand, not with Date, so the browser's own time zone can never
 * shift the store's month.
 */
export function monthLabel(yearMonth: string): string {
  const match = /^(\d{4})-(\d{2})$/.exec(yearMonth);
  const month = match ? Number(match[2]) : 0;
  if (!match || month < 1 || month > 12) {
    return yearMonth;
  }
  return `${MONTH_NAMES[month - 1]} ${match[1]}`;
}

/** A month row ready to draw: its known loss as a share of the worst month, for a simple bar. */
export interface MonthBar {
  month: string;
  label: string;
  unitsWasted: number;
  knownLossCents: number;
  entriesWithUnknownLoss: number;
  /** 0 to 100. A month with waste but only unknown cost gets no bar: we never guess a dollar value. */
  percent: number;
}

/** Bars for each month, newest first so the current month is at the top on a phone. */
export function monthBars(months: readonly WasteMonth[]): MonthBar[] {
  const worst = Math.max(0, ...months.map((m) => m.knownLossCents));
  return [...months].reverse().map((m) => ({
    month: m.month,
    label: monthLabel(m.month),
    unitsWasted: m.unitsWasted,
    knownLossCents: m.knownLossCents,
    entriesWithUnknownLoss: m.entriesWithUnknownLoss,
    percent: worst > 0 ? Math.round((m.knownLossCents / worst) * 100) : 0,
  }));
}

/** True when nothing at all was thrown away in the window, priced or not. */
export function noWaste(report: WasteReport): boolean {
  return report.unitsWasted === 0 && report.entriesWithUnknownLoss === 0;
}

/** "3 entries without a cost" style note, or null when every entry had a cost. */
export function unknownCostNote(entries: number): string | null {
  if (entries <= 0) {
    return null;
  }
  return `+ ${entries} ${entries === 1 ? 'entry' : 'entries'} without a cost (not counted in dollars)`;
}

/**
 * The dollar loss to show: null (a dash) when nothing priced was lost but some entries had no cost,
 * so an all-unknown month or item never reads as $0.00.
 */
export function shownLoss(knownLossCents: number, entriesWithUnknownLoss: number): number | null {
  return knownLossCents === 0 && entriesWithUnknownLoss > 0 ? null : knownLossCents;
}
