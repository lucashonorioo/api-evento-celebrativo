import { ScheduleParticipationStatus } from './schedule-participation.models';

const PARTICIPATION_STATUS_LABELS: Record<ScheduleParticipationStatus, string> = {
  PENDING: 'Aguardando resposta',
  CONFIRMED: 'Participação confirmada',
  DECLINED: 'Não participará',
};

export function scheduleParticipationStatusLabel(status: ScheduleParticipationStatus): string {
  return PARTICIPATION_STATUS_LABELS[status];
}
