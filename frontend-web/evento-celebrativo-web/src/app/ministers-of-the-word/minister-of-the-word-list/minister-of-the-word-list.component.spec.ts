import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';

import { AuthSessionService } from '../../auth-session.service';
import { MinisterOfTheWordResponse } from '../minister-of-the-word.models';
import { MinisterOfTheWordService } from '../minister-of-the-word.service';
import { MinisterOfTheWordListComponent } from './minister-of-the-word-list.component';

describe('MinisterOfTheWordListComponent', () => {
  let component: MinisterOfTheWordListComponent;
  let fixture: ComponentFixture<MinisterOfTheWordListComponent>;
  let authSessionService: jasmine.SpyObj<AuthSessionService>;
  let ministerOfTheWordService: jasmine.SpyObj<MinisterOfTheWordService>;

  const ministers: MinisterOfTheWordResponse[] = [
    {
      id: 98765,
      name: 'Maria Ministra',
      phoneNumber: '34999999994',
      birthdayDate: '1985-04-13',
    },
    {
      id: 54321,
      name: 'José Ministro',
      phoneNumber: null,
      birthdayDate: null,
    },
  ];

  async function setup(response = of(ministers), isAdmin = false): Promise<void> {
    authSessionService = jasmine.createSpyObj<AuthSessionService>('AuthSessionService', [
      'hasAuthority',
    ]);
    authSessionService.hasAuthority.and.returnValue(isAdmin);
    ministerOfTheWordService = jasmine.createSpyObj<MinisterOfTheWordService>(
      'MinisterOfTheWordService',
      ['findAll'],
    );
    ministerOfTheWordService.findAll.and.returnValue(response);

    await TestBed.configureTestingModule({
      imports: [MinisterOfTheWordListComponent],
      providers: [
        provideRouter([]),
        { provide: AuthSessionService, useValue: authSessionService },
        { provide: MinisterOfTheWordService, useValue: ministerOfTheWordService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(MinisterOfTheWordListComponent);
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

  it('should request ministers on initialization', async () => {
    await setup();

    fixture.detectChanges();

    expect(ministerOfTheWordService.findAll).toHaveBeenCalledOnceWith();
  });

  it('should show the loading state while the request is pending', async () => {
    const pendingRequest = new Subject<MinisterOfTheWordResponse[]>();
    await setup(pendingRequest);

    fixture.detectChanges();

    expect(component.isLoading()).toBeTrue();
    expect(textContent()).toContain('Carregando ministros da Palavra...');

    pendingRequest.next(ministers);
    pendingRequest.complete();
  });

  it('should render ministers returned by the service', async () => {
    await setup();

    fixture.detectChanges();

    const pageText = textContent();

    expect(pageText).toContain('Maria Ministra');
    expect(pageText).toContain('José Ministro');
  });

  it('should show minister of the Word management link for administrators', async () => {
    await setup(of(ministers), true);

    fixture.detectChanges();

    const link = fixture.nativeElement.querySelector('.page-action') as HTMLAnchorElement;

    expect(authSessionService.hasAuthority).toHaveBeenCalledOnceWith('ROLE_ADMIN');
    expect(link.textContent).toContain('Gerenciar ministros da Palavra');
    expect(link.getAttribute('href')).toBe('/app/admin/ministros-palavra');
  });

  it('should hide minister of the Word management link for operators', async () => {
    await setup(of(ministers), false);

    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.page-action')).toBeNull();
    expect(textContent()).not.toContain('Gerenciar ministros da Palavra');
  });

  it('should keep the common listing without administrative operations', async () => {
    await setup();

    fixture.detectChanges();

    const text = textContent();

    expect(text).toContain('Maria Ministra');
    expect(text).not.toContain('Cadastrar');
    expect(text).not.toContain('Editar');
    expect(text).not.toContain('Excluir');
  });

  it('should not render minister identifiers', async () => {
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

    expect(pageText).not.toContain('34999999994');
    expect(pageText).not.toContain('1985-04-13');
  });

  it('should handle ministers with optional personal fields missing', async () => {
    await setup();

    fixture.detectChanges();

    const items = Array.from(
      fixture.nativeElement.querySelectorAll('.ministers-of-the-word__item'),
    );
    const secondItem = items[1] as HTMLElement;

    expect(secondItem.textContent).toContain('José Ministro');
    expect(secondItem.textContent).not.toContain('null');
  });

  it('should show the empty state when there are no ministers', async () => {
    await setup(of([]));

    fixture.detectChanges();

    expect(textContent()).toContain('Nenhum ministro da Palavra cadastrado foi encontrado.');
  });

  it('should show a generic error message when the request fails', async () => {
    await setup(throwError(() => new HttpErrorResponse({ status: 500 })));

    fixture.detectChanges();

    expect(textContent()).toContain(
      'Não foi possível carregar os ministros da Palavra. Tente novamente.',
    );
  });

  it('should show a permission message when the backend returns 403', async () => {
    await setup(throwError(() => new HttpErrorResponse({ status: 403 })));

    fixture.detectChanges();

    expect(textContent()).toContain(
      'Você não possui permissão para consultar os ministros da Palavra.',
    );
  });

  it('should try loading ministers again when retry button is clicked', async () => {
    await setup();
    ministerOfTheWordService.findAll.and.returnValues(
      throwError(() => new HttpErrorResponse({ status: 500 })),
      of(ministers),
    );

    fixture.detectChanges();
    fixture.detectChanges();

    const retryButton = fixture.nativeElement.querySelector(
      '.ministers-of-the-word__button',
    ) as HTMLButtonElement;
    retryButton.click();
    fixture.detectChanges();

    expect(ministerOfTheWordService.findAll).toHaveBeenCalledTimes(2);
    expect(textContent()).toContain('Maria Ministra');
  });

  function textContent(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }
});
