import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_BASE_URL } from '../api.config';
import { ReaderResponse } from './reader.models';
import { ReaderService } from './reader.service';

describe('ReaderService', () => {
  let service: ReaderService;
  let httpTestingController: HttpTestingController;

  const readers: ReaderResponse[] = [
    {
      id: 1,
      name: 'Maria Leitora',
      phoneNumber: '34999999991',
      birthdayDate: '1990-01-10',
    },
  ];

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });

    service = TestBed.inject(ReaderService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  it('should request all readers from the authenticated endpoint', () => {
    service.findAll().subscribe((response) => {
      expect(response).toEqual(readers);
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/leitores`);

    expect(request.request.method).toBe('GET');
    expect(request.request.headers.has('Authorization')).toBeFalse();

    request.flush(readers);
  });

  it('should return an empty list when the API returns no readers', () => {
    service.findAll().subscribe((response) => {
      expect(response).toEqual([]);
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/leitores`);

    request.flush([]);
  });

  it('should propagate HTTP errors', (done) => {
    service.findAll().subscribe({
      next: () => {
        fail('Expected readers request to fail');
      },
      error: (error: unknown) => {
        expect(error).toBeTruthy();
        done();
      },
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/leitores`);
    request.flush(
      { message: 'Forbidden' },
      {
        status: 403,
        statusText: 'Forbidden',
      },
    );
  });
});
