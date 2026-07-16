import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_BASE_URL } from '../api.config';
import {
  MinisterOfTheWordRequest,
  MinisterOfTheWordResponse,
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
  const ministerRequest: MinisterOfTheWordRequest = {
    name: 'Maria Ministra',
    phoneNumber: '34999999994',
    birthdayDate: '1985-04-13',
    password: '123456',
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

  it('should create a minister of the Word without adding authorization manually', () => {
    const createdMinister: MinisterOfTheWordResponse = {
      id: 2,
      name: ministerRequest.name,
      phoneNumber: ministerRequest.phoneNumber,
      birthdayDate: ministerRequest.birthdayDate,
    };

    service.create(ministerRequest).subscribe((response) => {
      expect(response).toEqual(createdMinister);
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/ministrosDaPalavra`);

    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(ministerRequest);
    expect(request.request.headers.has('Authorization')).toBeFalse();

    request.flush(createdMinister);
  });

  it('should update a minister of the Word without adding authorization manually', () => {
    const updatedMinister: MinisterOfTheWordResponse = {
      id: 1,
      name: ministerRequest.name,
      phoneNumber: ministerRequest.phoneNumber,
      birthdayDate: ministerRequest.birthdayDate,
    };

    service.update(1, ministerRequest).subscribe((response) => {
      expect(response).toEqual(updatedMinister);
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/ministrosDaPalavra/1`);

    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual(ministerRequest);
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
    it(`should propagate ${status} errors when creating ministers of the Word`, (done) => {
      service.create(ministerRequest).subscribe({
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
      service.update(1, ministerRequest).subscribe({
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
