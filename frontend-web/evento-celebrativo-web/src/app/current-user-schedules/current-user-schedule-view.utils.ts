import { EventScheduleType } from '../event-schedules/event-schedule.models';
import { ScheduleParticipationStatus } from './current-user-schedule.models';

const ASSIGNMENT_LABELS: Record<EventScheduleType, string> = {
  PRIEST: 'Padre',
  READER: 'Leitor',
  COMMENTATOR: 'Comentarista',
  MINISTER_OF_THE_WORD: 'Ministro da Palavra',
  EUCHARISTIC_MINISTER: 'Ministro da Eucaristia',
};

export function scheduleAssignmentLabel(assignment: EventScheduleType): string {
  return ASSIGNMENT_LABELS[assignment] ?? assignment;
}

const PARTICIPATION_STATUS_LABELS: Record<ScheduleParticipationStatus, string> = {
  PENDING: 'Aguardando resposta',
  CONFIRMED: 'Participação confirmada',
  DECLINED: 'Não participará',
};

export function scheduleParticipationStatusLabel(status: ScheduleParticipationStatus): string {
  return PARTICIPATION_STATUS_LABELS[status];
}

export function formatLocalDate(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');

  return `${year}-${month}-${day}`;
}
