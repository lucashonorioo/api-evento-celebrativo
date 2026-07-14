import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_BASE_URL } from '../api.config';
import { LocationResponse } from './location.models';
import { LocationService } from './location.service';

describe('LocationService', () => {
  let service: LocationService;
  let httpTestingController: HttpTestingController;

  const locations: LocationResponse[] = [
    {
      id: 1,
      churchName: 'Igreja Matriz',
      address: 'Rua Central',
    },
  ];

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });

    service = TestBed.inject(LocationService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  it('should request all locations from the authenticated endpoint', () => {
    service.findAll().subscribe((response) => {
      expect(response).toEqual(locations);
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/locais`);

    expect(request.request.method).toBe('GET');
    expect(request.request.headers.has('Authorization')).toBeFalse();

    request.flush(locations);
  });

  it('should return an empty list when the API returns no locations', () => {
    service.findAll().subscribe((response) => {
      expect(response).toEqual([]);
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/locais`);

    request.flush([]);
  });

  it('should propagate HTTP errors', (done) => {
    service.findAll().subscribe({
      next: () => {
        fail('Expected locations request to fail');
      },
      error: (error: unknown) => {
        expect(error).toBeTruthy();
        done();
      },
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/locais`);
    request.flush(
      { message: 'Forbidden' },
      {
        status: 403,
        statusText: 'Forbidden',
      },
    );
  });
});
