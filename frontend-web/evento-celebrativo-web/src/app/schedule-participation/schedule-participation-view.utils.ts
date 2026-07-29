import { EventScheduleType } from '../event-schedules/event-schedule.models';
import { ScheduleParticipationStatus } from './schedule-participation.models';

const PARTICIPATION_STATUS_LABELS: Record<ScheduleParticipationStatus, string> = {
  PENDING: 'Aguardando resposta',
  CONFIRMED: 'Participação confirmada',
  DECLINED: 'Não participará',
};

export function scheduleParticipationStatusLabel(status: ScheduleParticipationStatus): string {
  return PARTICIPATION_STATUS_LABELS[status];
}

const EVENT_SCHEDULE_TYPE_SINGULAR_LABELS: Record<EventScheduleType, string> = {
  PRIEST: 'Padre',
  READER: 'Leitor',
  COMMENTATOR: 'Comentarista',
  MINISTER_OF_THE_WORD: 'Ministro da Palavra',
  EUCHARISTIC_MINISTER: 'Ministro da Eucaristia',
};

export function eventScheduleTypeSingularLabel(type: EventScheduleType): string {
  return EVENT_SCHEDULE_TYPE_SINGULAR_LABELS[type];
}
