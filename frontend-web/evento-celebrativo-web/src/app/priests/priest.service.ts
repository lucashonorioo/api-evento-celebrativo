import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../api.config';
import { PriestRequest, PriestResponse } from './priest.models';

@Injectable({
  providedIn: 'root',
})
export class PriestService {
  private readonly http = inject(HttpClient);

  findAll(): Observable<PriestResponse[]> {
    return this.http.get<PriestResponse[]>(`${API_BASE_URL}/padres`);
  }

  create(request: PriestRequest): Observable<PriestResponse> {
    return this.http.post<PriestResponse>(`${API_BASE_URL}/padres`, request);
  }

  update(id: number, request: PriestRequest): Observable<PriestResponse> {
    return this.http.put<PriestResponse>(`${API_BASE_URL}/padres/${id}`, request);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${API_BASE_URL}/padres/${id}`);
  }
}
