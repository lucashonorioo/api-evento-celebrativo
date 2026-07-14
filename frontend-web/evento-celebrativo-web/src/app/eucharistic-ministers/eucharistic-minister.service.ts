import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../api.config';
import { EucharisticMinisterResponse } from './eucharistic-minister.models';

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
}
