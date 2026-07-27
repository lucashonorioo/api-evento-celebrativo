import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { AuthSessionService } from '../auth-session.service';
import { CelebrationEventResponse } from '../events/event.models';
import { EventService } from '../events/event.service';

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
  private readonly eventService = inject(EventService);

  readonly isAdmin = this.authSessionService.hasAuthority('ROLE_ADMIN');
  readonly username = greetingNameFor(this.authSessionService.getUsername());

  readonly quickAccessLinks: readonly QuickAccessLink[] = [
    {
      label: 'Eventos',
      description: 'Veja os eventos celebrativos publicados.',
      path: '/app/eventos',
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

function greetingNameFor(username: string | null): string {
  const trimmedUsername = username?.trim() ?? '';

  return trimmedUsername.length > 0 ? trimmedUsername : 'Usuário';
}

function selectUpcomingEvents(
  events: readonly CelebrationEventResponse[],
  now: Date = new Date(),
): CelebrationEventResponse[] {
  return [...events]
    .filter((event) => toLocalDateTime(event.eventDate, event.eventTime).getTime() >= now.getTime())
    .sort(
      (first, second) =>
        toLocalDateTime(first.eventDate, first.eventTime).getTime() -
        toLocalDateTime(second.eventDate, second.eventTime).getTime(),
    )
    .slice(0, MAX_UPCOMING_EVENTS);
}

function toLocalDateTime(eventDate: string, eventTime: string): Date {
  const [year, month, day] = eventDate.split('-').map(Number);
  const [hours, minutes, seconds] = eventTime.split(':').map(Number);

  return new Date(year, month - 1, day, hours, minutes, seconds ?? 0, 0);
}
