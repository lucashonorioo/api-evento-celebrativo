import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
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

import { eventLocalTimestamp } from '../events/event-view.utils';
import { EventScheduleType } from '../event-schedules/event-schedule.models';
import {
  formatLocalDate,
  scheduleAssignmentLabel,
  scheduleParticipationStatusLabel,
} from './current-user-schedule-view.utils';
import {
  CurrentUserSchedule,
  CurrentUserSchedulePage,
  CurrentUserScheduleQuery,
  ScheduleParticipationResponse,
  ScheduleParticipationUpdateRequest,
} from './current-user-schedule.models';
import { CurrentUserScheduleService } from './current-user-schedule.service';

const PAGE_SIZE = 10;
const QUERY_PARAM_KEYS = ['startDate', 'endDate', 'page'] as const;
const DECLINE_REASON_MAX_LENGTH = 500;

interface ParticipationFeedback {
  readonly eventId: number;
  readonly type: 'success' | 'error';
  readonly message: string;
}

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

  readonly respondingEventId = signal<number | null>(null);
  readonly activeDeclineEventId = signal<number | null>(null);
  readonly participationFeedback = signal<ParticipationFeedback | null>(null);
  readonly isBusy = computed(() => this.isLoading() || this.respondingEventId() !== null);

  readonly declineReasonControl = new FormControl('', {
    nonNullable: true,
    validators: [Validators.maxLength(DECLINE_REASON_MAX_LENGTH)],
  });

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
    if (this.isBusy()) {
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
    if (this.isBusy() || this.lastValidQuery === null) {
      return;
    }

    this.syncQueryParams(this.lastValidQuery);
    this.loadSchedules(this.lastValidQuery);
  }

  previousPage(): void {
    if (this.isBusy() || this.lastValidQuery === null) {
      return;
    }

    const previousPage = this.currentPage() - 1;

    if (previousPage < 0) {
      return;
    }

    this.loadPage(previousPage);
  }

  nextPage(): void {
    if (this.isBusy() || this.lastValidQuery === null) {
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

  participationStatusLabel(schedule: CurrentUserSchedule): string {
    return scheduleParticipationStatusLabel(schedule.participationStatus);
  }

  isEventStarted(schedule: CurrentUserSchedule): boolean {
    return eventLocalTimestamp(schedule) <= Date.now();
  }

  declineReasonErrorMessage(): string | null {
    if (this.declineReasonControl.hasError('maxlength')) {
      return `O motivo deve ter no máximo ${DECLINE_REASON_MAX_LENGTH} caracteres.`;
    }

    return null;
  }

  confirmParticipation(schedule: CurrentUserSchedule): void {
    if (this.respondingEventId() !== null || this.isEventStarted(schedule)) {
      return;
    }

    this.sendParticipation(schedule.eventId, { status: 'CONFIRMED', declineReason: null });
  }

  openDeclineForm(schedule: CurrentUserSchedule): void {
    if (this.respondingEventId() !== null || this.isEventStarted(schedule)) {
      return;
    }

    this.declineReasonControl.reset(schedule.declineReason ?? '');
    this.activeDeclineEventId.set(schedule.eventId);

    if (this.participationFeedback()?.eventId === schedule.eventId) {
      this.participationFeedback.set(null);
    }

    const eventId = schedule.eventId;
    setTimeout(() => {
      document.getElementById(`current-user-schedules-decline-reason-${eventId}`)?.focus();
    });
  }

  cancelDeclineForm(eventId: number): void {
    this.activeDeclineEventId.set(null);
    this.declineReasonControl.reset('');

    if (this.participationFeedback()?.eventId === eventId) {
      this.participationFeedback.set(null);
    }
  }

  submitDecline(schedule: CurrentUserSchedule): void {
    if (this.respondingEventId() !== null || this.declineReasonControl.invalid) {
      return;
    }

    const trimmedReason = this.declineReasonControl.value.trim();
    const declineReason = trimmedReason === '' ? null : trimmedReason;

    this.sendParticipation(schedule.eventId, { status: 'DECLINED', declineReason }, true);
  }

  private sendParticipation(
    eventId: number,
    request: ScheduleParticipationUpdateRequest,
    closesDeclineFormOnSuccess = false,
  ): void {
    this.respondingEventId.set(eventId);
    this.participationFeedback.set(null);

    this.currentUserScheduleService
      .updateParticipation(eventId, request)
      .pipe(finalize(() => this.respondingEventId.set(null)))
      .subscribe({
        next: (response) => {
          this.applyParticipationUpdate(response);

          if (closesDeclineFormOnSuccess) {
            this.activeDeclineEventId.set(null);
            this.declineReasonControl.reset('');
          }

          this.participationFeedback.set({
            eventId,
            type: 'success',
            message:
              request.status === 'CONFIRMED'
                ? 'Participação confirmada com sucesso.'
                : 'Participação atualizada com sucesso.',
          });
        },
        error: (error: unknown) => {
          this.handleParticipationError(eventId, error);
        },
      });
  }

  private applyParticipationUpdate(response: ScheduleParticipationResponse): void {
    this.schedules.update((schedules) =>
      schedules.map((schedule) =>
        schedule.eventId === response.eventId
          ? {
              ...schedule,
              participationStatus: response.status,
              declineReason: response.declineReason,
              respondedAt: response.respondedAt,
            }
          : schedule,
      ),
    );
  }

  private handleParticipationError(eventId: number, error: unknown): void {
    if (!(error instanceof HttpErrorResponse)) {
      this.participationFeedback.set({
        eventId,
        type: 'error',
        message: 'Não foi possível atualizar sua participação. Tente novamente.',
      });
      return;
    }

    if (error.status === 400) {
      this.participationFeedback.set({
        eventId,
        type: 'error',
        message: 'Não foi possível registrar a participação. Revise os dados informados.',
      });
      return;
    }

    if (error.status === 401) {
      return;
    }

    if (error.status === 403) {
      this.participationFeedback.set({
        eventId,
        type: 'error',
        message: 'Você não tem permissão para responder a esta escala.',
      });
      return;
    }

    if (error.status === 404) {
      this.participationFeedback.set({
        eventId,
        type: 'error',
        message: 'Não foi possível localizar o evento ou a pessoa associada à conta autenticada.',
      });
      return;
    }

    if (error.status === 409) {
      const backendMessage = extractBackendErrorMessage(error)?.toLocaleLowerCase() ?? '';

      if (backendMessage.includes('inicio') || backendMessage.includes('iniciad')) {
        this.participationFeedback.set({
          eventId,
          type: 'error',
          message: 'Não é mais possível alterar a participação porque o evento já começou.',
        });
        return;
      }

      if (backendMessage.includes('atribui') || backendMessage.includes('escalad')) {
        this.participationFeedback.set({
          eventId,
          type: 'error',
          message: 'Você não está mais escalado para este evento.',
        });
        this.reconcileAfterConflict();
        return;
      }

      this.participationFeedback.set({
        eventId,
        type: 'error',
        message:
          'Não foi possível alterar a participação. A escala pode ter sido modificada ou o evento já ter começado.',
      });
      return;
    }

    this.participationFeedback.set({
      eventId,
      type: 'error',
      message: 'Não foi possível atualizar sua participação. Tente novamente.',
    });
  }

  private reconcileAfterConflict(): void {
    if (this.lastValidQuery === null) {
      return;
    }

    this.currentUserScheduleService.findSchedules(this.lastValidQuery).subscribe({
      next: (page) => this.applyPage(page),
      error: () => {
        // Reconciliação é apenas um esforço adicional; o feedback de conflito já foi exibido.
      },
    });
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

function extractBackendErrorMessage(error: HttpErrorResponse): string | null {
  const body: unknown = error.error;

  if (body !== null && typeof body === 'object' && typeof (body as { error?: unknown }).error === 'string') {
    return (body as { error: string }).error;
  }

  return null;
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
