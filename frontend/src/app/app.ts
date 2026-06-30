import { Component, OnDestroy, OnInit, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { HealthService } from './core/services/health.service';
import { AuthService } from './core/services/auth.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App implements OnInit, OnDestroy {
  checkingDatabase = signal(true);
  databaseConnected = signal(false);
  databaseMessage = signal('Checking PostgreSQL connection...');
  private databaseWatch?: number;

  constructor(private health: HealthService, private auth: AuthService) {}

  ngOnInit() {
    this.checkDatabase();
    this.databaseWatch = window.setInterval(() => this.checkDatabase(false), 30_000);
  }

  ngOnDestroy() {
    if (this.databaseWatch) {
      window.clearInterval(this.databaseWatch);
    }
  }

  checkDatabase(showLoader = true) {
    if (showLoader) {
      this.checkingDatabase.set(true);
    }
    this.health.getDatabaseHealth().subscribe({
      next: health => {
        this.databaseConnected.set(health.connected);
        this.databaseMessage.set(health.connected ? 'Database connected' : 'Database connection unavailable. Live business data cannot be loaded.');
        if (!health.connected) {
          this.auth.clearLocalSession(false);
        }
        this.checkingDatabase.set(false);
      },
      error: error => {
        this.databaseConnected.set(false);
        this.auth.clearLocalSession(false);
        this.databaseMessage.set(error?.error?.message || 'Database connection unavailable. Live business data cannot be loaded.');
        this.checkingDatabase.set(false);
      }
    });
  }
}
