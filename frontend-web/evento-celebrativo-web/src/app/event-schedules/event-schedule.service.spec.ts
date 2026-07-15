import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_BASE_URL } from '../api.config';
import {
  EventScheduleDetailResponse,
  EventSchedulePage,
  UpdateEventScheduleRequest,
  UpdateEventScheduleResponse,
} from './event-schedule.models';
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
  const detail: EventScheduleDetailResponse = {
    eventId: 1,
    eventName: 'Missa de Domingo da manha',
    eventDate: '2025-07-13',
    eventTime: '10:00:00',
    massOrCelebration: true,
    location: {
      id: 1,
      churchName: 'Igreja Matriz Nossa Senhora do Rosario',
    },
    priest: {
      id: 13,
      name: 'Padre Miguel',
    },
    readers: [{ id: 4, name: 'Alice Lima' }],
    commentators: [{ id: 1, name: 'Luana Odinson' }],
    ministersOfTheWord: [{ id: 7, name: 'Davi Gomes' }],
    eucharisticMinisters: [{ id: 10, name: 'Mariana Ferraz' }],
  };
  const updateRequest: UpdateEventScheduleRequest = {
    locationId: 2,
    priestId: 14,
    readerIds: [5, 6],
    commentatorIds: [2],
    ministerOfTheWordIds: [8],
    eucharisticMinisterIds: [11, 12],
  };
  const updateResponse: UpdateEventScheduleResponse = {
    eventId: 1,
    nameMassOrEvent: 'Missa atualizada',
    eventDate: '2025-07-13',
    eventTime: '10:00:00',
    massOrCelebration: true,
    location: {
      id: 2,
      churchName: 'Capela Sao Jose',
    },
    priest: {
      id: 14,
      name: 'Padre Antonio',
    },
    readers: [
      { id: 5, name: 'Alice Lima' },
      { id: 6, name: 'Arthur Costa' },
    ],
    commentators: [{ id: 2, name: 'Luana Odinson' }],
    ministersOfTheWord: [{ id: 8, name: 'Davi Gomes' }],
    eucharisticMinisters: [
      { id: 11, name: 'Mariana Ferraz' },
      { id: 12, name: 'Carlos Mendes' },
    ],
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

  it('should request the full schedule detail by event id', () => {
    service.findByEventId(1).subscribe((response) => {
      expect(response).toEqual(detail);
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/eventos/1/escala`);

    expect(request.request.method).toBe('GET');
    expect(request.request.body).toBeNull();
    expect(request.request.headers.has('Authorization')).toBeFalse();

    request.flush(detail);
  });

  it('should accept nullable location, nullable priest and empty participant lists', () => {
    const emptyDetail: EventScheduleDetailResponse = {
      ...detail,
      location: null,
      priest: null,
      readers: [],
      commentators: [],
      ministersOfTheWord: [],
      eucharisticMinisters: [],
    };

    service.findByEventId(99).subscribe((response) => {
      expect(response.location).toBeNull();
      expect(response.priest).toBeNull();
      expect(response.readers).toEqual([]);
      expect(response.commentators).toEqual([]);
      expect(response.ministersOfTheWord).toEqual([]);
      expect(response.eucharisticMinisters).toEqual([]);
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/eventos/99/escala`);

    request.flush(emptyDetail);
  });

  it('should propagate not found errors from the detail endpoint', (done) => {
    service.findByEventId(404).subscribe({
      next: () => {
        fail('Expected schedule detail request to fail');
      },
      error: (error: unknown) => {
        expect(error).toBeTruthy();
        done();
      },
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/eventos/404/escala`);
    request.flush(
      { message: 'Not found' },
      {
        status: 404,
        statusText: 'Not Found',
      },
    );
  });

  it('should propagate forbidden errors from the detail endpoint', (done) => {
    service.findByEventId(403).subscribe({
      next: () => {
        fail('Expected schedule detail request to fail');
      },
      error: (error: unknown) => {
        expect(error).toBeTruthy();
        done();
      },
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/eventos/403/escala`);
    request.flush(
      { message: 'Forbidden' },
      {
        status: 403,
        statusText: 'Forbidden',
      },
    );
  });

  it('should update an event schedule with the expected body', () => {
    service.updateEventSchedule(1, updateRequest).subscribe((response) => {
      expect(response).toEqual(updateResponse);
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/eventos/1/escala`);

    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual(updateRequest);
    expect(request.request.headers.has('Authorization')).toBeFalse();

    request.flush(updateResponse);
  });

  it('should send empty participant lists and nullable priest when updating', () => {
    const emptyRequest: UpdateEventScheduleRequest = {
      locationId: 2,
      priestId: null,
      readerIds: [],
      commentatorIds: [],
      ministerOfTheWordIds: [],
      eucharisticMinisterIds: [],
    };
    const emptyResponse: UpdateEventScheduleResponse = {
      ...updateResponse,
      priest: null,
      readers: [],
      commentators: [],
      ministersOfTheWord: [],
      eucharisticMinisters: [],
    };

    service.updateEventSchedule(2, emptyRequest).subscribe((response) => {
      expect(response.priest).toBeNull();
      expect(response.readers).toEqual([]);
      expect(response.commentators).toEqual([]);
      expect(response.ministersOfTheWord).toEqual([]);
      expect(response.eucharisticMinisters).toEqual([]);
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/eventos/2/escala`);

    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual(emptyRequest);

    request.flush(emptyResponse);
  });

  it('should propagate bad request errors from the update endpoint', (done) => {
    service.updateEventSchedule(1, updateRequest).subscribe({
      next: () => {
        fail('Expected update request to fail');
      },
      error: (error: unknown) => {
        expect(error).toBeTruthy();
        done();
      },
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/eventos/1/escala`);
    request.flush(
      { message: 'Invalid data' },
      {
        status: 400,
        statusText: 'Bad Request',
      },
    );
  });

  it('should propagate forbidden errors from the update endpoint', (done) => {
    service.updateEventSchedule(1, updateRequest).subscribe({
      next: () => {
        fail('Expected update request to fail');
      },
      error: (error: unknown) => {
        expect(error).toBeTruthy();
        done();
      },
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/eventos/1/escala`);
    request.flush(
      { message: 'Forbidden' },
      {
        status: 403,
        statusText: 'Forbidden',
      },
    );
  });

  it('should propagate not found errors from the update endpoint', (done) => {
    service.updateEventSchedule(404, updateRequest).subscribe({
      next: () => {
        fail('Expected update request to fail');
      },
      error: (error: unknown) => {
        expect(error).toBeTruthy();
        done();
      },
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/eventos/404/escala`);
    request.flush(
      { message: 'Not found' },
      {
        status: 404,
        statusText: 'Not Found',
      },
    );
  });

  it('should propagate conflict errors from the update endpoint', (done) => {
    service.updateEventSchedule(1, updateRequest).subscribe({
      next: () => {
        fail('Expected update request to fail');
      },
      error: (error: unknown) => {
        expect(error).toBeTruthy();
        done();
      },
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/eventos/1/escala`);
    request.flush(
      { message: 'Conflict' },
      {
        status: 409,
        statusText: 'Conflict',
      },
    );
  });
});
