import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_BASE_URL } from '../api.config';
import { PriestRequest, PriestResponse } from './priest.models';
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
  const priestRequest: PriestRequest = {
    name: 'Padre Joao',
    phoneNumber: '34999999993',
    birthdayDate: '1980-03-12',
    password: '123456',
  };

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

  it('should create a priest without adding authorization manually', () => {
    const createdPriest: PriestResponse = {
      id: 2,
      name: priestRequest.name,
      phoneNumber: priestRequest.phoneNumber,
      birthdayDate: priestRequest.birthdayDate,
    };

    service.create(priestRequest).subscribe((response) => {
      expect(response).toEqual(createdPriest);
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/padres`);

    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(priestRequest);
    expect(request.request.headers.has('Authorization')).toBeFalse();

    request.flush(createdPriest);
  });

  it('should update a priest without adding authorization manually', () => {
    const updatedPriest: PriestResponse = {
      id: 1,
      name: priestRequest.name,
      phoneNumber: priestRequest.phoneNumber,
      birthdayDate: priestRequest.birthdayDate,
    };

    service.update(1, priestRequest).subscribe((response) => {
      expect(response).toEqual(updatedPriest);
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/padres/1`);

    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual(priestRequest);
    expect(request.request.headers.has('Authorization')).toBeFalse();

    request.flush(updatedPriest);
  });

  it('should delete a priest without adding authorization manually', () => {
    service.delete(1).subscribe((response) => {
      expect(response).toBeNull();
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/padres/1`);

    expect(request.request.method).toBe('DELETE');
    expect(request.request.body).toBeNull();
    expect(request.request.headers.has('Authorization')).toBeFalse();

    request.flush(null);
  });

  [400, 403, 404, 409].forEach((status) => {
    it(`should propagate ${status} errors when creating priests`, (done) => {
      service.create(priestRequest).subscribe({
        next: () => {
          fail('Expected create request to fail');
        },
        error: (error: unknown) => {
          expect(error).toBeTruthy();
          done();
        },
      });

      const request = httpTestingController.expectOne(`${API_BASE_URL}/padres`);
      request.flush({ message: 'Error' }, { status, statusText: 'Error' });
    });

    it(`should propagate ${status} errors when updating priests`, (done) => {
      service.update(1, priestRequest).subscribe({
        next: () => {
          fail('Expected update request to fail');
        },
        error: (error: unknown) => {
          expect(error).toBeTruthy();
          done();
        },
      });

      const request = httpTestingController.expectOne(`${API_BASE_URL}/padres/1`);
      request.flush({ message: 'Error' }, { status, statusText: 'Error' });
    });

    it(`should propagate ${status} errors when deleting priests`, (done) => {
      service.delete(1).subscribe({
        next: () => {
          fail('Expected delete request to fail');
        },
        error: (error: unknown) => {
          expect(error).toBeTruthy();
          done();
        },
      });

      const request = httpTestingController.expectOne(`${API_BASE_URL}/padres/1`);
      request.flush({ message: 'Error' }, { status, statusText: 'Error' });
    });
  });
});
