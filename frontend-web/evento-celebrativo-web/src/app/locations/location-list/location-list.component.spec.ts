import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';

import { AuthSessionService } from '../../auth-session.service';
import { LocationResponse } from '../location.models';
import { LocationService } from '../location.service';
import { LocationListComponent } from './location-list.component';

describe('LocationListComponent', () => {
  let component: LocationListComponent;
  let fixture: ComponentFixture<LocationListComponent>;
  let authSessionService: jasmine.SpyObj<AuthSessionService>;
  let locationService: jasmine.SpyObj<LocationService>;

  const locations: LocationResponse[] = [
    {
      id: 98765,
      churchName: 'Igreja Matriz',
      address: 'Rua Central',
    },
    {
      id: 54321,
      churchName: 'Capela Santa Luzia',
      address: '',
    },
  ];

  async function setup(response = of(locations), isAdmin = false): Promise<void> {
    authSessionService = jasmine.createSpyObj<AuthSessionService>('AuthSessionService', [
      'hasAuthority',
    ]);
    authSessionService.hasAuthority.and.returnValue(isAdmin);
    locationService = jasmine.createSpyObj<LocationService>('LocationService', ['findAll']);
    locationService.findAll.and.returnValue(response);

    await TestBed.configureTestingModule({
      imports: [LocationListComponent],
      providers: [
        provideRouter([]),
        { provide: AuthSessionService, useValue: authSessionService },
        { provide: LocationService, useValue: locationService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(LocationListComponent);
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

  it('should request locations on initialization', async () => {
    await setup();

    fixture.detectChanges();

    expect(locationService.findAll).toHaveBeenCalledOnceWith();
  });

  it('should show the loading state while the request is pending', async () => {
    const pendingRequest = new Subject<LocationResponse[]>();
    await setup(pendingRequest);

    fixture.detectChanges();

    expect(component.isLoading()).toBeTrue();
    expect(textContent()).toContain('Carregando locais...');

    pendingRequest.next(locations);
    pendingRequest.complete();
  });

  it('should render locations returned by the service', async () => {
    await setup();

    fixture.detectChanges();

    const pageText = textContent();

    expect(pageText).toContain('Igreja Matriz');
    expect(pageText).toContain('Rua Central');
    expect(pageText).toContain('Capela Santa Luzia');
  });

  it('should show location management link for administrators', async () => {
    await setup(of(locations), true);

    fixture.detectChanges();

    const link = fixture.nativeElement.querySelector('.page-action') as HTMLAnchorElement;

    expect(authSessionService.hasAuthority).toHaveBeenCalledOnceWith('ROLE_ADMIN');
    expect(link.textContent).toContain('Gerenciar locais');
    expect(link.getAttribute('href')).toBe('/app/admin/locais');
  });

  it('should hide location management link for operators', async () => {
    await setup(of(locations), false);

    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.page-action')).toBeNull();
    expect(textContent()).not.toContain('Gerenciar locais');
  });

  it('should keep the common listing without administrative operations', async () => {
    await setup();

    fixture.detectChanges();

    const text = textContent();

    expect(text).toContain('Igreja Matriz');
    expect(text).not.toContain('Cadastrar');
    expect(text).not.toContain('Editar');
    expect(text).not.toContain('Excluir');
  });

  it('should not render location identifiers', async () => {
    await setup();

    fixture.detectChanges();

    const pageText = textContent();

    expect(pageText).not.toContain('98765');
    expect(pageText).not.toContain('54321');
  });

  it('should not render an empty address label when the address is empty', async () => {
    await setup();

    fixture.detectChanges();

    const items = Array.from(fixture.nativeElement.querySelectorAll('.locations__item'));
    const secondItem = items[1] as HTMLElement;

    expect(secondItem.textContent).toContain('Capela Santa Luzia');
    expect(secondItem.querySelector('p')).toBeNull();
  });

  it('should show the empty state when there are no locations', async () => {
    await setup(of([]));

    fixture.detectChanges();

    expect(textContent()).toContain('Nenhum local cadastrado foi encontrado.');
  });

  it('should show a generic error message when the request fails', async () => {
    await setup(throwError(() => new HttpErrorResponse({ status: 500 })));

    fixture.detectChanges();

    expect(textContent()).toContain('Não foi possível carregar os locais. Tente novamente.');
  });

  it('should show a permission message when the backend returns 403', async () => {
    await setup(throwError(() => new HttpErrorResponse({ status: 403 })));

    fixture.detectChanges();

    expect(textContent()).toContain('Você não possui permissão para consultar os locais.');
  });

  it('should try loading locations again when retry button is clicked', async () => {
    await setup();
    locationService.findAll.and.returnValues(
      throwError(() => new HttpErrorResponse({ status: 500 })),
      of(locations),
    );

    fixture.detectChanges();
    fixture.detectChanges();

    const retryButton = fixture.nativeElement.querySelector(
      '.locations__button',
    ) as HTMLButtonElement;
    retryButton.click();
    fixture.detectChanges();

    expect(locationService.findAll).toHaveBeenCalledTimes(2);
    expect(textContent()).toContain('Igreja Matriz');
  });

  function textContent(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }
});
