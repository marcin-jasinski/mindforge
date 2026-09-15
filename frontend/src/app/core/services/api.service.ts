import { HttpClient, HttpContext, HttpContextToken } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

/** Set on a request whose error the calling page answers itself, so no toast is shown for it. */
const API = '/api';

export const HANDLES_ERRORS = new HttpContextToken<boolean>(() => false);

/** Every HTTP call goes through here; the session travels in its HttpOnly cookie. */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  /** The absolute URL of an API path, for the one client HttpClient cannot be: an EventSource. */
  url(path: string): string {
    return API + path;
  }

  get<T>(path: string, handlesErrors = false): Observable<T> {
    return this.http.get<T>(API + path, { context: context(handlesErrors) });
  }

  post<T>(path: string, body: unknown = {}, handlesErrors = false): Observable<T> {
    return this.http.post<T>(API + path, body, { context: context(handlesErrors) });
  }

  patch<T>(path: string, body: unknown): Observable<T> {
    return this.http.patch<T>(API + path, body);
  }

  delete<T>(path: string, handlesErrors = false): Observable<T> {
    return this.http.delete<T>(API + path, { context: context(handlesErrors) });
  }

  getResponse<T>(path: string): Observable<import('@angular/common/http').HttpResponse<T>> {
    return this.http.get<T>(API + path, { observe: 'response' });
  }
}

function context(handlesErrors: boolean): HttpContext {
  return new HttpContext().set(HANDLES_ERRORS, handlesErrors);
}
