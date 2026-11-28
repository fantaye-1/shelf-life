import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

@Component({
  selector: 'sl-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <header>
      <a class="brand" routerLink="/">ShelfLife</a>
      <nav>
        <a routerLink="/expiring" routerLinkActive="active">Expiring</a>
        <a routerLink="/batches/new" routerLinkActive="active">Log batch</a>
        <a routerLink="/waste" routerLinkActive="active">Waste</a>
        <a routerLink="/settings" routerLinkActive="active">Settings</a>
      </nav>
    </header>
    <main>
      <router-outlet />
    </main>
  `,
})
export class AppComponent {}
