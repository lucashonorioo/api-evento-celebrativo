import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_BASE_URL } from '../api.config';
import { CurrentUserSchedulePage, CurrentUserScheduleQuery } from './current-user-schedule.models';
import { CurrentUserScheduleService } from './current-user-schedule.service';

describe('CurrentUserScheduleService', () => {
  let service: CurrentUserScheduleService;
  let httpTestingController: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });

    service = TestBed.inject(CurrentUserScheduleService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  it('should GET /pessoas/me/escalas', () => {
    service.findSchedules(createQuery()).subscribe();

    const request = httpTestingController.expectOne(
      (candidate) => candidate.url === `${API_BASE_URL}/pessoas/me/escalas`,
    );

    expect(request.request.method).toBe('GET');
    request.flush(createPage());
  });

  it('should send startDate as a query parameter', () => {
    service.findSchedules(createQuery({ startDate: '2026-07-01' })).subscribe();

    const request = httpTestingController.expectOne(
      (candidate) => candidate.url === `${API_BASE_URL}/pessoas/me/escalas`,
    );

    expect(request.request.params.get('startDate')).toBe('2026-07-01');
    request.flush(createPage());
  });

  it('should send endDate as a query parameter', () => {
    service.findSchedules(createQuery({ endDate: '2026-07-31' })).subscribe();

    const request = httpTestingController.expectOne(
      (candidate) => candidate.url === `${API_BASE_URL}/pessoas/me/escalas`,
    );

    expect(request.request.params.get('endDate')).toBe('2026-07-31');
    request.flush(createPage());
  });

  it('should send page as a query parameter', () => {
    service.findSchedules(createQuery({ page: 2 })).subscribe();

    const request = httpTestingController.expectOne(
      (candidate) => candidate.url === `${API_BASE_URL}/pessoas/me/escalas`,
    );

    expect(request.request.params.get('page')).toBe('2');
    request.flush(createPage());
  });

  it('should send size as a query parameter', () => {
    service.findSchedules(createQuery({ size: 10 })).subscribe();

    const request = httpTestingController.expectOne(
      (candidate) => candidate.url === `${API_BASE_URL}/pessoas/me/escalas`,
    );

    expect(request.request.params.get('size')).toBe('10');
    request.flush(createPage());
  });

  it('should send only the four known parameters, without personId', () => {
    service.findSchedules(createQuery()).subscribe();

    const request = httpTestingController.expectOne(
      (candidate) => candidate.url === `${API_BASE_URL}/pessoas/me/escalas`,
    );

    expect(request.request.params.keys().sort()).toEqual(['endDate', 'page', 'size', 'startDate']);
    expect(request.request.params.has('personId')).toBeFalse();
    request.flush(createPage());
  });

  it('should never send phoneNumber as a query parameter', () => {
    service.findSchedules(createQuery()).subscribe();

    const request = httpTestingController.expectOne(
      (candidate) => candidate.url === `${API_BASE_URL}/pessoas/me/escalas`,
    );

    expect(request.request.params.has('phoneNumber')).toBeFalse();
    request.flush(createPage());
  });

  it('should never send username as a query parameter', () => {
    service.findSchedules(createQuery()).subscribe();

    const request = httpTestingController.expectOne(
      (candidate) => candidate.url === `${API_BASE_URL}/pessoas/me/escalas`,
    );

    expect(request.request.params.has('username')).toBeFalse();
    request.flush(createPage());
  });

  it('should return the paginated response emitted by the backend', () => {
    let result: CurrentUserSchedulePage | undefined;
    service.findSchedules(createQuery()).subscribe((response) => (result = response));

    const request = httpTestingController.expectOne(
      (candidate) => candidate.url === `${API_BASE_URL}/pessoas/me/escalas`,
    );
    request.flush(createPage());

    expect(result).toEqual(createPage());
  });

  it('should propagate an HTTP error to the caller', () => {
    let error: unknown;
    service.findSchedules(createQuery()).subscribe({ error: (err: unknown) => (error = err) });

    const request = httpTestingController.expectOne(
      (candidate) => candidate.url === `${API_BASE_URL}/pessoas/me/escalas`,
    );
    request.flush({ error: 'Falha' }, { status: 500, statusText: 'Error' });

    expect(error).toBeDefined();
  });

  function createQuery(overrides: Partial<CurrentUserScheduleQuery> = {}): CurrentUserScheduleQuery {
    return {
      startDate: '2026-07-01',
      endDate: '2026-07-31',
      page: 0,
      size: 10,
      ...overrides,
    };
  }

  function createPage(overrides: Partial<CurrentUserSchedulePage> = {}): CurrentUserSchedulePage {
    return {
      content: [
        {
          eventId: 15,
          eventName: 'Missa das 19h',
          eventDate: '2026-07-20',
          eventTime: '19:00:00',
          massOrCelebration: true,
          locationId: 2,
          locationName: 'Igreja Matriz',
          assignments: ['READER', 'COMMENTATOR'],
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
      ...overrides,
    };
  }
});
