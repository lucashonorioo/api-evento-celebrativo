import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_BASE_URL } from '../api.config';
import { EventAssignmentAuditResponse } from './event-assignment-audit.models';
import { EventAssignmentAuditService } from './event-assignment-audit.service';

describe('EventAssignmentAuditService', () => {
  let service: EventAssignmentAuditService;
  let httpTestingController: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });

    service = TestBed.inject(EventAssignmentAuditService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  it('should request the consistency audit with default pagination and details', () => {
    const response = createResponse();

    service.auditConsistency({}).subscribe((result) => {
      expect(result).toEqual(response);
    });

    const request = httpTestingController.expectOne(
      `${API_BASE_URL}/admin/event-assignments/consistency?page=0&size=20&includeDetails=true`,
    );

    expect(request.request.method).toBe('GET');
    request.flush(response);
  });

  it('should send only the provided filters as query params', () => {
    service
      .auditConsistency({
        startDate: '2026-07-01',
        endDate: '2026-07-31',
        eventId: 15,
        page: 2,
        size: 50,
        includeDetails: false,
      })
      .subscribe();

    const request = httpTestingController.expectOne((candidate) => {
      const params = candidate.params;

      return (
        candidate.url === `${API_BASE_URL}/admin/event-assignments/consistency` &&
        params.get('startDate') === '2026-07-01' &&
        params.get('endDate') === '2026-07-31' &&
        params.get('eventId') === '15' &&
        params.get('page') === '2' &&
        params.get('size') === '50' &&
        params.get('includeDetails') === 'false'
      );
    });

    expect(request.request.method).toBe('GET');
    request.flush(createResponse({ events: undefined }));
  });

  it('should propagate HTTP errors', () => {
    let receivedStatus = 0;

    service.auditConsistency({}).subscribe({
      error: (error: { readonly status: number }) => {
        receivedStatus = error.status;
      },
    });

    const request = httpTestingController.expectOne(
      `${API_BASE_URL}/admin/event-assignments/consistency?page=0&size=20&includeDetails=true`,
    );
    request.flush({ message: 'Forbidden' }, { status: 403, statusText: 'Forbidden' });

    expect(receivedStatus).toBe(403);
  });
});

function createResponse(
  overrides: Partial<EventAssignmentAuditResponse> = {},
): EventAssignmentAuditResponse {
  return {
    summary: {
      eventsChecked: 1,
      consistentEvents: 0,
      inconsistentEvents: 1,
      legacyParticipants: 2,
      parallelAssignments: 1,
      totalIssues: 1,
      missingParallelAssignments: 1,
      extraParallelAssignments: 0,
      assignmentTypeMismatches: 0,
      duplicateParallelAssignments: 0,
      multiplePriests: 0,
      unknownLegacyPersonTypes: 0,
    },
    page: 0,
    size: 20,
    totalElements: 1,
    totalPages: 1,
    numberOfElements: 1,
    empty: false,
    events: [
      {
        eventId: 10,
        consistent: false,
        legacyParticipantCount: 2,
        parallelAssignmentCount: 1,
        issueCount: 1,
        issues: [
          {
            issueType: 'MISSING_PARALLEL_ASSIGNMENT',
            eventId: 10,
            personId: 20,
            legacyType: 'READER',
            parallelType: null,
          },
        ],
      },
    ],
    ...overrides,
  };
}
