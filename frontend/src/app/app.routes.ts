import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./pages/login/login.component').then(m => m.LoginComponent),
  },
  {
    path: '',
    canActivate: [authGuard],
    children: [
      {
        path: '',
        redirectTo: 'dashboard',
        pathMatch: 'full',
      },
      {
        path: 'dashboard',
        loadComponent: () => import('./pages/dashboard/dashboard.component').then(m => m.DashboardComponent),
      },
      {
        path: 'concepts',
        loadComponent: () => import('./pages/concept-map/concept-map.component').then(m => m.ConceptMapComponent),
      },
      {
        path: 'quiz',
        loadComponent: () => import('./pages/quiz/quiz.component').then(m => m.QuizComponent),
      },
      {
        path: 'flashcards',
        loadComponent: () => import('./pages/flashcards/flashcards.component').then(m => m.FlashcardsComponent),
      },
      {
        path: 'search',
        loadComponent: () => import('./pages/search/search.component').then(m => m.SearchComponent),
      },
      {
        path: 'upload',
        loadComponent: () => import('./pages/upload/upload.component').then(m => m.UploadComponent),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
