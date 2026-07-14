import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_BASE_URL } from '../api.config';
import { EucharistSchedulePage } from './eucharist-schedule.models';
import { EucharistScheduleService } from './eucharist-schedule.service';

describe('EucharistScheduleService', () => {
  let service: EucharistScheduleService;
  let httpTestingController: HttpTestingController;

  const page: EucharistSchedulePage = {
    content: [
      {
        nameMassOrEvent: 'Missa Dominical',
        eventDate: '2026-08-15',
        eventTime: '19:30:00',
        churchName: 'Igreja Matriz',
        nameMinisters: ['Maria Silva', 'João Pereira'],
      },
    ],
    number: 0,
    size: 10,
    totalElements: 1,
    totalPages: 1,
    first: true,
    last: true,
    empty: false,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });

    service = TestBed.inject(EucharistScheduleService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  it('should request the Eucharist schedule with the required period and pagination params', () => {
    service
      .findEucharistSchedule({
        startDate: '2026-08-01',
        endDate: '2026-08-31',
        page: 0,
        size: 10,
      })
      .subscribe((response) => {
        expect(response).toEqual(page);
      });

    const request = httpTestingController.expectOne(
      (req) => req.url === `${API_BASE_URL}/eventos/escala/eucaristia`,
    );

    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('startDate')).toBe('2026-08-01');
    expect(request.request.params.get('endDate')).toBe('2026-08-31');
    expect(request.request.params.get('page')).toBe('0');
    expect(request.request.params.get('size')).toBe('10');

    request.flush(page);
  });

  it('should return an empty page when the API returns no schedules', () => {
    const emptyPage: EucharistSchedulePage = {
      content: [],
      number: 0,
      size: 10,
      totalElements: 0,
      totalPages: 0,
      first: true,
      last: true,
      empty: true,
    };

    service
      .findEucharistSchedule({
        startDate: '2030-01-01',
        endDate: '2030-01-31',
        page: 0,
        size: 10,
      })
      .subscribe((response) => {
        expect(response).toEqual(emptyPage);
      });

    const request = httpTestingController.expectOne(
      `${API_BASE_URL}/eventos/escala/eucaristia?startDate=2030-01-01&endDate=2030-01-31&page=0&size=10`,
    );

    request.flush(emptyPage);
  });

  it('should preserve the period and size when requesting another page', () => {
    service
      .findEucharistSchedule({
        startDate: '2026-08-01',
        endDate: '2026-08-31',
        page: 1,
        size: 10,
      })
      .subscribe();

    const request = httpTestingController.expectOne(
      (req) => req.url === `${API_BASE_URL}/eventos/escala/eucaristia`,
    );

    expect(request.request.params.get('startDate')).toBe('2026-08-01');
    expect(request.request.params.get('endDate')).toBe('2026-08-31');
    expect(request.request.params.get('page')).toBe('1');
    expect(request.request.params.get('size')).toBe('10');

    request.flush({ ...page, number: 1 });
  });

  it('should propagate HTTP errors', (done) => {
    service
      .findEucharistSchedule({
        startDate: '2026-08-01',
        endDate: '2026-08-31',
        page: 0,
        size: 10,
      })
      .subscribe({
        next: () => {
          fail('Expected Eucharist schedule request to fail');
        },
        error: (error: unknown) => {
          expect(error).toBeTruthy();
          done();
        },
      });

    const request = httpTestingController.expectOne(
      (req) => req.url === `${API_BASE_URL}/eventos/escala/eucaristia`,
    );
    request.flush(
      { message: 'Internal server error' },
      {
        status: 500,
        statusText: 'Server Error',
      },
    );
  });
});
