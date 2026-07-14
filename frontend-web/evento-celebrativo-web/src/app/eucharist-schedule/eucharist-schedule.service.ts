import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../api.config';
import { EucharistSchedulePage, EucharistScheduleQuery } from './eucharist-schedule.models';

@Injectable({
  providedIn: 'root',
})
export class EucharistScheduleService {
  private readonly http = inject(HttpClient);

  findEucharistSchedule(query: EucharistScheduleQuery): Observable<EucharistSchedulePage> {
    const params = new HttpParams()
      .set('startDate', query.startDate)
      .set('endDate', query.endDate)
      .set('page', String(query.page))
      .set('size', String(query.size));

    return this.http.get<EucharistSchedulePage>(
      `${API_BASE_URL}/eventos/escala/eucaristia`,
      { params },
    );
  }
}
