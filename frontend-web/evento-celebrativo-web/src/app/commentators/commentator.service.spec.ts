import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_BASE_URL } from '../api.config';
import { CommentatorResponse } from './commentator.models';
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
});
