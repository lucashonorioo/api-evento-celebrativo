import { FormControl } from '@angular/forms';

import {
  notBlankValidator,
  pastDateValidator,
  personPasswordValidators,
  personPhoneNumberValidators,
} from './person-form.validators';

describe('person form validators', () => {
  it('should reject blank text values', () => {
    const control = new FormControl('   ');

    expect(notBlankValidator(control)).toEqual({ blank: true });

    control.setValue('Maria Pessoa');

    expect(notBlankValidator(control)).toBeNull();
  });

  it('should validate phone numbers with exactly 11 digits', () => {
    const control = new FormControl('', personPhoneNumberValidators());

    control.setValue('3499999999');
    expect(control.hasError('minlength')).toBeTrue();

    control.setValue('349999999999');
    expect(control.hasError('maxlength')).toBeTrue();

    control.setValue('34999999999');
    expect(control.valid).toBeTrue();
  });

  it('should validate password minimum length', () => {
    const control = new FormControl('', personPasswordValidators());

    control.setValue('12345');
    expect(control.hasError('minlength')).toBeTrue();

    control.setValue('123456');
    expect(control.valid).toBeTrue();
  });

  it('should validate birthday dates in the past without parsing timezone-sensitive dates', () => {
    jasmine.clock().install();

    try {
      jasmine.clock().mockDate(new Date(2026, 6, 16, 8, 0, 0));

      const control = new FormControl('2026-07-15');

      expect(pastDateValidator(control)).toBeNull();

      control.setValue('2026-07-16');
      expect(pastDateValidator(control)).toEqual({ pastDate: true });

      control.setValue('2026-07-17');
      expect(pastDateValidator(control)).toEqual({ pastDate: true });
    } finally {
      jasmine.clock().uninstall();
    }
  });
});
