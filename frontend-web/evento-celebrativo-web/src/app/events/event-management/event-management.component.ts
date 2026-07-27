import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { CelebrationEventRequest, CelebrationEventResponse } from '../event.models';
import { EventService } from '../event.service';
import {
  compareEventsByDateTimeAscending,
  compareEventsByDateTimeDescending,
  eventLocalTimestamp,
  normalizeEventSearchText,
} from '../event-view.utils';

type EventPeriodFilter = 'upcoming' | 'past' | 'all';
type EventTypeFilter = 'all' | 'mass' | 'celebration';

interface EventFilters {
  readonly period: EventPeriodFilter;
  readonly search: string;
  readonly type: EventTypeFilter;
}

interface EventPeriodOption {
  readonly value: EventPeriodFilter;
  readonly label: string;
}

interface EventTypeOption {
  readonly value: EventTypeFilter;
  readonly label: string;
}

@Component({
  selector: 'app-event-management',
  standalone: true,
  imports: [DatePipe, ReactiveFormsModule, RouterLink],
  templateUrl: './event-management.component.html',
  styleUrl: './event-management.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EventManagementComponent implements OnInit {
  private readonly eventService = inject(EventService);

  readonly today = todayLocalDate();
  readonly form = new FormGroup({
    nameMassOrEvent: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, notBlankValidator],
    }),
    eventDate: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, futureOrPresentDateValidator],
    }),
    eventTime: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required],
    }),
    massOrCelebration: new FormControl<boolean | null>(null, {
      validators: [Validators.required],
    }),
  });
  readonly events = signal<CelebrationEventResponse[]>([]);
  readonly isLoading = signal(false);
  readonly isSaving = signal(false);
  readonly deletingId = signal<number | null>(null);
  readonly editingEventId = signal<number | null>(null);
  readonly errorMessage = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);
  readonly pendingDeletion = signal<CelebrationEventResponse | null>(null);

  readonly periodFilter = signal<EventPeriodFilter>('upcoming');
  readonly searchTerm = signal('');
  readonly typeFilter = signal<EventTypeFilter>('all');

  readonly periodOptions: readonly EventPeriodOption[] = [
    { value: 'upcoming', label: 'Próximos' },
    { value: 'past', label: 'Passados' },
    { value: 'all', label: 'Todos' },
  ];

  readonly typeOptions: readonly EventTypeOption[] = [
    { value: 'all', label: 'Todos' },
    { value: 'mass', label: 'Missa' },
    { value: 'celebration', label: 'Celebração' },
  ];

  readonly visibleEvents = computed(() =>
    applyEventFilters(this.events(), {
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

    if (this.events().length === 0) {
      return 'Nenhum evento cadastrado foi encontrado.';
    }

    const hasActiveSearchOrType = this.searchTerm().trim().length > 0 || this.typeFilter() !== 'all';

    if (!hasActiveSearchOrType) {
      if (this.periodFilter() === 'upcoming') {
        return 'Nenhum próximo evento cadastrado foi encontrado.';
      }

      if (this.periodFilter() === 'past') {
        return 'Nenhum evento passado foi encontrado.';
      }
    }

    return 'Nenhum evento corresponde aos filtros informados.';
  });

  readonly showClearFiltersInEmptyState = computed(
    () => this.events().length > 0 && this.visibleEvents().length === 0,
  );

  get isEditing(): boolean {
    return this.editingEventId() !== null;
  }

  ngOnInit(): void {
    this.loadEvents();
  }

  loadEvents(clearErrorMessage = true): void {
    if (this.isLoading()) {
      return;
    }

    this.isLoading.set(true);

    if (clearErrorMessage) {
      this.errorMessage.set(null);
    }

    this.eventService
      .findAll()
      .pipe(finalize(() => this.isLoading.set(false)))
      .subscribe({
        next: (events) => {
          this.events.set(events);
        },
        error: () => {
          this.errorMessage.set('Nao foi possivel carregar os eventos. Tente novamente.');
        },
      });
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    if (this.isSaving()) {
      return;
    }

    const request = this.eventRequest();
    const editingEventId = this.editingEventId();

    if (request === null) {
      return;
    }

    this.setSaving(true);
    this.clearMessages();

    const operation =
      editingEventId === null
        ? this.eventService.create(request)
        : this.eventService.update(editingEventId, request);

    operation.pipe(finalize(() => this.setSaving(false))).subscribe({
      next: (event) => {
        if (editingEventId === null) {
          this.events.update((events) => [...events, event]);
          this.successMessage.set('Evento cadastrado com sucesso.');
        } else {
          this.events.update((events) =>
            events.map((currentEvent) => (currentEvent.id === event.id ? event : currentEvent)),
          );
          this.successMessage.set('Evento atualizado com sucesso.');
        }

        this.resetForm();
      },
      error: (error: unknown) => {
        this.errorMessage.set(saveErrorMessageFor(error));

        if (error instanceof HttpErrorResponse && error.status === 404) {
          this.loadEvents(false);
        }
      },
    });
  }

  startEditing(event: CelebrationEventResponse): void {
    this.clearMessages();
    this.pendingDeletion.set(null);
    this.editingEventId.set(event.id);
    this.form.setValue({
      nameMassOrEvent: event.nameMassOrEvent,
      eventDate: event.eventDate,
      eventTime: event.eventTime.slice(0, 5),
      massOrCelebration: event.massOrCelebration,
    });
    this.form.markAsPristine();
    this.form.markAsUntouched();
  }

  cancelEditing(): void {
    this.clearMessages();
    this.resetForm();
  }

  requestDeletion(event: CelebrationEventResponse): void {
    this.clearMessages();
    this.pendingDeletion.set(event);
  }

  cancelDeletion(): void {
    this.pendingDeletion.set(null);
  }

  confirmDeletion(): void {
    const event = this.pendingDeletion();

    if (event === null || this.deletingId() !== null) {
      return;
    }

    this.deletingId.set(event.id);
    this.clearMessages();

    this.eventService
      .delete(event.id)
      .pipe(finalize(() => this.deletingId.set(null)))
      .subscribe({
        next: () => {
          this.events.update((events) =>
            events.filter((currentEvent) => currentEvent.id !== event.id),
          );

          if (this.editingEventId() === event.id) {
            this.resetForm();
          }

          this.pendingDeletion.set(null);
          this.successMessage.set('Evento excluido com sucesso.');
        },
        error: (error: unknown) => {
          this.errorMessage.set(deleteErrorMessageFor(error));

          if (error instanceof HttpErrorResponse && error.status === 404) {
            this.pendingDeletion.set(null);
            this.loadEvents(false);
          }
        },
      });
  }

  fieldErrorMessage(controlName: keyof CelebrationEventRequest): string | null {
    const control = this.form.controls[controlName];

    if (!control.touched || control.valid) {
      return null;
    }

    if (control.hasError('required') || control.hasError('blank')) {
      if (controlName === 'nameMassOrEvent') {
        return 'Informe o nome do evento.';
      }

      if (controlName === 'eventDate') {
        return 'Informe a data do evento.';
      }

      if (controlName === 'eventTime') {
        return 'Informe o horario do evento.';
      }

      return 'Informe se o evento e uma missa ou celebracao.';
    }

    if (control.hasError('pastDate')) {
      return 'A data deve ser hoje ou uma data futura.';
    }

    return null;
  }

  getEventType(event: CelebrationEventResponse): string {
    return event.massOrCelebration ? 'Missa' : 'Celebracao';
  }

  formatTime(eventTime: string): string {
    return eventTime.slice(0, 5);
  }

  setPeriodFilter(period: EventPeriodFilter): void {
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

    this.typeFilter.set(target.value as EventTypeFilter);
  }

  clearFilters(): void {
    this.periodFilter.set('upcoming');
    this.searchTerm.set('');
    this.typeFilter.set('all');
  }

  private eventRequest(): CelebrationEventRequest | null {
    const value = this.form.getRawValue();

    if (value.massOrCelebration === null) {
      return null;
    }

    return {
      nameMassOrEvent: value.nameMassOrEvent.trim(),
      eventDate: value.eventDate,
      eventTime: normalizeTime(value.eventTime),
      massOrCelebration: value.massOrCelebration,
    };
  }

  private resetForm(): void {
    this.editingEventId.set(null);
    this.form.reset({
      nameMassOrEvent: '',
      eventDate: '',
      eventTime: '',
      massOrCelebration: null,
    });
  }

  private clearMessages(): void {
    this.errorMessage.set(null);
    this.successMessage.set(null);
  }

  private setSaving(isSaving: boolean): void {
    this.isSaving.set(isSaving);

    if (isSaving) {
      this.form.disable({ emitEvent: false });
      return;
    }

    this.form.enable({ emitEvent: false });
  }
}

