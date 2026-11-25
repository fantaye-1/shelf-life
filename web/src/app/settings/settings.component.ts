import { Component, inject, signal } from '@angular/core';
import { MerchantStore } from '../core/merchant.store';

/** Temporary until app login (M8): which store this browser works for. */
@Component({
  selector: 'sl-settings',
  template: `
    <h1>Settings</h1>
    <p>
      Until sign-in arrives, paste your store's id here. It is kept in this browser only.
    </p>
    <form (submit)="save(id.value); $event.preventDefault()">
      <label>
        Store id
        <input #id name="merchantId" [value]="merchant.merchantId() ?? ''" autocomplete="off"
               spellcheck="false" placeholder="00000000-0000-0000-0000-000000000000">
      </label>
      <div class="actions">
        <button type="submit">Save</button>
        @if (merchant.merchantId()) {
          <button type="button" class="secondary" (click)="forget()">Forget</button>
        }
      </div>
    </form>
    @if (message()) {
      <p role="status">{{ message() }}</p>
    }
  `,
  styles: `
    form { display: flex; flex-direction: column; gap: 12px; max-width: 28rem; }
    label { display: flex; flex-direction: column; gap: 4px; }
    .actions { display: flex; gap: 8px; }
  `,
})
export class SettingsComponent {
  protected readonly merchant = inject(MerchantStore);
  readonly message = signal<string | null>(null);

  save(value: string): void {
    this.message.set(this.merchant.set(value) ? 'Saved.' : "That doesn't look like a store id.");
  }

  forget(): void {
    this.merchant.clear();
    this.message.set('Forgotten.');
  }
}
