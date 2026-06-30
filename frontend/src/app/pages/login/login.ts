import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { switchMap } from 'rxjs';
import { AuthService } from '../../core/services/auth.service';
import { HealthService } from '../../core/services/health.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login implements OnInit {
  email    = '';
  password = '';
  totpCode = '';
  loading  = signal(false);
  error    = signal('');
  twoFactorRequired = signal(false);
  checkingDatabase = signal(true);
  databaseConnected = signal(false);
  databaseMessage = signal('Checking PostgreSQL connection...');

  constructor(private auth: AuthService, private health: HealthService, private router: Router) {}

  ngOnInit() {
    this.checkDatabase();
  }

  checkDatabase() {
    this.checkingDatabase.set(true);
    this.health.getDatabaseHealth().subscribe({
      next: status => {
        this.databaseConnected.set(status.connected);
        this.databaseMessage.set(status.connected ? 'Database connected' : 'PostgreSQL is not connected.');
        if (!status.connected) {
          this.auth.clearLocalSession(false);
        }
        this.checkingDatabase.set(false);
      },
      error: err => {
        this.databaseConnected.set(false);
        this.databaseMessage.set(err?.error?.message || 'Backend/database health check failed.');
        this.auth.clearLocalSession(false);
        this.checkingDatabase.set(false);
      }
    });
  }

  submit() {
    if (!this.databaseConnected()) {
      this.error.set('Login is blocked until PostgreSQL is connected.');
      return;
    }
    if (!this.email || !this.password) {
      this.error.set('Please enter your email and password.');
      return;
    }
    if (this.twoFactorRequired() && !this.totpCode) {
      this.error.set('Enter the 6-digit authenticator code.');
      return;
    }
    this.loading.set(true);
    this.error.set('');

    this.health.getDatabaseHealth().pipe(
      switchMap(status => {
        if (!status.connected) {
          this.auth.clearLocalSession(false);
          throw new Error('DATABASE_DOWN');
        }
        return this.auth.login(this.email, this.password, this.totpCode);
      }),
      switchMap(() => this.auth.validateSession())
    ).subscribe({
      next: () => {
        this.loading.set(false);
        this.router.navigate(['/dashboard']);
      },
      error: (err) => {
        if (err?.message === 'DATABASE_DOWN') {
          this.error.set('Database connection required. Start PostgreSQL before login.');
        } else if (err.status === 428 || err.error?.two_factor_required) {
          this.twoFactorRequired.set(true);
          this.error.set('Enter your authenticator app code to finish signing in.');
        } else {
          this.error.set(
            err.status === 401
              ? (this.twoFactorRequired() ? 'Invalid authenticator code.' : 'Invalid email or password.')
              : 'Server unavailable. Please start the backend first.'
          );
        }
        this.loading.set(false);
      },
    });
  }
}
