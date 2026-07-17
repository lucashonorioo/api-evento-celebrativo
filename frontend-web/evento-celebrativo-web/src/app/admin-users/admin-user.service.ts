import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../api.config';
import {
  PersonAdmin,
  PersonAdminFilters,
  PersonAdminPage,
  PersonRoleUpdateRequest,
  UserRole,
} from './admin-user.models';

@Injectable({
  providedIn: 'root',
})
export class AdminUserService {
  private readonly http = inject(HttpClient);

  findAll(filters: PersonAdminFilters): Observable<PersonAdminPage> {
    const params = this.paramsFor(filters);

    return this.http.get<PersonAdminPage>(`${API_BASE_URL}/pessoas`, { params });
  }

  findById(id: number): Observable<PersonAdmin> {
    return this.http.get<PersonAdmin>(`${API_BASE_URL}/pessoas/${id}`);
  }

  updateRole(id: number, role: UserRole): Observable<PersonAdmin> {
    const request: PersonRoleUpdateRequest = { role };

    return this.http.put<PersonAdmin>(`${API_BASE_URL}/pessoas/${id}/roles`, request);
  }

  private paramsFor(filters: PersonAdminFilters): HttpParams {
    let params = new HttpParams().set('page', String(filters.page)).set('size', String(filters.size));

    params = appendTrimmedParam(params, 'name', filters.name);
    params = appendTrimmedParam(params, 'phoneNumber', filters.phoneNumber);
    params = appendTrimmedParam(params, 'personType', filters.personType);
    params = appendTrimmedParam(params, 'role', filters.role);

    return params;
  }
}

function appendTrimmedParam(
  params: HttpParams,
  key: string,
  value: string | undefined,
): HttpParams {
  const trimmedValue = value?.trim();

  return trimmedValue ? params.set(key, trimmedValue) : params;
}
