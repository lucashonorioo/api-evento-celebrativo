import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../api.config';
import { CommentatorRequest, CommentatorResponse } from './commentator.models';

@Injectable({
  providedIn: 'root',
})
export class CommentatorService {
  private readonly http = inject(HttpClient);

  findAll(): Observable<CommentatorResponse[]> {
    return this.http.get<CommentatorResponse[]>(`${API_BASE_URL}/comentaristas`);
  }

  create(request: CommentatorRequest): Observable<CommentatorResponse> {
    return this.http.post<CommentatorResponse>(`${API_BASE_URL}/comentaristas`, request);
  }

  update(id: number, request: CommentatorRequest): Observable<CommentatorResponse> {
    return this.http.put<CommentatorResponse>(`${API_BASE_URL}/comentaristas/${id}`, request);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${API_BASE_URL}/comentaristas/${id}`);
  }
}
