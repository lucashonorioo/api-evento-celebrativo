import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_BASE_URL } from '../api.config';
import { LocationRequest, LocationResponse } from './location.models';
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
  const locationRequest: LocationRequest = {
    churchName: 'Igreja Matriz',
    address: 'Rua Central',
  };

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

  it('should create a location without adding authorization manually', () => {
    const createdLocation: LocationResponse = {
      id: 2,
      ...locationRequest,
    };

    service.create(locationRequest).subscribe((response) => {
      expect(response).toEqual(createdLocation);
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/locais`);

    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(locationRequest);
    expect(request.request.headers.has('Authorization')).toBeFalse();

    request.flush(createdLocation);
  });

  it('should update a location without adding authorization manually', () => {
    const updatedLocation: LocationResponse = {
      id: 1,
      ...locationRequest,
    };

    service.update(1, locationRequest).subscribe((response) => {
      expect(response).toEqual(updatedLocation);
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/locais/1`);

    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual(locationRequest);
    expect(request.request.headers.has('Authorization')).toBeFalse();

    request.flush(updatedLocation);
  });

  it('should delete a location without adding authorization manually', () => {
    service.delete(1).subscribe((response) => {
      expect(response).toBeNull();
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/locais/1`);

    expect(request.request.method).toBe('DELETE');
    expect(request.request.body).toBeNull();
    expect(request.request.headers.has('Authorization')).toBeFalse();

    request.flush(null);
  });

  [400, 403, 404, 409].forEach((status) => {
    it(`should propagate ${status} errors when creating locations`, (done) => {
      service.create(locationRequest).subscribe({
        next: () => {
          fail('Expected create request to fail');
        },
        error: (error: unknown) => {
          expect(error).toBeTruthy();
          done();
        },
      });

      const request = httpTestingController.expectOne(`${API_BASE_URL}/locais`);
      request.flush({ message: 'Error' }, { status, statusText: 'Error' });
    });

    it(`should propagate ${status} errors when updating locations`, (done) => {
      service.update(1, locationRequest).subscribe({
        next: () => {
          fail('Expected update request to fail');
        },
        error: (error: unknown) => {
          expect(error).toBeTruthy();
          done();
        },
      });

      const request = httpTestingController.expectOne(`${API_BASE_URL}/locais/1`);
      request.flush({ message: 'Error' }, { status, statusText: 'Error' });
    });

    it(`should propagate ${status} errors when deleting locations`, (done) => {
      service.delete(1).subscribe({
        next: () => {
          fail('Expected delete request to fail');
        },
        error: (error: unknown) => {
          expect(error).toBeTruthy();
          done();
        },
      });

      const request = httpTestingController.expectOne(`${API_BASE_URL}/locais/1`);
      request.flush({ message: 'Error' }, { status, statusText: 'Error' });
    });
  });
});
