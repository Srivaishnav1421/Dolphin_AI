import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

const ICON_PATHS: Record<string, string> = {
  analytics: 'M3 3v18h18M7 15l3-3 3 2 5-7M7 19v-4M13 19v-5M19 19V7',
  auto_awesome: 'M12 3l1.7 4.6L18 9.3l-4.3 1.7L12 16l-1.7-5L6 9.3l4.3-1.7L12 3zM5 15l.8 2.2L8 18l-2.2.8L5 21l-.8-2.2L2 18l2.2-.8L5 15zM19 14l.9 2.1L22 17l-2.1.9L19 20l-.9-2.1L16 17l2.1-.9L19 14z',
  business: 'M4 21V5a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v16M9 7h3M9 11h3M9 15h3M3 21h18M17 10h2a1 1 0 0 1 1 1v10',
  campaign: 'M4 13h3l9 5V6l-9 5H4v2zM7 13l1 5h3l-1.4-4.2',
  chat: 'M21 12a8 8 0 0 1-8 8H7l-4 3 1.4-5.2A8 8 0 1 1 21 12z',
  grid_view: 'M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z',
  group: 'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM22 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75',
  insights: 'M4 19V5M4 19h16M8 16v-5M12 16V7M16 16v-8M20 16v-3',
  key: 'M15 7a4 4 0 1 0 2.8 6.8L21 17v3h-3v-2h-2v-2h-2l-1.2-1.2',
  lock: 'M6 10V8a6 6 0 0 1 12 0v2M5 10h14v11H5z',
  logout: 'M10 17l5-5-5-5M15 12H3M21 3v18h-6',
  moon: 'M21 12.8A8.5 8.5 0 1 1 11.2 3 7 7 0 0 0 21 12.8z',
  palette: 'M12 3a9 9 0 0 0 0 18h1.2a2 2 0 0 0 1.4-3.4l-.3-.3a2 2 0 0 1 1.4-3.4H17a4 4 0 0 0 4-4A7 7 0 0 0 12 3zM7.5 10h.01M10 7.5h.01M14 7.5h.01M16.5 10h.01',
  payments: 'M3 7h18v10H3zM3 10h18M7 15h4',
  psychology: 'M9 18h6M10 22h4M8 14a6 6 0 1 1 8 0c-.9.8-1.4 1.8-1.5 3h-5c-.1-1.2-.6-2.2-1.5-3z',
  refresh: 'M21 12a9 9 0 0 1-15.5 6.2M3 12A9 9 0 0 1 18.5 5.8M18 3v4h-4M6 21v-4h4',
  search: 'M21 21l-4.3-4.3M10.5 18a7.5 7.5 0 1 1 0-15 7.5 7.5 0 0 1 0 15z',
  settings: 'M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM19.4 15a1.7 1.7 0 0 0 .3 1.9l.1.1-2 3.5-.2-.1a1.7 1.7 0 0 0-1.9.3l-.2.1-3.5-2v-.3a1.7 1.7 0 0 0-1.6-1.1h-.2l-3.5 2-.2-.1a1.7 1.7 0 0 0-1.9-.3l-.2.1-2-3.5.1-.1a1.7 1.7 0 0 0 .3-1.9l-.1-.2V9.6l.1-.2a1.7 1.7 0 0 0-.3-1.9l-.1-.1 2-3.5.2.1a1.7 1.7 0 0 0 1.9-.3l.2-.1 3.5 2v.3A1.7 1.7 0 0 0 10.4 7h.2l3.5-2 .2.1a1.7 1.7 0 0 0 1.9.3l.2-.1 2 3.5-.1.1a1.7 1.7 0 0 0-.3 1.9l.1.2v4z',
  settings_accessibility: 'M12 4a2 2 0 1 0 0 .1M4 9h16M12 9v12M8 13l-3 7M16 13l3 7',
  shield: 'M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10zM9 12l2 2 4-5',
  sun: 'M12 4V2M12 22v-2M4 12H2M22 12h-2M5 5l-1.4-1.4M20.4 20.4L19 19M19 5l1.4-1.4M3.6 20.4L5 19M12 17a5 5 0 1 0 0-10 5 5 0 0 0 0 10z',
  monitor: 'M3 4h18v12H3zM8 21h8M12 16v5',
  verified_user: 'M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10zM9 12l2 2 4-5',
};

@Component({
  selector: 'app-icon',
  standalone: true,
  imports: [CommonModule],
  template: `
    <svg
      class="app-icon"
      [attr.width]="size"
      [attr.height]="size"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
      aria-hidden="true"
    >
      <path [attr.d]="path"></path>
    </svg>
  `,
  styles: [`
    .app-icon {
      display: block;
      flex: 0 0 auto;
    }
  `]
})
export class AppIcon {
  @Input() name = 'grid_view';
  @Input() size = 18;

  get path() {
    return ICON_PATHS[this.name] ?? ICON_PATHS['grid_view'];
  }
}
