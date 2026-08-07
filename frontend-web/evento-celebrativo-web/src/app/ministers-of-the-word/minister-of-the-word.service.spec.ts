import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_BASE_URL } from '../api.config';
import {
  MinisterOfTheWordCreateRequest,
  MinisterOfTheWordResponse,
  MinisterOfTheWordUpdateRequest,
} from './minister-of-the-word.models';
import { MinisterOfTheWordService } from './minister-of-the-word.service';

describe('MinisterOfTheWordService', () => {
  let service: MinisterOfTheWordService;
  let httpTestingController: HttpTestingController;

  const ministers: MinisterOfTheWordResponse[] = [
    {
      id: 1,
      name: 'Maria Ministra',
      phoneNumber: '34999999994',
      birthdayDate: '1985-04-13',
    },
  ];
  const createRequestWithoutAccess: MinisterOfTheWordCreateRequest = {
    name: 'Maria Ministra',
    phoneNumber: '34999999994',
    birthdayDate: '1985-04-13',
    createAccess: false,
  };
  const createRequestWithAccess: MinisterOfTheWordCreateRequest = {
    name: 'Maria Ministra',
    phoneNumber: '34999999994',
    birthdayDate: '1985-04-13',
    createAccess: true,
    password: '123456',
    accessRole: 'ROLE_OPERATOR',
  };
  const updateRequest: MinisterOfTheWordUpdateRequest = {
    name: 'Maria Ministra',
    phoneNumber: '34999999994',
    birthdayDate: '1985-04-13',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });

    service = TestBed.inject(MinisterOfTheWordService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  it('should request all ministers of the Word from the authenticated endpoint', () => {
    service.findAll().subscribe((response) => {
      expect(response).toEqual(ministers);
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/ministrosDaPalavra`);

    expect(request.request.method).toBe('GET');
    expect(request.request.headers.has('Authorization')).toBeFalse();

    request.flush(ministers);
  });

  it('should return an empty list when the API returns no ministers of the Word', () => {
    service.findAll().subscribe((response) => {
      expect(response).toEqual([]);
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/ministrosDaPalavra`);

    request.flush([]);
  });

  it('should propagate HTTP errors', (done) => {
    service.findAll().subscribe({
      next: () => {
        fail('Expected ministers of the Word request to fail');
      },
      error: (error: unknown) => {
        expect(error).toBeTruthy();
        done();
      },
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/ministrosDaPalavra`);
    request.flush(
      { message: 'Forbidden' },
      {
        status: 403,
        statusText: 'Forbidden',
      },
    );
  });

  it('should create a minister of the Word without access, sending createAccess=false and no credentials', () => {
    const createdMinister: MinisterOfTheWordResponse = {
      id: 2,
      name: createRequestWithoutAccess.name,
      phoneNumber: createRequestWithoutAccess.phoneNumber,
      birthdayDate: createRequestWithoutAccess.birthdayDate,
    };

    service.create(createRequestWithoutAccess).subscribe((response) => {
      expect(response).toEqual(createdMinister);
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/ministrosDaPalavra`);

    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(createRequestWithoutAccess);
    expect(request.request.body.password).toBeUndefined();
    expect(request.request.body.accessRole).toBeUndefined();
    expect(request.request.body.confirmPassword).toBeUndefined();
    expect(request.request.headers.has('Authorization')).toBeFalse();

    request.flush(createdMinister);
  });

  it('should create a minister of the Word with access, sending createAccess=true, password and ROLE_OPERATOR', () => {
    const createdMinister: MinisterOfTheWordResponse = {
      id: 2,
      name: createRequestWithAccess.name,
      phoneNumber: createRequestWithAccess.phoneNumber,
      birthdayDate: createRequestWithAccess.birthdayDate,
    };

    service.create(createRequestWithAccess).subscribe((response) => {
      expect(response).toEqual(createdMinister);
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/ministrosDaPalavra`);

    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(createRequestWithAccess);
    expect(request.request.body.accessRole).toBe('ROLE_OPERATOR');
    expect(request.request.body.confirmPassword).toBeUndefined();
    expect(request.request.headers.has('Authorization')).toBeFalse();

    request.flush(createdMinister);
  });

  it('should update a minister of the Word sending only cadastral fields, without account fields', () => {
    const updatedMinister: MinisterOfTheWordResponse = {
      id: 1,
      name: updateRequest.name,
      phoneNumber: updateRequest.phoneNumber,
      birthdayDate: updateRequest.birthdayDate,
    };

    service.update(1, updateRequest).subscribe((response) => {
      expect(response).toEqual(updatedMinister);
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/ministrosDaPalavra/1`);

    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual(updateRequest);
    expect(Object.keys(request.request.body)).toEqual(['name', 'phoneNumber', 'birthdayDate']);
    expect(request.request.body.password).toBeUndefined();
    expect(request.request.body.createAccess).toBeUndefined();
    expect(request.request.body.accessRole).toBeUndefined();
    expect(request.request.body.confirmPassword).toBeUndefined();
    expect(request.request.headers.has('Authorization')).toBeFalse();

    request.flush(updatedMinister);
  });

  it('should delete a minister of the Word without adding authorization manually', () => {
    service.delete(1).subscribe((response) => {
      expect(response).toBeNull();
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/ministrosDaPalavra/1`);

    expect(request.request.method).toBe('DELETE');
    expect(request.request.body).toBeNull();
    expect(request.request.headers.has('Authorization')).toBeFalse();

    request.flush(null);
  });

  [400, 403, 404, 409].forEach((status) => {
    it(`should propagate ${status} errors when creating ministers of the Word without access`, (done) => {
      service.create(createRequestWithoutAccess).subscribe({
        next: () => {
          fail('Expected create request to fail');
        },
        error: (error: unknown) => {
          expect(error).toBeTruthy();
          done();
        },
      });

      const request = httpTestingController.expectOne(`${API_BASE_URL}/ministrosDaPalavra`);
      request.flush({ message: 'Error' }, { status, statusText: 'Error' });
    });

    it(`should propagate ${status} errors when creating ministers of the Word with access`, (done) => {
      service.create(createRequestWithAccess).subscribe({
        next: () => {
          fail('Expected create request to fail');
        },
        error: (error: unknown) => {
          expect(error).toBeTruthy();
          done();
        },
      });

      const request = httpTestingController.expectOne(`${API_BASE_URL}/ministrosDaPalavra`);
      request.flush({ message: 'Error' }, { status, statusText: 'Error' });
    });

    it(`should propagate ${status} errors when updating ministers of the Word`, (done) => {
      service.update(1, updateRequest).subscribe({
        next: () => {
          fail('Expected update request to fail');
        },
        error: (error: unknown) => {
          expect(error).toBeTruthy();
          done();
        },
      });

      const request = httpTestingController.expectOne(`${API_BASE_URL}/ministrosDaPalavra/1`);
      request.flush({ message: 'Error' }, { status, statusText: 'Error' });
    });

    it(`should propagate ${status} errors when deleting ministers of the Word`, (done) => {
      service.delete(1).subscribe({
        next: () => {
          fail('Expected delete request to fail');
        },
        error: (error: unknown) => {
          expect(error).toBeTruthy();
          done();
        },
      });

      const request = httpTestingController.expectOne(`${API_BASE_URL}/ministrosDaPalavra/1`);
      request.flush({ message: 'Error' }, { status, statusText: 'Error' });
    });
  });
});
