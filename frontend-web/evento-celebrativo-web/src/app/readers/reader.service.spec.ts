import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_BASE_URL } from '../api.config';
import { ReaderRequest, ReaderResponse } from './reader.models';
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
  const readerRequest: ReaderRequest = {
    name: 'Maria Leitora',
    phoneNumber: '34999999991',
    birthdayDate: '1990-01-10',
    password: '123456',
  };

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

  it('should create a reader without adding authorization manually', () => {
    const createdReader: ReaderResponse = {
      id: 2,
      name: readerRequest.name,
      phoneNumber: readerRequest.phoneNumber,
      birthdayDate: readerRequest.birthdayDate,
    };

    service.create(readerRequest).subscribe((response) => {
      expect(response).toEqual(createdReader);
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/leitores`);

    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(readerRequest);
    expect(request.request.headers.has('Authorization')).toBeFalse();

    request.flush(createdReader);
  });

  it('should update a reader without adding authorization manually', () => {
    const updatedReader: ReaderResponse = {
      id: 1,
      name: readerRequest.name,
      phoneNumber: readerRequest.phoneNumber,
      birthdayDate: readerRequest.birthdayDate,
    };

    service.update(1, readerRequest).subscribe((response) => {
      expect(response).toEqual(updatedReader);
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/leitores/1`);

    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual(readerRequest);
    expect(request.request.headers.has('Authorization')).toBeFalse();

    request.flush(updatedReader);
  });

  it('should delete a reader without adding authorization manually', () => {
    service.delete(1).subscribe((response) => {
      expect(response).toBeNull();
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/leitores/1`);

    expect(request.request.method).toBe('DELETE');
    expect(request.request.body).toBeNull();
    expect(request.request.headers.has('Authorization')).toBeFalse();

    request.flush(null);
  });

  [400, 403, 404, 409].forEach((status) => {
    it(`should propagate ${status} errors when creating readers`, (done) => {
      service.create(readerRequest).subscribe({
        next: () => {
          fail('Expected create request to fail');
        },
        error: (error: unknown) => {
          expect(error).toBeTruthy();
          done();
        },
      });

      const request = httpTestingController.expectOne(`${API_BASE_URL}/leitores`);
      request.flush({ message: 'Error' }, { status, statusText: 'Error' });
    });

    it(`should propagate ${status} errors when updating readers`, (done) => {
      service.update(1, readerRequest).subscribe({
        next: () => {
          fail('Expected update request to fail');
        },
        error: (error: unknown) => {
          expect(error).toBeTruthy();
          done();
        },
      });

      const request = httpTestingController.expectOne(`${API_BASE_URL}/leitores/1`);
      request.flush({ message: 'Error' }, { status, statusText: 'Error' });
    });

    it(`should propagate ${status} errors when deleting readers`, (done) => {
      service.delete(1).subscribe({
        next: () => {
          fail('Expected delete request to fail');
        },
        error: (error: unknown) => {
          expect(error).toBeTruthy();
          done();
        },
      });

      const request = httpTestingController.expectOne(`${API_BASE_URL}/leitores/1`);
      request.flush({ message: 'Error' }, { status, statusText: 'Error' });
    });
  });
});
