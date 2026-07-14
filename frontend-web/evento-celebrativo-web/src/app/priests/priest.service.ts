import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../api.config';
import { PriestResponse } from './priest.models';

@Injectable({
  providedIn: 'root',
})
export class PriestService {
  private readonly http = inject(HttpClient);

  findAll(): Observable<PriestResponse[]> {
    return this.http.get<PriestResponse[]>(`${API_BASE_URL}/padres`);
  }
}
