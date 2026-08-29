import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_BASE_URL } from '../api.config';
import {
  MinistryType,
  PersonAdmin,
  PersonAdminPage,
  PersonMinistriesResponse,
  PersonRoleUpdateResponse,
} from './admin-user.models';
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
    expect(request.request.params.has('personType')).toBeFalse();
    request.flush(pageResponse());
  });

  it('should send combined filters with the official ministry value and role', () => {
    service
      .findAll({
        name: '  Maria  ',
        phoneNumber: ' 3499 ',
        ministry: 'MINISTER_OF_THE_WORD',
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
        currentRequest.params.get('ministry') === 'MINISTER_OF_THE_WORD' &&
        currentRequest.params.get('role') === 'ROLE_ADMIN',
    );

    expect(request.request.method).toBe('GET');
    expect(request.request.params.has('personType')).toBeFalse();
    request.flush(pageResponse());
  });

  it('should trim name before sending it', () => {
    service.findAll({ name: '  Maria  ', page: 0, size: 10 }).subscribe();

    const request = httpTestingController.expectOne(
      (currentRequest) => currentRequest.params.get('name') === 'Maria',
    );

    expect(request.request.method).toBe('GET');
    request.flush(pageResponse());
  });

  it('should trim phoneNumber before sending it', () => {
    service.findAll({ phoneNumber: ' 3499 ', page: 0, size: 10 }).subscribe();

    const request = httpTestingController.expectOne(
      (currentRequest) => currentRequest.params.get('phoneNumber') === '3499',
    );

    expect(request.request.method).toBe('GET');
    request.flush(pageResponse());
  });

  it('should send the ministry filter', () => {
    service.findAll({ ministry: 'READER', page: 0, size: 10 }).subscribe();

    const request = httpTestingController.expectOne(
      (currentRequest) => currentRequest.params.get('ministry') === 'READER',
    );

    expect(request.request.method).toBe('GET');
    request.flush(pageResponse());
  });

  it('should send the role filter', () => {
    service.findAll({ role: 'ROLE_ADMIN', page: 0, size: 10 }).subscribe();

    const request = httpTestingController.expectOne(
      (currentRequest) => currentRequest.params.get('role') === 'ROLE_ADMIN',
    );

    expect(request.request.method).toBe('GET');
    request.flush(pageResponse());
  });

  it('should send personActive=true as the string "true"', () => {
    service.findAll({ personActive: true, page: 0, size: 10 }).subscribe();

    const request = httpTestingController.expectOne(
      (currentRequest) => currentRequest.params.get('personActive') === 'true',
    );

    expect(request.request.method).toBe('GET');
    request.flush(pageResponse());
  });

  it('should send personActive=false as the string "false"', () => {
    service.findAll({ personActive: false, page: 0, size: 10 }).subscribe();

    const request = httpTestingController.expectOne(
      (currentRequest) => currentRequest.params.get('personActive') === 'false',
    );

    expect(request.request.method).toBe('GET');
    request.flush(pageResponse());
  });

  it('should send accountExists=true as the string "true"', () => {
    service.findAll({ accountExists: true, page: 0, size: 10 }).subscribe();

    const request = httpTestingController.expectOne(
      (currentRequest) => currentRequest.params.get('accountExists') === 'true',
    );

    expect(request.request.method).toBe('GET');
    request.flush(pageResponse());
  });

  it('should send accountExists=false as the string "false"', () => {
    service.findAll({ accountExists: false, page: 0, size: 10 }).subscribe();

    const request = httpTestingController.expectOne(
      (currentRequest) => currentRequest.params.get('accountExists') === 'false',
    );

    expect(request.request.method).toBe('GET');
    request.flush(pageResponse());
  });

  it('should send accountEnabled=true as the string "true"', () => {
    service.findAll({ accountEnabled: true, page: 0, size: 10 }).subscribe();

    const request = httpTestingController.expectOne(
      (currentRequest) => currentRequest.params.get('accountEnabled') === 'true',
    );

    expect(request.request.method).toBe('GET');
    request.flush(pageResponse());
  });

  it('should send accountEnabled=false as the string "false"', () => {
    service.findAll({ accountEnabled: false, page: 0, size: 10 }).subscribe();

    const request = httpTestingController.expectOne(
      (currentRequest) => currentRequest.params.get('accountEnabled') === 'false',
    );

    expect(request.request.method).toBe('GET');
    request.flush(pageResponse());
  });

  it('should omit undefined filters, including undefined booleans, and never send personType', () => {
    service
      .findAll({
        name: '   ',
        phoneNumber: '',
        ministry: undefined,
        role: undefined,
        personActive: undefined,
        accountExists: undefined,
        accountEnabled: undefined,
        page: 0,
        size: 10,
      })
      .subscribe();

    const request = httpTestingController.expectOne(`${API_BASE_URL}/pessoas?page=0&size=10`);

    expect(request.request.params.has('name')).toBeFalse();
    expect(request.request.params.has('phoneNumber')).toBeFalse();
    expect(request.request.params.has('ministry')).toBeFalse();
    expect(request.request.params.has('role')).toBeFalse();
    expect(request.request.params.has('personActive')).toBeFalse();
    expect(request.request.params.has('accountExists')).toBeFalse();
    expect(request.request.params.has('accountEnabled')).toBeFalse();
    expect(request.request.params.has('personType')).toBeFalse();
    request.flush(pageResponse());
  });

  it('should not call the account or ministries endpoints when listing people', () => {
    httpTestingController.verify();
    service.findAll({ page: 0, size: 10 }).subscribe();

    const request = httpTestingController.expectOne(`${API_BASE_URL}/pessoas?page=0&size=10`);
    request.flush(pageResponse());

    expect(request.request.method).toBe('GET');
    httpTestingController.expectNone((current) => current.url.includes('/conta'));
    httpTestingController.expectNone((current) => current.url.includes('/ministries'));
  });

  it('should propagate the consolidated response including accountEnabled=null and username=null', () => {
    const withoutAccount = pageResponse({
      content: [person({ accountExists: false, accountEnabled: null, username: null, roles: [] })],
    });

    service.findAll({ page: 0, size: 10 }).subscribe((response) => {
      expect(response).toEqual(withoutAccount);
      expect(response.content[0].accountEnabled).toBeNull();
      expect(response.content[0].username).toBeNull();
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/pessoas?page=0&size=10`);
    request.flush(withoutAccount);
  });

  it('should request a person by id without ministries', () => {
    service.findById(7).subscribe((response) => {
      expect(response).toEqual(person({ ministries: [] }));
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/pessoas/7`);

    expect(request.request.method).toBe('GET');
    request.flush(person({ ministries: [] }));
  });

  it('should request a person by id with multiple ministries', () => {
    service.findById(7).subscribe((response) => {
      expect(response).toEqual(person({ ministries: ['READER', 'COMMENTATOR'] }));
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/pessoas/7`);

    request.flush(person({ ministries: ['READER', 'COMMENTATOR'] }));
  });

  it('should update a person role with a single role payload and receive the partial role update DTO back', () => {
    service.updateRole(7, 'ROLE_OPERATOR').subscribe((response) => {
      expect(response).toEqual(roleUpdateResponse({ roles: ['ROLE_OPERATOR'], ministries: ['READER'] }));
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/pessoas/7/roles`);

    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ role: 'ROLE_OPERATOR' });
    expect(Object.keys(request.request.body as Record<string, unknown>)).toEqual(['role']);

    request.flush(roleUpdateResponse({ roles: ['ROLE_OPERATOR'], ministries: ['READER'] }));
  });

  it('should not pretend the role update response is a full PersonAdmin', () => {
    service.updateRole(7, 'ROLE_OPERATOR').subscribe((response) => {
      expect((response as unknown as Record<string, unknown>)['birthdayDate']).toBeUndefined();
      expect((response as unknown as Record<string, unknown>)['personActive']).toBeUndefined();
      expect((response as unknown as Record<string, unknown>)['accountExists']).toBeUndefined();
      expect((response as unknown as Record<string, unknown>)['accountEnabled']).toBeUndefined();
      expect((response as unknown as Record<string, unknown>)['username']).toBeUndefined();
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/pessoas/7/roles`);
    request.flush(roleUpdateResponse());
  });

  it('should request the ministries of a person', () => {
    service.findMinistries(10).subscribe((response) => {
      expect(response).toEqual(ministriesResponse());
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/pessoas/10/ministries`);

    expect(request.request.method).toBe('GET');
    request.flush(ministriesResponse());
  });

  it('should update ministries with multiple values sending only the ministries payload', () => {
    service.updateMinistries(10, ['READER', 'COMMENTATOR']).subscribe((response) => {
      expect(response).toEqual(ministriesResponse());
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/pessoas/10/ministries`);

    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ ministries: ['READER', 'COMMENTATOR'] });
    expect(Object.keys(request.request.body as Record<string, unknown>)).toEqual(['ministries']);
    request.flush(ministriesResponse());
  });

  it('should update ministries with an empty list', () => {
    service.updateMinistries(10, []).subscribe((response) => {
      expect(response).toEqual(ministriesResponse({ ministries: [] }));
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/pessoas/10/ministries`);

    expect(request.request.body).toEqual({ ministries: [] });
    request.flush(ministriesResponse({ ministries: [] }));
  });

  it('should propagate expected HTTP errors from findById', () => {
    const statuses = [400, 403, 404, 409, 422];

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

  it('should propagate expected HTTP errors from the ministries endpoint', () => {
    const statuses = [400, 403, 404, 409, 422];

    for (const status of statuses) {
      service.updateMinistries(status, ['READER']).subscribe({
        error: (error: unknown) => {
          expect(error).toBeTruthy();
        },
      });

      const request = httpTestingController.expectOne(`${API_BASE_URL}/pessoas/${status}/ministries`);
      request.flush({ errorCode: 'ERROR' }, { status, statusText: 'Error' });
    }
  });

  it('should propagate a PERSON_ADMIN_FILTERS_INVALID error from findAll', () => {
    service.findAll({ accountExists: false, role: 'ROLE_ADMIN', page: 0, size: 10 }).subscribe({
      error: (error: unknown) => {
        expect(error).toBeTruthy();
      },
    });

    const request = httpTestingController.expectOne(
      (currentRequest) => currentRequest.url === `${API_BASE_URL}/pessoas`,
    );
    request.flush({ errorCode: 'PERSON_ADMIN_FILTERS_INVALID' }, { status: 400, statusText: 'Bad Request' });
  });

  function pageResponse(overrides: Partial<PersonAdminPage> = {}): PersonAdminPage {
    return {
      content: [person()],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 10,
      first: true,
      last: true,
      empty: false,
      ...overrides,
    };
  }

  function person(overrides: Partial<PersonAdmin> = {}): PersonAdmin {
    return {
      id: 7,
      name: 'Maria Silva',
      phoneNumber: '34999999999',
      birthdayDate: '1988-04-16',
      personActive: true,
      ministries: ['READER' as MinistryType],
      accountExists: true,
      accountEnabled: true,
      username: '34999999999',
      roles: ['ROLE_ADMIN'],
      ...overrides,
    };
  }

  function roleUpdateResponse(
    overrides: Partial<PersonRoleUpdateResponse> = {},
  ): PersonRoleUpdateResponse {
    return {
      id: 7,
      name: 'Maria Silva',
      phoneNumber: '34999999999',
      ministries: ['READER' as MinistryType],
      roles: ['ROLE_ADMIN'],
      ...overrides,
    };
  }

  function ministriesResponse(
    overrides: Partial<PersonMinistriesResponse> = {},
  ): PersonMinistriesResponse {
    return {
      id: 10,
      ministries: ['READER', 'COMMENTATOR'],
      ...overrides,
    };
  }
});
