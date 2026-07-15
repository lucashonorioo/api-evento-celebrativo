import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../api.config';
import { LocationRequest, LocationResponse } from './location.models';

@Injectable({
  providedIn: 'root',
})
export class LocationService {
  private readonly http = inject(HttpClient);

  findAll(): Observable<LocationResponse[]> {
    return this.http.get<LocationResponse[]>(`${API_BASE_URL}/locais`);
  }

  create(request: LocationRequest): Observable<LocationResponse> {
    return this.http.post<LocationResponse>(`${API_BASE_URL}/locais`, request);
  }

  update(id: number, request: LocationRequest): Observable<LocationResponse> {
    return this.http.put<LocationResponse>(`${API_BASE_URL}/locais/${id}`, request);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${API_BASE_URL}/locais/${id}`);
  }
}
