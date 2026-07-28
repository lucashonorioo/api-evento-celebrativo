import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { ActivatedRoute, Params, Router } from '@angular/router';
import { finalize } from 'rxjs';

import { EventScheduleType } from '../event-schedules/event-schedule.models';
import { formatLocalDate, scheduleAssignmentLabel } from './current-user-schedule-view.utils';
import {
  CurrentUserSchedule,
  CurrentUserSchedulePage,
  CurrentUserScheduleQuery,
} from './current-user-schedule.models';
import { CurrentUserScheduleService } from './current-user-schedule.service';

const PAGE_SIZE = 10;
const QUERY_PARAM_KEYS = ['startDate', 'endDate', 'page'] as const;

@Component({
  selector: 'app-current-user-schedules',
  standalone: true,
  imports: [DatePipe, ReactiveFormsModule],
  templateUrl: './current-user-schedules.component.html',
  styleUrl: './current-user-schedules.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CurrentUserSchedulesComponent implements OnInit {
  private readonly activatedRoute = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly currentUserScheduleService = inject(CurrentUserScheduleService);

  readonly form = new FormGroup(
    {
      startDate: new FormControl('', {
        nonNullable: true,
        validators: [Validators.required, validDateValidator],
      }),
      endDate: new FormControl('', {
        nonNullable: true,
        validators: [Validators.required, validDateValidator],
      }),
    },
    { validators: [dateRangeValidator] },
  );

  readonly schedules = signal<CurrentUserSchedule[]>([]);
  readonly isLoading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly validationMessage = signal<string | null>(null);
  readonly currentPage = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);
  readonly isFirstPage = signal(true);
  readonly isLastPage = signal(true);

  private lastValidQuery: CurrentUserScheduleQuery | null = null;

  ngOnInit(): void {
    const restoredQuery = this.restoreQueryFromParams();

    this.form.setValue(
      { startDate: restoredQuery.startDate, endDate: restoredQuery.endDate },
      { emitEvent: false },
    );

    this.syncQueryParams(restoredQuery);
    this.loadSchedules(restoredQuery);
  }

  submit(): void {
    if (this.isLoading()) {
      return;
    }

    this.form.markAllAsTouched();

    if (this.form.invalid) {
      this.validationMessage.set(
        this.form.hasError('invertedRange')
          ? 'A data inicial não pode ser posterior à data final.'
          : null,
      );
      return;
    }

    this.validationMessage.set(null);

    const query: CurrentUserScheduleQuery = {
      startDate: this.form.controls.startDate.value,
      endDate: this.form.controls.endDate.value,
      page: 0,
      size: PAGE_SIZE,
    };

    this.syncQueryParams(query);
    this.loadSchedules(query);
  }

  retry(): void {
    if (this.isLoading() || this.lastValidQuery === null) {
      return;
    }

    this.syncQueryParams(this.lastValidQuery);
    this.loadSchedules(this.lastValidQuery);
  }

  previousPage(): void {
    if (this.isLoading() || this.lastValidQuery === null) {
      return;
    }

    const previousPage = this.currentPage() - 1;

    if (previousPage < 0) {
      return;
    }

    this.loadPage(previousPage);
  }

  nextPage(): void {
    if (this.isLoading() || this.lastValidQuery === null) {
      return;
    }

    const nextPage = this.currentPage() + 1;

    if (nextPage >= this.totalPages()) {
      return;
    }

    this.loadPage(nextPage);
  }

  fieldErrorMessage(controlName: 'startDate' | 'endDate'): string | null {
    const control = this.form.controls[controlName];

    if (!control.touched) {
      return null;
    }

    if (control.hasError('required')) {
      return controlName === 'startDate' ? 'Informe a data inicial.' : 'Informe a data final.';
    }

    if (control.hasError('invalidDate')) {
      return 'Informe um período válido.';
    }

    return null;
  }

  eventKindLabel(massOrCelebration: boolean): string {
    return massOrCelebration ? 'Missa' : 'Celebração';
  }

  formatTime(eventTime: string): string {
    return eventTime.slice(0, 5);
  }

  assignmentLabel(assignment: EventScheduleType): string {
    return scheduleAssignmentLabel(assignment);
  }

  private loadPage(page: number): void {
    if (this.lastValidQuery === null) {
      return;
    }

    const query: CurrentUserScheduleQuery = { ...this.lastValidQuery, page };

    this.syncQueryParams(query);
    this.loadSchedules(query);
  }

  private loadSchedules(query: CurrentUserScheduleQuery): void {
    this.lastValidQuery = query;
    this.isLoading.set(true);
    this.errorMessage.set(null);

    let isCorrectingPage = false;

    this.currentUserScheduleService
      .findSchedules(query)
      .pipe(
        finalize(() => {
          if (!isCorrectingPage) {
            this.isLoading.set(false);
          }
        }),
      )
      .subscribe({
        next: (page) => {
          if (isPageBeyondLimit(query, page)) {
            isCorrectingPage = true;

            const correctedQuery: CurrentUserScheduleQuery = {
              ...query,
              page: page.totalPages - 1,
            };

            this.syncQueryParams(correctedQuery);
            this.loadSchedules(correctedQuery);
            return;
          }

          this.applyPage(page);

          if (page.number !== query.page) {
            const correctedQuery: CurrentUserScheduleQuery = { ...query, page: page.number };

            this.lastValidQuery = correctedQuery;
            this.syncQueryParams(correctedQuery);
          }
        },
        error: (error: unknown) => {
          this.errorMessage.set(scheduleErrorMessageFor(error));
        },
      });
  }

  private applyPage(page: CurrentUserSchedulePage): void {
    this.schedules.set(page.content);
    this.currentPage.set(page.number);
    this.totalPages.set(page.totalPages);
    this.totalElements.set(page.totalElements);
    this.isFirstPage.set(page.first);
    this.isLastPage.set(page.last);
  }

  private syncQueryParams(query: CurrentUserScheduleQuery): void {
    const desired: Params = {
      startDate: query.startDate,
      endDate: query.endDate,
      page: String(query.page),
    };

    const current = this.activatedRoute.snapshot.queryParamMap;
    const hasChanges = QUERY_PARAM_KEYS.some((key) => current.get(key) !== desired[key]);

    if (!hasChanges) {
      return;
    }

    void this.router.navigate([], {
      relativeTo: this.activatedRoute,
      queryParams: desired,
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }

  private restoreQueryFromParams(): CurrentUserScheduleQuery {
    const queryParamMap = this.activatedRoute.snapshot.queryParamMap;
    const startDateParam = queryParamMap.get('startDate');
    const endDateParam = queryParamMap.get('endDate');
    const pageParam = queryParamMap.get('page');

    const hasValidRange =
      isValidDateString(startDateParam) &&
      isValidDateString(endDateParam) &&
      startDateParam <= endDateParam;

    const period = hasValidRange
      ? { startDate: startDateParam, endDate: endDateParam }
      : currentMonthPeriod();

    return {
      startDate: period.startDate,
      endDate: period.endDate,
      page: validPageOrDefault(pageParam),
      size: PAGE_SIZE,
    };
  }
}

function validDateValidator(control: AbstractControl): ValidationErrors | null {
  const value = control.value as string;

  if (value === '' || isValidDateString(value)) {
    return null;
  }

  return { invalidDate: true };
}

function dateRangeValidator(group: AbstractControl): ValidationErrors | null {
  const startDate = group.get('startDate')?.value as string;
  const endDate = group.get('endDate')?.value as string;

  if (!isValidDateString(startDate) || !isValidDateString(endDate)) {
    return null;
  }

  return startDate > endDate ? { invertedRange: true } : null;
}

function isValidDateString(value: string | null): value is string {
  if (value === null || !/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    return false;
  }

  const [year, month, day] = value.split('-').map(Number);
  const date = new Date(year, month - 1, day);

  return date.getFullYear() === year && date.getMonth() === month - 1 && date.getDate() === day;
}

function validPageOrDefault(value: string | null): number {
  if (value === null || !/^\d+$/.test(value)) {
    return 0;
  }

  return Number(value);
}

function currentMonthPeriod(now: Date = new Date()): {
  readonly startDate: string;
  readonly endDate: string;
} {
  const firstDay = new Date(now.getFullYear(), now.getMonth(), 1);
  const lastDay = new Date(now.getFullYear(), now.getMonth() + 1, 0);

  return {
    startDate: formatLocalDate(firstDay),
    endDate: formatLocalDate(lastDay),
  };
}

function isPageBeyondLimit(query: CurrentUserScheduleQuery, page: CurrentUserSchedulePage): boolean {
  return page.empty && page.totalElements > 0 && page.totalPages > 0 && query.page >= page.totalPages;
}

function scheduleErrorMessageFor(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 400) {
      return 'O período informado é inválido. Revise as datas e tente novamente.';
    }

    if (error.status === 403) {
      return 'Não foi possível acessar suas escalas.';
    }

    if (error.status === 404) {
      return 'Não foi possível localizar a pessoa associada à sua conta autenticada.';
    }
  }

  return 'Não foi possível carregar suas escalas. Tente novamente.';
}
