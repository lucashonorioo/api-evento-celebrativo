import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../api.config';
import {
  CreateEventWithScheduleRequest,
  CreateEventWithScheduleResponse,
  EventScheduleDetailResponse,
  EventSchedulePage,
  EventScheduleQuery,
  UpdateEventScheduleRequest,
  UpdateEventScheduleResponse,
} from './event-schedule.models';

@Injectable({
  providedIn: 'root',
})
export class EventScheduleService {
  private readonly http = inject(HttpClient);

  findMonthlySchedules(query: EventScheduleQuery): Observable<EventSchedulePage> {
    const params = new HttpParams()
      .set('startDate', query.startDate)
      .set('endDate', query.endDate)
      .set('type', query.type)
      .set('page', String(query.page))
      .set('size', String(query.size))
      .set('includeUnassigned', String(query.includeUnassigned));

    return this.http.get<EventSchedulePage>(`${API_BASE_URL}/eventos/escalas`, { params });
  }

  findByEventId(eventId: number): Observable<EventScheduleDetailResponse> {
    return this.http.get<EventScheduleDetailResponse>(
      `${API_BASE_URL}/eventos/${eventId}/escala`,
    );
  }

  updateEventSchedule(
    eventId: number,
    request: UpdateEventScheduleRequest,
  ): Observable<UpdateEventScheduleResponse> {
    return this.http.put<UpdateEventScheduleResponse>(
      `${API_BASE_URL}/eventos/${eventId}/escala`,
      request,
    );
  }

  createEventWithSchedule(
    request: CreateEventWithScheduleRequest,
  ): Observable<CreateEventWithScheduleResponse> {
    return this.http.post<CreateEventWithScheduleResponse>(
      `${API_BASE_URL}/eventos/com-escala`,
      request,
    );
  }
}
