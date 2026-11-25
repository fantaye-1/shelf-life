import { Routes } from '@angular/router';
import { BatchEntryComponent } from './batch-entry/batch-entry.component';
import { ExpiringComponent } from './expiring/expiring.component';
import { SettingsComponent } from './settings/settings.component';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'expiring' },
  { path: 'expiring', component: ExpiringComponent, title: 'Expiring soon · ShelfLife' },
  { path: 'batches/new', component: BatchEntryComponent, title: 'Log a batch · ShelfLife' },
  { path: 'settings', component: SettingsComponent, title: 'Settings · ShelfLife' },
  { path: '**', redirectTo: 'expiring' },
];
