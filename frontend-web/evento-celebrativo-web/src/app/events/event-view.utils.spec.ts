import {
  compareEventsByDateTimeAscending,
  compareEventsByDateTimeDescending,
  eventLocalTimestamp,
  normalizeEventSearchText,
} from './event-view.utils';

describe('eventLocalTimestamp', () => {
  it('should use zero seconds for an HH:mm event time', () => {
    const withSeconds = eventLocalTimestamp({ eventDate: '2026-07-20', eventTime: '19:30:00' });
    const withoutSeconds = eventLocalTimestamp({ eventDate: '2026-07-20', eventTime: '19:30' });

    expect(withoutSeconds).toBe(withSeconds);
  });

  it('should use the informed seconds for an HH:mm:ss event time', () => {
    const atThirtySeconds = eventLocalTimestamp({ eventDate: '2026-07-20', eventTime: '19:30:30' });
    const atZeroSeconds = eventLocalTimestamp({ eventDate: '2026-07-20', eventTime: '19:30:00' });

    expect(atThirtySeconds).toBeGreaterThan(atZeroSeconds);
    expect(atThirtySeconds - atZeroSeconds).toBe(30_000);
  });

  it('should build the date and time using local components, not UTC or ISO parsing', () => {
    const timestamp = eventLocalTimestamp({ eventDate: '2026-07-20', eventTime: '19:30:00' });
    const expected = new Date(2026, 6, 20, 19, 30, 0, 0).getTime();

    expect(timestamp).toBe(expected);
  });

  it('should move to the next day correctly when the time rolls past midnight', () => {
    const endOfDay = eventLocalTimestamp({ eventDate: '2026-07-20', eventTime: '23:59:59' });
    const startOfNextDay = eventLocalTimestamp({ eventDate: '2026-07-21', eventTime: '00:00:00' });

    expect(startOfNextDay).toBeGreaterThan(endOfDay);
    expect(startOfNextDay - endOfDay).toBe(1_000);
  });

  it('should not modify the original eventDate and eventTime values', () => {
    const event = { eventDate: '2026-07-20', eventTime: '19:30' };

    eventLocalTimestamp(event);

    expect(event).toEqual({ eventDate: '2026-07-20', eventTime: '19:30' });
  });
});

describe('compareEventsByDateTimeAscending / compareEventsByDateTimeDescending', () => {
  it('should distinguish events within the same minute by their seconds', () => {
    const earlier = { eventDate: '2026-07-20', eventTime: '19:30:10' };
    const later = { eventDate: '2026-07-20', eventTime: '19:30:40' };

    expect(compareEventsByDateTimeAscending(earlier, later)).toBeLessThan(0);
    expect(compareEventsByDateTimeAscending(later, earlier)).toBeGreaterThan(0);
  });

  it('should order events ascending, oldest first', () => {
    const events = [
      { eventDate: '2026-07-25', eventTime: '09:00' },
      { eventDate: '2026-07-20', eventTime: '10:00' },
      { eventDate: '2026-07-21', eventTime: '08:00' },
    ];

    const sorted = [...events].sort(compareEventsByDateTimeAscending);

    expect(sorted).toEqual([
      { eventDate: '2026-07-20', eventTime: '10:00' },
      { eventDate: '2026-07-21', eventTime: '08:00' },
      { eventDate: '2026-07-25', eventTime: '09:00' },
    ]);
  });

  it('should order events descending, most recent first', () => {
    const events = [
      { eventDate: '2026-07-20', eventTime: '10:00' },
      { eventDate: '2026-07-25', eventTime: '09:00' },
      { eventDate: '2026-07-21', eventTime: '08:00' },
    ];

    const sorted = [...events].sort(compareEventsByDateTimeDescending);

    expect(sorted).toEqual([
      { eventDate: '2026-07-25', eventTime: '09:00' },
      { eventDate: '2026-07-21', eventTime: '08:00' },
      { eventDate: '2026-07-20', eventTime: '10:00' },
    ]);
  });

  it('should return equality for identical timestamps', () => {
    const first = { eventDate: '2026-07-25', eventTime: '09:00:00' };
    const second = { eventDate: '2026-07-25', eventTime: '09:00:00' };

    expect(compareEventsByDateTimeAscending(first, second)).toBe(0);
    expect(compareEventsByDateTimeDescending(first, second)).toBe(0);
  });

  it('should preserve distinct events with the same timestamp instead of deduplicating them', () => {
    const events = [
      { id: 4, eventDate: '2026-07-25', eventTime: '09:00:00' },
      { id: 5, eventDate: '2026-07-25', eventTime: '09:00:00' },
    ];

    const sorted = [...events].sort(compareEventsByDateTimeAscending);

    expect(sorted.map((event) => event.id)).toEqual([4, 5]);
    expect(sorted.length).toBe(2);
  });
});

describe('normalizeEventSearchText', () => {
  it('should normalize letter case', () => {
    expect(normalizeEventSearchText('CELEBRAÇÃO')).toBe(normalizeEventSearchText('celebração'));
  });

  it('should trim surrounding whitespace', () => {
    expect(normalizeEventSearchText('  Celebração  ')).toBe(normalizeEventSearchText('Celebração'));
  });

  it('should remove diacritical marks', () => {
    expect(normalizeEventSearchText('Celebração')).toBe('celebracao');
  });

  it('should return an empty string for blank input', () => {
    expect(normalizeEventSearchText('   ')).toBe('');
  });

  it('should not modify the original string', () => {
    const original = '  Celebração  ';

    normalizeEventSearchText(original);

    expect(original).toBe('  Celebração  ');
  });

  it('should treat equivalent variants as the same normalized value', () => {
    const variants = ['Celebração', 'celebracao', ' CELEBRAÇÃO '];
    const normalized = variants.map((value) => normalizeEventSearchText(value));

    expect(new Set(normalized).size).toBe(1);
  });
});
