import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Params, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { AuthSessionService } from '../../auth-session.service';
import {
  EventSchedulePage,
  EventScheduleQuery,
  EventScheduleResponse,
  EventScheduleType,
} from '../event-schedule.models';
import { EventScheduleService } from '../event-schedule.service';

const DEFAULT_PAGE_SIZE = 10;

interface EventScheduleTypeOption {
  readonly label: string;
  readonly value: EventScheduleType;
}

@Component({
  selector: 'app-event-schedule-list',
  standalone: true,
  imports: [DatePipe, FormsModule, RouterLink],
  templateUrl: './event-schedule-list.component.html',
  styleUrl: './event-schedule-list.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EventScheduleListComponent implements OnInit {
  private readonly activatedRoute = inject(ActivatedRoute);
  private readonly authSessionService = inject(AuthSessionService);
  private readonly eventScheduleService = inject(EventScheduleService);

  readonly scheduleTypeOptions: readonly EventScheduleTypeOption[] = [
    { label: 'Padres', value: 'PRIEST' },
    { label: 'Leitores', value: 'READER' },
    { label: 'Comentaristas', value: 'COMMENTATOR' },
    { label: 'Ministros da Palavra', value: 'MINISTER_OF_THE_WORD' },
    { label: 'Ministros da Eucaristia', value: 'EUCHARISTIC_MINISTER' },
  ];

  readonly schedules = signal<EventScheduleResponse[]>([]);
  readonly isLoading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly validationMessage = signal<string | null>(null);
  readonly currentPage = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);
  readonly isFirstPage = signal(true);
  readonly isLastPage = signal(true);

  selectedType: EventScheduleType = 'READER';
  selectedMonth = formatYearMonth(new Date());
  includeUnassigned = false;

  readonly pageSize = DEFAULT_PAGE_SIZE;

  private lastValidQuery: EventScheduleQuery | null = null;

  ngOnInit(): void {
    const restoredPage = this.restoreFiltersFromQueryParams();

    this.loadPage(restoredPage);
  }

  onSubmit(): void {
    this.loadPage(0);
  }

  previousMonth(): void {
    this.selectedMonth = shiftMonth(this.selectedMonth, -1);
    this.loadPage(0);
  }

  nextMonth(): void {
    this.selectedMonth = shiftMonth(this.selectedMonth, 1);
    this.loadPage(0);
  }

  retry(): void {
    if (this.lastValidQuery === null) {
      return;
    }

    this.loadSchedules(this.lastValidQuery);
  }

  previousPage(): void {
    const previousPage = this.currentPage() - 1;

    if (previousPage < 0) {
      return;
    }

    this.loadPage(previousPage);
  }

  nextPage(): void {
    const nextPage = this.currentPage() + 1;

    if (nextPage >= this.totalPages()) {
      return;
    }

    this.loadPage(nextPage);
  }

  formatTime(eventTime: string): string {
    return eventTime.slice(0, 5);
  }

  eventKind(massOrCelebration: boolean): string {
    return massOrCelebration ? 'Missa' : 'Celebracao';
  }

  selectedTypeLabel(): string {
    return this.labelForType(this.selectedType);
  }

  assignmentTypeLabel(type: EventScheduleType): string {
    return this.labelForType(type);
  }

  detailQueryParams(): Params {
    return {
      type: this.selectedType,
      month: this.selectedMonth,
      includeUnassigned: this.includeUnassigned,
      page: this.currentPage(),
    };
  }

  detailLinkFor(schedule: EventScheduleResponse): readonly string[] {
    return ['/app/escalas/eventos', String(schedule.eventId)];
  }

  isAdmin(): boolean {
    return this.authSessionService.hasAuthority('ROLE_ADMIN');
  }

  private loadPage(page: number): void {
    const query = this.createQuery(page);

    if (query === null) {
      return;
    }

    this.loadSchedules(query);
  }

  private createQuery(page: number): EventScheduleQuery | null {
    if (!isYearMonth(this.selectedMonth)) {
      this.validationMessage.set('Informe um mes valido.');
      return null;
    }

    const period = periodForMonth(this.selectedMonth);
    this.validationMessage.set(null);

    return {
      startDate: period.startDate,
      endDate: period.endDate,
      type: this.selectedType,
      page,
      size: this.pageSize,
      includeUnassigned: this.includeUnassigned,
    };
  }

  private loadSchedules(query: EventScheduleQuery): void {
    this.lastValidQuery = query;
    this.isLoading.set(true);
    this.errorMessage.set(null);
    this.validationMessage.set(null);

    this.eventScheduleService
      .findMonthlySchedules(query)
      .pipe(finalize(() => this.isLoading.set(false)))
      .subscribe({
        next: (page) => {
          this.applyPage(page);
        },
        error: (error: unknown) => {
          this.errorMessage.set(errorMessageFor(error));
        },
      });
  }

  private applyPage(page: EventSchedulePage): void {
    this.schedules.set(page.content);
    this.currentPage.set(page.number);
    this.totalPages.set(page.totalPages);
    this.totalElements.set(page.totalElements);
    this.isFirstPage.set(page.first);
    this.isLastPage.set(page.last);
  }

  private labelForType(type: EventScheduleType): string {
    return this.scheduleTypeOptions.find((option) => option.value === type)?.label ?? 'Escala';
  }

  private restoreFiltersFromQueryParams(): number {
    const queryParamMap = this.activatedRoute.snapshot.queryParamMap;
    const type = queryParamMap.get('type');
    const month = queryParamMap.get('month');
    const includeUnassigned = queryParamMap.get('includeUnassigned');
    const page = queryParamMap.get('page');

    if (isEventScheduleType(type)) {
      this.selectedType = type;
    }

    if (month !== null && isYearMonth(month)) {
      this.selectedMonth = month;
    }

    if (includeUnassigned === 'true') {
      this.includeUnassigned = true;
    } else if (includeUnassigned === 'false') {
      this.includeUnassigned = false;
    }

    return validPageOrDefault(page);
  }
}

function isEventScheduleType(value: string | null): value is EventScheduleType {
  return (
    value === 'PRIEST' ||
    value === 'READER' ||
    value === 'COMMENTATOR' ||
    value === 'MINISTER_OF_THE_WORD' ||
    value === 'EUCHARISTIC_MINISTER'
  );
}

function validPageOrDefault(value: string | null): number {
  if (value === null || !/^\d+$/.test(value)) {
    return 0;
  }

  return Number(value);
}

function errorMessageFor(error: unknown): string {
  if (error instanceof HttpErrorResponse && error.status === 403) {
    return 'Voce nao possui permissao para consultar as escalas.';
  }

  return 'Nao foi possivel carregar as escalas. Tente novamente.';
}

function isYearMonth(value: string): boolean {
  return /^\d{4}-\d{2}$/.test(value);
}

function periodForMonth(yearMonth: string): { readonly startDate: string; readonly endDate: string } {
  const [year, month] = yearMonth.split('-').map(Number);
  const firstDay = new Date(year, month - 1, 1);
  const lastDay = new Date(year, month, 0);

  return {
    startDate: formatLocalDate(firstDay),
    endDate: formatLocalDate(lastDay),
  };
}

function shiftMonth(yearMonth: string, offset: number): string {
  if (!isYearMonth(yearMonth)) {
    return formatYearMonth(new Date());
  }

  const [year, month] = yearMonth.split('-').map(Number);
  const shifted = new Date(year, month - 1 + offset, 1);

  return formatYearMonth(shifted);
}

function formatYearMonth(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');

  return `${year}-${month}`;
}

function formatLocalDate(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');

  return `${year}-${month}-${day}`;
}
