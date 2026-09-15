import { Routes } from '@angular/router';

import { authGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  { path: 'login', loadComponent: () => import('./pages/login/login').then((m) => m.Login) },
  {
    path: '',
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      { path: 'dashboard', loadComponent: () => import('./pages/dashboard/dashboard').then((m) => m.Dashboard) },
      {
        path: 'kb/:kbId',
        loadComponent: () => import('./pages/kb/kb-shell').then((m) => m.KbShell),
        children: [
          { path: '', loadComponent: () => import('./pages/kb/documents').then((m) => m.Documents) },
          { path: 'pages', loadComponent: () => import('./pages/wiki/page-index').then((m) => m.PageIndex) },
          {
            path: 'pages/:directory/:name',
            loadComponent: () => import('./pages/wiki/page-view').then((m) => m.PageView),
          },
          { path: 'graph', loadComponent: () => import('./pages/graph/graph').then((m) => m.GraphView) },
          { path: 'runs/:runId', loadComponent: () => import('./pages/runs/run-report').then((m) => m.RunReportView) },
          { path: 'health', loadComponent: () => import('./pages/health/health').then((m) => m.HealthView) },
          { path: 'study', loadComponent: () => import('./pages/study/study').then((m) => m.Study) },
          { path: 'chat', loadComponent: () => import('./pages/chat/chat').then((m) => m.Chat) },
        ],
      },
    ],
  },
  { path: '**', redirectTo: 'dashboard' },
];
