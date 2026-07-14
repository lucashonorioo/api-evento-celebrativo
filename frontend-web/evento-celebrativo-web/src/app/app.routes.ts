import { Routes } from '@angular/router';
import { AuthenticatedLayoutComponent } from './authenticated-layout/authenticated-layout.component';
import { authGuard } from './auth.guard';
import { EventListComponent } from './events/event-list/event-list.component';
import { guestGuard } from './guest.guard';
import { HomeComponent } from './home/home.component';
import { LoginComponent } from './login/login.component';

export const routes: Routes = [
  { path: 'login', component: LoginComponent, canActivate: [guestGuard] },
  { path: 'eventos', component: EventListComponent },
  {
    path: 'app',
    component: AuthenticatedLayoutComponent,
    canActivate: [authGuard],
    children: [
      { path: 'inicio', component: HomeComponent },
      { path: 'eventos', component: EventListComponent },
      { path: '', redirectTo: 'inicio', pathMatch: 'full' },
    ],
  },
  { path: '', redirectTo: '/login', pathMatch: 'full' },
];
