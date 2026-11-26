import { actionDoneMessage, parseActionQuantity } from './batch-actions';

describe('parseActionQuantity', () => {
  it('accepts a whole number up to what is left', () => {
    expect(parseActionQuantity(' 3 ', 5)).toEqual({ ok: true, quantity: 3 });
    expect(parseActionQuantity('5', 5)).toEqual({ ok: true, quantity: 5 });
  });

  it('rejects zero, blanks, decimals and signs', () => {
    for (const bad of ['', '0', '1.5', '-2', '+2', 'two', '1e2']) {
      expect(parseActionQuantity(bad, 5).ok).withContext(bad).toBeFalse();
    }
  });

  it('rejects more than is left, naming how many are left', () => {
    expect(parseActionQuantity('6', 5)).toEqual({ ok: false, error: 'Only 5 left in this batch.' });
  });
});

describe('actionDoneMessage', () => {
  it('confirms a sale', () => {
    expect(actionDoneMessage('sold', 'Injera pack', 2)).toBe('Recorded 2 × Injera pack sold.');
  });

  it('shows the dollar loss of waste', () => {
    expect(actionDoneMessage('wasted', 'Whole milk', 2, 822)).toBe('Recorded 2 × Whole milk thrown away: $8.22 lost.');
  });

  it('says the loss is unknown rather than $0 when there is no cost', () => {
    expect(actionDoneMessage('wasted', 'Whole milk', 1, null)).toContain('loss is unknown');
  });

  it('confirms a markdown and a sold-out shelf', () => {
    expect(actionDoneMessage('markDown', 'Bread', 0)).toBe('Bread is marked down.');
    expect(actionDoneMessage('soldOut', 'Bread', 0)).toBe('Bread is sold out.');
  });
});
