import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../api.config';
import { LocationResponse } from './location.models';

@Injectable({
  providedIn: 'root',
})
export class LocationService {
  private readonly http = inject(HttpClient);

  findAll(): Observable<LocationResponse[]> {
    return this.http.get<LocationResponse[]>(`${API_BASE_URL}/locais`);
  }
}
