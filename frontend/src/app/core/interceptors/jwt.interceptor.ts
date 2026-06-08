import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

let isRefreshing = false;

/**
 * JWT Interceptor — Attaches Bearer token to every request.
 * On 401 (token expired):
 *   1. Uses refresh token to get a new access token silently
 *   2. Retries the original request with the new token
 *   3. If refresh also fails → logs out
 */
export const jwtInterceptor: HttpInterceptorFn = (req, next) => {
  const auth  = inject(AuthService);
  const token = auth.getToken();

  // Skip auth header for auth endpoints
  const isAuthEndpoint = req.url.includes('/api/auth/login') ||
                         req.url.includes('/api/auth/refresh');

  const authReq = (token && !isAuthEndpoint)
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : req;

  return next(authReq).pipe(
    catchError((error: HttpErrorResponse) => {
      // On 401 — try to refresh the access token
      if (error.status === 401 && !isAuthEndpoint && !isRefreshing) {
        isRefreshing = true;
        return auth.refreshAccessToken().pipe(
          switchMap(res => {
            isRefreshing = false;
            // Retry original request with new token
            const retryReq = req.clone({
              setHeaders: { Authorization: `Bearer ${res.access_token}` }
            });
            return next(retryReq);
          }),
          catchError(refreshErr => {
            isRefreshing = false;
            auth.logout(); // Refresh failed — force re-login
            return throwError(() => refreshErr);
          })
        );
      }
      return throwError(() => error);
    })
  );
};
