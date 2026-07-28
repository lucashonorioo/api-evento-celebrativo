import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { AuthSessionService } from '../auth-session.service';
import { CurrentUserProfile } from '../current-user-profile/current-user-profile.models';
import { CurrentUserProfileService } from '../current-user-profile/current-user-profile.service';
import {
  formatLocalDate,
  scheduleAssignmentLabel,
} from '../current-user-schedules/current-user-schedule-view.utils';
import { CurrentUserSchedule, CurrentUserScheduleQuery } from '../current-user-schedules/current-user-schedule.models';
import { CurrentUserScheduleService } from '../current-user-schedules/current-user-schedule.service';
import { EventScheduleType } from '../event-schedules/event-schedule.models';
import { CelebrationEventResponse } from '../events/event.models';
import { EventService } from '../events/event.service';
import {
  EventDateTime,
  compareEventsByDateTimeAscending,
  eventLocalTimestamp,
} from '../events/event-view.utils';

const MAX_UPCOMING_EVENTS = 5;
const MAX_UPCOMING_USER_SCHEDULES = 5;
const USER_SCHEDULES_LOOKAHEAD_DAYS = 90;
const USER_SCHEDULES_PAGE_SIZE = 20;

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
  private readonly currentUserScheduleService = inject(CurrentUserScheduleService);

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

  readonly upcomingUserSchedules = signal<readonly CurrentUserSchedule[]>([]);
  readonly isLoadingUserSchedules = signal(false);
  readonly userSchedulesErrorMessage = signal<string | null>(null);

  ngOnInit(): void {
    this.loadEvents();
    this.loadUserSchedules();
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

  loadUserSchedules(): void {
    if (this.isLoadingUserSchedules()) {
      return;
    }

    this.isLoadingUserSchedules.set(true);
    this.userSchedulesErrorMessage.set(null);

    this.currentUserScheduleService
      .findSchedules(userSchedulesQuery())
      .pipe(finalize(() => this.isLoadingUserSchedules.set(false)))
      .subscribe({
        next: (page) => {
          this.upcomingUserSchedules.set(selectUpcomingUserSchedules(page.content));
        },
        error: () => {
          this.userSchedulesErrorMessage.set('Não foi possível carregar suas próximas escalas.');
        },
      });
  }

  eventTypeLabel(item: { readonly massOrCelebration: boolean }): string {
    return item.massOrCelebration ? 'Missa' : 'Celebração';
  }

  assignmentLabel(assignment: EventScheduleType): string {
    return scheduleAssignmentLabel(assignment);
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
  return selectUpcoming(events, MAX_UPCOMING_EVENTS, now);
}

function selectUpcomingUserSchedules(
  schedules: readonly CurrentUserSchedule[],
  now: Date = new Date(),
): CurrentUserSchedule[] {
  return selectUpcoming(schedules, MAX_UPCOMING_USER_SCHEDULES, now);
}

function selectUpcoming<T extends EventDateTime>(
  items: readonly T[],
  maxItems: number,
  now: Date,
): T[] {
  return [...items]
    .filter((item) => eventLocalTimestamp(item) >= now.getTime())
    .sort(compareEventsByDateTimeAscending)
    .slice(0, maxItems);
}

function userSchedulesQuery(now: Date = new Date()): CurrentUserScheduleQuery {
  const endDate = new Date(now.getFullYear(), now.getMonth(), now.getDate() + USER_SCHEDULES_LOOKAHEAD_DAYS);

  return {
    startDate: formatLocalDate(now),
    endDate: formatLocalDate(endDate),
    page: 0,
    size: USER_SCHEDULES_PAGE_SIZE,
  };
}
