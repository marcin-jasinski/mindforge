import { Injectable, signal, computed } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { catchError, tap, of } from 'rxjs';
import { UserInfo } from '../models/api.models';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly user = signal<UserInfo | null>(null);
  private readonly loading = signal(true);

  readonly currentUser = this.user.asReadonly();
  readonly isAuthenticated = computed(() => this.user() !== null);
  readonly isLoading = this.loading.asReadonly();

  constructor(private http: HttpClient, private router: Router) {}

  checkAuth() {
    this.loading.set(true);
    return this.http.get<UserInfo>('/api/auth/me', { withCredentials: true }).pipe(
      tap(user => {
        this.user.set(user);
        this.loading.set(false);
      }),
      catchError(() => {
        this.user.set(null);
        this.loading.set(false);
        return of(null);
      })
    );
  }

  login() {
    window.location.href = '/api/auth/login';
  }

  logout() {
    this.http.post('/api/auth/logout', {}, { withCredentials: true }).subscribe(() => {
      this.user.set(null);
      this.router.navigate(['/login']);
    });
  }

  getAvatarUrl(): string | null {
    const u = this.user();
    if (!u?.avatar) return null;
    return `https://cdn.discordapp.com/avatars/${u.discord_id}/${u.avatar}.png?size=64`;
  }
}
