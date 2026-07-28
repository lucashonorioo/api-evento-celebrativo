import { EventScheduleType } from '../event-schedules/event-schedule.models';

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
