import { describe, it, expect } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { App } from './app';
import { HealthService } from './core/services/health.service';
import { AuthService } from './core/services/auth.service';

describe('App database truth gate', () => {
  function setup(healthResponse: any) {
    const health =
      healthResponse instanceof Error
        ? { getDatabaseHealth: () => throwError(() => healthResponse) }
        : { getDatabaseHealth: () => of(healthResponse) };
    const auth = { clearLocalSession: vi.fn() };

    TestBed.configureTestingModule({
      imports: [App],
      providers: [
        { provide: HealthService, useValue: health },
        { provide: AuthService, useValue: auth },
      ],
    });

    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    return { fixture, auth };
  }

  it('shows the database blocking screen when health reports down', () => {
    const { fixture, auth } = setup({
      database: 'DOWN',
      connected: false,
      message: 'Database connection unavailable. Live business data cannot be loaded.',
    });

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Database connection unavailable. Live business data cannot be loaded.');
    expect(compiled.querySelector('router-outlet')).toBeNull();
    expect(auth.clearLocalSession).toHaveBeenCalledWith(false);
    fixture.destroy();
  });

  it('does not render the app router when the health check fails', () => {
    const { fixture, auth } = setup(new Error('backend unavailable'));

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Database connection unavailable. Live business data cannot be loaded.');
    expect(compiled.querySelector('router-outlet')).toBeNull();
    expect(auth.clearLocalSession).toHaveBeenCalledWith(false);
    fixture.destroy();
  });
});
