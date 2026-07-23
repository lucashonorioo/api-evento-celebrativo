import { Type } from '@angular/core';
import { Route } from '@angular/router';
import { firstValueFrom, isObservable } from 'rxjs';

import { AccessDeniedComponent } from './access-denied/access-denied.component';
import { AdminUserManagementComponent } from './admin-users/admin-user-management/admin-user-management.component';
import { adminGuard } from './admin.guard';
import { AuthenticatedLayoutComponent } from './authenticated-layout/authenticated-layout.component';
import { authGuard } from './auth.guard';
import { CommentatorListComponent } from './commentators/commentator-list/commentator-list.component';
import { CommentatorManagementComponent } from './commentators/commentator-management/commentator-management.component';
import { EucharisticMinisterListComponent } from './eucharistic-ministers/eucharistic-minister-list/eucharistic-minister-list.component';
import { EucharisticMinisterManagementComponent } from './eucharistic-ministers/eucharistic-minister-management/eucharistic-minister-management.component';
import { EucharistScheduleListComponent } from './eucharist-schedule/eucharist-schedule-list/eucharist-schedule-list.component';
import { EventAssignmentAuditPageComponent } from './event-assignment-audit/event-assignment-audit-page/event-assignment-audit-page.component';
import { EventScheduleCreateComponent } from './event-schedules/event-schedule-create/event-schedule-create.component';
import { EventScheduleDetailComponent } from './event-schedules/event-schedule-detail/event-schedule-detail.component';
import { EventScheduleEditComponent } from './event-schedules/event-schedule-edit/event-schedule-edit.component';
import { EventScheduleListComponent } from './event-schedules/event-schedule-list/event-schedule-list.component';
import { EventDetailComponent } from './events/event-detail/event-detail.component';
import { EventListComponent } from './events/event-list/event-list.component';
import { EventManagementComponent } from './events/event-management/event-management.component';
import { guestGuard } from './guest.guard';
import { HomeComponent } from './home/home.component';
import { LoginComponent } from './login/login.component';
import { LocationManagementComponent } from './locations/location-management/location-management.component';
import { LocationListComponent } from './locations/location-list/location-list.component';
import { MinisterOfTheWordListComponent } from './ministers-of-the-word/minister-of-the-word-list/minister-of-the-word-list.component';
import { MinisterOfTheWordManagementComponent } from './ministers-of-the-word/minister-of-the-word-management/minister-of-the-word-management.component';
import { PeopleHubComponent } from './people/people-hub.component';
import { PriestListComponent } from './priests/priest-list/priest-list.component';
import { PriestManagementComponent } from './priests/priest-management/priest-management.component';
import { ReaderListComponent } from './readers/reader-list/reader-list.component';
import { ReaderManagementComponent } from './readers/reader-management/reader-management.component';
import { routes } from './app.routes';

type LazyLoadedComponent = Type<unknown> | { default: Type<unknown> };

