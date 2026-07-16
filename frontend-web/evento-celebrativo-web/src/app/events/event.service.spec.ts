import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_BASE_URL } from '../api.config';
import { CelebrationEventRequest, CelebrationEventResponse } from './event.models';
import { EventService } from './event.service';

describe('EventService', () => {
  let service: EventService;
  let httpTestingController: HttpTestingController;

  const events: CelebrationEventResponse[] = [
    {
      id: 1,
      nameMassOrEvent: 'Missa de Domingo',
      eventDate: '2026-08-02',
      eventTime: '10:00:00',
      massOrCelebration: true,
    },
  ];
  const eventRequest: CelebrationEventRequest = {
    nameMassOrEvent: 'Missa de Domingo',
    eventDate: '2026-08-02',
    eventTime: '10:00:00',
    massOrCelebration: true,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });

    service = TestBed.inject(EventService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  it('should request all events from the public events endpoint', () => {
    service.findAll().subscribe((response) => {
      expect(response).toEqual(events);
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/eventos`);

    expect(request.request.method).toBe('GET');

    request.flush(events);
  });

  it('should return an empty list when the API returns no events', () => {
    service.findAll().subscribe((response) => {
      expect(response).toEqual([]);
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/eventos`);

    request.flush([]);
  });

  it('should request an event by id from the public event endpoint', () => {
    const event = events[0];

    service.findById(1).subscribe((response) => {
      expect(response).toEqual(event);
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/eventos/1`);

    expect(request.request.method).toBe('GET');

    request.flush(event);
  });

  it('should propagate not found errors when requesting an event by id', (done) => {
    service.findById(99).subscribe({
      next: () => {
        fail('Expected event request to fail');
      },
      error: (error: unknown) => {
        expect(error).toBeTruthy();
        done();
      },
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/eventos/99`);
    request.flush(
      { errorCode: 'RESOURCE_NOT_FOUND' },
      {
        status: 404,
        statusText: 'Not Found',
      },
    );
  });

  it('should propagate server errors when requesting an event by id', (done) => {
    service.findById(1).subscribe({
      next: () => {
        fail('Expected event request to fail');
      },
      error: (error: unknown) => {
        expect(error).toBeTruthy();
        done();
      },
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/eventos/1`);
    request.flush(
      { message: 'Internal server error' },
      {
        status: 500,
        statusText: 'Server Error',
      },
    );
  });

  it('should propagate HTTP errors', (done) => {
    service.findAll().subscribe({
      next: () => {
        fail('Expected events request to fail');
      },
      error: (error: unknown) => {
        expect(error).toBeTruthy();
        done();
      },
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/eventos`);
    request.flush(
      { message: 'Internal server error' },
      {
        status: 500,
        statusText: 'Server Error',
      },
    );
  });

  it('should create an event without adding authorization manually', () => {
    const createdEvent: CelebrationEventResponse = {
      id: 2,
      ...eventRequest,
    };

    service.create(eventRequest).subscribe((response) => {
      expect(response).toEqual(createdEvent);
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/eventos`);

    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(eventRequest);
    expect(request.request.headers.has('Authorization')).toBeFalse();

    request.flush(createdEvent);
  });

  it('should update an event without adding authorization manually', () => {
    const updatedEvent: CelebrationEventResponse = {
      id: 1,
      ...eventRequest,
    };

    service.update(1, eventRequest).subscribe((response) => {
      expect(response).toEqual(updatedEvent);
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/eventos/1`);

    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual(eventRequest);
    expect(request.request.headers.has('Authorization')).toBeFalse();

    request.flush(updatedEvent);
  });

  it('should delete an event without adding authorization manually', () => {
    service.delete(1).subscribe((response) => {
      expect(response).toBeNull();
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/eventos/1`);

    expect(request.request.method).toBe('DELETE');
    expect(request.request.body).toBeNull();
    expect(request.request.headers.has('Authorization')).toBeFalse();

    request.flush(null);
  });

  [400, 403, 404, 409].forEach((status) => {
    it(`should propagate ${status} errors when creating events`, (done) => {
      service.create(eventRequest).subscribe({
        next: () => {
          fail('Expected create request to fail');
        },
        error: (error: unknown) => {
          expect(error).toBeTruthy();
          done();
        },
      });

      const request = httpTestingController.expectOne(`${API_BASE_URL}/eventos`);
      request.flush({ message: 'Error' }, { status, statusText: 'Error' });
    });

    it(`should propagate ${status} errors when updating events`, (done) => {
      service.update(1, eventRequest).subscribe({
        next: () => {
          fail('Expected update request to fail');
        },
        error: (error: unknown) => {
          expect(error).toBeTruthy();
          done();
        },
      });

      const request = httpTestingController.expectOne(`${API_BASE_URL}/eventos/1`);
      request.flush({ message: 'Error' }, { status, statusText: 'Error' });
    });

    it(`should propagate ${status} errors when deleting events`, (done) => {
      service.delete(1).subscribe({
        next: () => {
          fail('Expected delete request to fail');
        },
        error: (error: unknown) => {
          expect(error).toBeTruthy();
          done();
        },
      });

      const request = httpTestingController.expectOne(`${API_BASE_URL}/eventos/1`);
      request.flush({ message: 'Error' }, { status, statusText: 'Error' });
    });
  });
});
