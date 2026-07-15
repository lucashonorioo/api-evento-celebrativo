import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Params, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import {
  EventScheduleDetailResponse,
  EventSchedulePersonSummary,
  EventScheduleType,
} from '../event-schedule.models';
import { EventScheduleService } from '../event-schedule.service';

interface ParticipantSection {
  readonly title: string;
  readonly emptyMessage: string;
  readonly people: readonly EventSchedulePersonSummary[];
}

@Component({
  selector: 'app-event-schedule-detail',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './event-schedule-detail.component.html',
  styleUrl: './event-schedule-detail.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EventScheduleDetailComponent implements OnInit {
  private readonly activatedRoute = inject(ActivatedRoute);
  private readonly eventScheduleService = inject(EventScheduleService);

  readonly schedule = signal<EventScheduleDetailResponse | null>(null);
  readonly isLoading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly eventId = signal<number | null>(null);
  readonly backQueryParams = signal<Params>({});

  ngOnInit(): void {
    this.backQueryParams.set(validBackQueryParams(this.activatedRoute.snapshot.queryParamMap));

    const eventId = parseEventId(this.activatedRoute.snapshot.paramMap.get('id'));

    if (eventId === null) {
      this.errorMessage.set('Nao foi possivel identificar o evento solicitado.');
      return;
    }

    this.eventId.set(eventId);
    this.loadSchedule(eventId);
  }

  retry(): void {
    const eventId = this.eventId();

    if (eventId === null) {
      return;
    }

    this.loadSchedule(eventId);
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

  participantSections(schedule: EventScheduleDetailResponse): readonly ParticipantSection[] {
    return [
      {
        title: 'Leitores',
        emptyMessage: 'Nenhum leitor escalado.',
        people: schedule.readers,
      },
      {
        title: 'Comentaristas',
        emptyMessage: 'Nenhum comentarista escalado.',
        people: schedule.commentators,
      },
      {
        title: 'Ministros da Palavra',
        emptyMessage: 'Nenhum ministro da Palavra escalado.',
        people: schedule.ministersOfTheWord,
      },
      {
        title: 'Ministros da Eucaristia',
        emptyMessage: 'Nenhum ministro da Eucaristia escalado.',
        people: schedule.eucharisticMinisters,
      },
    ];
  }

  private loadSchedule(eventId: number): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);
    this.schedule.set(null);

    this.eventScheduleService
      .findByEventId(eventId)
      .pipe(finalize(() => this.isLoading.set(false)))
      .subscribe({
        next: (schedule) => {
          this.schedule.set(schedule);
        },
        error: (error: unknown) => {
          this.errorMessage.set(errorMessageFor(error));
        },
      });
  }
}

function parseEventId(value: string | null): number | null {
  if (value === null || !/^[1-9]\d*$/.test(value)) {
    return null;
  }

  return Number(value);
}

function errorMessageFor(error: unknown): string {
  if (error instanceof HttpErrorResponse && error.status === 404) {
    return 'A escala do evento solicitado nao foi encontrada.';
  }

  if (error instanceof HttpErrorResponse && error.status === 403) {
    return 'Voce nao possui permissao para consultar esta escala.';
  }

  return 'Nao foi possivel carregar a escala. Tente novamente.';
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
