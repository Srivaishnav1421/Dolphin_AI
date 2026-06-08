import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { ApiService } from '../../core/services/api.service';

@Component({
  selector: 'app-meta-callback',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './meta-callback.html',
  styleUrl: './meta-callback.scss',
})
export class MetaCallback implements OnInit {
  status = signal<'loading' | 'success' | 'error'>('loading');
  errorMsg = signal('');

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private api: ApiService
  ) {}

  ngOnInit() {
    this.route.queryParams.subscribe(params => {
      const code = params['code'];
      const state = params['state'];

      if (!code) {
        this.status.set('error');
        this.errorMsg.set('No authorization code provided from Meta.');
        return;
      }

      this.api.metaCallback(code, state || '').subscribe({
        next: (res) => {
          this.status.set('success');
          // Notify opener popup parent that we succeeded
          if (window.opener) {
            window.opener.postMessage('META_CONNECTED', '*');
            setTimeout(() => {
              window.close();
            }, 1500);
          } else {
            // Fallback: redirect back to Meta page
            setTimeout(() => {
              this.router.navigate(['/meta']);
            }, 2000);
          }
        },
        error: (err) => {
          this.status.set('error');
          this.errorMsg.set(
            err.error?.error || err.message || 'Failed to exchange Meta OAuth token.'
          );
        }
      });
    });
  }

  closePopup() {
    window.close();
  }
}
