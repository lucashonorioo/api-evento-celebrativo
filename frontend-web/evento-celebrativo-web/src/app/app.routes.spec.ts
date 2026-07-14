import { AuthenticatedLayoutComponent } from './authenticated-layout/authenticated-layout.component';
import { authGuard } from './auth.guard';
import { EventListComponent } from './events/event-list/event-list.component';
import { guestGuard } from './guest.guard';
import { HomeComponent } from './home/home.component';
import { LoginComponent } from './login/login.component';
import { routes } from './app.routes';

describe('routes', () => {
  it('should keep the login route public for guests only', () => {
    const loginRoute = routes.find((route) => route.path === 'login');

    expect(loginRoute?.component).toBe(LoginComponent);
    expect(loginRoute?.canActivate).toEqual([guestGuard]);
  });

  it('should expose the public events route without guards', () => {
    const eventsRoute = routes.find((route) => route.path === 'eventos');

    expect(eventsRoute?.component).toBe(EventListComponent);
    expect(eventsRoute?.canActivate).toBeUndefined();
  });

  it('should render authenticated events inside the protected app route', () => {
    const appRoute = routes.find((route) => route.path === 'app');
    const appEventsRoute = appRoute?.children?.find((route) => route.path === 'eventos');

    expect(appRoute?.component).toBe(AuthenticatedLayoutComponent);
    expect(appRoute?.canActivate).toEqual([authGuard]);
    expect(appEventsRoute?.component).toBe(EventListComponent);
  });

  it('should preserve the authenticated home route', () => {
    const appRoute = routes.find((route) => route.path === 'app');
    const homeRoute = appRoute?.children?.find((route) => route.path === 'inicio');

    expect(homeRoute?.component).toBe(HomeComponent);
  });
});
