import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, catchError, map, of, tap } from 'rxjs';

import { User } from '../models/api';
import { ApiService } from './api.service';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly api = inject(ApiService);
  private readonly _user = signal<User | null>(null);
  private loaded = false;

  readonly user = this._user.asReadonly();
  readonly isAuthenticated = computed(() => this._user() !== null);

  /** Resolves the signed-in user once per page load; a missing cookie resolves to null. */
  ensureLoaded(): Observable<User | null> {
    if (this.loaded) {
      return of(this._user());
    }
    return this.api.get<User>('/auth/me', true).pipe(
      catchError(() => of(null)),
      tap((user) => {
        this.loaded = true;
        this._user.set(user);
      }),
    );
  }

  login(email: string, password: string): Observable<User> {
    return this.api.post<User>('/auth/login', { email, password }, true).pipe(tap((user) => this.signedIn(user)));
  }

  register(displayName: string, email: string, password: string): Observable<User> {
    return this.api
      .post<User>('/auth/register', { displayName, email, password }, true)
      .pipe(tap((user) => this.signedIn(user)));
  }

  logout(): Observable<void> {
    return this.api.post<void>('/auth/logout').pipe(
      map(() => undefined),
      tap(() => this._user.set(null)),
    );
  }

  private signedIn(user: User): void {
    this.loaded = true;
    this._user.set(user);
  }
}
