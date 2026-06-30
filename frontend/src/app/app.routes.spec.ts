import { describe, expect, it } from 'vitest';
import { routes } from './app.routes';

describe('app routes', () => {
  it('registers first-class Approval Queue route', () => {
    const shell = routes.find(route => route.path === '');
    const approvalsRoute = shell?.children?.find(route => route.path === 'approvals');

    expect(approvalsRoute).toBeTruthy();
  });
});
