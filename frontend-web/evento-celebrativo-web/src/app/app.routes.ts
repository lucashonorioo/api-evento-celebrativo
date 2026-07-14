import { Routes } from '@angular/router';
import { AuthenticatedLayoutComponent } from './authenticated-layout/authenticated-layout.component';
import { authGuard } from './auth.guard';
import { CommentatorListComponent } from './commentators/commentator-list/commentator-list.component';
import { EucharisticMinisterListComponent } from './eucharistic-ministers/eucharistic-minister-list/eucharistic-minister-list.component';
import { EucharistScheduleListComponent } from './eucharist-schedule/eucharist-schedule-list/eucharist-schedule-list.component';
import { EventDetailComponent } from './events/event-detail/event-detail.component';
import { EventListComponent } from './events/event-list/event-list.component';
import { guestGuard } from './guest.guard';
import { HomeComponent } from './home/home.component';
import { LoginComponent } from './login/login.component';
import { LocationListComponent } from './locations/location-list/location-list.component';
import { MinisterOfTheWordListComponent } from './ministers-of-the-word/minister-of-the-word-list/minister-of-the-word-list.component';
import { PriestListComponent } from './priests/priest-list/priest-list.component';
import { ReaderListComponent } from './readers/reader-list/reader-list.component';

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
      { path: 'eventos', component: EventListComponent },
      { path: 'eventos/:id', component: EventDetailComponent },
      { path: 'escala/eucaristia', component: EucharistScheduleListComponent },
      { path: 'locais', component: LocationListComponent },
      { path: 'leitores', component: ReaderListComponent },
      { path: 'comentaristas', component: CommentatorListComponent },
      { path: 'padres', component: PriestListComponent },
      { path: 'ministros-palavra', component: MinisterOfTheWordListComponent },
      { path: 'ministros-eucaristia', component: EucharisticMinisterListComponent },
      { path: '', redirectTo: 'inicio', pathMatch: 'full' },
    ],
  },
  { path: '', redirectTo: '/login', pathMatch: 'full' },
];
