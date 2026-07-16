import { Routes } from '@angular/router';

import { adminGuard } from './admin.guard';
import { authGuard } from './auth.guard';
import { guestGuard } from './guest.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () =>
      import('./login/login.component').then(({ LoginComponent }) => LoginComponent),
    canActivate: [guestGuard],
  },
  {
    path: 'eventos',
    loadComponent: () =>
      import('./events/event-list/event-list.component').then(
        ({ EventListComponent }) => EventListComponent,
      ),
  },
  {
    path: 'eventos/:id',
    loadComponent: () =>
      import('./events/event-detail/event-detail.component').then(
        ({ EventDetailComponent }) => EventDetailComponent,
      ),
  },
  {
    path: 'escala/eucaristia',
    loadComponent: () =>
      import('./eucharist-schedule/eucharist-schedule-list/eucharist-schedule-list.component').then(
        ({ EucharistScheduleListComponent }) => EucharistScheduleListComponent,
      ),
  },
  {
    path: 'app',
    loadComponent: () =>
      import('./authenticated-layout/authenticated-layout.component').then(
        ({ AuthenticatedLayoutComponent }) => AuthenticatedLayoutComponent,
      ),
    canActivate: [authGuard],
    children: [
      {
        path: 'inicio',
        loadComponent: () =>
          import('./home/home.component').then(({ HomeComponent }) => HomeComponent),
      },
      {
        path: 'acesso-negado',
        loadComponent: () =>
          import('./access-denied/access-denied.component').then(
            ({ AccessDeniedComponent }) => AccessDeniedComponent,
          ),
      },
      {
        path: 'eventos',
        loadComponent: () =>
          import('./events/event-list/event-list.component').then(
            ({ EventListComponent }) => EventListComponent,
          ),
      },
      {
        path: 'eventos/:id',
        loadComponent: () =>
          import('./events/event-detail/event-detail.component').then(
            ({ EventDetailComponent }) => EventDetailComponent,
          ),
      },
      {
        path: 'admin/eventos',
        loadComponent: () =>
          import('./events/event-management/event-management.component').then(
            ({ EventManagementComponent }) => EventManagementComponent,
          ),
        canActivate: [adminGuard],
      },
      {
        path: 'escalas',
        loadComponent: () =>
          import('./event-schedules/event-schedule-list/event-schedule-list.component').then(
            ({ EventScheduleListComponent }) => EventScheduleListComponent,
          ),
      },
      {
        path: 'escalas/eventos/:id',
        loadComponent: () =>
          import('./event-schedules/event-schedule-detail/event-schedule-detail.component').then(
            ({ EventScheduleDetailComponent }) => EventScheduleDetailComponent,
          ),
      },
      {
        path: 'admin/escalas/novo-evento',
        loadComponent: () =>
          import('./event-schedules/event-schedule-create/event-schedule-create.component').then(
            ({ EventScheduleCreateComponent }) => EventScheduleCreateComponent,
          ),
        canActivate: [adminGuard],
      },
      {
        path: 'admin/escalas/eventos/:id/editar',
        loadComponent: () =>
          import('./event-schedules/event-schedule-edit/event-schedule-edit.component').then(
            ({ EventScheduleEditComponent }) => EventScheduleEditComponent,
          ),
        canActivate: [adminGuard],
      },
      {
        path: 'escala/eucaristia',
        loadComponent: () =>
          import(
            './eucharist-schedule/eucharist-schedule-list/eucharist-schedule-list.component'
          ).then(({ EucharistScheduleListComponent }) => EucharistScheduleListComponent),
      },
      {
        path: 'locais',
        loadComponent: () =>
          import('./locations/location-list/location-list.component').then(
            ({ LocationListComponent }) => LocationListComponent,
          ),
      },
      {
        path: 'pessoas',
        loadComponent: () =>
          import('./people/people-hub.component').then(
            ({ PeopleHubComponent }) => PeopleHubComponent,
          ),
      },
      {
        path: 'admin/locais',
        loadComponent: () =>
          import('./locations/location-management/location-management.component').then(
            ({ LocationManagementComponent }) => LocationManagementComponent,
          ),
        canActivate: [adminGuard],
      },
      {
        path: 'leitores',
        loadComponent: () =>
          import('./readers/reader-list/reader-list.component').then(
            ({ ReaderListComponent }) => ReaderListComponent,
          ),
      },
      {
        path: 'admin/leitores',
        loadComponent: () =>
          import('./readers/reader-management/reader-management.component').then(
            ({ ReaderManagementComponent }) => ReaderManagementComponent,
          ),
        canActivate: [adminGuard],
      },
      {
        path: 'comentaristas',
        loadComponent: () =>
          import('./commentators/commentator-list/commentator-list.component').then(
            ({ CommentatorListComponent }) => CommentatorListComponent,
          ),
      },
      {
        path: 'admin/comentaristas',
        loadComponent: () =>
          import('./commentators/commentator-management/commentator-management.component').then(
            ({ CommentatorManagementComponent }) => CommentatorManagementComponent,
          ),
        canActivate: [adminGuard],
      },
      {
        path: 'padres',
        loadComponent: () =>
          import('./priests/priest-list/priest-list.component').then(
            ({ PriestListComponent }) => PriestListComponent,
          ),
      },
      {
        path: 'admin/padres',
        loadComponent: () =>
          import('./priests/priest-management/priest-management.component').then(
            ({ PriestManagementComponent }) => PriestManagementComponent,
          ),
        canActivate: [adminGuard],
      },
      {
        path: 'ministros-palavra',
        loadComponent: () =>
          import(
            './ministers-of-the-word/minister-of-the-word-list/minister-of-the-word-list.component'
          ).then(({ MinisterOfTheWordListComponent }) => MinisterOfTheWordListComponent),
      },
      {
        path: 'admin/ministros-palavra',
        loadComponent: () =>
          import(
            './ministers-of-the-word/minister-of-the-word-management/minister-of-the-word-management.component'
          ).then(
            ({ MinisterOfTheWordManagementComponent }) => MinisterOfTheWordManagementComponent,
          ),
        canActivate: [adminGuard],
      },
      {
        path: 'ministros-eucaristia',
        loadComponent: () =>
          import(
            './eucharistic-ministers/eucharistic-minister-list/eucharistic-minister-list.component'
          ).then(({ EucharisticMinisterListComponent }) => EucharisticMinisterListComponent),
      },
      {
        path: 'admin/ministros-eucaristia',
        loadComponent: () =>
          import(
            './eucharistic-ministers/eucharistic-minister-management/eucharistic-minister-management.component'
          ).then(
            ({ EucharisticMinisterManagementComponent }) =>
              EucharisticMinisterManagementComponent,
          ),
        canActivate: [adminGuard],
      },
      { path: '', redirectTo: 'inicio', pathMatch: 'full' },
    ],
  },
  { path: '', redirectTo: '/login', pathMatch: 'full' },
];
