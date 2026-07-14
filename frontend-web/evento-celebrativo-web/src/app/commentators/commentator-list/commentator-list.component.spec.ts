import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';

import { CommentatorResponse } from '../commentator.models';
import { CommentatorService } from '../commentator.service';
import { CommentatorListComponent } from './commentator-list.component';

describe('CommentatorListComponent', () => {
  let component: CommentatorListComponent;
  let fixture: ComponentFixture<CommentatorListComponent>;
  let commentatorService: jasmine.SpyObj<CommentatorService>;

  const commentators: CommentatorResponse[] = [
    {
      id: 98765,
      name: 'Maria Comentarista',
      phoneNumber: '34999999992',
      birthdayDate: '1991-02-11',
    },
    {
      id: 54321,
      name: 'João Comentarista',
      phoneNumber: null,
      birthdayDate: null,
    },
  ];

  async function setup(response = of(commentators)): Promise<void> {
    commentatorService = jasmine.createSpyObj<CommentatorService>('CommentatorService', [
      'findAll',
    ]);
    commentatorService.findAll.and.returnValue(response);

    await TestBed.configureTestingModule({
      imports: [CommentatorListComponent],
      providers: [{ provide: CommentatorService, useValue: commentatorService }],
    }).compileComponents();

    fixture = TestBed.createComponent(CommentatorListComponent);
    component = fixture.componentInstance;
  }

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('should create', async () => {
    await setup();

    fixture.detectChanges();

    expect(component).toBeTruthy();
  });

  it('should request commentators on initialization', async () => {
    await setup();

    fixture.detectChanges();

    expect(commentatorService.findAll).toHaveBeenCalledOnceWith();
  });

  it('should show the loading state while the request is pending', async () => {
    const pendingRequest = new Subject<CommentatorResponse[]>();
    await setup(pendingRequest);

    fixture.detectChanges();

    expect(component.isLoading()).toBeTrue();
    expect(textContent()).toContain('Carregando comentaristas...');

    pendingRequest.next(commentators);
    pendingRequest.complete();
  });

  it('should render commentators returned by the service', async () => {
    await setup();

    fixture.detectChanges();

    const pageText = textContent();

    expect(pageText).toContain('Maria Comentarista');
    expect(pageText).toContain('João Comentarista');
  });

  it('should not render commentator identifiers', async () => {
    await setup();

    fixture.detectChanges();

    const pageText = textContent();

    expect(pageText).not.toContain('98765');
    expect(pageText).not.toContain('54321');
  });

  it('should not render personal fields that are not required for the listing', async () => {
    await setup();

    fixture.detectChanges();

    const pageText = textContent();

    expect(pageText).not.toContain('34999999992');
    expect(pageText).not.toContain('1991-02-11');
  });

  it('should handle commentators with optional personal fields missing', async () => {
    await setup();

    fixture.detectChanges();

    const items = Array.from(fixture.nativeElement.querySelectorAll('.commentators__item'));
    const secondItem = items[1] as HTMLElement;

    expect(secondItem.textContent).toContain('João Comentarista');
    expect(secondItem.textContent).not.toContain('null');
  });

  it('should show the empty state when there are no commentators', async () => {
    await setup(of([]));

    fixture.detectChanges();

    expect(textContent()).toContain('Nenhum comentarista cadastrado foi encontrado.');
  });

  it('should show a generic error message when the request fails', async () => {
    await setup(throwError(() => new HttpErrorResponse({ status: 500 })));

    fixture.detectChanges();

    expect(textContent()).toContain('Não foi possível carregar os comentaristas. Tente novamente.');
  });

  it('should show a permission message when the backend returns 403', async () => {
    await setup(throwError(() => new HttpErrorResponse({ status: 403 })));

    fixture.detectChanges();

    expect(textContent()).toContain('Você não possui permissão para consultar os comentaristas.');
  });

  it('should try loading commentators again when retry button is clicked', async () => {
    await setup();
    commentatorService.findAll.and.returnValues(
      throwError(() => new HttpErrorResponse({ status: 500 })),
      of(commentators),
    );

    fixture.detectChanges();
    fixture.detectChanges();

    const retryButton = fixture.nativeElement.querySelector(
      '.commentators__button',
    ) as HTMLButtonElement;
    retryButton.click();
    fixture.detectChanges();

    expect(commentatorService.findAll).toHaveBeenCalledTimes(2);
    expect(textContent()).toContain('Maria Comentarista');
  });

  function textContent(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }
});
