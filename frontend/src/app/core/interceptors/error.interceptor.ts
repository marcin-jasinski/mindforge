import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

import { ApiError } from '../models/api';
import { HANDLES_ERRORS } from '../services/api.service';

/** Shows every 4xx and 5xx as a toast, unless the request's page answers it; a 401 returns to the login page and a 429 asks the user to slow down. */
export const errorInterceptor: HttpInterceptorFn = (request, next) => {
  const snackBar = inject(MatSnackBar);
  const router = inject(Router);
  return next(request).pipe(
    catchError((error: HttpErrorResponse) => {
      if (!request.context.get(HANDLES_ERRORS)) {
        if (error.status === 401) {
          router.navigateByUrl('/login');
        } else if (error.status === 429) {
          snackBar.open('Zwolnij — zbyt wiele zapytań. Spróbuj ponownie za chwilę.', 'OK', { duration: 6000 });
        } else {
          const body = error.error as ApiError | null;
          snackBar.open(body?.detail ?? body?.error ?? 'Coś poszło nie tak.', 'OK', { duration: 6000 });
        }
      }
      return throwError(() => error);
    }),
  );
};
