import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../api.config';
import { EventAssignmentAuditQuery, EventAssignmentAuditResponse } from './event-assignment-audit.models';

@Injectable({
  providedIn: 'root',
})
export class EventAssignmentAuditService {
  private readonly http = inject(HttpClient);

  auditConsistency(query: EventAssignmentAuditQuery): Observable<EventAssignmentAuditResponse> {
    return this.http.get<EventAssignmentAuditResponse>(
      `${API_BASE_URL}/admin/event-assignments/consistency`,
      {
        params: paramsFromQuery(query),
      },
    );
  }
}

function paramsFromQuery(query: EventAssignmentAuditQuery): HttpParams {
  let params = new HttpParams()
    .set('page', String(query.page ?? 0))
    .set('size', String(query.size ?? 20))
    .set('includeDetails', String(query.includeDetails ?? true));

  if (query.startDate) {
    params = params.set('startDate', query.startDate);
  }

  if (query.endDate) {
    params = params.set('endDate', query.endDate);
  }

  if (query.eventId !== undefined) {
    params = params.set('eventId', String(query.eventId));
  }

  return params;
}
