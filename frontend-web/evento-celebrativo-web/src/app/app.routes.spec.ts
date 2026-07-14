import { AuthenticatedLayoutComponent } from './authenticated-layout/authenticated-layout.component';
import { authGuard } from './auth.guard';
import { EucharistScheduleListComponent } from './eucharist-schedule/eucharist-schedule-list/eucharist-schedule-list.component';
import { EventDetailComponent } from './events/event-detail/event-detail.component';
import { EventListComponent } from './events/event-list/event-list.component';
import { guestGuard } from './guest.guard';
import { HomeComponent } from './home/home.component';
import { LoginComponent } from './login/login.component';
import { LocationListComponent } from './locations/location-list/location-list.component';
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

  it('should expose the public event detail route without guards', () => {
    const eventDetailRoute = routes.find((route) => route.path === 'eventos/:id');

    expect(eventDetailRoute?.component).toBe(EventDetailComponent);
    expect(eventDetailRoute?.canActivate).toBeUndefined();
  });

  it('should expose the public Eucharist schedule route without guards', () => {
    const eucharistScheduleRoute = routes.find((route) => route.path === 'escala/eucaristia');

    expect(eucharistScheduleRoute?.component).toBe(EucharistScheduleListComponent);
    expect(eucharistScheduleRoute?.canActivate).toBeUndefined();
  });

  it('should not expose locations as a public route', () => {
    const publicLocationRoute = routes.find((route) => route.path === 'locais');

    expect(publicLocationRoute).toBeUndefined();
  });

  it('should render authenticated events inside the protected app route', () => {
    const appRoute = routes.find((route) => route.path === 'app');
    const appEventsRoute = appRoute?.children?.find((route) => route.path === 'eventos');

    expect(appRoute?.component).toBe(AuthenticatedLayoutComponent);
    expect(appRoute?.canActivate).toEqual([authGuard]);
    expect(appEventsRoute?.component).toBe(EventListComponent);
  });

  it('should render authenticated event details inside the protected app route', () => {
    const appRoute = routes.find((route) => route.path === 'app');
    const appEventDetailRoute = appRoute?.children?.find((route) => route.path === 'eventos/:id');

    expect(appRoute?.component).toBe(AuthenticatedLayoutComponent);
    expect(appRoute?.canActivate).toEqual([authGuard]);
    expect(appEventDetailRoute?.component).toBe(EventDetailComponent);
  });

  it('should render authenticated Eucharist schedule inside the protected app route', () => {
    const appRoute = routes.find((route) => route.path === 'app');
    const appScheduleRoute = appRoute?.children?.find(
      (route) => route.path === 'escala/eucaristia',
    );

    expect(appRoute?.component).toBe(AuthenticatedLayoutComponent);
    expect(appRoute?.canActivate).toEqual([authGuard]);
    expect(appScheduleRoute?.component).toBe(EucharistScheduleListComponent);
  });

  it('should render locations inside the protected app route', () => {
    const appRoute = routes.find((route) => route.path === 'app');
    const appLocationRoute = appRoute?.children?.find((route) => route.path === 'locais');

    expect(appRoute?.component).toBe(AuthenticatedLayoutComponent);
    expect(appRoute?.canActivate).toEqual([authGuard]);
    expect(appLocationRoute?.component).toBe(LocationListComponent);
  });

  it('should preserve the authenticated home route', () => {
    const appRoute = routes.find((route) => route.path === 'app');
    const homeRoute = appRoute?.children?.find((route) => route.path === 'inicio');

    expect(homeRoute?.component).toBe(HomeComponent);
  });
});
