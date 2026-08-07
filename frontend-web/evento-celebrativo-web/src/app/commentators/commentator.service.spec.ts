import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_BASE_URL } from '../api.config';
import { CommentatorCreateRequest, CommentatorResponse, CommentatorUpdateRequest } from './commentator.models';
import { CommentatorService } from './commentator.service';

describe('CommentatorService', () => {
  let service: CommentatorService;
  let httpTestingController: HttpTestingController;

  const commentators: CommentatorResponse[] = [
    {
      id: 1,
      name: 'Maria Comentarista',
      phoneNumber: '34999999992',
      birthdayDate: '1991-02-11',
    },
  ];
  const createRequestWithoutAccess: CommentatorCreateRequest = {
    name: 'Maria Comentarista',
    phoneNumber: '34999999992',
    birthdayDate: '1991-02-11',
    createAccess: false,
  };
  const createRequestWithAccess: CommentatorCreateRequest = {
    name: 'Maria Comentarista',
    phoneNumber: '34999999992',
    birthdayDate: '1991-02-11',
    createAccess: true,
    password: '123456',
    accessRole: 'ROLE_OPERATOR',
  };
  const updateRequest: CommentatorUpdateRequest = {
    name: 'Maria Comentarista',
    phoneNumber: '34999999992',
    birthdayDate: '1991-02-11',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });

    service = TestBed.inject(CommentatorService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  it('should request all commentators from the authenticated endpoint', () => {
    service.findAll().subscribe((response) => {
      expect(response).toEqual(commentators);
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/comentaristas`);

    expect(request.request.method).toBe('GET');
    expect(request.request.headers.has('Authorization')).toBeFalse();

    request.flush(commentators);
  });

  it('should return an empty list when the API returns no commentators', () => {
    service.findAll().subscribe((response) => {
      expect(response).toEqual([]);
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/comentaristas`);

    request.flush([]);
  });

  it('should propagate HTTP errors', (done) => {
    service.findAll().subscribe({
      next: () => {
        fail('Expected commentators request to fail');
      },
      error: (error: unknown) => {
        expect(error).toBeTruthy();
        done();
      },
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/comentaristas`);
    request.flush(
      { message: 'Forbidden' },
      {
        status: 403,
        statusText: 'Forbidden',
      },
    );
  });

  it('should create a commentator without access, sending createAccess=false and no credentials', () => {
    const createdCommentator: CommentatorResponse = {
      id: 2,
      name: createRequestWithoutAccess.name,
      phoneNumber: createRequestWithoutAccess.phoneNumber,
      birthdayDate: createRequestWithoutAccess.birthdayDate,
    };

    service.create(createRequestWithoutAccess).subscribe((response) => {
      expect(response).toEqual(createdCommentator);
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/comentaristas`);

    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(createRequestWithoutAccess);
    expect(request.request.body.password).toBeUndefined();
    expect(request.request.body.accessRole).toBeUndefined();
    expect(request.request.body.confirmPassword).toBeUndefined();
    expect(request.request.headers.has('Authorization')).toBeFalse();

    request.flush(createdCommentator);
  });

  it('should create a commentator with access, sending createAccess=true, password and ROLE_OPERATOR', () => {
    const createdCommentator: CommentatorResponse = {
      id: 2,
      name: createRequestWithAccess.name,
      phoneNumber: createRequestWithAccess.phoneNumber,
      birthdayDate: createRequestWithAccess.birthdayDate,
    };

    service.create(createRequestWithAccess).subscribe((response) => {
      expect(response).toEqual(createdCommentator);
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/comentaristas`);

    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(createRequestWithAccess);
    expect(request.request.body.accessRole).toBe('ROLE_OPERATOR');
    expect(request.request.body.confirmPassword).toBeUndefined();
    expect(request.request.headers.has('Authorization')).toBeFalse();

    request.flush(createdCommentator);
  });

  it('should update a commentator sending only cadastral fields, without account fields', () => {
    const updatedCommentator: CommentatorResponse = {
      id: 1,
      name: updateRequest.name,
      phoneNumber: updateRequest.phoneNumber,
      birthdayDate: updateRequest.birthdayDate,
    };

    service.update(1, updateRequest).subscribe((response) => {
      expect(response).toEqual(updatedCommentator);
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/comentaristas/1`);

    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual(updateRequest);
    expect(Object.keys(request.request.body)).toEqual(['name', 'phoneNumber', 'birthdayDate']);
    expect(request.request.body.password).toBeUndefined();
    expect(request.request.body.createAccess).toBeUndefined();
    expect(request.request.body.accessRole).toBeUndefined();
    expect(request.request.body.confirmPassword).toBeUndefined();
    expect(request.request.headers.has('Authorization')).toBeFalse();

    request.flush(updatedCommentator);
  });

  it('should delete a commentator without adding authorization manually', () => {
    service.delete(1).subscribe((response) => {
      expect(response).toBeNull();
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/comentaristas/1`);

    expect(request.request.method).toBe('DELETE');
    expect(request.request.body).toBeNull();
    expect(request.request.headers.has('Authorization')).toBeFalse();

    request.flush(null);
  });

  [400, 403, 404, 409].forEach((status) => {
    it(`should propagate ${status} errors when creating commentators without access`, (done) => {
      service.create(createRequestWithoutAccess).subscribe({
        next: () => {
          fail('Expected create request to fail');
        },
        error: (error: unknown) => {
          expect(error).toBeTruthy();
          done();
        },
      });

      const request = httpTestingController.expectOne(`${API_BASE_URL}/comentaristas`);
      request.flush({ message: 'Error' }, { status, statusText: 'Error' });
    });

    it(`should propagate ${status} errors when creating commentators with access`, (done) => {
      service.create(createRequestWithAccess).subscribe({
        next: () => {
          fail('Expected create request to fail');
        },
        error: (error: unknown) => {
          expect(error).toBeTruthy();
          done();
        },
      });

      const request = httpTestingController.expectOne(`${API_BASE_URL}/comentaristas`);
      request.flush({ message: 'Error' }, { status, statusText: 'Error' });
    });

    it(`should propagate ${status} errors when updating commentators`, (done) => {
      service.update(1, updateRequest).subscribe({
        next: () => {
          fail('Expected update request to fail');
        },
        error: (error: unknown) => {
          expect(error).toBeTruthy();
          done();
        },
      });

      const request = httpTestingController.expectOne(`${API_BASE_URL}/comentaristas/1`);
      request.flush({ message: 'Error' }, { status, statusText: 'Error' });
    });

    it(`should propagate ${status} errors when deleting commentators`, (done) => {
      service.delete(1).subscribe({
        next: () => {
          fail('Expected delete request to fail');
        },
        error: (error: unknown) => {
          expect(error).toBeTruthy();
          done();
        },
      });

      const request = httpTestingController.expectOne(`${API_BASE_URL}/comentaristas/1`);
      request.flush({ message: 'Error' }, { status, statusText: 'Error' });
    });
  });
});
