import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';

import { AuthSessionService } from '../../auth-session.service';
import { PriestResponse } from '../priest.models';
import { PriestService } from '../priest.service';
import { PriestListComponent } from './priest-list.component';

describe('PriestListComponent', () => {
  let component: PriestListComponent;
  let fixture: ComponentFixture<PriestListComponent>;
  let authSessionService: jasmine.SpyObj<AuthSessionService>;
  let priestService: jasmine.SpyObj<PriestService>;

  const priests: PriestResponse[] = [
    {
      id: 98765,
      name: 'Padre João',
      phoneNumber: '34999999993',
      birthdayDate: '1980-03-12',
    },
    {
      id: 54321,
      name: 'Padre Pedro',
      phoneNumber: null,
      birthdayDate: null,
    },
  ];

  async function setup(response = of(priests), isAdmin = false): Promise<void> {
    authSessionService = jasmine.createSpyObj<AuthSessionService>('AuthSessionService', [
      'hasAuthority',
    ]);
    authSessionService.hasAuthority.and.returnValue(isAdmin);
    priestService = jasmine.createSpyObj<PriestService>('PriestService', ['findAll']);
    priestService.findAll.and.returnValue(response);

    await TestBed.configureTestingModule({
      imports: [PriestListComponent],
      providers: [
        provideRouter([]),
        { provide: AuthSessionService, useValue: authSessionService },
        { provide: PriestService, useValue: priestService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(PriestListComponent);
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

  it('should request priests on initialization', async () => {
    await setup();

    fixture.detectChanges();

    expect(priestService.findAll).toHaveBeenCalledOnceWith();
  });

  it('should show the loading state while the request is pending', async () => {
    const pendingRequest = new Subject<PriestResponse[]>();
    await setup(pendingRequest);

    fixture.detectChanges();

    expect(component.isLoading()).toBeTrue();
    expect(textContent()).toContain('Carregando padres...');

    pendingRequest.next(priests);
    pendingRequest.complete();
  });

  it('should render priests returned by the service', async () => {
    await setup();

    fixture.detectChanges();

    const pageText = textContent();

    expect(pageText).toContain('Padre João');
    expect(pageText).toContain('Padre Pedro');
  });

  it('should show priest management link for administrators', async () => {
    await setup(of(priests), true);

    fixture.detectChanges();

    const link = fixture.nativeElement.querySelector('.page-action') as HTMLAnchorElement;

    expect(authSessionService.hasAuthority).toHaveBeenCalledOnceWith('ROLE_ADMIN');
    expect(link.textContent).toContain('Gerenciar padres');
    expect(link.getAttribute('href')).toBe('/app/admin/padres');
  });

  it('should hide priest management link for operators', async () => {
    await setup(of(priests), false);

    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.page-action')).toBeNull();
    expect(textContent()).not.toContain('Gerenciar padres');
  });

  it('should keep the common listing without administrative operations', async () => {
    await setup();

    fixture.detectChanges();

    const text = textContent();

    expect(text).toContain('Padre Pedro');
    expect(text).not.toContain('Cadastrar');
    expect(text).not.toContain('Editar');
    expect(text).not.toContain('Excluir');
  });

  it('should not render priest identifiers', async () => {
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

    expect(pageText).not.toContain('34999999993');
    expect(pageText).not.toContain('1980-03-12');
  });

  it('should handle priests with optional personal fields missing', async () => {
    await setup();

    fixture.detectChanges();

    const items = Array.from(fixture.nativeElement.querySelectorAll('.priests__item'));
    const secondItem = items[1] as HTMLElement;

    expect(secondItem.textContent).toContain('Padre Pedro');
    expect(secondItem.textContent).not.toContain('null');
  });

  it('should show the empty state when there are no priests', async () => {
    await setup(of([]));

    fixture.detectChanges();

    expect(textContent()).toContain('Nenhum padre cadastrado foi encontrado.');
  });

  it('should show a generic error message when the request fails', async () => {
    await setup(throwError(() => new HttpErrorResponse({ status: 500 })));

    fixture.detectChanges();

    expect(textContent()).toContain('Não foi possível carregar os padres. Tente novamente.');
  });

  it('should show a permission message when the backend returns 403', async () => {
    await setup(throwError(() => new HttpErrorResponse({ status: 403 })));

    fixture.detectChanges();

    expect(textContent()).toContain('Você não possui permissão para consultar os padres.');
  });

  it('should try loading priests again when retry button is clicked', async () => {
    await setup();
    priestService.findAll.and.returnValues(
      throwError(() => new HttpErrorResponse({ status: 500 })),
      of(priests),
    );

    fixture.detectChanges();
    fixture.detectChanges();

    const retryButton = fixture.nativeElement.querySelector('.priests__button') as HTMLButtonElement;
    retryButton.click();
    fixture.detectChanges();

    expect(priestService.findAll).toHaveBeenCalledTimes(2);
    expect(textContent()).toContain('Padre João');
  });

  function textContent(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }
});
