import { formatCents } from './format';

/** What staff can do to a batch from the expiring list. */
export type BatchAction = 'sold' | 'wasted' | 'markDown' | 'soldOut';

export type QuantityResult = { ok: true; quantity: number } | { ok: false; error: string };

/**
 * Reads the "how many" box for selling or throwing away units. It must be a whole number from 1
 * up to what is left, so the clerk hears about a typo here instead of from the server.
 */
export function parseActionQuantity(input: string, remaining: number): QuantityResult {
  const text = input.trim();
  const quantity = /^\d+$/.test(text) ? Number(text) : NaN;
  if (!Number.isSafeInteger(quantity) || quantity < 1) {
    return { ok: false, error: 'Enter how many, as a whole number of at least 1.' };
  }
  if (quantity > remaining) {
    return { ok: false, error: `Only ${remaining} left in this batch.` };
  }
  return { ok: true, quantity };
}

/** One line confirming what was recorded. A wasted loss with no cost says so instead of showing $0. */
export function actionDoneMessage(
  action: BatchAction,
  itemName: string,
  quantity: number,
  lossCents: number | null = null,
): string {
  switch (action) {
    case 'sold':
      return `Recorded ${quantity} × ${itemName} sold.`;
    case 'wasted':
      return lossCents === null
        ? `Recorded ${quantity} × ${itemName} thrown away (no unit cost, so the loss is unknown).`
        : `Recorded ${quantity} × ${itemName} thrown away: ${formatCents(lossCents)} lost.`;
    case 'markDown':
      return `${itemName} is marked down.`;
    case 'soldOut':
      return `${itemName} is sold out.`;
  }
}
