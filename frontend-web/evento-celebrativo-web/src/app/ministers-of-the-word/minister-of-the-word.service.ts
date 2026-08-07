import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../api.config';
import {
  MinisterOfTheWordCreateRequest,
  MinisterOfTheWordResponse,
  MinisterOfTheWordUpdateRequest,
} from './minister-of-the-word.models';

@Injectable({
  providedIn: 'root',
})
export class MinisterOfTheWordService {
  private readonly http = inject(HttpClient);

  findAll(): Observable<MinisterOfTheWordResponse[]> {
    return this.http.get<MinisterOfTheWordResponse[]>(`${API_BASE_URL}/ministrosDaPalavra`);
  }

  create(request: MinisterOfTheWordCreateRequest): Observable<MinisterOfTheWordResponse> {
    return this.http.post<MinisterOfTheWordResponse>(
      `${API_BASE_URL}/ministrosDaPalavra`,
      request,
    );
  }

  update(
    id: number,
    request: MinisterOfTheWordUpdateRequest,
  ): Observable<MinisterOfTheWordResponse> {
    return this.http.put<MinisterOfTheWordResponse>(
      `${API_BASE_URL}/ministrosDaPalavra/${id}`,
      request,
    );
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${API_BASE_URL}/ministrosDaPalavra/${id}`);
  }
}
