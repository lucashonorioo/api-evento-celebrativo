import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { finalize, forkJoin } from 'rxjs';

import { CommentatorResponse } from '../../commentators/commentator.models';
import { CommentatorService } from '../../commentators/commentator.service';
import { EucharisticMinisterResponse } from '../../eucharistic-ministers/eucharistic-minister.models';
import { EucharisticMinisterService } from '../../eucharistic-ministers/eucharistic-minister.service';
import { LocationResponse } from '../../locations/location.models';
import { LocationService } from '../../locations/location.service';
import { MinisterOfTheWordResponse } from '../../ministers-of-the-word/minister-of-the-word.models';
import { MinisterOfTheWordService } from '../../ministers-of-the-word/minister-of-the-word.service';
import { PriestResponse } from '../../priests/priest.models';
import { PriestService } from '../../priests/priest.service';
import { ReaderResponse } from '../../readers/reader.models';
import { ReaderService } from '../../readers/reader.service';
import {
  CreateEventWithScheduleRequest,
  CreateEventWithScheduleResponse,
} from '../event-schedule.models';
import { EventScheduleService } from '../event-schedule.service';

type SelectionControlName =
  | 'readerIds'
  | 'commentatorIds'
  | 'ministerOfTheWordIds'
  | 'eucharisticMinisterIds';

type SearchName = 'readers' | 'commentators' | 'ministersOfTheWord' | 'eucharisticMinisters';

interface PersonOption {
  readonly id: number;
  readonly name: string;
}

@Component({
  selector: 'app-event-schedule-create',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './event-schedule-create.component.html',
  styleUrl: './event-schedule-create.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EventScheduleCreateComponent implements OnInit {
  private readonly commentatorService = inject(CommentatorService);
  private readonly eucharisticMinisterService = inject(EucharisticMinisterService);
  private readonly eventScheduleService = inject(EventScheduleService);
  private readonly locationService = inject(LocationService);
  private readonly ministerOfTheWordService = inject(MinisterOfTheWordService);
  private readonly priestService = inject(PriestService);
  private readonly readerService = inject(ReaderService);
  private readonly router = inject(Router);

  readonly form = new FormGroup({
    nameMassOrEvent: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/\S/)],
    }),
    eventDate: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required],
    }),
    eventTime: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required],
    }),
    massOrCelebration: new FormControl(true, { nonNullable: true }),
    locationId: new FormControl<number | null>(null, { validators: [Validators.required] }),
    priestId: new FormControl<number | null>(null),
    readerIds: new FormControl<number[]>([], { nonNullable: true }),
    commentatorIds: new FormControl<number[]>([], { nonNullable: true }),
    ministerOfTheWordIds: new FormControl<number[]>([], { nonNullable: true }),
    eucharisticMinisterIds: new FormControl<number[]>([], { nonNullable: true }),
  });

  readonly today = todayLocalDate();
  readonly locations = signal<readonly LocationResponse[]>([]);
  readonly priests = signal<readonly PriestResponse[]>([]);
  readonly readers = signal<readonly ReaderResponse[]>([]);
  readonly commentators = signal<readonly CommentatorResponse[]>([]);
  readonly ministersOfTheWord = signal<readonly MinisterOfTheWordResponse[]>([]);
  readonly eucharisticMinisters = signal<readonly EucharisticMinisterResponse[]>([]);
  readonly isLoading = signal(false);
  readonly isSaving = signal(false);
  readonly loadErrorMessage = signal<string | null>(null);
  readonly saveErrorMessage = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);
  readonly readerSearch = signal('');
  readonly commentatorSearch = signal('');
  readonly ministerOfTheWordSearch = signal('');
  readonly eucharisticMinisterSearch = signal('');

  ngOnInit(): void {
    this.loadOptions();
  }

  retry(): void {
    this.loadOptions();
  }

  onSubmit(): void {
    if (this.isSaving()) {
      return;
    }

    this.successMessage.set(null);
    this.saveErrorMessage.set(null);
    this.form.markAllAsTouched();

    if (this.form.invalid) {
      return;
    }

    const request = this.buildRequest();

    if (request === null) {
      return;
    }

    this.isSaving.set(true);

    this.eventScheduleService
      .createEventWithSchedule(request)
      .pipe(finalize(() => this.isSaving.set(false)))
      .subscribe({
        next: (response) => {
          this.successMessage.set('Evento e escala cadastrados com sucesso.');
          void this.router.navigate(destinationFor(response));
        },
        error: (error: unknown) => {
          this.saveErrorMessage.set(saveErrorMessageFor(error));
        },
      });
  }

  isSelected(controlName: SelectionControlName, personId: number): boolean {
    return this.selectionControl(controlName).value.includes(personId);
  }

  onSelectionChange(controlName: SelectionControlName, personId: number, event: Event): void {
    const target = event.target;

    if (!(target instanceof HTMLInputElement)) {
      return;
    }

    this.toggleSelection(controlName, personId, target.checked);
  }

  toggleSelection(controlName: SelectionControlName, personId: number, checked: boolean): void {
    const control = this.selectionControl(controlName);
    const currentIds = control.value;
    const nextIds = checked
      ? uniqueIds([...currentIds, personId])
      : currentIds.filter((id) => id !== personId);

    control.setValue(nextIds);
    control.markAsDirty();
  }

  selectedCount(controlName: SelectionControlName): number {
    return this.selectionControl(controlName).value.length;
  }

  setSearch(name: SearchName, event: Event): void {
    const target = event.target;

    if (!(target instanceof HTMLInputElement)) {
      return;
    }

    if (name === 'readers') {
      this.readerSearch.set(target.value);
    } else if (name === 'commentators') {
      this.commentatorSearch.set(target.value);
    } else if (name === 'ministersOfTheWord') {
      this.ministerOfTheWordSearch.set(target.value);
    } else {
      this.eucharisticMinisterSearch.set(target.value);
    }
  }

  filteredReaders(): readonly ReaderResponse[] {
    return filterByName(this.readers(), this.readerSearch());
  }

  filteredCommentators(): readonly CommentatorResponse[] {
    return filterByName(this.commentators(), this.commentatorSearch());
  }

  filteredMinistersOfTheWord(): readonly MinisterOfTheWordResponse[] {
    return filterByName(this.ministersOfTheWord(), this.ministerOfTheWordSearch());
  }

  filteredEucharisticMinisters(): readonly EucharisticMinisterResponse[] {
    return filterByName(this.eucharisticMinisters(), this.eucharisticMinisterSearch());
  }

  private loadOptions(): void {
    this.isLoading.set(true);
    this.loadErrorMessage.set(null);
    this.saveErrorMessage.set(null);
    this.successMessage.set(null);

    forkJoin({
      locations: this.locationService.findAll(),
      priests: this.priestService.findAll(),
      readers: this.readerService.findAll(),
      commentators: this.commentatorService.findAll(),
      ministersOfTheWord: this.ministerOfTheWordService.findAll(),
      eucharisticMinisters: this.eucharisticMinisterService.findAll(),
    })
      .pipe(finalize(() => this.isLoading.set(false)))
      .subscribe({
        next: (result) => {
          this.locations.set(result.locations);
          this.priests.set(result.priests);
          this.readers.set(result.readers);
          this.commentators.set(result.commentators);
          this.ministersOfTheWord.set(result.ministersOfTheWord);
          this.eucharisticMinisters.set(result.eucharisticMinisters);
        },
        error: () => {
          this.loadErrorMessage.set('Nao foi possivel carregar os dados do formulario. Tente novamente.');
        },
      });
  }

  private buildRequest(): CreateEventWithScheduleRequest | null {
    const locationId = this.form.controls.locationId.value;

    if (locationId === null) {
      return null;
    }

    return {
      nameMassOrEvent: this.form.controls.nameMassOrEvent.value.trim(),
      eventDate: this.form.controls.eventDate.value,
      eventTime: normalizeTime(this.form.controls.eventTime.value),
      massOrCelebration: this.form.controls.massOrCelebration.value,
      locationId,
      priestId: this.form.controls.priestId.value,
      readerIds: uniqueIds(this.form.controls.readerIds.value),
      commentatorIds: uniqueIds(this.form.controls.commentatorIds.value),
      ministerOfTheWordIds: uniqueIds(this.form.controls.ministerOfTheWordIds.value),
      eucharisticMinisterIds: uniqueIds(this.form.controls.eucharisticMinisterIds.value),
    };
  }

  private selectionControl(controlName: SelectionControlName): FormControl<number[]> {
    return this.form.controls[controlName];
  }
}

