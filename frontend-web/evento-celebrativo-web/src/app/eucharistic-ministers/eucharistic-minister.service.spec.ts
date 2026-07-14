import { HttpErrorResponse } from '@angular/common/http';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_BASE_URL } from '../api.config';
import { EucharisticMinisterResponse } from './eucharistic-minister.models';
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
});
