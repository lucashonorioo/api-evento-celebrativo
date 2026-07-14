import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_BASE_URL } from '../api.config';
import { MinisterOfTheWordResponse } from './minister-of-the-word.models';
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
});
