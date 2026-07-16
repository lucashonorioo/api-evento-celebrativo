import { HttpErrorResponse } from '@angular/common/http';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_BASE_URL } from '../api.config';
import {
  EucharisticMinisterRequest,
  EucharisticMinisterResponse,
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
  const ministerRequest: EucharisticMinisterRequest = {
    name: 'Ana Ministra',
    phoneNumber: '34999999991',
    birthdayDate: '1980-01-15',
    password: '123456',
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

  it('should request all eucharistic ministers from the backend', () => {
    service.findAll().subscribe((response) => {
      expect(response).toEqual(ministers);
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/ministrosDeEucaristia`);

    expect(request.request.method).toBe('GET');
    expect(request.request.headers.has('Authorization')).toBeFalse();

    request.flush(ministers);
  });

  it('should return an empty list when there are no eucharistic ministers', () => {
    service.findAll().subscribe((response) => {
      expect(response).toEqual([]);
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/ministrosDeEucaristia`);

    request.flush([]);
  });

  it('should propagate HTTP errors', () => {
    service.findAll().subscribe({
      next: () => fail('Expected request to fail.'),
      error: (error: HttpErrorResponse) => {
        expect(error.status).toBe(500);
      },
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/ministrosDeEucaristia`);

    request.flush(
      { message: 'Unexpected error' },
      {
        status: 500,
        statusText: 'Server Error',
      },
    );
  });

  it('should create a eucharistic minister without adding authorization manually', () => {
    const createdMinister: EucharisticMinisterResponse = {
      id: 3,
      name: ministerRequest.name,
      phoneNumber: ministerRequest.phoneNumber,
      birthdayDate: ministerRequest.birthdayDate,
    };

    service.create(ministerRequest).subscribe((response) => {
      expect(response).toEqual(createdMinister);
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/ministrosDeEucaristia`);

    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(ministerRequest);
    expect(request.request.headers.has('Authorization')).toBeFalse();

    request.flush(createdMinister);
  });

  it('should update a eucharistic minister without adding authorization manually', () => {
    const updatedMinister: EucharisticMinisterResponse = {
      id: 1,
      name: ministerRequest.name,
      phoneNumber: ministerRequest.phoneNumber,
      birthdayDate: ministerRequest.birthdayDate,
    };

    service.update(1, ministerRequest).subscribe((response) => {
      expect(response).toEqual(updatedMinister);
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/ministrosDeEucaristia/1`);

    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual(ministerRequest);
    expect(request.request.headers.has('Authorization')).toBeFalse();

    request.flush(updatedMinister);
  });

  it('should delete a eucharistic minister without adding authorization manually', () => {
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
    it(`should propagate ${status} errors when creating eucharistic ministers`, (done) => {
      service.create(ministerRequest).subscribe({
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
      service.update(1, ministerRequest).subscribe({
        next: () => {
          fail('Expected update request to fail');
        },
        error: (error: unknown) => {
          expect(error).toBeTruthy();
          done();
        },
      });

      const request = httpTestingController.expectOne(
        `${API_BASE_URL}/ministrosDeEucaristia/1`,
      );
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

      const request = httpTestingController.expectOne(
        `${API_BASE_URL}/ministrosDeEucaristia/1`,
      );
      request.flush({ message: 'Error' }, { status, statusText: 'Error' });
    });
  });
});
