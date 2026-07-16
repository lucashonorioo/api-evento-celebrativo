import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../api.config';
import { CelebrationEventRequest, CelebrationEventResponse } from './event.models';

@Injectable({
  providedIn: 'root',
})
export class EventService {
  private readonly http = inject(HttpClient);

  findAll(): Observable<CelebrationEventResponse[]> {
    return this.http.get<CelebrationEventResponse[]>(`${API_BASE_URL}/eventos`);
  }

  findById(id: number): Observable<CelebrationEventResponse> {
    return this.http.get<CelebrationEventResponse>(`${API_BASE_URL}/eventos/${id}`);
  }

  create(request: CelebrationEventRequest): Observable<CelebrationEventResponse> {
    return this.http.post<CelebrationEventResponse>(`${API_BASE_URL}/eventos`, request);
  }

  update(id: number, request: CelebrationEventRequest): Observable<CelebrationEventResponse> {
    return this.http.put<CelebrationEventResponse>(`${API_BASE_URL}/eventos/${id}`, request);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${API_BASE_URL}/eventos/${id}`);
  }
}
