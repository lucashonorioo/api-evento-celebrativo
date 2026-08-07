import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../api.config';
import { ReaderCreateRequest, ReaderResponse, ReaderUpdateRequest } from './reader.models';

@Injectable({
  providedIn: 'root',
})
export class ReaderService {
  private readonly http = inject(HttpClient);

  findAll(): Observable<ReaderResponse[]> {
    return this.http.get<ReaderResponse[]>(`${API_BASE_URL}/leitores`);
  }

  create(request: ReaderCreateRequest): Observable<ReaderResponse> {
    return this.http.post<ReaderResponse>(`${API_BASE_URL}/leitores`, request);
  }

  update(id: number, request: ReaderUpdateRequest): Observable<ReaderResponse> {
    return this.http.put<ReaderResponse>(`${API_BASE_URL}/leitores/${id}`, request);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${API_BASE_URL}/leitores/${id}`);
  }
}
