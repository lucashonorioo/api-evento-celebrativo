import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../api.config';
import {
  EucharisticMinisterRequest,
  EucharisticMinisterResponse,
} from './eucharistic-minister.models';

@Injectable({
  providedIn: 'root',
})
export class EucharisticMinisterService {
  private readonly http = inject(HttpClient);

  findAll(): Observable<EucharisticMinisterResponse[]> {
    return this.http.get<EucharisticMinisterResponse[]>(
      `${API_BASE_URL}/ministrosDeEucaristia`,
    );
  }

  create(request: EucharisticMinisterRequest): Observable<EucharisticMinisterResponse> {
    return this.http.post<EucharisticMinisterResponse>(
      `${API_BASE_URL}/ministrosDeEucaristia`,
      request,
    );
  }

  update(id: number, request: EucharisticMinisterRequest): Observable<EucharisticMinisterResponse> {
    return this.http.put<EucharisticMinisterResponse>(
      `${API_BASE_URL}/ministrosDeEucaristia/${id}`,
      request,
    );
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${API_BASE_URL}/ministrosDeEucaristia/${id}`);
  }
}
