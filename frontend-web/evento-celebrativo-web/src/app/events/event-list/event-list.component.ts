import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { AuthSessionService } from '../../auth-session.service';
import { CelebrationEventResponse } from '../event.models';
import { EventService } from '../event.service';
import {
  compareEventsByDateTimeAscending,
  compareEventsByDateTimeDescending,
  eventLocalTimestamp,
  normalizeEventSearchText,
} from '../event-view.utils';

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

const DEFAULT_PERIOD_FILTER: PeriodFilter = 'upcoming';
const DEFAULT_TYPE_FILTER: TypeFilter = 'all';
const PERIOD_FILTER_VALUES: readonly PeriodFilter[] = ['upcoming', 'past', 'all'];
const TYPE_FILTER_VALUES: readonly TypeFilter[] = ['all', 'mass', 'celebration'];
const FILTER_QUERY_PARAM_KEYS = ['period', 'search', 'type'] as const;

type FilterQueryParams = Record<(typeof FILTER_QUERY_PARAM_KEYS)[number], string | null>;

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
  private readonly route = inject(ActivatedRoute);

  readonly isAdmin = this.authSessionService.hasAuthority('ROLE_ADMIN');
  readonly allEvents = signal<CelebrationEventResponse[]>([]);
  readonly isLoading = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly periodFilter = signal<PeriodFilter>(
    periodFilterFromQueryParam(this.route.snapshot.queryParamMap.get('period')),
  );
  readonly searchTerm = signal(searchTermFromQueryParam(this.route.snapshot.queryParamMap.get('search')));
  readonly typeFilter = signal<TypeFilter>(
    typeFilterFromQueryParam(this.route.snapshot.queryParamMap.get('type')),
  );

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
    this.syncQueryParams();
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
    this.syncQueryParams();
  }

  setSearchTerm(event: Event): void {
    const target = event.target;

    if (!(target instanceof HTMLInputElement)) {
      return;
    }

    this.searchTerm.set(target.value);
    this.syncQueryParams();
  }

  setTypeFilter(event: Event): void {
    const target = event.target;

    if (!(target instanceof HTMLSelectElement)) {
      return;
    }

    this.typeFilter.set(target.value as TypeFilter);
    this.syncQueryParams();
  }

  clearFilters(): void {
    this.periodFilter.set(DEFAULT_PERIOD_FILTER);
    this.searchTerm.set('');
    this.typeFilter.set(DEFAULT_TYPE_FILTER);
    this.syncQueryParams();
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

  private syncQueryParams(): void {
    const desired = filterQueryParamsFor(this.periodFilter(), this.searchTerm(), this.typeFilter());
    const current = this.route.snapshot.queryParamMap;

    const hasChanges = FILTER_QUERY_PARAM_KEYS.some((key) => (current.get(key) ?? null) !== desired[key]);

    if (!hasChanges) {
      return;
    }

    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: desired,
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }
}

function periodFilterFromQueryParam(value: string | null): PeriodFilter {
  return isPeriodFilterValue(value) ? value : DEFAULT_PERIOD_FILTER;
}

function typeFilterFromQueryParam(value: string | null): TypeFilter {
  return isTypeFilterValue(value) ? value : DEFAULT_TYPE_FILTER;
}

function searchTermFromQueryParam(value: string | null): string {
  return value?.trim() ?? '';
}

function isPeriodFilterValue(value: string | null): value is PeriodFilter {
  return (PERIOD_FILTER_VALUES as readonly string[]).includes(value ?? '');
}

function isTypeFilterValue(value: string | null): value is TypeFilter {
  return (TYPE_FILTER_VALUES as readonly string[]).includes(value ?? '');
}

function filterQueryParamsFor(
  period: PeriodFilter,
  search: string,
  type: TypeFilter,
): FilterQueryParams {
  const trimmedSearch = search.trim();

  return {
    period: period === DEFAULT_PERIOD_FILTER ? null : period,
    search: trimmedSearch.length === 0 ? null : trimmedSearch,
    type: type === DEFAULT_TYPE_FILTER ? null : type,
  };
}

function applyEventFilters(
  events: readonly CelebrationEventResponse[],
  filters: EventFilters,
  now: Date = new Date(),
): CelebrationEventResponse[] {
  const nowTime = now.getTime();

  return events
    .filter((event) => {
      const eventTime = eventLocalTimestamp(event);

      if (filters.period === 'upcoming' && eventTime < nowTime) {
        return false;
      }

      if (filters.period === 'past' && eventTime >= nowTime) {
        return false;
      }

      return matchesType(event, filters.type) && matchesSearch(event, filters.search);
    })
    .sort(
      filters.period === 'past' ? compareEventsByDateTimeDescending : compareEventsByDateTimeAscending,
    );
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
  const normalizedSearch = normalizeEventSearchText(search);

  if (normalizedSearch.length === 0) {
    return true;
  }

  return normalizeEventSearchText(event.nameMassOrEvent).includes(normalizedSearch);
}

function resultCountLabelFor(count: number): string {
  return count === 1 ? '1 evento encontrado' : `${count} eventos encontrados`;
}
