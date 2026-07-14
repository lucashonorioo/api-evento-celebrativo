import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../api.config';
import { ReaderResponse } from './reader.models';

@Injectable({
  providedIn: 'root',
})
export class ReaderService {
  private readonly http = inject(HttpClient);

  findAll(): Observable<ReaderResponse[]> {
    return this.http.get<ReaderResponse[]>(`${API_BASE_URL}/leitores`);
  }
}
