import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_BASE_URL } from '../api.config';
import { CurrentUserProfile, CurrentUserProfileUpdateRequest } from './current-user-profile.models';
import { CurrentUserProfileService } from './current-user-profile.service';

describe('CurrentUserProfileService', () => {
  let service: CurrentUserProfileService;
  let httpTestingController: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });

    service = TestBed.inject(CurrentUserProfileService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  it('should GET /pessoas/me when loading the profile', () => {
    service.loadProfile();

    const request = httpTestingController.expectOne(`${API_BASE_URL}/pessoas/me`);

    expect(request.request.method).toBe('GET');
    request.flush(currentUserProfile());
  });

  it('should not send an id or query parameters on the GET request', () => {
    service.loadProfile();

    const request = httpTestingController.expectOne(`${API_BASE_URL}/pessoas/me`);

    expect(request.request.url).toBe(`${API_BASE_URL}/pessoas/me`);
    expect(request.request.params.keys().length).toBe(0);
    request.flush(currentUserProfile());
  });

  it('should set the loading state while the GET request is pending', () => {
    service.loadProfile();

    expect(service.isLoading()).toBeTrue();

    const request = httpTestingController.expectOne(`${API_BASE_URL}/pessoas/me`);
    request.flush(currentUserProfile());

    expect(service.isLoading()).toBeFalse();
  });

  it('should update the profile signal after a successful GET', () => {
    service.loadProfile();

    const request = httpTestingController.expectOne(`${API_BASE_URL}/pessoas/me`);
    request.flush(currentUserProfile());

    expect(service.profile()).toEqual(currentUserProfile());
  });

  it('should store an error message after a GET failure', () => {
    service.loadProfile();

    const request = httpTestingController.expectOne(`${API_BASE_URL}/pessoas/me`);
    request.flush({ error: 'Not found' }, { status: 404, statusText: 'Not Found' });

    expect(service.errorMessage()).toContain('Não foi possível localizar o perfil');
    expect(service.isLoading()).toBeFalse();
    expect(service.profile()).toBeNull();
  });

  it('should block a concurrent GET while one is already loading', () => {
    service.loadProfile();
    service.loadProfile();

    const request = httpTestingController.expectOne(`${API_BASE_URL}/pessoas/me`);

    expect(request.request.method).toBe('GET');
    request.flush(currentUserProfile());
  });

  it('should clear a previously loaded profile as soon as a new load starts', () => {
    service.loadProfile();

    const firstRequest = httpTestingController.expectOne(`${API_BASE_URL}/pessoas/me`);
    firstRequest.flush(currentUserProfile({ name: 'Usuário A' }));

    expect(service.profile()?.name).toBe('Usuário A');

    service.loadProfile();

    expect(service.profile()).toBeNull();
    expect(service.isLoading()).toBeTrue();
    expect(service.errorMessage()).toBeNull();

    const secondRequest = httpTestingController.expectOne(`${API_BASE_URL}/pessoas/me`);
    secondRequest.flush(currentUserProfile({ name: 'Usuário B' }));

    expect(service.profile()?.name).toBe('Usuário B');
  });

  it('should keep the profile null when a new load fails after clearing a previous session profile', () => {
    service.loadProfile();

    const firstRequest = httpTestingController.expectOne(`${API_BASE_URL}/pessoas/me`);
    firstRequest.flush(currentUserProfile({ name: 'Usuário A' }));

    service.loadProfile();

    const secondRequest = httpTestingController.expectOne(`${API_BASE_URL}/pessoas/me`);
    secondRequest.flush({ error: 'Falha' }, { status: 500, statusText: 'Error' });

    expect(service.profile()).toBeNull();
    expect(service.errorMessage()).not.toBeNull();
  });

  it('should still issue a single GET when starting a new load after a previous session profile', () => {
    service.loadProfile();

    const firstRequest = httpTestingController.expectOne(`${API_BASE_URL}/pessoas/me`);
    firstRequest.flush(currentUserProfile());

    service.loadProfile();

    const secondRequest = httpTestingController.expectOne(`${API_BASE_URL}/pessoas/me`);

    expect(secondRequest.request.method).toBe('GET');
    secondRequest.flush(currentUserProfile());
  });

  it('should allow retrying loadProfile after an error', () => {
    service.loadProfile();

    const firstRequest = httpTestingController.expectOne(`${API_BASE_URL}/pessoas/me`);
    firstRequest.flush({ error: 'Falha' }, { status: 500, statusText: 'Error' });

    expect(service.errorMessage()).not.toBeNull();

    service.loadProfile();

    const secondRequest = httpTestingController.expectOne(`${API_BASE_URL}/pessoas/me`);
    secondRequest.flush(currentUserProfile());

    expect(service.errorMessage()).toBeNull();
    expect(service.profile()).toEqual(currentUserProfile());
  });

  it('should PUT /pessoas/me when updating the profile', () => {
    const request: CurrentUserProfileUpdateRequest = {
      name: 'João da Silva',
      birthdayDate: '1995-05-20',
    };

    service.updateProfile(request).subscribe();

    const httpRequest = httpTestingController.expectOne(`${API_BASE_URL}/pessoas/me`);

    expect(httpRequest.request.method).toBe('PUT');
    httpRequest.flush(currentUserProfile());
  });

  it('should send a payload containing only name and birthdayDate', () => {
    const request: CurrentUserProfileUpdateRequest = {
      name: 'João da Silva',
      birthdayDate: '1995-05-20',
    };

    service.updateProfile(request).subscribe();

    const httpRequest = httpTestingController.expectOne(`${API_BASE_URL}/pessoas/me`);

    expect(httpRequest.request.body).toEqual(request);
    expect(Object.keys(httpRequest.request.body as Record<string, unknown>)).toEqual([
      'name',
      'birthdayDate',
    ]);
    httpRequest.flush(currentUserProfile());
  });

  it('should update the profile signal with the authoritative PUT response', () => {
    const updatedProfile = currentUserProfile({ name: 'João Atualizado' });

    service.updateProfile({ name: 'João Atualizado', birthdayDate: '1995-05-20' }).subscribe();

    const httpRequest = httpTestingController.expectOne(`${API_BASE_URL}/pessoas/me`);
    httpRequest.flush(updatedProfile);

    expect(service.profile()).toEqual(updatedProfile);
  });

  it('should clear the entire profile state', () => {
    service.loadProfile();

    const request = httpTestingController.expectOne(`${API_BASE_URL}/pessoas/me`);
    request.flush(currentUserProfile());

    expect(service.profile()).not.toBeNull();

    service.clearProfile();

    expect(service.profile()).toBeNull();
    expect(service.isLoading()).toBeFalse();
    expect(service.errorMessage()).toBeNull();
  });

  function currentUserProfile(overrides: Partial<CurrentUserProfile> = {}): CurrentUserProfile {
    return {
      id: 10,
      name: 'João da Silva',
      phoneNumber: '34999999999',
      birthdayDate: '1995-05-20',
      roles: ['ROLE_OPERATOR'],
      ministries: ['READER', 'COMMENTATOR'],
      ...overrides,
    };
  }
});