function notBlankValidator(control: AbstractControl): ValidationErrors | null {
  return typeof control.value === 'string' && control.value.trim().length === 0
    ? { blank: true }
    : null;
}

function futureOrPresentDateValidator(control: AbstractControl): ValidationErrors | null {
  if (typeof control.value !== 'string' || control.value.length === 0) {
    return null;
  }

  return control.value < todayLocalDate() ? { pastDate: true } : null;
}

function normalizeTime(value: string): string {
  return value.length === 5 ? `${value}:00` : value;
}

function todayLocalDate(): string {
  const today = new Date();
  const year = today.getFullYear();
  const month = String(today.getMonth() + 1).padStart(2, '0');
  const day = String(today.getDate()).padStart(2, '0');

  return `${year}-${month}-${day}`;
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

      return matchesEventType(event, filters.type) && matchesEventSearch(event, filters.search);
    })
    .sort(
      filters.period === 'past' ? compareEventsByDateTimeDescending : compareEventsByDateTimeAscending,
    );
}

function matchesEventType(event: CelebrationEventResponse, type: EventTypeFilter): boolean {
  if (type === 'mass') {
    return event.massOrCelebration;
  }

  if (type === 'celebration') {
    return !event.massOrCelebration;
  }

  return true;
}

function matchesEventSearch(event: CelebrationEventResponse, search: string): boolean {
  const normalizedSearch = normalizeEventSearchText(search);

  if (normalizedSearch.length === 0) {
    return true;
  }

  return normalizeEventSearchText(event.nameMassOrEvent).includes(normalizedSearch);
}

function resultCountLabelFor(count: number): string {
  return count === 1 ? '1 evento encontrado' : `${count} eventos encontrados`;
}

function saveErrorMessageFor(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 400) {
      return 'Revise os dados do evento antes de salvar.';
    }

    if (error.status === 403) {
      return 'Voce nao possui permissao para salvar eventos.';
    }

    if (error.status === 404) {
      return 'O evento solicitado nao foi encontrado.';
    }

    if (error.status === 409) {
      return 'Nao foi possivel salvar o evento devido a um conflito com os dados atuais.';
    }
  }

  return 'Nao foi possivel concluir a operacao. Tente novamente.';
}

function deleteErrorMessageFor(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 403) {
      return 'Voce nao possui permissao para excluir eventos.';
    }

    if (error.status === 404) {
      return 'O evento solicitado nao foi encontrado.';
    }

    if (error.status === 409) {
      return 'Nao e possivel excluir este evento porque ele possui vinculos com a escala.';
    }
  }

  return 'Nao foi possivel concluir a operacao. Tente novamente.';
}
