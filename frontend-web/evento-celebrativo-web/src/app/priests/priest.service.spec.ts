import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_BASE_URL } from '../api.config';
import { PriestResponse } from './priest.models';
import { PriestService } from './priest.service';

describe('PriestService', () => {
  let service: PriestService;
  let httpTestingController: HttpTestingController;

  const priests: PriestResponse[] = [
    {
      id: 1,
      name: 'Padre João',
      phoneNumber: '34999999993',
      birthdayDate: '1980-03-12',
    },
  ];

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });

    service = TestBed.inject(PriestService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  it('should request all priests from the authenticated endpoint', () => {
    service.findAll().subscribe((response) => {
      expect(response).toEqual(priests);
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/padres`);

    expect(request.request.method).toBe('GET');
    expect(request.request.headers.has('Authorization')).toBeFalse();

    request.flush(priests);
  });

  it('should return an empty list when the API returns no priests', () => {
    service.findAll().subscribe((response) => {
      expect(response).toEqual([]);
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/padres`);

    request.flush([]);
  });

  it('should propagate HTTP errors', (done) => {
    service.findAll().subscribe({
      next: () => {
        fail('Expected priests request to fail');
      },
      error: (error: unknown) => {
        expect(error).toBeTruthy();
        done();
      },
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/padres`);
    request.flush(
      { message: 'Forbidden' },
      {
        status: 403,
        statusText: 'Forbidden',
      },
    );
  });
});
