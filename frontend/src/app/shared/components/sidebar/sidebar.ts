import { Component, OnInit, signal } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { ApiService } from '../../../core/services/api.service';

interface NavItem { path: string; icon: string; label: string; badge?: string; }

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive],
  templateUrl: './sidebar.html',
  styleUrl: './sidebar.scss',
})
export class Sidebar implements OnInit {
  pendingCount = signal(0);
  metaConnected = signal(false);
  llmProvider = signal('—');

  constructor(public auth: AuthService, private api: ApiService) {}

  navItems: NavItem[] = [
    { path: '/dashboard',  icon: '⬡',  label: 'Dashboard'  },
    { path: '/leads',      icon: '👥',  label: 'CRM'        },
    { path: '/campaigns',  icon: '📢',  label: 'Campaigns'  },
    { path: '/creatives',  icon: '🎨',  label: 'AI Studio'  },
    { path: '/ad-brain',   icon: '🧠',  label: 'AI Brain'   },
    { path: '/automation', icon: '⚡',  label: 'Automation' },
    { path: '/analytics',  icon: '📊',  label: 'Analytics'  },
    { path: '/settings',   icon: '⚙️',  label: 'Settings'   }
  ];

  ngOnInit() {
    this.loadStatus();
  }

  loadStatus() {
    this.api.getDashboard().subscribe({
      next: s => {
        this.pendingCount.set(s.pending_approvals ?? 0);
        this.metaConnected.set(s.meta_connected ?? false);
        if (s.llm_status) {
          this.llmProvider.set(s.llm_status.active_provider ?? '—');
        }
      },
      error: () => {}
    });
  }

  logout() { this.auth.logout(); }
}
