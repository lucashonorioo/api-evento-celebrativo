import { EventScheduleType } from '../event-schedules/event-schedule.models';

export type ScheduleParticipationStatus = 'PENDING' | 'CONFIRMED' | 'DECLINED';

export type ScheduleParticipationResponseStatus = 'CONFIRMED' | 'DECLINED';

export interface ScheduleParticipationUpdateRequest {
  readonly status: ScheduleParticipationResponseStatus;
  readonly declineReason?: string | null;
}

export interface ScheduleParticipationResponse {
  readonly eventId: number;
  readonly status: ScheduleParticipationResponseStatus;
  readonly declineReason: string | null;
  readonly respondedAt: string;
}

export interface CurrentUserScheduleQuery {
  readonly startDate: string;
  readonly endDate: string;
  readonly page: number;
  readonly size: number;
}

export interface CurrentUserSchedule {
  readonly eventId: number;
  readonly eventName: string;
  readonly eventDate: string;
  readonly eventTime: string;
  readonly massOrCelebration: boolean;
  readonly locationId: number | null;
  readonly locationName: string | null;
  readonly assignments: EventScheduleType[];
  readonly participationStatus: ScheduleParticipationStatus;
  readonly declineReason: string | null;
  readonly respondedAt: string | null;
}

export interface CurrentUserSchedulePage {
  readonly content: CurrentUserSchedule[];
  readonly totalPages: number;
  readonly totalElements: number;
  readonly first: boolean;
  readonly last: boolean;
  readonly size: number;
  readonly number: number;
  readonly numberOfElements: number;
  readonly empty: boolean;
}
