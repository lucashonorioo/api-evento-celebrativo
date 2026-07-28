import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../api.config';
import { CurrentUserSchedulePage, CurrentUserScheduleQuery } from './current-user-schedule.models';

@Injectable({
  providedIn: 'root',
})
export class CurrentUserScheduleService {
  private readonly http = inject(HttpClient);

  private readonly schedulesUrl = `${API_BASE_URL}/pessoas/me/escalas`;

  findSchedules(query: CurrentUserScheduleQuery): Observable<CurrentUserSchedulePage> {
    const params = new HttpParams()
      .set('startDate', query.startDate)
      .set('endDate', query.endDate)
      .set('page', String(query.page))
      .set('size', String(query.size));

    return this.http.get<CurrentUserSchedulePage>(this.schedulesUrl, { params });
  }
}
