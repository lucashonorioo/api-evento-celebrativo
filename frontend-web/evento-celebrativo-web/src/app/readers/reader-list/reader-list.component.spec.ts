import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';

import { ReaderResponse } from '../reader.models';
import { ReaderService } from '../reader.service';
import { ReaderListComponent } from './reader-list.component';

describe('ReaderListComponent', () => {
  let component: ReaderListComponent;
  let fixture: ComponentFixture<ReaderListComponent>;
  let readerService: jasmine.SpyObj<ReaderService>;

  const readers: ReaderResponse[] = [
    {
      id: 98765,
      name: 'Maria Leitora',
      phoneNumber: '34999999991',
      birthdayDate: '1990-01-10',
    },
    {
      id: 54321,
      name: 'João Leitor',
      phoneNumber: null,
      birthdayDate: null,
    },
  ];

  async function setup(response = of(readers)): Promise<void> {
    readerService = jasmine.createSpyObj<ReaderService>('ReaderService', ['findAll']);
    readerService.findAll.and.returnValue(response);

    await TestBed.configureTestingModule({
      imports: [ReaderListComponent],
      providers: [{ provide: ReaderService, useValue: readerService }],
    }).compileComponents();

    fixture = TestBed.createComponent(ReaderListComponent);
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

  it('should request readers on initialization', async () => {
    await setup();

    fixture.detectChanges();

    expect(readerService.findAll).toHaveBeenCalledOnceWith();
  });

  it('should show the loading state while the request is pending', async () => {
    const pendingRequest = new Subject<ReaderResponse[]>();
    await setup(pendingRequest);

    fixture.detectChanges();

    expect(component.isLoading()).toBeTrue();
    expect(textContent()).toContain('Carregando leitores...');

    pendingRequest.next(readers);
    pendingRequest.complete();
  });

  it('should render readers returned by the service', async () => {
    await setup();

    fixture.detectChanges();

    const pageText = textContent();

    expect(pageText).toContain('Maria Leitora');
    expect(pageText).toContain('João Leitor');
  });

  it('should not render reader identifiers', async () => {
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

    expect(pageText).not.toContain('34999999991');
    expect(pageText).not.toContain('1990-01-10');
  });

  it('should handle readers with optional personal fields missing', async () => {
    await setup();

    fixture.detectChanges();

    const items = Array.from(fixture.nativeElement.querySelectorAll('.readers__item'));
    const secondItem = items[1] as HTMLElement;

    expect(secondItem.textContent).toContain('João Leitor');
    expect(secondItem.textContent).not.toContain('null');
  });

  it('should show the empty state when there are no readers', async () => {
    await setup(of([]));

    fixture.detectChanges();

    expect(textContent()).toContain('Nenhum leitor cadastrado foi encontrado.');
  });

  it('should show a generic error message when the request fails', async () => {
    await setup(throwError(() => new HttpErrorResponse({ status: 500 })));

    fixture.detectChanges();

    expect(textContent()).toContain('Não foi possível carregar os leitores. Tente novamente.');
  });

  it('should show a permission message when the backend returns 403', async () => {
    await setup(throwError(() => new HttpErrorResponse({ status: 403 })));

    fixture.detectChanges();

    expect(textContent()).toContain('Você não possui permissão para consultar os leitores.');
  });

  it('should try loading readers again when retry button is clicked', async () => {
    await setup();
    readerService.findAll.and.returnValues(
      throwError(() => new HttpErrorResponse({ status: 500 })),
      of(readers),
    );

    fixture.detectChanges();
    fixture.detectChanges();

    const retryButton = fixture.nativeElement.querySelector('.readers__button') as HTMLButtonElement;
    retryButton.click();
    fixture.detectChanges();

    expect(readerService.findAll).toHaveBeenCalledTimes(2);
    expect(textContent()).toContain('Maria Leitora');
  });

  function textContent(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }
});
