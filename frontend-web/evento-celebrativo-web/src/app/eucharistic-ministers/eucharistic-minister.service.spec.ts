import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_BASE_URL } from '../api.config';
import {
  EucharisticMinisterCreateRequest,
  EucharisticMinisterResponse,
  EucharisticMinisterUpdateRequest,
} from './eucharistic-minister.models';
import { EucharisticMinisterService } from './eucharistic-minister.service';

describe('EucharisticMinisterService', () => {
  let service: EucharisticMinisterService;
  let httpTestingController: HttpTestingController;

  const ministers: EucharisticMinisterResponse[] = [
    {
      id: 1,
      name: 'Ana Ministra',
      phoneNumber: '34999999991',
      birthdayDate: '1980-01-15',
    },
    {
      id: 2,
      name: 'Carlos Ministro',
      phoneNumber: null,
      birthdayDate: null,
    },
  ];
  const createRequestWithoutAccess: EucharisticMinisterCreateRequest = {
    name: 'Ana Ministra',
    phoneNumber: '34999999991',
    birthdayDate: '1980-01-15',
    createAccess: false,
  };
  const createRequestWithAccess: EucharisticMinisterCreateRequest = {
    name: 'Ana Ministra',
    phoneNumber: '34999999991',
    birthdayDate: '1980-01-15',
    createAccess: true,
    password: '123456',
    accessRole: 'ROLE_OPERATOR',
  };
  const updateRequest: EucharisticMinisterUpdateRequest = {
    name: 'Ana Ministra',
    phoneNumber: '34999999991',
    birthdayDate: '1980-01-15',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });

    service = TestBed.inject(EucharisticMinisterService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  it('should request all eucharistic ministers from the authenticated endpoint', () => {
    service.findAll().subscribe((response) => {
      expect(response).toEqual(ministers);
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/ministrosDeEucaristia`);

    expect(request.request.method).toBe('GET');
    expect(request.request.headers.has('Authorization')).toBeFalse();

    request.flush(ministers);
  });

  it('should return an empty list when the API returns no eucharistic ministers', () => {
    service.findAll().subscribe((response) => {
      expect(response).toEqual([]);
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/ministrosDeEucaristia`);

    request.flush([]);
  });

  it('should propagate HTTP errors', (done) => {
    service.findAll().subscribe({
      next: () => {
        fail('Expected eucharistic ministers request to fail');
      },
      error: (error: unknown) => {
        expect(error).toBeTruthy();
        done();
      },
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/ministrosDeEucaristia`);
    request.flush(
      { message: 'Forbidden' },
      {
        status: 403,
        statusText: 'Forbidden',
      },
    );
  });

  it('should create an eucharistic minister without access, sending createAccess=false and no credentials', () => {
    const createdMinister: EucharisticMinisterResponse = {
      id: 3,
      name: createRequestWithoutAccess.name,
      phoneNumber: createRequestWithoutAccess.phoneNumber,
      birthdayDate: createRequestWithoutAccess.birthdayDate,
    };

    service.create(createRequestWithoutAccess).subscribe((response) => {
      expect(response).toEqual(createdMinister);
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/ministrosDeEucaristia`);

    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(createRequestWithoutAccess);
    expect(request.request.body.password).toBeUndefined();
    expect(request.request.body.accessRole).toBeUndefined();
    expect(request.request.body.confirmPassword).toBeUndefined();
    expect(request.request.headers.has('Authorization')).toBeFalse();

    request.flush(createdMinister);
  });

  it('should create an eucharistic minister with access, sending createAccess=true, password and ROLE_OPERATOR', () => {
    const createdMinister: EucharisticMinisterResponse = {
      id: 3,
      name: createRequestWithAccess.name,
      phoneNumber: createRequestWithAccess.phoneNumber,
      birthdayDate: createRequestWithAccess.birthdayDate,
    };

    service.create(createRequestWithAccess).subscribe((response) => {
      expect(response).toEqual(createdMinister);
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/ministrosDeEucaristia`);

    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(createRequestWithAccess);
    expect(request.request.body.accessRole).toBe('ROLE_OPERATOR');
    expect(request.request.body.confirmPassword).toBeUndefined();
    expect(request.request.headers.has('Authorization')).toBeFalse();

    request.flush(createdMinister);
  });

  it('should update an eucharistic minister sending only cadastral fields, without account fields', () => {
    const updatedMinister: EucharisticMinisterResponse = {
      id: 1,
      name: updateRequest.name,
      phoneNumber: updateRequest.phoneNumber,
      birthdayDate: updateRequest.birthdayDate,
    };

    service.update(1, updateRequest).subscribe((response) => {
      expect(response).toEqual(updatedMinister);
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/ministrosDeEucaristia/1`);

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

  it('should delete an eucharistic minister without adding authorization manually', () => {
    service.delete(1).subscribe((response) => {
      expect(response).toBeNull();
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/ministrosDeEucaristia/1`);

    expect(request.request.method).toBe('DELETE');
    expect(request.request.body).toBeNull();
    expect(request.request.headers.has('Authorization')).toBeFalse();

    request.flush(null);
  });

  [400, 403, 404, 409].forEach((status) => {
    it(`should propagate ${status} errors when creating eucharistic ministers without access`, (done) => {
      service.create(createRequestWithoutAccess).subscribe({
        next: () => {
          fail('Expected create request to fail');
        },
        error: (error: unknown) => {
          expect(error).toBeTruthy();
          done();
        },
      });

      const request = httpTestingController.expectOne(`${API_BASE_URL}/ministrosDeEucaristia`);
      request.flush({ message: 'Error' }, { status, statusText: 'Error' });
    });

    it(`should propagate ${status} errors when creating eucharistic ministers with access`, (done) => {
      service.create(createRequestWithAccess).subscribe({
        next: () => {
          fail('Expected create request to fail');
        },
        error: (error: unknown) => {
          expect(error).toBeTruthy();
          done();
        },
      });

      const request = httpTestingController.expectOne(`${API_BASE_URL}/ministrosDeEucaristia`);
      request.flush({ message: 'Error' }, { status, statusText: 'Error' });
    });

    it(`should propagate ${status} errors when updating eucharistic ministers`, (done) => {
      service.update(1, updateRequest).subscribe({
        next: () => {
          fail('Expected update request to fail');
        },
        error: (error: unknown) => {
          expect(error).toBeTruthy();
          done();
        },
      });

      const request = httpTestingController.expectOne(`${API_BASE_URL}/ministrosDeEucaristia/1`);
      request.flush({ message: 'Error' }, { status, statusText: 'Error' });
    });

    it(`should propagate ${status} errors when deleting eucharistic ministers`, (done) => {
      service.delete(1).subscribe({
        next: () => {
          fail('Expected delete request to fail');
        },
        error: (error: unknown) => {
          expect(error).toBeTruthy();
          done();
        },
      });

      const request = httpTestingController.expectOne(`${API_BASE_URL}/ministrosDeEucaristia/1`);
      request.flush({ message: 'Error' }, { status, statusText: 'Error' });
    });
  });
});
