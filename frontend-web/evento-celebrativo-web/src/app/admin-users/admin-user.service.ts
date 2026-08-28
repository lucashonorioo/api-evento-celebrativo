import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../api.config';
import {
  MinistryCatalogItem,
  PersonAdmin,
  PersonAdminFilters,
  PersonAdminPage,
  PersonMinistriesResponse,
  PersonMinistriesUpdateRequest,
  PersonRoleUpdateRequest,
  PersonRoleUpdateResponse,
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

  findMinistryCatalog(): Observable<MinistryCatalogItem[]> {
    return this.http.get<MinistryCatalogItem[]>(`${API_BASE_URL}/ministerios`);
  }

  updateRole(id: number, role: UserRole): Observable<PersonRoleUpdateResponse> {
    const request: PersonRoleUpdateRequest = { role };

    return this.http.put<PersonRoleUpdateResponse>(`${API_BASE_URL}/pessoas/${id}/roles`, request);
  }

  findMinistries(id: number): Observable<PersonMinistriesResponse> {
    return this.http.get<PersonMinistriesResponse>(`${API_BASE_URL}/pessoas/${id}/ministries`);
  }

  updateMinistries(id: number, ministryIds: number[]): Observable<PersonMinistriesResponse> {
    const request: PersonMinistriesUpdateRequest = { ministryIds };

    return this.http.put<PersonMinistriesResponse>(
      `${API_BASE_URL}/pessoas/${id}/ministries`,
      request,
    );
  }

  private paramsFor(filters: PersonAdminFilters): HttpParams {
    let params = new HttpParams().set('page', String(filters.page)).set('size', String(filters.size));

    params = appendTrimmedParam(params, 'name', filters.name);
    params = appendTrimmedParam(params, 'phoneNumber', filters.phoneNumber);
    params = appendNumberParam(params, 'ministryId', filters.ministryId);
    params = appendTrimmedParam(params, 'role', filters.role);
    params = appendBooleanParam(params, 'personActive', filters.personActive);
    params = appendBooleanParam(params, 'accountExists', filters.accountExists);
    params = appendBooleanParam(params, 'accountEnabled', filters.accountEnabled);

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

function appendNumberParam(
  params: HttpParams,
  key: string,
  value: number | undefined,
): HttpParams {
  return value === undefined ? params : params.set(key, String(value));
}

function appendBooleanParam(
  params: HttpParams,
  key: string,
  value: boolean | undefined,
): HttpParams {
  return value === undefined ? params : params.set(key, String(value));
}
