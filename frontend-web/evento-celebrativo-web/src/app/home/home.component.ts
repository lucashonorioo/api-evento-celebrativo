import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { AuthSessionService } from '../auth-session.service';
import { CurrentUserProfile } from '../current-user-profile/current-user-profile.models';
import { CurrentUserProfileService } from '../current-user-profile/current-user-profile.service';
import { CelebrationEventResponse } from '../events/event.models';
import { EventService } from '../events/event.service';
import { compareEventsByDateTimeAscending, eventLocalTimestamp } from '../events/event-view.utils';

const MAX_UPCOMING_EVENTS = 5;

interface QuickAccessLink {
  readonly label: string;
  readonly description: string;
  readonly path: string;
}

interface AdminActionLink {
  readonly label: string;
  readonly path: string;
}

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [DatePipe, RouterLink],
  templateUrl: './home.component.html',
  styleUrl: './home.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HomeComponent implements OnInit {
  private readonly authSessionService = inject(AuthSessionService);
  private readonly currentUserProfileService = inject(CurrentUserProfileService);
  private readonly eventService = inject(EventService);

  readonly isAdmin = this.authSessionService.hasAuthority('ROLE_ADMIN');
  readonly profileName = computed(() =>
    profileNameFor(this.currentUserProfileService.profile()),
  );

  readonly quickAccessLinks: readonly QuickAccessLink[] = [
    {
      label: 'Eventos',
      description: 'Veja os eventos celebrativos publicados.',
      path: '/app/eventos',
    },
    {
      label: 'Minhas escalas',
      description: 'Consulte os eventos em que você está escalado.',
      path: '/app/minhas-escalas',
    },
    {
      label: 'Escalas',
      description: 'Acesse a consulta mensal de escalas.',
      path: '/app/escalas',
    },
    {
      label: 'Pessoas',
      description: this.isAdmin
        ? 'Gerencie pessoas, ministérios e perfis de acesso.'
        : 'Consulte as categorias ministeriais cadastradas.',
      path: this.isAdmin ? '/app/admin/usuarios' : '/app/pessoas',
    },
    {
      label: 'Locais',
      description: 'Consulte os locais cadastrados para os eventos.',
      path: '/app/locais',
    },
  ];

  readonly adminActionLinks: readonly AdminActionLink[] = [
    { label: 'Gerenciar eventos', path: '/app/admin/eventos' },
    { label: 'Novo evento com escala', path: '/app/admin/escalas/novo-evento' },
    { label: 'Pessoas e acessos', path: '/app/admin/usuarios' },
    { label: 'Gerenciar locais', path: '/app/admin/locais' },
  ];

  readonly upcomingEvents = signal<readonly CelebrationEventResponse[]>([]);
  readonly isLoadingEvents = signal(false);
  readonly eventsErrorMessage = signal<string | null>(null);

  ngOnInit(): void {
    this.loadEvents();
  }

  loadEvents(): void {
    if (this.isLoadingEvents()) {
      return;
    }

    this.isLoadingEvents.set(true);
    this.eventsErrorMessage.set(null);

    this.eventService
      .findAll()
      .pipe(finalize(() => this.isLoadingEvents.set(false)))
      .subscribe({
        next: (events) => {
          this.upcomingEvents.set(selectUpcomingEvents(events));
        },
        error: () => {
          this.eventsErrorMessage.set('Não foi possível carregar os próximos eventos.');
        },
      });
  }

  eventTypeLabel(event: CelebrationEventResponse): string {
    return event.massOrCelebration ? 'Missa' : 'Celebração';
  }

  formatEventTime(eventTime: string): string {
    return eventTime.slice(0, 5);
  }
}

function profileNameFor(profile: CurrentUserProfile | null): string | null {
  if (profile === null) {
    return null;
  }

  const trimmedName = profile.name.trim();

  return trimmedName.length > 0 ? trimmedName : null;
}

function selectUpcomingEvents(
  events: readonly CelebrationEventResponse[],
  now: Date = new Date(),
): CelebrationEventResponse[] {
  return [...events]
    .filter((event) => eventLocalTimestamp(event) >= now.getTime())
    .sort(compareEventsByDateTimeAscending)
    .slice(0, MAX_UPCOMING_EVENTS);
}
