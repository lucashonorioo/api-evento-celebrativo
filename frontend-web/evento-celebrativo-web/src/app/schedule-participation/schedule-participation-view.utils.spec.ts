import { scheduleParticipationStatusLabel } from './schedule-participation-view.utils';

describe('scheduleParticipationStatusLabel', () => {
  it('should translate PENDING', () => {
    expect(scheduleParticipationStatusLabel('PENDING')).toBe('Aguardando resposta');
  });

  it('should translate CONFIRMED', () => {
    expect(scheduleParticipationStatusLabel('CONFIRMED')).toBe('Participação confirmada');
  });

  it('should translate DECLINED', () => {
    expect(scheduleParticipationStatusLabel('DECLINED')).toBe('Não participará');
  });
});
