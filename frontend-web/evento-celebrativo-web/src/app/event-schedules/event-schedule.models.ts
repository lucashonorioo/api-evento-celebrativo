export type EventScheduleType =
  | 'PRIEST'
  | 'READER'
  | 'COMMENTATOR'
  | 'MINISTER_OF_THE_WORD'
  | 'EUCHARISTIC_MINISTER';

export interface EventScheduleQuery {
  readonly startDate: string;
  readonly endDate: string;
  readonly type: EventScheduleType;
  readonly page: number;
  readonly size: number;
  readonly includeUnassigned: boolean;
}

export interface EventScheduleAssignmentResponse {
  readonly personId: number;
  readonly personName: string;
}

export interface EventScheduleResponse {
  readonly eventId: number;
  readonly eventName: string;
  readonly eventDate: string;
  readonly eventTime: string;
  readonly massOrCelebration: boolean;
  readonly locationId: number;
  readonly churchName: string;
  readonly assignmentType: EventScheduleType;
  readonly assignments: EventScheduleAssignmentResponse[];
}

export interface EventSchedulePage {
  readonly content: EventScheduleResponse[];
  readonly totalPages: number;
  readonly totalElements: number;
  readonly first: boolean;
  readonly last: boolean;
  readonly size: number;
  readonly number: number;
  readonly numberOfElements: number;
  readonly empty: boolean;
}

export interface EventSchedulePersonSummary {
  readonly id: number;
  readonly name: string;
}

export interface EventScheduleLocationSummary {
  readonly id: number;
  readonly churchName: string;
}

export interface EventScheduleDetailResponse {
  readonly eventId: number;
  readonly eventName: string;
  readonly eventDate: string;
  readonly eventTime: string;
  readonly massOrCelebration: boolean;
  readonly location: EventScheduleLocationSummary | null;
  readonly priest: EventSchedulePersonSummary | null;
  readonly readers: EventSchedulePersonSummary[];
  readonly commentators: EventSchedulePersonSummary[];
  readonly ministersOfTheWord: EventSchedulePersonSummary[];
  readonly eucharisticMinisters: EventSchedulePersonSummary[];
}

export interface UpdateEventScheduleRequest {
  readonly locationId: number;
  readonly priestId: number | null;
  readonly readerIds: number[];
  readonly commentatorIds: number[];
  readonly ministerOfTheWordIds: number[];
  readonly eucharisticMinisterIds: number[];
}

export interface UpdateEventScheduleResponse {
  readonly eventId: number;
  readonly nameMassOrEvent: string;
  readonly eventDate: string;
  readonly eventTime: string;
  readonly massOrCelebration: boolean;
  readonly location: EventScheduleLocationSummary | null;
  readonly priest: EventSchedulePersonSummary | null;
  readonly readers: EventSchedulePersonSummary[];
  readonly commentators: EventSchedulePersonSummary[];
  readonly ministersOfTheWord: EventSchedulePersonSummary[];
  readonly eucharisticMinisters: EventSchedulePersonSummary[];
}
