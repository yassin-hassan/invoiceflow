import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs/operators';
import { of } from 'rxjs';
import { AuthService } from '../services/auth.service';

export const adminGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  const ensure$ = auth.currentUser() ? of(auth.currentUser()) : auth.refreshCurrentUser();

  return ensure$.pipe(
    map(user => {
      if (user?.role === 'ADMIN') return true;
      router.navigate(['/dashboard']);
      return false;
    })
  );
};
