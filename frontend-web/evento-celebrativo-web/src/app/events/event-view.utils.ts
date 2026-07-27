import { CelebrationEventResponse } from './event.models';

export type EventDateTime = Pick<CelebrationEventResponse, 'eventDate' | 'eventTime'>;

const DIACRITIC_MARKS_PATTERN = /\p{Diacritic}/gu;

export function eventLocalTimestamp(event: EventDateTime): number {
  const [year, month, day] = event.eventDate.split('-').map(Number);
  const [hours, minutes, seconds] = event.eventTime.split(':').map(Number);

  return new Date(year, month - 1, day, hours, minutes, seconds ?? 0, 0).getTime();
}

export function compareEventsByDateTimeAscending(first: EventDateTime, second: EventDateTime): number {
  return eventLocalTimestamp(first) - eventLocalTimestamp(second);
}

export function compareEventsByDateTimeDescending(first: EventDateTime, second: EventDateTime): number {
  return eventLocalTimestamp(second) - eventLocalTimestamp(first);
}

export function normalizeEventSearchText(value: string): string {
  return value.trim().toLocaleLowerCase().normalize('NFD').replace(DIACRITIC_MARKS_PATTERN, '');
}
