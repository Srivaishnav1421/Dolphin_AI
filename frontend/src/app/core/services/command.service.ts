import { Injectable, HostListener } from '@angular/core';
import { Router } from '@angular/router';
import { StateService } from './state.service';

export interface CommandItem {
  id: string;
  name: string;
  category: string;
  shortcut?: string;
  action: () => void;
}

@Injectable({
  providedIn: 'root'
})
export class CommandService {
  private commands: CommandItem[] = [];

  constructor(private state: StateService, private router: Router) {
    this.registerDefaultCommands();
    this.setupGlobalKeyListener();
  }

  public getCommands(): CommandItem[] {
    return this.commands;
  }

  public registerCommand(command: CommandItem): void {
    if (!this.commands.some(c => c.id === command.id)) {
      this.commands.push(command);
    }
  }

  private registerDefaultCommands(): void {
    const nav = (path: string) => {
      this.router.navigate([path]);
      this.state.isCommandPaletteOpen.set(false);
    };

    this.commands = [
      { id: 'nav-db', name: 'Open Growth Home', category: 'Navigation', shortcut: 'G D', action: () => nav('/dashboard') },
      { id: 'nav-crm', name: 'Open CRM', category: 'Navigation', shortcut: 'G C', action: () => nav('/leads') },
      { id: 'nav-camp', name: 'Open Campaigns', category: 'Navigation', shortcut: 'G P', action: () => nav('/campaigns') },
      { id: 'nav-studio', name: 'Open Creative Studio', category: 'Navigation', shortcut: 'G I', action: () => nav('/creatives') },
      { id: 'nav-brain', name: 'Open AI Insights', category: 'Navigation', shortcut: 'G B', action: () => nav('/ad-brain') },
      { id: 'nav-wf', name: 'Open Automation', category: 'Navigation', shortcut: 'G W', action: () => nav('/automation') },
      { id: 'nav-set', name: 'Open Settings', category: 'Navigation', shortcut: 'G S', action: () => nav('/settings') },
      { id: 'nav-an', name: 'Open Analytics', category: 'Navigation', shortcut: 'G N', action: () => nav('/analytics') },
      
      { 
        id: 'run-agent', 
        name: 'Execute Autonomous Ad Agent', 
        category: 'Actions', 
        action: () => {
          this.state.isCommandPaletteOpen.set(false);
          nav('/ad-brain');
        } 
      },
      { 
        id: 'create-wf', 
        name: 'Create New Workflow Template', 
        category: 'Actions', 
        action: () => {
          this.state.isCommandPaletteOpen.set(false);
          nav('/automation');
        } 
      }
    ];
  }

  private setupGlobalKeyListener(): void {
    window.addEventListener('keydown', (event: KeyboardEvent) => {
      const isMac = navigator.platform.toUpperCase().indexOf('MAC') >= 0;
      const isTrigger = (isMac && event.metaKey && event.key.toLowerCase() === 'k') || 
                        (!isMac && event.ctrlKey && event.key.toLowerCase() === 'k');
      
      if (isTrigger) {
        event.preventDefault();
        this.state.isCommandPaletteOpen.update(open => !open);
      }
    });
  }
}
