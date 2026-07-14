import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';

import {
  EucharistSchedulePage,
  EucharistScheduleQuery,
  EucharistScheduleResponse,
} from '../eucharist-schedule.models';
import { EucharistScheduleService } from '../eucharist-schedule.service';

const DEFAULT_PAGE_SIZE = 10;

@Component({
  selector: 'app-eucharist-schedule-list',
  standalone: true,
  imports: [DatePipe, FormsModule],
  templateUrl: './eucharist-schedule-list.component.html',
  styleUrl: './eucharist-schedule-list.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EucharistScheduleListComponent implements OnInit {
  private readonly eucharistScheduleService = inject(EucharistScheduleService);

  readonly schedules = signal<EucharistScheduleResponse[]>([]);
  readonly isLoading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly validationMessage = signal<string | null>(null);
  readonly currentPage = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);
  readonly isFirstPage = signal(true);
  readonly isLastPage = signal(true);

  startDate = formatLocalDate(firstDayOfCurrentMonth());
  endDate = formatLocalDate(lastDayOfCurrentMonth());

  readonly pageSize = DEFAULT_PAGE_SIZE;

  private lastValidQuery: EucharistScheduleQuery | null = null;

  ngOnInit(): void {
    this.loadPage(0);
  }

  onSubmit(): void {
    this.loadPage(0);
  }

  retry(): void {
    if (this.lastValidQuery === null) {
      return;
    }

    this.loadSchedule(this.lastValidQuery);
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

  private loadPage(page: number): void {
    const query = this.createQuery(page);

    if (query === null) {
      return;
    }

    this.loadSchedule(query);
  }

  private createQuery(page: number): EucharistScheduleQuery | null {
    const validationMessage = validatePeriod(this.startDate, this.endDate);

    if (validationMessage !== null) {
      this.validationMessage.set(validationMessage);
      return null;
    }

    this.validationMessage.set(null);

    return {
      startDate: this.startDate,
      endDate: this.endDate,
      page,
      size: this.pageSize,
    };
  }

  private loadSchedule(query: EucharistScheduleQuery): void {
    this.lastValidQuery = query;
    this.isLoading.set(true);
    this.errorMessage.set(null);
    this.validationMessage.set(null);

    this.eucharistScheduleService
      .findEucharistSchedule(query)
      .pipe(finalize(() => this.isLoading.set(false)))
      .subscribe({
        next: (page) => {
          this.applyPage(page);
        },
        error: () => {
          this.errorMessage.set('Não foi possível carregar a escala. Tente novamente.');
        },
      });
  }

  private applyPage(page: EucharistSchedulePage): void {
    this.schedules.set(page.content);
    this.currentPage.set(page.number);
    this.totalPages.set(page.totalPages);
    this.totalElements.set(page.totalElements);
    this.isFirstPage.set(page.first);
    this.isLastPage.set(page.last);
  }
}

function validatePeriod(startDate: string, endDate: string): string | null {
  if (!isIsoDate(startDate)) {
    return 'Informe a data inicial.';
  }

  if (!isIsoDate(endDate)) {
    return 'Informe a data final.';
  }

  if (startDate > endDate) {
    return 'A data inicial não pode ser posterior à data final.';
  }

  return null;
}

function isIsoDate(value: string): boolean {
  return /^\d{4}-\d{2}-\d{2}$/.test(value);
}

function firstDayOfCurrentMonth(): Date {
  const today = new Date();

  return new Date(today.getFullYear(), today.getMonth(), 1);
}

function lastDayOfCurrentMonth(): Date {
  const today = new Date();

  return new Date(today.getFullYear(), today.getMonth() + 1, 0);
}

function formatLocalDate(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');

  return `${year}-${month}-${day}`;
}
