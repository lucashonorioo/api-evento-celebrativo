import { AuthenticatedLayoutComponent } from './authenticated-layout/authenticated-layout.component';
import { adminGuard } from './admin.guard';
import { AccessDeniedComponent } from './access-denied/access-denied.component';
import { authGuard } from './auth.guard';
import { CommentatorListComponent } from './commentators/commentator-list/commentator-list.component';
import { EucharisticMinisterListComponent } from './eucharistic-ministers/eucharistic-minister-list/eucharistic-minister-list.component';
import { EucharistScheduleListComponent } from './eucharist-schedule/eucharist-schedule-list/eucharist-schedule-list.component';
import { EventScheduleCreateComponent } from './event-schedules/event-schedule-create/event-schedule-create.component';
import { EventScheduleDetailComponent } from './event-schedules/event-schedule-detail/event-schedule-detail.component';
import { EventScheduleEditComponent } from './event-schedules/event-schedule-edit/event-schedule-edit.component';
import { EventScheduleListComponent } from './event-schedules/event-schedule-list/event-schedule-list.component';
import { EventDetailComponent } from './events/event-detail/event-detail.component';
import { EventListComponent } from './events/event-list/event-list.component';
import { guestGuard } from './guest.guard';
import { HomeComponent } from './home/home.component';
import { LoginComponent } from './login/login.component';
import { LocationManagementComponent } from './locations/location-management/location-management.component';
import { LocationListComponent } from './locations/location-list/location-list.component';
import { MinisterOfTheWordListComponent } from './ministers-of-the-word/minister-of-the-word-list/minister-of-the-word-list.component';
import { PeopleHubComponent } from './people/people-hub.component';
import { PriestListComponent } from './priests/priest-list/priest-list.component';
import { ReaderListComponent } from './readers/reader-list/reader-list.component';
import { ReaderManagementComponent } from './readers/reader-management/reader-management.component';
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

  it('should not expose location management as a public route', () => {
    const publicManagementRoute = routes.find((route) => route.path === 'admin/locais');

    expect(publicManagementRoute).toBeUndefined();
  });

  it('should not expose people as a public route', () => {
    const publicPeopleRoute = routes.find((route) => route.path === 'pessoas');

    expect(publicPeopleRoute).toBeUndefined();
  });

  it('should not expose readers as a public route', () => {
    const publicReaderRoute = routes.find((route) => route.path === 'leitores');

    expect(publicReaderRoute).toBeUndefined();
  });

  it('should not expose reader management as a public route', () => {
    const publicManagementRoute = routes.find((route) => route.path === 'admin/leitores');

    expect(publicManagementRoute).toBeUndefined();
  });

  it('should not expose commentators as a public route', () => {
    const publicCommentatorRoute = routes.find((route) => route.path === 'comentaristas');

    expect(publicCommentatorRoute).toBeUndefined();
  });

  it('should not expose priests as a public route', () => {
    const publicPriestRoute = routes.find((route) => route.path === 'padres');

    expect(publicPriestRoute).toBeUndefined();
  });

  it('should not expose ministers of the Word as a public route', () => {
    const publicMinisterRoute = routes.find((route) => route.path === 'ministros-palavra');

    expect(publicMinisterRoute).toBeUndefined();
  });

  it('should not expose eucharistic ministers as a public route', () => {
    const publicMinisterRoute = routes.find((route) => route.path === 'ministros-eucaristia');

    expect(publicMinisterRoute).toBeUndefined();
  });

  it('should not expose full schedule details as a public route', () => {
    const publicScheduleDetailRoute = routes.find((route) => route.path === 'escalas/eventos/:id');

    expect(publicScheduleDetailRoute).toBeUndefined();
  });

  it('should not expose schedule editing as a public route', () => {
    const publicScheduleEditRoute = routes.find(
      (route) => route.path === 'admin/escalas/eventos/:id/editar',
    );

    expect(publicScheduleEditRoute).toBeUndefined();
  });

  it('should not expose event with schedule creation as a public route', () => {
    const publicScheduleCreateRoute = routes.find(
      (route) => route.path === 'admin/escalas/novo-evento',
    );

    expect(publicScheduleCreateRoute).toBeUndefined();
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

  it('should render authenticated monthly schedules inside the protected app route', () => {
    const appRoute = routes.find((route) => route.path === 'app');
    const appScheduleRoute = appRoute?.children?.find((route) => route.path === 'escalas');

    expect(appRoute?.component).toBe(AuthenticatedLayoutComponent);
    expect(appRoute?.canActivate).toEqual([authGuard]);
    expect(appScheduleRoute?.component).toBe(EventScheduleListComponent);
    expect(appScheduleRoute?.canActivate).toBeUndefined();
  });

  it('should render authenticated full schedule detail inside the protected app route', () => {
    const appRoute = routes.find((route) => route.path === 'app');
    const appScheduleDetailRoute = appRoute?.children?.find(
      (route) => route.path === 'escalas/eventos/:id',
    );

    expect(appRoute?.component).toBe(AuthenticatedLayoutComponent);
    expect(appRoute?.canActivate).toEqual([authGuard]);
    expect(appScheduleDetailRoute?.component).toBe(EventScheduleDetailComponent);
    expect(appScheduleDetailRoute?.canActivate).toBeUndefined();
  });

  it('should render schedule editing inside the protected app route for admins only', () => {
    const appRoute = routes.find((route) => route.path === 'app');
    const appScheduleEditRoute = appRoute?.children?.find(
      (route) => route.path === 'admin/escalas/eventos/:id/editar',
    );
    const appScheduleDetailRoute = appRoute?.children?.find(
      (route) => route.path === 'escalas/eventos/:id',
    );

    expect(appRoute?.component).toBe(AuthenticatedLayoutComponent);
    expect(appRoute?.canActivate).toEqual([authGuard]);
    expect(appScheduleEditRoute?.component).toBe(EventScheduleEditComponent);
    expect(appScheduleEditRoute?.canActivate).toEqual([adminGuard]);
    expect(appScheduleDetailRoute?.canActivate).toBeUndefined();
  });

  it('should render event with schedule creation inside the protected app route for admins only', () => {
    const appRoute = routes.find((route) => route.path === 'app');
    const appScheduleCreateRoute = appRoute?.children?.find(
      (route) => route.path === 'admin/escalas/novo-evento',
    );

    expect(appRoute?.component).toBe(AuthenticatedLayoutComponent);
    expect(appRoute?.canActivate).toEqual([authGuard]);
    expect(appScheduleCreateRoute?.component).toBe(EventScheduleCreateComponent);
    expect(appScheduleCreateRoute?.canActivate).toEqual([adminGuard]);
  });

  it('should render locations inside the protected app route', () => {
    const appRoute = routes.find((route) => route.path === 'app');
    const appLocationRoute = appRoute?.children?.find((route) => route.path === 'locais');

    expect(appRoute?.component).toBe(AuthenticatedLayoutComponent);
    expect(appRoute?.canActivate).toEqual([authGuard]);
    expect(appLocationRoute?.component).toBe(LocationListComponent);
    expect(appLocationRoute?.canActivate).toBeUndefined();
  });

  it('should render access denied inside the protected app route', () => {
    const appRoute = routes.find((route) => route.path === 'app');
    const accessDeniedRoute = appRoute?.children?.find((route) => route.path === 'acesso-negado');

    expect(appRoute?.component).toBe(AuthenticatedLayoutComponent);
    expect(appRoute?.canActivate).toEqual([authGuard]);
    expect(accessDeniedRoute?.component).toBe(AccessDeniedComponent);
    expect(accessDeniedRoute?.canActivate).toBeUndefined();
  });

  it('should render people hub inside the protected app route', () => {
    const appRoute = routes.find((route) => route.path === 'app');
    const peopleRoute = appRoute?.children?.find((route) => route.path === 'pessoas');

    expect(appRoute?.component).toBe(AuthenticatedLayoutComponent);
    expect(appRoute?.canActivate).toEqual([authGuard]);
    expect(peopleRoute?.component).toBe(PeopleHubComponent);
    expect(peopleRoute?.canActivate).toBeUndefined();
  });

  it('should render location management inside the protected app route for admins only', () => {
    const appRoute = routes.find((route) => route.path === 'app');
    const managementRoute = appRoute?.children?.find((route) => route.path === 'admin/locais');

    expect(appRoute?.component).toBe(AuthenticatedLayoutComponent);
    expect(appRoute?.canActivate).toEqual([authGuard]);
    expect(managementRoute?.component).toBe(LocationManagementComponent);
    expect(managementRoute?.canActivate).toEqual([adminGuard]);
  });

  it('should render readers inside the protected app route', () => {
    const appRoute = routes.find((route) => route.path === 'app');
    const appReaderRoute = appRoute?.children?.find((route) => route.path === 'leitores');

    expect(appRoute?.component).toBe(AuthenticatedLayoutComponent);
    expect(appRoute?.canActivate).toEqual([authGuard]);
    expect(appReaderRoute?.component).toBe(ReaderListComponent);
    expect(appReaderRoute?.canActivate).toBeUndefined();
  });

  it('should render reader management inside the protected app route for admins only', () => {
    const appRoute = routes.find((route) => route.path === 'app');
    const managementRoute = appRoute?.children?.find((route) => route.path === 'admin/leitores');

    expect(appRoute?.component).toBe(AuthenticatedLayoutComponent);
    expect(appRoute?.canActivate).toEqual([authGuard]);
    expect(managementRoute?.component).toBe(ReaderManagementComponent);
    expect(managementRoute?.canActivate).toEqual([adminGuard]);
  });

  it('should render commentators inside the protected app route', () => {
    const appRoute = routes.find((route) => route.path === 'app');
    const appCommentatorRoute = appRoute?.children?.find(
      (route) => route.path === 'comentaristas',
    );

    expect(appRoute?.component).toBe(AuthenticatedLayoutComponent);
    expect(appRoute?.canActivate).toEqual([authGuard]);
    expect(appCommentatorRoute?.component).toBe(CommentatorListComponent);
  });

  it('should render priests inside the protected app route', () => {
    const appRoute = routes.find((route) => route.path === 'app');
    const appPriestRoute = appRoute?.children?.find((route) => route.path === 'padres');

    expect(appRoute?.component).toBe(AuthenticatedLayoutComponent);
    expect(appRoute?.canActivate).toEqual([authGuard]);
    expect(appPriestRoute?.component).toBe(PriestListComponent);
  });

  it('should render ministers of the Word inside the protected app route', () => {
    const appRoute = routes.find((route) => route.path === 'app');
    const appMinisterRoute = appRoute?.children?.find(
      (route) => route.path === 'ministros-palavra',
    );

    expect(appRoute?.component).toBe(AuthenticatedLayoutComponent);
    expect(appRoute?.canActivate).toEqual([authGuard]);
    expect(appMinisterRoute?.component).toBe(MinisterOfTheWordListComponent);
  });

  it('should render eucharistic ministers inside the protected app route', () => {
    const appRoute = routes.find((route) => route.path === 'app');
    const appMinisterRoute = appRoute?.children?.find(
      (route) => route.path === 'ministros-eucaristia',
    );

    expect(appRoute?.component).toBe(AuthenticatedLayoutComponent);
    expect(appRoute?.canActivate).toEqual([authGuard]);
    expect(appMinisterRoute?.component).toBe(EucharisticMinisterListComponent);
  });

  it('should preserve the authenticated home route', () => {
    const appRoute = routes.find((route) => route.path === 'app');
    const homeRoute = appRoute?.children?.find((route) => route.path === 'inicio');

    expect(homeRoute?.component).toBe(HomeComponent);
  });
});