describe('routes', () => {
  it('should keep the login route public for guests only', async () => {
    const loginRoute = findPublicRoute('login');

    await expectLazyComponent(loginRoute, LoginComponent);
    expect(loginRoute?.canActivate).toEqual([guestGuard]);
  });

  it('should expose the public events route without guards', async () => {
    const eventsRoute = findPublicRoute('eventos');

    await expectLazyComponent(eventsRoute, EventListComponent);
    expect(eventsRoute?.canActivate).toBeUndefined();
  });

  it('should expose the public event detail route without guards', async () => {
    const eventDetailRoute = findPublicRoute('eventos/:id');

    await expectLazyComponent(eventDetailRoute, EventDetailComponent);
    expect(eventDetailRoute?.canActivate).toBeUndefined();
  });

  it('should expose the public Eucharist schedule route without guards', async () => {
    const eucharistScheduleRoute = findPublicRoute('escala/eucaristia');

    await expectLazyComponent(eucharistScheduleRoute, EucharistScheduleListComponent);
    expect(eucharistScheduleRoute?.canActivate).toBeUndefined();
  });

  it('should not expose authenticated-only routes as public routes', () => {
    const privatePaths = [
      'locais',
      'admin/eventos',
      'admin/usuarios',
      'admin/locais',
      'pessoas',
      'leitores',
      'admin/leitores',
      'comentaristas',
      'admin/comentaristas',
      'padres',
      'admin/padres',
      'ministros-palavra',
      'admin/ministros-palavra',
      'ministros-eucaristia',
      'admin/ministros-eucaristia',
      'escalas/eventos/:id',
      'admin/escalas/eventos/:id/editar',
      'admin/escalas/novo-evento',
      'admin/auditoria-de-escalas',
    ];

    for (const path of privatePaths) {
      expect(findPublicRoute(path)).toBeUndefined();
    }
  });

  it('should render authenticated events inside the protected app route', async () => {
    const appEventsRoute = findAppChildRoute('eventos');

    expectAppRouteProtection();
    await expectLazyComponent(appEventsRoute, EventListComponent);
  });

  it('should render authenticated event details inside the protected app route', async () => {
    const appEventDetailRoute = findAppChildRoute('eventos/:id');

    expectAppRouteProtection();
    await expectLazyComponent(appEventDetailRoute, EventDetailComponent);
  });

  it('should render event management inside the protected app route for admins only', async () => {
    const managementRoute = findAppChildRoute('admin/eventos');

    expectAppRouteProtection();
    await expectLazyComponent(managementRoute, EventManagementComponent);
    expect(managementRoute?.canActivate).toEqual([adminGuard]);
  });

  it('should render authenticated Eucharist schedule inside the protected app route', async () => {
    const appScheduleRoute = findAppChildRoute('escala/eucaristia');

    expectAppRouteProtection();
    await expectLazyComponent(appScheduleRoute, EucharistScheduleListComponent);
  });

  it('should render authenticated monthly schedules inside the protected app route', async () => {
    const appScheduleRoute = findAppChildRoute('escalas');

    expectAppRouteProtection();
    await expectLazyComponent(appScheduleRoute, EventScheduleListComponent);
    expect(appScheduleRoute?.canActivate).toBeUndefined();
  });

  it('should render authenticated full schedule detail inside the protected app route', async () => {
    const appScheduleDetailRoute = findAppChildRoute('escalas/eventos/:id');

    expectAppRouteProtection();
    await expectLazyComponent(appScheduleDetailRoute, EventScheduleDetailComponent);
    expect(appScheduleDetailRoute?.canActivate).toBeUndefined();
  });

  it('should render schedule editing inside the protected app route for admins only', async () => {
    const appScheduleEditRoute = findAppChildRoute('admin/escalas/eventos/:id/editar');
    const appScheduleDetailRoute = findAppChildRoute('escalas/eventos/:id');

    expectAppRouteProtection();
    await expectLazyComponent(appScheduleEditRoute, EventScheduleEditComponent);
    expect(appScheduleEditRoute?.canActivate).toEqual([adminGuard]);
    expect(appScheduleDetailRoute?.canActivate).toBeUndefined();
  });

  it('should render event with schedule creation inside the protected app route for admins only', async () => {
    const appScheduleCreateRoute = findAppChildRoute('admin/escalas/novo-evento');

    expectAppRouteProtection();
    await expectLazyComponent(appScheduleCreateRoute, EventScheduleCreateComponent);
    expect(appScheduleCreateRoute?.canActivate).toEqual([adminGuard]);
  });

  it('should render event assignment audit inside the protected app route for admins only', async () => {
    const auditRoute = findAppChildRoute('admin/auditoria-de-escalas');

    expectAppRouteProtection();
    await expectLazyComponent(auditRoute, EventAssignmentAuditPageComponent);
    expect(auditRoute?.canActivate).toEqual([adminGuard]);
  });

  it('should render locations inside the protected app route', async () => {
    const appLocationRoute = findAppChildRoute('locais');

    expectAppRouteProtection();
    await expectLazyComponent(appLocationRoute, LocationListComponent);
    expect(appLocationRoute?.canActivate).toBeUndefined();
  });

  it('should render access denied inside the protected app route', async () => {
    const accessDeniedRoute = findAppChildRoute('acesso-negado');

    expectAppRouteProtection();
    await expectLazyComponent(accessDeniedRoute, AccessDeniedComponent);
    expect(accessDeniedRoute?.canActivate).toBeUndefined();
  });

  it('should render people hub inside the protected app route', async () => {
    const peopleRoute = findAppChildRoute('pessoas');

    expectAppRouteProtection();
    await expectLazyComponent(peopleRoute, PeopleHubComponent);
    expect(peopleRoute?.canActivate).toBeUndefined();
  });

  it('should render user management inside the protected app route for admins only', async () => {
    const managementRoute = findAppChildRoute('admin/usuarios');

    expectAppRouteProtection();
    await expectLazyComponent(managementRoute, AdminUserManagementComponent);
    expect(managementRoute?.canActivate).toEqual([adminGuard]);
  });

  it('should render location management inside the protected app route for admins only', async () => {
    const managementRoute = findAppChildRoute('admin/locais');

    expectAppRouteProtection();
    await expectLazyComponent(managementRoute, LocationManagementComponent);
    expect(managementRoute?.canActivate).toEqual([adminGuard]);
  });

  it('should render readers inside the protected app route', async () => {
    const appReaderRoute = findAppChildRoute('leitores');

    expectAppRouteProtection();
    await expectLazyComponent(appReaderRoute, ReaderListComponent);
    expect(appReaderRoute?.canActivate).toBeUndefined();
  });

  it('should render reader management inside the protected app route for admins only', async () => {
    const managementRoute = findAppChildRoute('admin/leitores');

    expectAppRouteProtection();
    await expectLazyComponent(managementRoute, ReaderManagementComponent);
    expect(managementRoute?.canActivate).toEqual([adminGuard]);
  });

  it('should render commentators inside the protected app route', async () => {
    const appCommentatorRoute = findAppChildRoute('comentaristas');

    expectAppRouteProtection();
    await expectLazyComponent(appCommentatorRoute, CommentatorListComponent);
  });

  it('should render commentator management inside the protected app route for admins only', async () => {
    const managementRoute = findAppChildRoute('admin/comentaristas');

    expectAppRouteProtection();
    await expectLazyComponent(managementRoute, CommentatorManagementComponent);
    expect(managementRoute?.canActivate).toEqual([adminGuard]);
  });

  it('should render priests inside the protected app route', async () => {
    const appPriestRoute = findAppChildRoute('padres');

    expectAppRouteProtection();
    await expectLazyComponent(appPriestRoute, PriestListComponent);
  });

  it('should render priest management inside the protected app route for admins only', async () => {
    const managementRoute = findAppChildRoute('admin/padres');

    expectAppRouteProtection();
    await expectLazyComponent(managementRoute, PriestManagementComponent);
    expect(managementRoute?.canActivate).toEqual([adminGuard]);
  });

  it('should render ministers of the Word inside the protected app route', async () => {
    const appMinisterRoute = findAppChildRoute('ministros-palavra');

    expectAppRouteProtection();
    await expectLazyComponent(appMinisterRoute, MinisterOfTheWordListComponent);
  });

  it('should render minister of the Word management inside the protected app route for admins only', async () => {
    const managementRoute = findAppChildRoute('admin/ministros-palavra');

    expectAppRouteProtection();
    await expectLazyComponent(managementRoute, MinisterOfTheWordManagementComponent);
    expect(managementRoute?.canActivate).toEqual([adminGuard]);
  });

  it('should render eucharistic ministers inside the protected app route', async () => {
    const appMinisterRoute = findAppChildRoute('ministros-eucaristia');

    expectAppRouteProtection();
    await expectLazyComponent(appMinisterRoute, EucharisticMinisterListComponent);
  });

  it('should render eucharistic minister management inside the protected app route for admins only', async () => {
    const managementRoute = findAppChildRoute('admin/ministros-eucaristia');

    expectAppRouteProtection();
    await expectLazyComponent(managementRoute, EucharisticMinisterManagementComponent);
    expect(managementRoute?.canActivate).toEqual([adminGuard]);
  });

  it('should preserve the authenticated home route', async () => {
    const homeRoute = findAppChildRoute('inicio');

    await expectLazyComponent(homeRoute, HomeComponent);
  });

  function findPublicRoute(path: string): Route | undefined {
    return routes.find((route) => route.path === path);
  }

  function findAppRoute(): Route | undefined {
    return findPublicRoute('app');
  }

  function findAppChildRoute(path: string): Route | undefined {
    return findAppRoute()?.children?.find((route) => route.path === path);
  }

  function expectAppRouteProtection(): void {
    const appRoute = findAppRoute();

    expect(appRoute?.component).toBeUndefined();
    expect(appRoute?.loadComponent).toEqual(jasmine.any(Function));
    expect(appRoute?.canActivate).toEqual([authGuard]);
  }

  async function expectLazyComponent(
    route: Route | undefined,
    expectedComponent: Type<unknown>,
  ): Promise<void> {
    expect(route?.component).toBeUndefined();
    expect(route?.loadComponent).toEqual(jasmine.any(Function));

    const component = await resolveLazyComponent(route);

    expect(component).toBe(expectedComponent);
  }

  async function resolveLazyComponent(route: Route | undefined): Promise<Type<unknown> | null> {
    const component = route?.loadComponent?.();

    if (!component) {
      return null;
    }

    if (isObservable(component)) {
      const resolvedComponent = await firstValueFrom(component);

      return unwrapLazyComponent(resolvedComponent);
    }

    const resolvedComponent = component instanceof Promise ? await component : component;

    return unwrapLazyComponent(resolvedComponent);
  }

  function unwrapLazyComponent(component: LazyLoadedComponent): Type<unknown> {
    if (isDefaultExport(component)) {
      return component.default;
    }

    return component;
  }

  function isDefaultExport(component: LazyLoadedComponent): component is { default: Type<unknown> } {
    return typeof component === 'object' && 'default' in component;
  }
});
