import {
  PersonMinisterialFormValue,
  buildPersonMinisterialCreateRequest,
  buildPersonMinisterialUpdateRequest,
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

  describe('buildPersonMinisterialCreateRequest', () => {
    it('should trim textual cadastral fields', () => {
      const value: PersonMinisterialFormValue = {
        name: '  Maria Pessoa  ',
        phoneNumber: ' 34999999999 ',
        birthdayDate: '1990-01-10',
        createAccess: false,
        password: '',
        confirmPassword: '',
      };

      expect(buildPersonMinisterialCreateRequest(value)).toEqual({
        name: 'Maria Pessoa',
        phoneNumber: '34999999999',
        birthdayDate: '1990-01-10',
        createAccess: false,
      });
    });

    it('should not include password or accessRole when createAccess is false', () => {
      const value: PersonMinisterialFormValue = {
        name: 'Maria Pessoa',
        phoneNumber: '34999999999',
        birthdayDate: '1990-01-10',
        createAccess: false,
        password: 'should-not-leak',
        confirmPassword: 'should-not-leak',
      };

      const request = buildPersonMinisterialCreateRequest(value) as unknown as Record<string, unknown>;

      expect(request['password']).toBeUndefined();
      expect(request['accessRole']).toBeUndefined();
      expect(request['confirmPassword']).toBeUndefined();
    });

    it('should include password and ROLE_OPERATOR when createAccess is true', () => {
      const value: PersonMinisterialFormValue = {
        name: 'Maria Pessoa',
        phoneNumber: '34999999999',
        birthdayDate: '1990-01-10',
        createAccess: true,
        password: '123456',
        confirmPassword: '123456',
      };

      expect(buildPersonMinisterialCreateRequest(value)).toEqual({
        name: 'Maria Pessoa',
        phoneNumber: '34999999999',
        birthdayDate: '1990-01-10',
        createAccess: true,
        password: '123456',
        accessRole: 'ROLE_OPERATOR',
      });
    });

    it('should preserve the password exactly, without trimming it', () => {
      const value: PersonMinisterialFormValue = {
        name: 'Maria Pessoa',
        phoneNumber: '34999999999',
        birthdayDate: '1990-01-10',
        createAccess: true,
        password: ' 123456 ',
        confirmPassword: ' 123456 ',
      };

      const request = buildPersonMinisterialCreateRequest(value) as { password: string };

      expect(request.password).toBe(' 123456 ');
    });

    it('should never include confirmPassword in the request', () => {
      const value: PersonMinisterialFormValue = {
        name: 'Maria Pessoa',
        phoneNumber: '34999999999',
        birthdayDate: '1990-01-10',
        createAccess: true,
        password: '123456',
        confirmPassword: '123456',
      };

      const request = buildPersonMinisterialCreateRequest(value) as unknown as Record<string, unknown>;

      expect(request['confirmPassword']).toBeUndefined();
    });
  });

  describe('buildPersonMinisterialUpdateRequest', () => {
    it('should trim textual cadastral fields and contain only cadastral data', () => {
      const value = {
        name: '  Maria Pessoa  ',
        phoneNumber: ' 34999999999 ',
        birthdayDate: '1990-01-10',
      };

      const request = buildPersonMinisterialUpdateRequest(value) as unknown as Record<string, unknown>;

      expect(request).toEqual({
        name: 'Maria Pessoa',
        phoneNumber: '34999999999',
        birthdayDate: '1990-01-10',
      });
      expect(Object.keys(request)).toEqual(['name', 'phoneNumber', 'birthdayDate']);
    });

    it('should never include account fields even if present on the source object', () => {
      const value = {
        name: 'Maria Pessoa',
        phoneNumber: '34999999999',
        birthdayDate: '1990-01-10',
        createAccess: true,
        password: 'should-not-leak',
        confirmPassword: 'should-not-leak',
        accessRole: 'ROLE_OPERATOR',
      };

      const request = buildPersonMinisterialUpdateRequest(value) as unknown as Record<string, unknown>;

      expect(request['createAccess']).toBeUndefined();
      expect(request['password']).toBeUndefined();
      expect(request['confirmPassword']).toBeUndefined();
      expect(request['accessRole']).toBeUndefined();
    });
  });
});
