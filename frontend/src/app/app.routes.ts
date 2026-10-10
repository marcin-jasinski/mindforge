import { Routes } from '@angular/router';

import { authGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  { path: 'login', title: 'Logowanie', loadComponent: () => import('./pages/login/login').then((m) => m.Login) },
  {
    path: 'register',
    title: 'Rejestracja',
    data: { registering: true },
    loadComponent: () => import('./pages/login/login').then((m) => m.Login),
  },
  {
    path: '',
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      { path: 'dashboard', title: 'Bazy wiedzy', loadComponent: () => import('./pages/dashboard/dashboard').then((m) => m.Dashboard) },
      {
        path: 'kb/:kbId',
        loadComponent: () => import('./pages/kb/kb-shell').then((m) => m.KbShell),
        children: [
          { path: '', title: 'Dokumenty', loadComponent: () => import('./pages/kb/documents').then((m) => m.Documents) },
          { path: 'pages', title: 'Strony', loadComponent: () => import('./pages/wiki/page-index').then((m) => m.PageIndex) },
          {
            path: 'pages/:directory/:name',
            title: 'Strona',
            loadComponent: () => import('./pages/wiki/page-view').then((m) => m.PageView),
          },
          { path: 'graph', title: 'Graf', loadComponent: () => import('./pages/graph/graph').then((m) => m.GraphView) },
          { path: 'runs/:runId', title: 'Przebieg', loadComponent: () => import('./pages/runs/run-report').then((m) => m.RunReportView) },
          { path: 'health', title: 'Zdrowie bazy', loadComponent: () => import('./pages/health/health').then((m) => m.HealthView) },
          { path: 'study', title: 'Nauka', loadComponent: () => import('./pages/study/study').then((m) => m.Study) },
          { path: 'chat', title: 'Czat', loadComponent: () => import('./pages/chat/chat').then((m) => m.Chat) },
        ],
      },
    ],
  },
  { path: '**', redirectTo: 'dashboard' },
];
