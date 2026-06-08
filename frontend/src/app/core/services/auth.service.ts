import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { tap, catchError } from 'rxjs/operators';
import { Observable, throwError } from 'rxjs';
import { AuthResponse, User } from '../../shared/models';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly TOKEN_KEY   = 'cd_token';
  private readonly REFRESH_KEY = 'cd_refresh';
  private readonly USER_KEY    = 'cd_user';
  private readonly BASE        = 'http://localhost:8000';

  currentUser     = signal<User | null>(this.loadUser());
  isAuthenticated = signal<boolean>(!!localStorage.getItem(this.TOKEN_KEY));

  constructor(private http: HttpClient, private router: Router) {
    // On startup, check if token needs refresh
    this.checkTokenExpiry();
  }

  /** Login — stores access token (15min) + refresh token (7 days) */
  login(email: string, password: string) {
    return this.http.post<AuthResponse>(`${this.BASE}/api/auth/login`, { email, password }).pipe(
      tap(res => this.storeAuth(res)),
    );
  }

  /** Logout — revokes refresh token on server, clears local storage */
  logout() {
    const refreshToken = localStorage.getItem(this.REFRESH_KEY);
    if (refreshToken) {
      this.http.post(`${this.BASE}/api/auth/logout`, { refresh_token: refreshToken })
        .pipe(catchError(() => throwError(() => null)))
        .subscribe();
    }
    this.clearAuth();
  }

  /** Use refresh token to get a new access token silently */
  refreshAccessToken(): Observable<AuthResponse> {
    const refreshToken = localStorage.getItem(this.REFRESH_KEY);
    if (!refreshToken) {
      this.clearAuth();
      return throwError(() => new Error('No refresh token'));
    }
    return this.http.post<AuthResponse>(`${this.BASE}/api/auth/refresh`, { refresh_token: refreshToken }).pipe(
      tap(res => this.storeAuth(res)),
      catchError(err => {
        this.clearAuth(); // Refresh failed — force re-login
        return throwError(() => err);
      })
    );
  }

  /** Switch dynamic active workspace context */
  switchWorkspace(workspaceId: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.BASE}/api/auth/switch-workspace`, { workspace_id: workspaceId }).pipe(
      tap(res => this.storeAuth(res))
    );
  }

  getToken(): string | null {
    return localStorage.getItem(this.TOKEN_KEY);
  }

  getRefreshToken(): string | null {
    return localStorage.getItem(this.REFRESH_KEY);
  }

  /** Check if stored token is near expiry and refresh proactively */
  private checkTokenExpiry() {
    const token = this.getToken();
    if (!token) return;
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      const expiresAt = payload.exp * 1000;
      const now = Date.now();
      const timeLeft = expiresAt - now;
      // If < 2 minutes left, refresh immediately
      if (timeLeft > 0 && timeLeft < 120_000) {
        this.refreshAccessToken().subscribe();
      } else if (timeLeft <= 0) {
        // Already expired — try refresh
        this.refreshAccessToken().subscribe({
          error: () => this.clearAuth()
        });
      }
    } catch { /* invalid token — will fail on next API call */ }
  }

  private storeAuth(res: AuthResponse) {
    localStorage.setItem(this.TOKEN_KEY, res.access_token);
    if ((res as any).refresh_token) {
      localStorage.setItem(this.REFRESH_KEY, (res as any).refresh_token);
    }
    localStorage.setItem(this.USER_KEY, JSON.stringify(res.user));
    this.currentUser.set(res.user);
    this.isAuthenticated.set(true);
  }

  private clearAuth() {
    localStorage.removeItem(this.TOKEN_KEY);
    localStorage.removeItem(this.REFRESH_KEY);
    localStorage.removeItem(this.USER_KEY);
    this.currentUser.set(null);
    this.isAuthenticated.set(false);
    this.router.navigate(['/login']);
  }

  private loadUser(): User | null {
    try {
      const raw = localStorage.getItem(this.USER_KEY);
      return raw ? JSON.parse(raw) : null;
    } catch { return null; }
  }
}
