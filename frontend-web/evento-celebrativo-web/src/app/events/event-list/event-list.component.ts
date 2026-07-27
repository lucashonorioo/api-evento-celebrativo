import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { AuthSessionService } from '../../auth-session.service';
import { CelebrationEventResponse } from '../event.models';
import { EventService } from '../event.service';

type PeriodFilter = 'upcoming' | 'past' | 'all';
type TypeFilter = 'all' | 'mass' | 'celebration';

interface EventFilters {
  readonly period: PeriodFilter;
  readonly search: string;
  readonly type: TypeFilter;
}

interface PeriodOption {
  readonly value: PeriodFilter;
  readonly label: string;
}

interface TypeOption {
  readonly value: TypeFilter;
  readonly label: string;
}

@Component({
  selector: 'app-event-list',
  standalone: true,
  imports: [DatePipe, RouterLink],
  templateUrl: './event-list.component.html',
  styleUrl: './event-list.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EventListComponent implements OnInit {
  private readonly authSessionService = inject(AuthSessionService);
  private readonly eventService = inject(EventService);
  private readonly router = inject(Router);

  readonly isAdmin = this.authSessionService.hasAuthority('ROLE_ADMIN');
  readonly allEvents = signal<CelebrationEventResponse[]>([]);
  readonly isLoading = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly periodFilter = signal<PeriodFilter>('upcoming');
  readonly searchTerm = signal('');
  readonly typeFilter = signal<TypeFilter>('all');

  readonly periodOptions: readonly PeriodOption[] = [
    { value: 'upcoming', label: 'Próximos' },
    { value: 'past', label: 'Passados' },
    { value: 'all', label: 'Todos' },
  ];

  readonly typeOptions: readonly TypeOption[] = [
    { value: 'all', label: 'Todos' },
    { value: 'mass', label: 'Missa' },
    { value: 'celebration', label: 'Celebração' },
  ];

  readonly visibleEvents = computed(() =>
    applyEventFilters(this.allEvents(), {
      period: this.periodFilter(),
      search: this.searchTerm(),
      type: this.typeFilter(),
    }),
  );

  readonly resultCountLabel = computed(() => resultCountLabelFor(this.visibleEvents().length));

  readonly emptyStateMessage = computed(() => {
    if (this.visibleEvents().length > 0) {
      return null;
    }

    if (this.allEvents().length === 0) {
      return 'Nenhum evento foi cadastrado.';
    }

    const hasActiveSearchOrType = this.searchTerm().trim().length > 0 || this.typeFilter() !== 'all';

    if (!hasActiveSearchOrType) {
      if (this.periodFilter() === 'upcoming') {
        return 'Nenhum próximo evento foi encontrado.';
      }

      if (this.periodFilter() === 'past') {
        return 'Nenhum evento passado foi encontrado.';
      }
    }

    return 'Nenhum evento corresponde aos filtros informados.';
  });

  readonly showClearFiltersInEmptyState = computed(
    () => this.allEvents().length > 0 && this.visibleEvents().length === 0,
  );

  ngOnInit(): void {
    this.loadEvents();
  }

  loadEvents(): void {
    if (this.isLoading()) {
      return;
    }

    this.isLoading.set(true);
    this.errorMessage.set(null);

    this.eventService
      .findAll()
      .pipe(finalize(() => this.isLoading.set(false)))
      .subscribe({
        next: (events) => {
          this.allEvents.set(events);
        },
        error: () => {
          this.allEvents.set([]);
          this.errorMessage.set('Não foi possível carregar os eventos. Tente novamente.');
        },
      });
  }

  setPeriodFilter(period: PeriodFilter): void {
    this.periodFilter.set(period);
  }

  setSearchTerm(event: Event): void {
    const target = event.target;

    if (!(target instanceof HTMLInputElement)) {
      return;
    }

    this.searchTerm.set(target.value);
  }

  setTypeFilter(event: Event): void {
    const target = event.target;

    if (!(target instanceof HTMLSelectElement)) {
      return;
    }

    this.typeFilter.set(target.value as TypeFilter);
  }

  clearFilters(): void {
    this.periodFilter.set('upcoming');
    this.searchTerm.set('');
    this.typeFilter.set('all');
  }

  getEventType(event: CelebrationEventResponse): string {
    return event.massOrCelebration ? 'Missa' : 'Celebração';
  }

  formatTime(eventTime: string): string {
    return eventTime.slice(0, 5);
  }

  canManageEvents(): boolean {
    return this.isAdmin && this.router.url.startsWith('/app/eventos');
  }
}

function applyEventFilters(
  events: readonly CelebrationEventResponse[],
  filters: EventFilters,
  now: Date = new Date(),
): CelebrationEventResponse[] {
  const nowTime = now.getTime();

  return events
    .filter((event) => {
      const eventTime = toLocalDateTime(event.eventDate, event.eventTime).getTime();

      if (filters.period === 'upcoming' && eventTime < nowTime) {
        return false;
      }

      if (filters.period === 'past' && eventTime >= nowTime) {
        return false;
      }

      return matchesType(event, filters.type) && matchesSearch(event, filters.search);
    })
    .sort((first, second) => {
      const firstTime = toLocalDateTime(first.eventDate, first.eventTime).getTime();
      const secondTime = toLocalDateTime(second.eventDate, second.eventTime).getTime();

      return filters.period === 'past' ? secondTime - firstTime : firstTime - secondTime;
    });
}

function matchesType(event: CelebrationEventResponse, type: TypeFilter): boolean {
  if (type === 'mass') {
    return event.massOrCelebration;
  }

  if (type === 'celebration') {
    return !event.massOrCelebration;
  }

  return true;
}

function matchesSearch(event: CelebrationEventResponse, search: string): boolean {
  const normalizedSearch = normalizeForSearch(search);

  if (normalizedSearch.length === 0) {
    return true;
  }

  return normalizeForSearch(event.nameMassOrEvent).includes(normalizedSearch);
}

const DIACRITIC_MARKS_PATTERN = /\p{Diacritic}/gu;

function normalizeForSearch(value: string): string {
  return value
    .trim()
    .toLocaleLowerCase()
    .normalize('NFD')
    .replace(DIACRITIC_MARKS_PATTERN, '');
}

function resultCountLabelFor(count: number): string {
  return count === 1 ? '1 evento encontrado' : `${count} eventos encontrados`;
}

function toLocalDateTime(eventDate: string, eventTime: string): Date {
  const [year, month, day] = eventDate.split('-').map(Number);
  const [hours, minutes, seconds] = eventTime.split(':').map(Number);

  return new Date(year, month - 1, day, hours, minutes, seconds ?? 0, 0);
}
