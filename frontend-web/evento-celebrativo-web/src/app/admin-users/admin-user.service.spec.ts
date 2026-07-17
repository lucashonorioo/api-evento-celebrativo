import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_BASE_URL } from '../api.config';
import { PersonAdmin, PersonAdminPage } from './admin-user.models';
import { AdminUserService } from './admin-user.service';

describe('AdminUserService', () => {
  let service: AdminUserService;
  let httpTestingController: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });

    service = TestBed.inject(AdminUserService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  it('should request people with page and size', () => {
    service.findAll({ page: 0, size: 10 }).subscribe((response) => {
      expect(response).toEqual(pageResponse());
    });

    const request = httpTestingController.expectOne(
      (currentRequest) =>
        currentRequest.url === `${API_BASE_URL}/pessoas` &&
        currentRequest.params.get('page') === '0' &&
        currentRequest.params.get('size') === '10',
    );

    expect(request.request.method).toBe('GET');
    request.flush(pageResponse());
  });

  it('should send combined filters with exact person type and role values', () => {
    service
      .findAll({
        name: '  Maria  ',
        phoneNumber: ' 3499 ',
        personType: 'minister_of_the_word',
        role: 'ROLE_ADMIN',
        page: 2,
        size: 25,
      })
      .subscribe();

    const request = httpTestingController.expectOne(
      (currentRequest) =>
        currentRequest.url === `${API_BASE_URL}/pessoas` &&
        currentRequest.params.get('page') === '2' &&
        currentRequest.params.get('size') === '25' &&
        currentRequest.params.get('name') === 'Maria' &&
        currentRequest.params.get('phoneNumber') === '3499' &&
        currentRequest.params.get('personType') === 'minister_of_the_word' &&
        currentRequest.params.get('role') === 'ROLE_ADMIN',
    );

    expect(request.request.method).toBe('GET');
    request.flush(pageResponse());
  });

  it('should omit empty filters', () => {
    service
      .findAll({
        name: '   ',
        phoneNumber: '',
        personType: undefined,
        role: undefined,
        page: 0,
        size: 10,
      })
      .subscribe();

    const request = httpTestingController.expectOne(`${API_BASE_URL}/pessoas?page=0&size=10`);

    expect(request.request.params.has('name')).toBeFalse();
    expect(request.request.params.has('phoneNumber')).toBeFalse();
    expect(request.request.params.has('personType')).toBeFalse();
    expect(request.request.params.has('role')).toBeFalse();
    request.flush(pageResponse());
  });

  it('should request a person by id', () => {
    service.findById(7).subscribe((response) => {
      expect(response).toEqual(person());
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/pessoas/7`);

    expect(request.request.method).toBe('GET');
    request.flush(person());
  });

  it('should update a person role with a single role payload', () => {
    service.updateRole(7, 'ROLE_OPERATOR').subscribe((response) => {
      expect(response).toEqual(person({ roles: ['ROLE_OPERATOR'] }));
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/pessoas/7/roles`);

    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ role: 'ROLE_OPERATOR' });
    expect(Object.keys(request.request.body as Record<string, unknown>)).toEqual(['role']);
    request.flush(person({ roles: ['ROLE_OPERATOR'] }));
  });

  it('should propagate expected HTTP errors', () => {
    const statuses = [400, 403, 404, 409];

    for (const status of statuses) {
      service.findById(status).subscribe({
        error: (error: unknown) => {
          expect(error).toBeTruthy();
        },
      });

      const request = httpTestingController.expectOne(`${API_BASE_URL}/pessoas/${status}`);
      request.flush({ errorCode: 'ERROR' }, { status, statusText: 'Error' });
    }
  });

  function pageResponse(): PersonAdminPage {
    return {
      content: [person()],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 10,
      first: true,
      last: true,
      empty: false,
    };
  }

  function person(overrides: Partial<PersonAdmin> = {}): PersonAdmin {
    return {
      id: 7,
      name: 'Maria Silva',
      phoneNumber: '34999999999',
      personType: 'reader',
      roles: ['ROLE_ADMIN'],
      ...overrides,
    };
  }
});
