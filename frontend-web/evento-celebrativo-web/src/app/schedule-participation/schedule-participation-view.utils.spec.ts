import {
  eventScheduleTypeSingularLabel,
  scheduleParticipationStatusLabel,
} from './schedule-participation-view.utils';

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

describe('eventScheduleTypeSingularLabel', () => {
  it('should translate PRIEST', () => {
    expect(eventScheduleTypeSingularLabel('PRIEST')).toBe('Padre');
  });

  it('should translate READER', () => {
    expect(eventScheduleTypeSingularLabel('READER')).toBe('Leitor');
  });

  it('should translate COMMENTATOR', () => {
    expect(eventScheduleTypeSingularLabel('COMMENTATOR')).toBe('Comentarista');
  });

  it('should translate MINISTER_OF_THE_WORD', () => {
    expect(eventScheduleTypeSingularLabel('MINISTER_OF_THE_WORD')).toBe('Ministro da Palavra');
  });

  it('should translate EUCHARISTIC_MINISTER', () => {
    expect(eventScheduleTypeSingularLabel('EUCHARISTIC_MINISTER')).toBe('Ministro da Eucaristia');
  });
});
