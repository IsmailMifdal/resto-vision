import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { SessionService } from './session.service';

/** Ajoute le jeton à chaque appel et renvoie vers la connexion s'il a expiré. */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const sessions = inject(SessionService);
  const router = inject(Router);
  const token = sessions.session()?.token;
  const authorized = token ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : request;
  return next(authorized).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401 && token) {
        sessions.set(null);
        void router.navigateByUrl('/login');
      }
      return throwError(() => error);
    }),
  );
};

export const authGuard: CanActivateFn = () =>
  inject(SessionService).session() ? true : inject(Router).parseUrl('/login');
