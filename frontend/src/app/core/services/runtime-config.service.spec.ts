import { describe, it, expect, afterEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { RuntimeConfigService } from './runtime-config.service';

describe('RuntimeConfigService', () => {
  const originalConfig = (window as any).__DOLPHIN_CONFIG__;
  const originalLocation = window.location;

  afterEach(() => {
    (window as any).__DOLPHIN_CONFIG__ = originalConfig;
    Object.defineProperty(window, 'location', { value: originalLocation, configurable: true });
    TestBed.resetTestingModule();
  });

  it('uses explicit config.js API base when provided', () => {
    setLocation('http://127.0.0.1:4200/dashboard');
    (window as any).__DOLPHIN_CONFIG__ = { apiBase: 'http://localhost:8000/', wsBase: '' };

    const service = TestBed.inject(RuntimeConfigService);

    expect(service.apiBase).toBe('http://localhost:8000');
  });

  it('normalizes 127.0.0.1 dev UI to localhost backend when config is blank', () => {
    setLocation('http://127.0.0.1:4200/dashboard');
    (window as any).__DOLPHIN_CONFIG__ = { apiBase: '', wsBase: '' };

    const service = TestBed.inject(RuntimeConfigService);

    expect(service.apiBase).toBe('http://localhost:8000');
  });

  function setLocation(url: string) {
    Object.defineProperty(window, 'location', {
      value: new URL(url),
      configurable: true
    });
  }
});
