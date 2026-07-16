import {
  PersonManagementFormValue,
  normalizePersonManagementRequest,
  todayLocalDate,
} from './person-form.helpers';

describe('person form helpers', () => {
  it('should format the current local date without timezone conversion', () => {
    jasmine.clock().install();

    try {
      jasmine.clock().mockDate(new Date(2026, 6, 16, 23, 30, 0));

      expect(todayLocalDate()).toBe('2026-07-16');
    } finally {
      jasmine.clock().uninstall();
    }
  });

  it('should trim only textual request fields that need normalization', () => {
    const value: PersonManagementFormValue = {
      name: '  Maria Pessoa  ',
      phoneNumber: ' 34999999999 ',
      birthdayDate: '1990-01-10',
      password: ' 123456 ',
    };

    expect(normalizePersonManagementRequest(value)).toEqual({
      name: 'Maria Pessoa',
      phoneNumber: '34999999999',
      birthdayDate: '1990-01-10',
      password: ' 123456 ',
    });
  });
});
