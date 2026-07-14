import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../api.config';
import { CelebrationEventResponse } from './event.models';

@Injectable({
  providedIn: 'root',
})
export class EventService {
  private readonly http = inject(HttpClient);

  findAll(): Observable<CelebrationEventResponse[]> {
    return this.http.get<CelebrationEventResponse[]>(`${API_BASE_URL}/eventos`);
  }
}
