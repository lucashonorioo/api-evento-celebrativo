import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_BASE_URL } from '../api.config';
import { EventSchedulePage } from './event-schedule.models';
import { EventScheduleService } from './event-schedule.service';

describe('EventScheduleService', () => {
  let service: EventScheduleService;
  let httpTestingController: HttpTestingController;

  const page: EventSchedulePage = {
    content: [
      {
        eventId: 10,
        eventName: 'Missa Dominical',
        eventDate: '2026-07-20',
        eventTime: '19:00:00',
        massOrCelebration: true,
        locationId: 2,
        churchName: 'Igreja Matriz',
        assignmentType: 'READER',
        assignments: [{ personId: 15, personName: 'Maria da Silva' }],
      },
    ],
    totalPages: 1,
    totalElements: 1,
    first: true,
    last: true,
    size: 10,
    number: 0,
    numberOfElements: 1,
    empty: false,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });

    service = TestBed.inject(EventScheduleService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  it('should request monthly schedules with type, period, pagination and unassigned flag', () => {
    service
      .findMonthlySchedules({
        startDate: '2026-07-01',
        endDate: '2026-07-31',
        type: 'READER',
        page: 0,
        size: 10,
        includeUnassigned: false,
      })
      .subscribe((response) => {
        expect(response).toEqual(page);
      });

    const request = httpTestingController.expectOne(
      (req) => req.url === `${API_BASE_URL}/eventos/escalas`,
    );

    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('startDate')).toBe('2026-07-01');
    expect(request.request.params.get('endDate')).toBe('2026-07-31');
    expect(request.request.params.get('type')).toBe('READER');
    expect(request.request.params.get('page')).toBe('0');
    expect(request.request.params.get('size')).toBe('10');
    expect(request.request.params.get('includeUnassigned')).toBe('false');

    request.flush(page);
  });

  it('should preserve selected filters when requesting another page', () => {
    service
      .findMonthlySchedules({
        startDate: '2026-08-01',
        endDate: '2026-08-31',
        type: 'EUCHARISTIC_MINISTER',
        page: 2,
        size: 10,
        includeUnassigned: true,
      })
      .subscribe();

    const request = httpTestingController.expectOne(
      (req) => req.url === `${API_BASE_URL}/eventos/escalas`,
    );

    expect(request.request.params.get('startDate')).toBe('2026-08-01');
    expect(request.request.params.get('endDate')).toBe('2026-08-31');
    expect(request.request.params.get('type')).toBe('EUCHARISTIC_MINISTER');
    expect(request.request.params.get('page')).toBe('2');
    expect(request.request.params.get('size')).toBe('10');
    expect(request.request.params.get('includeUnassigned')).toBe('true');

    request.flush({ ...page, number: 2 });
  });

  it('should propagate HTTP errors', (done) => {
    service
      .findMonthlySchedules({
        startDate: '2026-07-01',
        endDate: '2026-07-31',
        type: 'READER',
        page: 0,
        size: 10,
        includeUnassigned: false,
      })
      .subscribe({
        next: () => {
          fail('Expected monthly schedule request to fail');
        },
        error: (error: unknown) => {
          expect(error).toBeTruthy();
          done();
        },
      });

    const request = httpTestingController.expectOne(
      (req) => req.url === `${API_BASE_URL}/eventos/escalas`,
    );
    request.flush(
      { message: 'Forbidden' },
      {
        status: 403,
        statusText: 'Forbidden',
      },
    );
  });
});
