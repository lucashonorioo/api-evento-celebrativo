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
