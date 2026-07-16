import { Routes } from '@angular/router';
import { AccessDeniedComponent } from './access-denied/access-denied.component';
import { adminGuard } from './admin.guard';
import { AuthenticatedLayoutComponent } from './authenticated-layout/authenticated-layout.component';
import { authGuard } from './auth.guard';
import { CommentatorListComponent } from './commentators/commentator-list/commentator-list.component';
import { CommentatorManagementComponent } from './commentators/commentator-management/commentator-management.component';
import { EucharisticMinisterListComponent } from './eucharistic-ministers/eucharistic-minister-list/eucharistic-minister-list.component';
import { EucharisticMinisterManagementComponent } from './eucharistic-ministers/eucharistic-minister-management/eucharistic-minister-management.component';
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
import { MinisterOfTheWordManagementComponent } from './ministers-of-the-word/minister-of-the-word-management/minister-of-the-word-management.component';
import { PeopleHubComponent } from './people/people-hub.component';
import { PriestListComponent } from './priests/priest-list/priest-list.component';
import { ReaderListComponent } from './readers/reader-list/reader-list.component';
import { ReaderManagementComponent } from './readers/reader-management/reader-management.component';

export const routes: Routes = [
  { path: 'login', component: LoginComponent, canActivate: [guestGuard] },
  { path: 'eventos', component: EventListComponent },
  { path: 'eventos/:id', component: EventDetailComponent },
  { path: 'escala/eucaristia', component: EucharistScheduleListComponent },
  {
    path: 'app',
    component: AuthenticatedLayoutComponent,
    canActivate: [authGuard],
    children: [
      { path: 'inicio', component: HomeComponent },
      { path: 'acesso-negado', component: AccessDeniedComponent },
      { path: 'eventos', component: EventListComponent },
      { path: 'eventos/:id', component: EventDetailComponent },
      { path: 'escalas', component: EventScheduleListComponent },
      { path: 'escalas/eventos/:id', component: EventScheduleDetailComponent },
      {
        path: 'admin/escalas/novo-evento',
        component: EventScheduleCreateComponent,
        canActivate: [adminGuard],
      },
      {
        path: 'admin/escalas/eventos/:id/editar',
        component: EventScheduleEditComponent,
        canActivate: [adminGuard],
      },
      { path: 'escala/eucaristia', component: EucharistScheduleListComponent },
      { path: 'locais', component: LocationListComponent },
      { path: 'pessoas', component: PeopleHubComponent },
      {
        path: 'admin/locais',
        component: LocationManagementComponent,
        canActivate: [adminGuard],
      },
      { path: 'leitores', component: ReaderListComponent },
      {
        path: 'admin/leitores',
        component: ReaderManagementComponent,
        canActivate: [adminGuard],
      },
      { path: 'comentaristas', component: CommentatorListComponent },
      {
        path: 'admin/comentaristas',
        component: CommentatorManagementComponent,
        canActivate: [adminGuard],
      },
      { path: 'padres', component: PriestListComponent },
      { path: 'ministros-palavra', component: MinisterOfTheWordListComponent },
      {
        path: 'admin/ministros-palavra',
        component: MinisterOfTheWordManagementComponent,
        canActivate: [adminGuard],
      },
      { path: 'ministros-eucaristia', component: EucharisticMinisterListComponent },
      {
        path: 'admin/ministros-eucaristia',
        component: EucharisticMinisterManagementComponent,
        canActivate: [adminGuard],
      },
      { path: '', redirectTo: 'inicio', pathMatch: 'full' },
    ],
  },
  { path: '', redirectTo: '/login', pathMatch: 'full' },
];
