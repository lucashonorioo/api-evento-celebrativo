import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_BASE_URL } from '../api.config';
import { CelebrationEventResponse } from './event.models';
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
});
