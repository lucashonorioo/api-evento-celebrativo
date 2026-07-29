import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Params, RouterLink } from '@angular/router';
import { Observable, finalize, map } from 'rxjs';

import {
  EventScheduleDetailResponse,
  EventScheduleParticipationDetailResponse,
  EventScheduleParticipationPersonSummary,
  EventSchedulePersonSummary,
  EventScheduleType,
} from '../event-schedule.models';
import { EventScheduleService } from '../event-schedule.service';
import { AuthSessionService } from '../../auth-session.service';
import { ScheduleParticipationStatus } from '../../schedule-participation/schedule-participation.models';
import { scheduleParticipationStatusLabel } from '../../schedule-participation/schedule-participation-view.utils';

interface ScheduleParticipant {
  readonly id: number;
  readonly name: string;
  readonly participationStatus: ScheduleParticipationStatus | null;
  readonly declineReason: string | null;
  readonly respondedAt: string | null;
}

interface ScheduleDetailView {
  readonly eventId: number;
  readonly eventName: string;
  readonly eventDate: string;
  readonly eventTime: string;
  readonly massOrCelebration: boolean;
  readonly location: EventScheduleDetailResponse['location'];
  readonly priest: ScheduleParticipant | null;
  readonly readers: ScheduleParticipant[];
  readonly commentators: ScheduleParticipant[];
  readonly ministersOfTheWord: ScheduleParticipant[];
  readonly eucharisticMinisters: ScheduleParticipant[];
}

interface ParticipantSection {
  readonly title: string;
  readonly emptyMessage: string;
  readonly people: readonly ScheduleParticipant[];
}

interface ParticipationSummary {
  readonly total: number;
  readonly confirmed: number;
  readonly pending: number;
  readonly declined: number;
}

const SCHEDULE_LIST_ROUTE_PATH = 'escalas/eventos/:id';
const EVENT_DETAIL_ROUTE_PATH = 'eventos/:id/escala';

