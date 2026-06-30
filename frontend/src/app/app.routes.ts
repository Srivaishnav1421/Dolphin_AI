import { Routes } from '@angular/router';
import { authGuard, dbHealthGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    canActivate: [dbHealthGuard],
    loadComponent: () => import('./pages/login/login').then(m => m.Login),
  },
  {
    path: 'database-unavailable',
    loadComponent: () => import('./pages/database-unavailable/database-unavailable').then(m => m.DatabaseUnavailable),
  },
  {
    path: 'meta/callback',
    loadComponent: () => import('./pages/meta-callback/meta-callback').then(m => m.MetaCallback),
  },
  {
    path: '',
    canActivate: [authGuard],
    canActivateChild: [authGuard],
    loadComponent: () => import('./pages/shell/shell').then(m => m.Shell),
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      { path: 'dashboard',   loadComponent: () => import('./pages/dashboard/dashboard').then(m => m.Dashboard) },
      { path: 'campaigns',   loadComponent: () => import('./pages/campaigns/campaigns').then(m => m.Campaigns) },
      { path: 'ad-brain',    loadComponent: () => import('./pages/ad-brain/ad-brain').then(m => m.AdBrain) },
      { path: 'approvals',   loadComponent: () => import('./pages/approvals/approvals').then(m => m.Approvals) },
      { path: 'creatives',   loadComponent: () => import('./pages/creatives/creatives').then(m => m.Creatives) },
      { path: 'meta',        loadComponent: () => import('./pages/meta-connect/meta-connect').then(m => m.MetaConnect) },
      { path: 'crm',         redirectTo: 'leads', pathMatch: 'full' },
      { path: 'leads',       loadComponent: () => import('./pages/leads/leads').then(m => m.Leads) },
      { path: 'settings',    loadComponent: () => import('./pages/settings/settings').then(m => m.Settings) },
      { path: 'integrations', loadComponent: () => import('./pages/integrations/integrations').then(m => m.Integrations) },
      { path: 'analytics',   loadComponent: () => import('./pages/analytics/analytics').then(m => m.Analytics) },
      { path: 'automation',  loadComponent: () => import('./pages/workflows-dashboard/workflows-dashboard').then(m => m.WorkflowsDashboard) }
    ],
  },
  { path: '**', redirectTo: '' },
];
