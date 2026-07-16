import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_BASE_URL } from '../api.config';
import { CommentatorRequest, CommentatorResponse } from './commentator.models';
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
  const commentatorRequest: CommentatorRequest = {
    name: 'Maria Comentarista',
    phoneNumber: '34999999992',
    birthdayDate: '1991-02-11',
    password: '123456',
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

  it('should create a commentator without adding authorization manually', () => {
    const createdCommentator: CommentatorResponse = {
      id: 2,
      name: commentatorRequest.name,
      phoneNumber: commentatorRequest.phoneNumber,
      birthdayDate: commentatorRequest.birthdayDate,
    };

    service.create(commentatorRequest).subscribe((response) => {
      expect(response).toEqual(createdCommentator);
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/comentaristas`);

    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(commentatorRequest);
    expect(request.request.headers.has('Authorization')).toBeFalse();

    request.flush(createdCommentator);
  });

  it('should update a commentator without adding authorization manually', () => {
    const updatedCommentator: CommentatorResponse = {
      id: 1,
      name: commentatorRequest.name,
      phoneNumber: commentatorRequest.phoneNumber,
      birthdayDate: commentatorRequest.birthdayDate,
    };

    service.update(1, commentatorRequest).subscribe((response) => {
      expect(response).toEqual(updatedCommentator);
    });

    const request = httpTestingController.expectOne(`${API_BASE_URL}/comentaristas/1`);

    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual(commentatorRequest);
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
    it(`should propagate ${status} errors when creating commentators`, (done) => {
      service.create(commentatorRequest).subscribe({
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
      service.update(1, commentatorRequest).subscribe({
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
