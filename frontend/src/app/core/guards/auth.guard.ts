import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of, switchMap } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { HealthService } from '../services/health.service';

export const authGuard: CanActivateFn = () => {
  const auth   = inject(AuthService);
  const router = inject(Router);
  const health = inject(HealthService);

  return health.getDatabaseHealth().pipe(
    switchMap(status => {
      if (!status.connected) {
        auth.clearLocalSession(false);
        return of(router.createUrlTree(['/database-unavailable']));
      }

      if (!auth.hasStoredToken()) {
        auth.clearLocalSession(false);
        return of(router.createUrlTree(['/login']));
      }

      return auth.validateSession().pipe(
        map(() => true),
        catchError(() => of(router.createUrlTree(['/login'])))
      );
    }),
    catchError(() => {
      auth.clearLocalSession(false);
      return of(router.createUrlTree(['/database-unavailable']));
    })
  );
};

export const dbHealthGuard: CanActivateFn = () => {
  const router = inject(Router);
  const health = inject(HealthService);
  const auth   = inject(AuthService);

  return health.getDatabaseHealth().pipe(
    map(status => {
      if (!status.connected) {
        auth.clearLocalSession(false);
        return router.createUrlTree(['/database-unavailable']);
      }
      return true;
    }),
    catchError(() => {
      auth.clearLocalSession(false);
      return of(router.createUrlTree(['/database-unavailable']));
    })
  );
};
