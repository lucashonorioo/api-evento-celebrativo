import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../api.config';
import { CommentatorResponse } from './commentator.models';

@Injectable({
  providedIn: 'root',
})
export class CommentatorService {
  private readonly http = inject(HttpClient);

  findAll(): Observable<CommentatorResponse[]> {
    return this.http.get<CommentatorResponse[]>(`${API_BASE_URL}/comentaristas`);
  }
}