function saveErrorMessageFor(error: unknown): string {
  if (error instanceof HttpErrorResponse && error.status === 400) {
    return 'Revise os dados do evento e da escala antes de salvar.';
  }

  if (error instanceof HttpErrorResponse && error.status === 403) {
    return 'Voce nao possui permissao para cadastrar evento com escala.';
  }

  if (error instanceof HttpErrorResponse && error.status === 404) {
    return 'Nao foi possivel encontrar algum cadastro selecionado.';
  }

  if (error instanceof HttpErrorResponse && error.status === 409) {
    return 'Nao foi possivel cadastrar o evento devido a um conflito com os dados atuais.';
  }

  if (error instanceof HttpErrorResponse && error.status === 422) {
    return 'Revise os participantes selecionados antes de salvar.';
  }

  return 'Nao foi possivel cadastrar o evento com escala. Tente novamente.';
}

function filterByName<T extends PersonOption>(people: readonly T[], searchTerm: string): readonly T[] {
  const normalizedSearch = searchTerm.trim().toLocaleLowerCase();

  if (normalizedSearch.length === 0) {
    return people;
  }

  return people.filter((person) => person.name.toLocaleLowerCase().includes(normalizedSearch));
}

function uniqueIds(ids: readonly number[]): number[] {
  return Array.from(new Set(ids));
}

function normalizeTime(value: string): string {
  return value.length === 5 ? `${value}:00` : value;
}

function destinationFor(response: CreateEventWithScheduleResponse): readonly string[] {
  return Number.isFinite(response.eventId) && response.eventId > 0
    ? ['/app/escalas/eventos', String(response.eventId)]
    : ['/app/escalas'];
}

function todayLocalDate(): string {
  const today = new Date();
  const year = today.getFullYear();
  const month = String(today.getMonth() + 1).padStart(2, '0');
  const day = String(today.getDate()).padStart(2, '0');

  return `${year}-${month}-${day}`;
}
