import { HttpErrorResponse } from '@angular/common/http';
import { errorMessage } from './shelf-api.service';

describe('errorMessage', () => {
  it('uses the problem+json detail', () => {
    const err = new HttpErrorResponse({ status: 404, error: { title: 'Not Found', detail: 'Location not found' } });
    expect(errorMessage(err)).toBe('Location not found');
  });

  it('explains an unreachable server', () => {
    expect(errorMessage(new HttpErrorResponse({ status: 0 }))).toContain("Can't reach");
  });

  it('falls back to the status code', () => {
    expect(errorMessage(new HttpErrorResponse({ status: 500, error: 'boom' }))).toContain('500');
  });

  it('handles non-HTTP errors', () => {
    expect(errorMessage(new Error('x'))).toBe('Something went wrong.');
  });
});
