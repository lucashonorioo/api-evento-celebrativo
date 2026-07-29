import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_BASE_URL } from '../api.config';
import {
  CurrentUserSchedulePage,
  CurrentUserScheduleQuery,
  ScheduleParticipationResponse,
} from './current-user-schedule.models';
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

  it('should receive participationStatus, declineReason and respondedAt in the schedule page', () => {
    let result: CurrentUserSchedulePage | undefined;
    service.findSchedules(createQuery()).subscribe((response) => (result = response));

    const request = httpTestingController.expectOne(
      (candidate) => candidate.url === `${API_BASE_URL}/pessoas/me/escalas`,
    );
    request.flush(
      createPage({
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
            participationStatus: 'DECLINED',
            declineReason: 'Estarei fora da cidade.',
            respondedAt: '2026-07-30T18:20:00',
          },
        ],
      }),
    );

    expect(result?.content[0].participationStatus).toBe('DECLINED');
    expect(result?.content[0].declineReason).toBe('Estarei fora da cidade.');
    expect(result?.content[0].respondedAt).toBe('2026-07-30T18:20:00');
  });

  describe('updateParticipation', () => {
    it('should PUT to /pessoas/me/escalas/{eventId}/participacao', () => {
      service.updateParticipation(15, { status: 'CONFIRMED', declineReason: null }).subscribe();

      const request = httpTestingController.expectOne(
        `${API_BASE_URL}/pessoas/me/escalas/15/participacao`,
      );

      expect(request.request.method).toBe('PUT');
      request.flush(createParticipationResponse());
    });

    it('should use the eventId in the path', () => {
      service.updateParticipation(42, { status: 'CONFIRMED', declineReason: null }).subscribe();

      const request = httpTestingController.expectOne(
        `${API_BASE_URL}/pessoas/me/escalas/42/participacao`,
      );

      expect(request.request.url).toBe(`${API_BASE_URL}/pessoas/me/escalas/42/participacao`);
      request.flush(createParticipationResponse({ eventId: 42 }));
    });

    it('should send status CONFIRMED for a confirmation', () => {
      service.updateParticipation(15, { status: 'CONFIRMED', declineReason: null }).subscribe();

      const request = httpTestingController.expectOne(
        `${API_BASE_URL}/pessoas/me/escalas/15/participacao`,
      );

      expect(request.request.body.status).toBe('CONFIRMED');
      request.flush(createParticipationResponse());
    });

    it('should send null or omit declineReason for a confirmation', () => {
      service.updateParticipation(15, { status: 'CONFIRMED', declineReason: null }).subscribe();

      const request = httpTestingController.expectOne(
        `${API_BASE_URL}/pessoas/me/escalas/15/participacao`,
      );

      expect(
        request.request.body.declineReason === null ||
          request.request.body.declineReason === undefined,
      ).toBeTrue();
      request.flush(createParticipationResponse());
    });

    it('should send status DECLINED for a decline', () => {
      service.updateParticipation(15, { status: 'DECLINED', declineReason: null }).subscribe();

      const request = httpTestingController.expectOne(
        `${API_BASE_URL}/pessoas/me/escalas/15/participacao`,
      );

      expect(request.request.body.status).toBe('DECLINED');
      request.flush(createParticipationResponse({ status: 'DECLINED' }));
    });

    it('should send the normalized reason received from the caller', () => {
      service
        .updateParticipation(15, { status: 'DECLINED', declineReason: 'Estarei fora da cidade.' })
        .subscribe();

      const request = httpTestingController.expectOne(
        `${API_BASE_URL}/pessoas/me/escalas/15/participacao`,
      );

      expect(request.request.body.declineReason).toBe('Estarei fora da cidade.');
      request.flush(
        createParticipationResponse({ status: 'DECLINED', declineReason: 'Estarei fora da cidade.' }),
      );
    });

    it('should never send personId in the request body', () => {
      service.updateParticipation(15, { status: 'CONFIRMED', declineReason: null }).subscribe();

      const request = httpTestingController.expectOne(
        `${API_BASE_URL}/pessoas/me/escalas/15/participacao`,
      );

      expect(request.request.body.personId).toBeUndefined();
      request.flush(createParticipationResponse());
    });

    it('should never send phoneNumber in the request body', () => {
      service.updateParticipation(15, { status: 'CONFIRMED', declineReason: null }).subscribe();

      const request = httpTestingController.expectOne(
        `${API_BASE_URL}/pessoas/me/escalas/15/participacao`,
      );

      expect(request.request.body.phoneNumber).toBeUndefined();
      request.flush(createParticipationResponse());
    });

    it('should never send username in the request body', () => {
      service.updateParticipation(15, { status: 'CONFIRMED', declineReason: null }).subscribe();

      const request = httpTestingController.expectOne(
        `${API_BASE_URL}/pessoas/me/escalas/15/participacao`,
      );

      expect(request.request.body.username).toBeUndefined();
      request.flush(createParticipationResponse());
    });

    it('should never send assignmentId in the request body', () => {
      service.updateParticipation(15, { status: 'CONFIRMED', declineReason: null }).subscribe();

      const request = httpTestingController.expectOne(
        `${API_BASE_URL}/pessoas/me/escalas/15/participacao`,
      );

      expect(request.request.body.assignmentId).toBeUndefined();
      request.flush(createParticipationResponse());
    });

    it('should return the typed response emitted by the backend', () => {
      let result: ScheduleParticipationResponse | undefined;
      service
        .updateParticipation(15, { status: 'DECLINED', declineReason: 'Estarei fora da cidade.' })
        .subscribe((response) => (result = response));

      const request = httpTestingController.expectOne(
        `${API_BASE_URL}/pessoas/me/escalas/15/participacao`,
      );
      const response = createParticipationResponse({
        status: 'DECLINED',
        declineReason: 'Estarei fora da cidade.',
      });
      request.flush(response);

      expect(result).toEqual(response);
    });

    it('should propagate a PUT error to the caller', () => {
      let error: unknown;
      service
        .updateParticipation(15, { status: 'CONFIRMED', declineReason: null })
        .subscribe({ error: (err: unknown) => (error = err) });

      const request = httpTestingController.expectOne(
        `${API_BASE_URL}/pessoas/me/escalas/15/participacao`,
      );
      request.flush({ error: 'Conflito' }, { status: 409, statusText: 'Conflict' });

      expect(error).toBeDefined();
    });
  });

  function createParticipationResponse(
    overrides: Partial<ScheduleParticipationResponse> = {},
  ): ScheduleParticipationResponse {
    return {
      eventId: 15,
      status: 'CONFIRMED',
      declineReason: null,
      respondedAt: '2026-07-30T18:20:00',
      ...overrides,
    };
  }

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
          participationStatus: 'PENDING',
          declineReason: null,
          respondedAt: null,
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