@Component({
  selector: 'app-event-schedule-detail',
  standalone: true,
  imports: [DatePipe, RouterLink],
  templateUrl: './event-schedule-detail.component.html',
  styleUrl: './event-schedule-detail.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EventScheduleDetailComponent implements OnInit {
  private readonly activatedRoute = inject(ActivatedRoute);
  private readonly authSessionService = inject(AuthSessionService);
  private readonly eventScheduleService = inject(EventScheduleService);

  readonly schedule = signal<ScheduleDetailView | null>(null);
  readonly isLoading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly eventId = signal<number | null>(null);
  readonly backQueryParams = signal<Params>({});

  readonly isRefreshing = signal(false);
  readonly refreshErrorMessage = signal<string | null>(null);

  readonly participationSummary = computed<ParticipationSummary | null>(() => {
    const schedule = this.schedule();

    if (schedule === null || !this.isAdmin()) {
      return null;
    }

    return buildParticipationSummary(schedule);
  });

  private readonly routeConfigPath = this.activatedRoute.snapshot.routeConfig?.path;
  private readonly isEventDetailContext = this.routeConfigPath === EVENT_DETAIL_ROUTE_PATH;
  private readonly isRecognizedContext =
    this.routeConfigPath === SCHEDULE_LIST_ROUTE_PATH || this.isEventDetailContext;
  private readonly rawEventIdParam = this.activatedRoute.snapshot.paramMap.get('id') ?? '';

  readonly backLabel = this.isEventDetailContext ? 'Voltar para o evento' : 'Voltar para Escalas';
  readonly backCommands: readonly string[] = this.isEventDetailContext
    ? ['/app/eventos', this.rawEventIdParam]
    : ['/app/escalas'];
  readonly backQueryParamsHandling: 'preserve' | '' = this.isEventDetailContext ? 'preserve' : '';

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

  refreshParticipation(): void {
    const eventId = this.eventId();

    if (eventId === null || this.isRefreshing() || !this.isAdmin()) {
      return;
    }

    this.isRefreshing.set(true);
    this.refreshErrorMessage.set(null);

    this.eventScheduleService
      .findParticipationByEventId(eventId)
      .pipe(
        map(toParticipationScheduleDetailView),
        finalize(() => this.isRefreshing.set(false)),
      )
      .subscribe({
        next: (schedule) => {
          this.schedule.set(schedule);
        },
        error: (error: unknown) => {
          this.refreshErrorMessage.set(participationErrorMessageFor(error));
        },
      });
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

  isAdmin(): boolean {
    return this.isRecognizedContext && this.authSessionService.hasAuthority('ROLE_ADMIN');
  }

  participationStatusLabel(status: ScheduleParticipationStatus): string {
    return scheduleParticipationStatusLabel(status);
  }

  respondedAtDate(respondedAt: string | null): Date | null {
    if (respondedAt === null) {
      return null;
    }

    const date = new Date(respondedAt);

    return Number.isNaN(date.getTime()) ? null : date;
  }

  participantSections(schedule: ScheduleDetailView): readonly ParticipantSection[] {
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

  editLinkFor(eventId: number): readonly string[] {
    return ['/app/admin/escalas/eventos', String(eventId), 'editar'];
  }

  private loadSchedule(eventId: number): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);
    this.schedule.set(null);

    const isAdmin = this.isAdmin();
    const request$: Observable<ScheduleDetailView> = isAdmin
      ? this.eventScheduleService
          .findParticipationByEventId(eventId)
          .pipe(map(toParticipationScheduleDetailView))
      : this.eventScheduleService.findByEventId(eventId).pipe(map(toScheduleDetailView));

    request$.pipe(finalize(() => this.isLoading.set(false))).subscribe({
      next: (schedule) => {
        this.schedule.set(schedule);
      },
      error: (error: unknown) => {
        this.errorMessage.set(isAdmin ? participationErrorMessageFor(error) : errorMessageFor(error));
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

function participationErrorMessageFor(error: unknown): string {
  if (error instanceof HttpErrorResponse && error.status === 404) {
    return 'A escala do evento solicitado não foi encontrada.';
  }

  if (error instanceof HttpErrorResponse && error.status === 403) {
    return 'Você não possui permissão para consultar as participações desta escala.';
  }

  return 'Não foi possível carregar as participações da escala. Tente novamente.';
}

function toParticipant(person: EventSchedulePersonSummary): ScheduleParticipant {
  return {
    id: person.id,
    name: person.name,
    participationStatus: null,
    declineReason: null,
    respondedAt: null,
  };
}

function toParticipationParticipant(
  person: EventScheduleParticipationPersonSummary,
): ScheduleParticipant {
  return {
    id: person.id,
    name: person.name,
    participationStatus: person.participationStatus,
    declineReason: person.declineReason,
    respondedAt: person.respondedAt,
  };
}

function toScheduleDetailView(detail: EventScheduleDetailResponse): ScheduleDetailView {
  return {
    eventId: detail.eventId,
    eventName: detail.eventName,
    eventDate: detail.eventDate,
    eventTime: detail.eventTime,
    massOrCelebration: detail.massOrCelebration,
    location: detail.location,
    priest: detail.priest ? toParticipant(detail.priest) : null,
    readers: detail.readers.map(toParticipant),
    commentators: detail.commentators.map(toParticipant),
    ministersOfTheWord: detail.ministersOfTheWord.map(toParticipant),
    eucharisticMinisters: detail.eucharisticMinisters.map(toParticipant),
  };
}

function toParticipationScheduleDetailView(
  detail: EventScheduleParticipationDetailResponse,
): ScheduleDetailView {
  return {
    eventId: detail.eventId,
    eventName: detail.eventName,
    eventDate: detail.eventDate,
    eventTime: detail.eventTime,
    massOrCelebration: detail.massOrCelebration,
    location: detail.location,
    priest: detail.priest ? toParticipationParticipant(detail.priest) : null,
    readers: detail.readers.map(toParticipationParticipant),
    commentators: detail.commentators.map(toParticipationParticipant),
    ministersOfTheWord: detail.ministersOfTheWord.map(toParticipationParticipant),
    eucharisticMinisters: detail.eucharisticMinisters.map(toParticipationParticipant),
  };
}

function buildParticipationSummary(schedule: ScheduleDetailView): ParticipationSummary {
  const uniquePeople = new Map<number, ScheduleParticipant>();
  const allPeople = [
    ...(schedule.priest ? [schedule.priest] : []),
    ...schedule.readers,
    ...schedule.commentators,
    ...schedule.ministersOfTheWord,
    ...schedule.eucharisticMinisters,
  ];

  for (const person of allPeople) {
    uniquePeople.set(person.id, person);
  }

  const people = Array.from(uniquePeople.values());

  return {
    total: people.length,
    confirmed: people.filter((person) => person.participationStatus === 'CONFIRMED').length,
    pending: people.filter((person) => person.participationStatus === 'PENDING').length,
    declined: people.filter((person) => person.participationStatus === 'DECLINED').length,
  };
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
