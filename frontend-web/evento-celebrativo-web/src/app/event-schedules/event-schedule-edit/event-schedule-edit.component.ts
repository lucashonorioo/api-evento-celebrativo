import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Params, Router, RouterLink } from '@angular/router';
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
  EventScheduleDetailResponse,
  EventScheduleType,
  UpdateEventScheduleRequest,
  UpdateEventScheduleResponse,
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
  selector: 'app-event-schedule-edit',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './event-schedule-edit.component.html',
  styleUrl: './event-schedule-edit.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EventScheduleEditComponent implements OnInit {
  private readonly activatedRoute = inject(ActivatedRoute);
  private readonly commentatorService = inject(CommentatorService);
  private readonly eucharisticMinisterService = inject(EucharisticMinisterService);
  private readonly eventScheduleService = inject(EventScheduleService);
  private readonly locationService = inject(LocationService);
  private readonly ministerOfTheWordService = inject(MinisterOfTheWordService);
  private readonly priestService = inject(PriestService);
  private readonly readerService = inject(ReaderService);
  private readonly router = inject(Router);

  readonly form = new FormGroup({
    locationId: new FormControl<number | null>(null, { validators: [Validators.required] }),
    priestId: new FormControl<number | null>(null),
    readerIds: new FormControl<number[]>([], { nonNullable: true }),
    commentatorIds: new FormControl<number[]>([], { nonNullable: true }),
    ministerOfTheWordIds: new FormControl<number[]>([], { nonNullable: true }),
    eucharisticMinisterIds: new FormControl<number[]>([], { nonNullable: true }),
  });

  readonly eventId = signal<number | null>(null);
  readonly schedule = signal<EventScheduleDetailResponse | null>(null);
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
  readonly backQueryParams = signal<Params>({});
  readonly readerSearch = signal('');
  readonly commentatorSearch = signal('');
  readonly ministerOfTheWordSearch = signal('');
  readonly eucharisticMinisterSearch = signal('');

  ngOnInit(): void {
    this.backQueryParams.set(validBackQueryParams(this.activatedRoute.snapshot.queryParamMap));

    const eventId = parseEventId(this.activatedRoute.snapshot.paramMap.get('id'));

    if (eventId === null) {
      this.loadErrorMessage.set('Nao foi possivel identificar o evento solicitado.');
      return;
    }

    this.eventId.set(eventId);
    this.loadData(eventId);
  }

  retry(): void {
    const eventId = this.eventId();

    if (eventId === null) {
      return;
    }

    this.loadData(eventId);
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

    const eventId = this.eventId();
    const request = this.buildRequest();

    if (eventId === null || request === null) {
      return;
    }

    this.isSaving.set(true);

    this.eventScheduleService
      .updateEventSchedule(eventId, request)
      .pipe(finalize(() => this.isSaving.set(false)))
      .subscribe({
        next: (response) => {
          this.schedule.set(toDetailResponse(response));
          this.patchFormFromSchedule(this.schedule());
          this.form.markAsPristine();
          this.successMessage.set('Escala atualizada com sucesso.');
        },
        error: (error: unknown) => {
          this.saveErrorMessage.set(saveErrorMessageFor(error));
        },
      });
  }

  cancel(): void {
    const eventId = this.eventId();

    if (eventId === null) {
      void this.router.navigate(['/app/escalas'], { queryParams: this.backQueryParams() });
      return;
    }

    void this.router.navigate(['/app/escalas/eventos', eventId], {
      queryParams: this.backQueryParams(),
    });
  }

  detailLink(): readonly string[] {
    const eventId = this.eventId();

    return eventId === null ? ['/app/escalas'] : ['/app/escalas/eventos', String(eventId)];
  }

  formatDate(eventDate: string): string {
    const [year, month, day] = eventDate.split('-');

    return `${day}/${month}/${year}`;
  }

  formatTime(eventTime: string): string {
    return eventTime.slice(0, 5);
  }

  eventKind(massOrCelebration: boolean): string {
    return massOrCelebration ? 'Missa' : 'Celebracao';
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

  private loadData(eventId: number): void {
    this.isLoading.set(true);
    this.loadErrorMessage.set(null);
    this.saveErrorMessage.set(null);
    this.successMessage.set(null);
    this.schedule.set(null);

    forkJoin({
      schedule: this.eventScheduleService.findByEventId(eventId),
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
          this.schedule.set(result.schedule);
          this.locations.set(result.locations);
          this.priests.set(result.priests);
          this.readers.set(result.readers);
          this.commentators.set(result.commentators);
          this.ministersOfTheWord.set(result.ministersOfTheWord);
          this.eucharisticMinisters.set(result.eucharisticMinisters);
          this.patchFormFromSchedule(result.schedule);
        },
        error: (error: unknown) => {
          this.loadErrorMessage.set(loadErrorMessageFor(error));
        },
      });
  }

  private patchFormFromSchedule(schedule: EventScheduleDetailResponse | null): void {
    if (schedule === null) {
      return;
    }

    this.form.setValue({
      locationId: schedule.location?.id ?? null,
      priestId: schedule.priest?.id ?? null,
      readerIds: schedule.readers.map((person) => person.id),
      commentatorIds: schedule.commentators.map((person) => person.id),
      ministerOfTheWordIds: schedule.ministersOfTheWord.map((person) => person.id),
      eucharisticMinisterIds: schedule.eucharisticMinisters.map((person) => person.id),
    });
    this.form.markAsPristine();
  }

  private buildRequest(): UpdateEventScheduleRequest | null {
    const locationId = this.form.controls.locationId.value;

    if (locationId === null) {
      return null;
    }

    return {
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

function parseEventId(value: string | null): number | null {
  if (value === null || !/^[1-9]\d*$/.test(value)) {
    return null;
  }

  return Number(value);
}

function validBackQueryParams(queryParamMap: {
  get(name: string): string | null;
}): Params {
  const params: Params = {};
  const type = queryParamMap.get('type');
  const month = queryParamMap.get('month');
  const includeUnassigned = queryParamMap.get('includeUnassigned');
  const page = queryParamMap.get('page');

  if (isEventScheduleType(type)) {
    params['type'] = type;
  }

  if (month !== null && /^\d{4}-\d{2}$/.test(month)) {
    params['month'] = month;
  }

  if (includeUnassigned === 'true' || includeUnassigned === 'false') {
    params['includeUnassigned'] = includeUnassigned;
  }

  if (page !== null && /^\d+$/.test(page)) {
    params['page'] = page;
  }

  return params;
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

function loadErrorMessageFor(error: unknown): string {
  if (error instanceof HttpErrorResponse && error.status === 404) {
    return 'A escala do evento solicitado nao foi encontrada.';
  }

  if (error instanceof HttpErrorResponse && error.status === 403) {
    return 'Voce nao possui permissao para editar esta escala.';
  }

  return 'Nao foi possivel carregar os dados da escala. Tente novamente.';
}

function saveErrorMessageFor(error: unknown): string {
  if (error instanceof HttpErrorResponse && error.status === 400) {
    return 'Revise os dados da escala antes de salvar.';
  }

  if (error instanceof HttpErrorResponse && error.status === 403) {
    return 'Voce nao possui permissao para atualizar esta escala.';
  }

  if (error instanceof HttpErrorResponse && error.status === 404) {
    return 'A escala do evento solicitado nao foi encontrada.';
  }

  if (error instanceof HttpErrorResponse && error.status === 409) {
    return 'Nao foi possivel atualizar a escala devido a um conflito com os dados atuais.';
  }

  return 'Nao foi possivel atualizar a escala. Tente novamente.';
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

function toDetailResponse(response: UpdateEventScheduleResponse): EventScheduleDetailResponse {
  return {
    eventId: response.eventId,
    eventName: response.nameMassOrEvent,
    eventDate: response.eventDate,
    eventTime: response.eventTime,
    massOrCelebration: response.massOrCelebration,
    location: response.location,
    priest: response.priest,
    readers: response.readers,
    commentators: response.commentators,
    ministersOfTheWord: response.ministersOfTheWord,
    eucharisticMinisters: response.eucharisticMinisters,
  };
}
