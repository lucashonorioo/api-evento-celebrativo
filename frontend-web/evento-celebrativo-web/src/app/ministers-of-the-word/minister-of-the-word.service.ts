import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../api.config';
import { MinisterOfTheWordResponse } from './minister-of-the-word.models';

@Injectable({
  providedIn: 'root',
})
export class MinisterOfTheWordService {
  private readonly http = inject(HttpClient);

  findAll(): Observable<MinisterOfTheWordResponse[]> {
    return this.http.get<MinisterOfTheWordResponse[]>(`${API_BASE_URL}/ministrosDaPalavra`);
  }
}
