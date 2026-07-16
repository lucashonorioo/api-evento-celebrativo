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
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { CelebrationEventRequest, CelebrationEventResponse } from '../event.models';
import { EventService } from '../event.service';

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

  get isEditing(): boolean {
    return this.editingEventId() !== null;
  }

  ngOnInit(): void {
    this.loadEvents();
  }

  loadEvents(clearErrorMessage = true): void {
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
