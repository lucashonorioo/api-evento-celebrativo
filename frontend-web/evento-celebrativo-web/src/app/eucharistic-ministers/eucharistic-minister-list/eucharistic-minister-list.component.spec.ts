import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';

import { EucharisticMinisterResponse } from '../eucharistic-minister.models';
import { EucharisticMinisterService } from '../eucharistic-minister.service';
import { EucharisticMinisterListComponent } from './eucharistic-minister-list.component';

describe('EucharisticMinisterListComponent', () => {
  let component: EucharisticMinisterListComponent;
  let fixture: ComponentFixture<EucharisticMinisterListComponent>;
  let eucharisticMinisterService: jasmine.SpyObj<EucharisticMinisterService>;

  const ministers: EucharisticMinisterResponse[] = [
    {
      id: 98765,
      name: 'Ana Ministra',
      phoneNumber: '34999999995',
      birthdayDate: '1986-05-14',
    },
    {
      id: 54321,
      name: 'Carlos Ministro',
      phoneNumber: null,
      birthdayDate: null,
    },
  ];

  async function setup(response = of(ministers)): Promise<void> {
    eucharisticMinisterService = jasmine.createSpyObj<EucharisticMinisterService>(
      'EucharisticMinisterService',
      ['findAll'],
    );
    eucharisticMinisterService.findAll.and.returnValue(response);

    await TestBed.configureTestingModule({
      imports: [EucharisticMinisterListComponent],
      providers: [
        { provide: EucharisticMinisterService, useValue: eucharisticMinisterService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(EucharisticMinisterListComponent);
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

    expect(eucharisticMinisterService.findAll).toHaveBeenCalledOnceWith();
  });

  it('should show the loading state while the request is pending', async () => {
    const pendingRequest = new Subject<EucharisticMinisterResponse[]>();
    await setup(pendingRequest);

    fixture.detectChanges();

    expect(component.isLoading()).toBeTrue();
    expect(textContent()).toContain('Carregando ministros da Eucaristia...');

    pendingRequest.next(ministers);
    pendingRequest.complete();
  });

  it('should render ministers returned by the service', async () => {
    await setup();

    fixture.detectChanges();

    const pageText = textContent();

    expect(pageText).toContain('Ana Ministra');
    expect(pageText).toContain('Carlos Ministro');
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

    expect(pageText).not.toContain('34999999995');
    expect(pageText).not.toContain('1986-05-14');
  });

  it('should handle ministers with optional personal fields missing', async () => {
    await setup();

    fixture.detectChanges();

    const items = Array.from(
      fixture.nativeElement.querySelectorAll('.eucharistic-ministers__item'),
    );
    const secondItem = items[1] as HTMLElement;

    expect(secondItem.textContent).toContain('Carlos Ministro');
    expect(secondItem.textContent).not.toContain('null');
  });

  it('should show the empty state when there are no ministers', async () => {
    await setup(of([]));

    fixture.detectChanges();

    expect(textContent()).toContain(
      'Nenhum ministro da Eucaristia cadastrado foi encontrado.',
    );
  });

  it('should show a generic error message when the request fails', async () => {
    await setup(throwError(() => new HttpErrorResponse({ status: 500 })));

    fixture.detectChanges();

    expect(textContent()).toContain(
      'Não foi possível carregar os ministros da Eucaristia. Tente novamente.',
    );
  });

  it('should show a permission message when the backend returns 403', async () => {
    await setup(throwError(() => new HttpErrorResponse({ status: 403 })));

    fixture.detectChanges();

    expect(textContent()).toContain(
      'Você não possui permissão para consultar os ministros da Eucaristia.',
    );
  });

  it('should try loading ministers again when retry button is clicked', async () => {
    await setup();
    eucharisticMinisterService.findAll.and.returnValues(
      throwError(() => new HttpErrorResponse({ status: 500 })),
      of(ministers),
    );

    fixture.detectChanges();
    fixture.detectChanges();

    const retryButton = fixture.nativeElement.querySelector(
      '.eucharistic-ministers__button',
    ) as HTMLButtonElement;
    retryButton.click();
    fixture.detectChanges();

    expect(eucharisticMinisterService.findAll).toHaveBeenCalledTimes(2);
    expect(textContent()).toContain('Ana Ministra');
  });

  function textContent(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }
});
