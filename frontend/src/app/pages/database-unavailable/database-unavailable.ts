import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HealthService } from '../../core/services/health.service';

@Component({
  selector: 'app-database-unavailable',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './database-unavailable.html',
  styleUrl: './database-unavailable.scss',
})
export class DatabaseUnavailable {
  checking = false;
  message = 'Database connection unavailable. Live business data cannot be loaded.';

  constructor(private health: HealthService) {}

  checkAgain() {
    this.checking = true;
    this.health.getDatabaseHealth().subscribe({
      next: status => {
        this.checking = false;
        if (status.connected) {
          window.location.assign('/dashboard');
          return;
        }
        this.message = status.message || 'Database connection unavailable. Live business data cannot be loaded.';
      },
      error: error => {
        this.checking = false;
        this.message = error?.error?.message || 'Database connection unavailable. Live business data cannot be loaded.';
      }
    });
  }
}
