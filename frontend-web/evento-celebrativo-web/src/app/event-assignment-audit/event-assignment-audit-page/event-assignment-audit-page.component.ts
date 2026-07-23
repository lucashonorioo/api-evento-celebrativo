import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { catchError, map, of, Subject, switchMap } from 'rxjs';

import {
  EventAssignmentAuditEvent,
  EventAssignmentAuditIssue,
  EventAssignmentAuditIssueType,
  EventAssignmentAuditQuery,
  EventAssignmentAuditResponse,
  EventAssignmentType,
} from '../event-assignment-audit.models';
import { EventAssignmentAuditService } from '../event-assignment-audit.service';

const DEFAULT_PAGE_SIZE = 20;

type AuditResult =
  | {
      readonly type: 'success';
      readonly query: EventAssignmentAuditQuery;
      readonly response: EventAssignmentAuditResponse;
    }
  | {
      readonly type: 'error';
      readonly query: EventAssignmentAuditQuery;
      readonly error: unknown;
    };

interface SummaryMetric {
  readonly label: string;
  readonly value: number;
}

interface IssueMetric {
  readonly label: string;
  readonly value: number;
}

@Component({
  selector: 'app-event-assignment-audit-page',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './event-assignment-audit-page.component.html',
  styleUrl: './event-assignment-audit-page.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EventAssignmentAuditPageComponent implements OnInit {
  private readonly auditService = inject(EventAssignmentAuditService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly auditRequests = new Subject<EventAssignmentAuditQuery>();

  readonly filtersForm = this.formBuilder.group(
    {
      startDate: [''],
      endDate: [''],
      eventId: ['', [positiveIntegerStringValidator]],
      size: [DEFAULT_PAGE_SIZE, [Validators.min(1), Validators.max(100), integerNumberValidator]],
      includeDetails: [true],
    },
    { validators: dateRangeValidator },
  );

  readonly isLoading = signal(false);
  readonly hasExecuted = signal(false);
  readonly result = signal<EventAssignmentAuditResponse | null>(null);
  readonly errorMessage = signal<string | null>(null);
  readonly expandedEventIds = signal<ReadonlySet<number>>(new Set());
  readonly lastValidQuery = signal<EventAssignmentAuditQuery | null>(null);

  readonly summaryMetrics = computed<readonly SummaryMetric[]>(() => {
    const summary = this.result()?.summary;

    if (!summary) {
      return [];
    }

    return [
      { label: 'Eventos auditados', value: summary.eventsChecked },
      { label: 'Eventos consistentes', value: summary.consistentEvents },
      { label: 'Eventos inconsistentes', value: summary.inconsistentEvents },
      { label: 'Participantes legados', value: summary.legacyParticipants },
      { label: 'Assignments paralelos', value: summary.parallelAssignments },
      { label: 'Total de problemas', value: summary.totalIssues },
    ];
  });

  readonly issueMetrics = computed<readonly IssueMetric[]>(() => {
    const summary = this.result()?.summary;

    if (!summary) {
      return [];
    }

    return [
      { label: 'Ausentes no paralelo', value: summary.missingParallelAssignments },
      { label: 'Extras no paralelo', value: summary.extraParallelAssignments },
      { label: 'Tipos divergentes', value: summary.assignmentTypeMismatches },
      { label: 'Duplicidades', value: summary.duplicateParallelAssignments },
      { label: 'Múltiplos padres', value: summary.multiplePriests },
      { label: 'Tipos legados desconhecidos', value: summary.unknownLegacyPersonTypes },
    ];
  });

  ngOnInit(): void {
    this.auditRequests
      .pipe(
        switchMap((query) => {
          this.isLoading.set(true);
          this.errorMessage.set(null);
          this.result.set(null);
          this.expandedEventIds.set(new Set());

          return this.auditService.auditConsistency(query).pipe(
            map((response): AuditResult => ({ type: 'success', query, response })),
            catchError((error: unknown) => of({ type: 'error', query, error } satisfies AuditResult)),
          );
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((auditResult) => {
        this.isLoading.set(false);

        if (auditResult.type === 'error') {
          this.errorMessage.set(errorMessageFor(auditResult.error));
          this.focusErrorMessage();
          return;
        }

        this.result.set(auditResult.response);
      });
  }

  executeAudit(): void {
    if (this.isLoading()) {
      return;
    }

    if (this.filtersForm.invalid) {
      this.filtersForm.markAllAsTouched();
      this.focusFirstInvalidControl();
      return;
    }

    const query = this.createQuery(0);

    this.hasExecuted.set(true);
    this.lastValidQuery.set(query);
    this.auditRequests.next(query);
  }

  clearFilters(): void {
    this.filtersForm.reset({
      startDate: '',
      endDate: '',
      eventId: '',
      size: DEFAULT_PAGE_SIZE,
      includeDetails: true,
    });
    this.hasExecuted.set(false);
    this.result.set(null);
    this.errorMessage.set(null);
    this.expandedEventIds.set(new Set());
    this.lastValidQuery.set(null);
  }

  retry(): void {
    const query = this.lastValidQuery();

    if (query === null || this.isLoading()) {
      return;
    }

    this.auditRequests.next(query);
  }

  previousPage(): void {
    const response = this.result();

    if (response === null || this.isLoading() || response.page <= 0) {
      return;
    }

    this.requestPage(response.page - 1);
  }

  nextPage(): void {
    const response = this.result();

    if (
      response === null ||
      this.isLoading() ||
      response.totalPages === 0 ||
      response.page + 1 >= response.totalPages
    ) {
      return;
    }

    this.requestPage(response.page + 1);
  }

  isDetailsHidden(): boolean {
    return this.lastValidQuery()?.includeDetails === false && this.result() !== null;
  }

  events(): readonly EventAssignmentAuditEvent[] {
    return this.result()?.events ?? [];
  }

  hasInconsistencies(): boolean {
    return (this.result()?.summary.inconsistentEvents ?? 0) > 0;
  }

  isConsistentPage(): boolean {
    const summary = this.result()?.summary;

    return Boolean(summary && summary.eventsChecked > 0 && summary.inconsistentEvents === 0);
  }

  isEmptyPage(): boolean {
    const response = this.result();

    return Boolean(response && (response.empty || response.summary.eventsChecked === 0));
  }

  canGoPrevious(): boolean {
    return (this.result()?.page ?? 0) > 0 && !this.isLoading();
  }

  canGoNext(): boolean {
    const response = this.result();

    return Boolean(
      response &&
        response.totalPages > 0 &&
        response.page + 1 < response.totalPages &&
        !this.isLoading(),
    );
  }

  toggleEvent(event: EventAssignmentAuditEvent): void {
    const expandedEventIds = new Set(this.expandedEventIds());

    if (expandedEventIds.has(event.eventId)) {
      expandedEventIds.delete(event.eventId);
    } else {
      expandedEventIds.add(event.eventId);
    }

    this.expandedEventIds.set(expandedEventIds);
  }

  isEventExpanded(event: EventAssignmentAuditEvent): boolean {
    return this.expandedEventIds().has(event.eventId);
  }

  eventDetailsId(event: EventAssignmentAuditEvent): string {
    return `event-assignment-audit-event-${event.eventId}`;
  }

  eventStatusLabel(event: EventAssignmentAuditEvent): string {
    return event.consistent ? 'Consistente' : 'Inconsistente';
  }

  issueTypeLabel(issueType: EventAssignmentAuditIssueType): string {
    const labels: Record<EventAssignmentAuditIssueType, string> = {
      MISSING_PARALLEL_ASSIGNMENT: 'Vínculo ausente na estrutura paralela',
      EXTRA_PARALLEL_ASSIGNMENT: 'Assignment sem vínculo legado',
      ASSIGNMENT_TYPE_MISMATCH: 'Tipo de atribuição divergente',
      DUPLICATE_PARALLEL_ASSIGNMENT: 'Assignment duplicado',
      MULTIPLE_PRIESTS: 'Mais de um padre no evento',
      UNKNOWN_LEGACY_PERSON_TYPE: 'Tipo legado não reconhecido',
    };

    return labels[issueType];
  }

  assignmentTypeLabel(type: EventAssignmentType | null): string {
    if (type === null) {
      return 'Não aplicável';
    }

    const labels: Record<EventAssignmentType, string> = {
      PRIEST: 'Padre',
      READER: 'Leitor',
      COMMENTATOR: 'Comentarista',
      MINISTER_OF_THE_WORD: 'Ministro da Palavra',
      EUCHARISTIC_MINISTER: 'Ministro da Eucaristia',
    };

    return labels[type];
  }

  nullableNumberLabel(value: number | null): string {
    return value === null ? 'Não informado' : String(value);
  }

  eventIdLabel(issue: EventAssignmentAuditIssue): string {
    return this.nullableNumberLabel(issue.eventId);
  }

  showDateRangeError(): boolean {
    return this.filtersForm.hasError('dateRange') && this.filtersForm.controls.endDate.touched;
  }

  showEventIdError(): boolean {
    const control = this.filtersForm.controls.eventId;

    return control.invalid && control.touched;
  }

  showPageSizeError(): boolean {
    const control = this.filtersForm.controls.size;

    return control.invalid && control.touched;
  }

  private requestPage(page: number): void {
    const lastValidQuery = this.lastValidQuery();

    if (lastValidQuery === null) {
      return;
    }

    const query = {
      ...lastValidQuery,
      page,
    };

    this.lastValidQuery.set(query);
    this.auditRequests.next(query);
  }

  private createQuery(page: number): EventAssignmentAuditQuery {
    const value = this.filtersForm.getRawValue();
    const startDate = trimmedOrUndefined(value.startDate);
    const endDate = trimmedOrUndefined(value.endDate);
    const eventId = trimmedOrUndefined(value.eventId);
    const query: EventAssignmentAuditQuery = {
      page,
      size: Number(value.size),
      includeDetails: value.includeDetails,
    };

    return {
      ...query,
      ...(startDate === undefined ? {} : { startDate }),
      ...(endDate === undefined ? {} : { endDate }),
      ...(eventId === undefined ? {} : { eventId: Number(eventId) }),
    };
  }

  private focusErrorMessage(): void {
    window.setTimeout(() => {
      this.host.nativeElement
        .querySelector<HTMLElement>('.audit__feedback--error')
        ?.focus();
    });
  }

  private focusFirstInvalidControl(): void {
    window.setTimeout(() => {
      this.host.nativeElement
        .querySelector<HTMLElement>('.ng-invalid[formControlName], .ng-invalid input')
        ?.focus();
    });
  }
}

function trimmedOrUndefined(value: string): string | undefined {
  const trimmedValue = value.trim();

  return trimmedValue.length === 0 ? undefined : trimmedValue;
}

function positiveIntegerStringValidator(control: AbstractControl): ValidationErrors | null {
  const value = control.value;

  if (value === null || value === undefined || value === '') {
    return null;
  }

  if (typeof value !== 'string' || !/^[1-9]\d*$/.test(value.trim())) {
    return { positiveInteger: true };
  }

  return null;
}

function integerNumberValidator(control: AbstractControl): ValidationErrors | null {
  const value = Number(control.value);

  if (!Number.isInteger(value)) {
    return { integer: true };
  }

  return null;
}

function dateRangeValidator(control: AbstractControl): ValidationErrors | null {
  const startDate = control.get('startDate')?.value;
  const endDate = control.get('endDate')?.value;

  if (
    typeof startDate === 'string' &&
    typeof endDate === 'string' &&
    startDate.length > 0 &&
    endDate.length > 0 &&
    startDate > endDate
  ) {
    return { dateRange: true };
  }

  return null;
}

function errorMessageFor(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 403) {
      return 'Você não possui permissão para executar esta auditoria.';
    }

    if (error.status === 404) {
      return 'O evento informado não foi encontrado.';
    }

    if (error.status === 400 || error.status === 422) {
      return 'Não foi possível executar a auditoria com os filtros informados.';
    }
  }

  return 'Não foi possível executar a auditoria. Tente novamente.';
}
