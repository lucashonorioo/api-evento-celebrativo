import { HttpErrorResponse } from '@angular/common/http';
import { WritableSignal, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';

import {
  CurrentUserProfile,
  CurrentUserProfileUpdateRequest,
} from './current-user-profile.models';
import { CurrentUserProfileService } from './current-user-profile.service';
import { CurrentUserProfileComponent } from './current-user-profile.component';

const NOW = new Date(2026, 6, 20, 10, 0, 0);

interface CurrentUserProfileServiceDouble {
  readonly profile: WritableSignal<CurrentUserProfile | null>;
  readonly isLoading: WritableSignal<boolean>;
  readonly errorMessage: WritableSignal<string | null>;
  readonly loadProfile: jasmine.Spy;
  readonly updateProfile: jasmine.Spy;
  readonly clearProfile: jasmine.Spy;
}

describe('CurrentUserProfileComponent', () => {
  let fixture: ComponentFixture<CurrentUserProfileComponent>;
  let component: CurrentUserProfileComponent;
  let currentUserProfileService: CurrentUserProfileServiceDouble;

  async function setup(
    options: {
      profile?: CurrentUserProfile | null;
      isLoading?: boolean;
      errorMessage?: string | null;
    } = {},
  ): Promise<void> {
    currentUserProfileService = {
      profile: signal(options.profile === undefined ? createProfile() : options.profile),
      isLoading: signal(options.isLoading ?? false),
      errorMessage: signal(options.errorMessage ?? null),
      loadProfile: jasmine.createSpy('loadProfile'),
      updateProfile: jasmine.createSpy('updateProfile').and.returnValue(of(createProfile())),
      clearProfile: jasmine.createSpy('clearProfile'),
    };

    await TestBed.configureTestingModule({
      imports: [CurrentUserProfileComponent],
      providers: [{ provide: CurrentUserProfileService, useValue: currentUserProfileService }],
    }).compileComponents();

    fixture = TestBed.createComponent(CurrentUserProfileComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  beforeEach(() => {
    jasmine.clock().install();
    jasmine.clock().mockDate(NOW);
  });

  afterEach(() => {
    jasmine.clock().uninstall();
    TestBed.resetTestingModule();
  });

  it('should create', async () => {
    await setup();

    expect(component).toBeTruthy();
  });

  it('should show the loading state initially', async () => {
    await setup({ profile: null, isLoading: true });

    expect(textContent()).toContain('Carregando perfil...');
  });

  it('should fill the form once the profile is available', async () => {
    await setup({ profile: createProfile({ name: 'João da Silva', birthdayDate: '1995-05-20' }) });

    expect(component.form.controls.name.value).toBe('João da Silva');
    expect(component.form.controls.birthdayDate.value).toBe('1995-05-20');
  });

  it('should show the phone number as read-only information', async () => {
    await setup({ profile: createProfile({ phoneNumber: '34999999999' }) });

    const text = textContent();

    expect(text).toContain('Telefone de acesso');
    expect(text).toContain('34999999999');
    expect(fixture.nativeElement.querySelectorAll('input').length).toBe(2);
  });

  it('should list roles as read-only information', async () => {
    await setup({ profile: createProfile({ roles: ['ROLE_ADMIN', 'ROLE_OPERATOR'] }) });

    const text = textContent();

    expect(text).toContain('Administrador');
    expect(text).toContain('Operador');
  });

  it('should list ministries as read-only information', async () => {
    await setup({ profile: createProfile({ ministries: ['READER', 'PRIEST'] }) });

    const text = textContent();

    expect(text).toContain('Leitor');
    expect(text).toContain('Padre');
  });

  it('should show a message when there are no active ministries', async () => {
    await setup({ profile: createProfile({ ministries: [] }) });

    expect(textContent()).toContain('Nenhum ministério ativo.');
  });

  it('should require the name field', async () => {
    await setup();
    component.form.controls.name.setValue('');

    clickButton('Salvar alterações');

    expect(textContent()).toContain('Informe o nome.');
    expect(currentUserProfileService.updateProfile).not.toHaveBeenCalled();
  });

  it('should reject a name with only spaces', async () => {
    await setup();
    component.form.controls.name.setValue('   ');

    clickButton('Salvar alterações');

    expect(textContent()).toContain('Informe o nome.');
    expect(currentUserProfileService.updateProfile).not.toHaveBeenCalled();
  });

  it('should require the birthday date field', async () => {
    await setup();
    component.form.controls.birthdayDate.setValue('');

    clickButton('Salvar alterações');

    expect(textContent()).toContain('Informe a data de nascimento.');
    expect(currentUserProfileService.updateProfile).not.toHaveBeenCalled();
  });

  it('should reject today as the birthday date', async () => {
    await setup();
    component.form.controls.birthdayDate.setValue('2026-07-20');

    clickButton('Salvar alterações');

    expect(textContent()).toContain('A data de nascimento deve ser anterior a hoje.');
    expect(currentUserProfileService.updateProfile).not.toHaveBeenCalled();
  });

  it('should reject a future birthday date', async () => {
    await setup();
    component.form.controls.birthdayDate.setValue('2026-07-21');

    clickButton('Salvar alterações');

    expect(textContent()).toContain('A data de nascimento deve ser anterior a hoje.');
    expect(currentUserProfileService.updateProfile).not.toHaveBeenCalled();
  });

  it('should accept a past birthday date', async () => {
    await setup();
    component.form.patchValue({ name: 'João da Silva', birthdayDate: '1995-05-20' });

    clickButton('Salvar alterações');

    expect(currentUserProfileService.updateProfile).toHaveBeenCalled();
  });

  it('should trim the name before sending it', async () => {
    await setup();
    component.form.patchValue({ name: '  João da Silva  ', birthdayDate: '1995-05-20' });

    clickButton('Salvar alterações');

    expect(currentUserProfileService.updateProfile).toHaveBeenCalledOnceWith({
      name: 'João da Silva',
      birthdayDate: '1995-05-20',
    });
  });

  it('should send a payload containing only name and birthdayDate', async () => {
    await setup();
    component.form.patchValue({ name: 'João da Silva', birthdayDate: '1995-05-20' });

    clickButton('Salvar alterações');

    const payload = currentUserProfileService.updateProfile.calls.mostRecent()
      .args[0] as CurrentUserProfileUpdateRequest;

    expect(Object.keys(payload)).toEqual(['name', 'birthdayDate']);
  });

  it('should prevent duplicate submissions while saving', async () => {
    await setup();
    const pending = new Subject<CurrentUserProfile>();
    currentUserProfileService.updateProfile.and.returnValue(pending);
    component.form.patchValue({ name: 'João da Silva', birthdayDate: '1995-05-20' });

    component.submit();
    component.submit();

    expect(currentUserProfileService.updateProfile).toHaveBeenCalledTimes(1);

    pending.next(createProfile());
    pending.complete();
  });

  it('should keep the form filled after a successful save', async () => {
    await setup();
    currentUserProfileService.updateProfile.and.returnValue(
      of(createProfile({ name: 'João Atualizado', birthdayDate: '1995-05-20' })),
    );
    component.form.patchValue({ name: 'João Atualizado', birthdayDate: '1995-05-20' });

    clickButton('Salvar alterações');

    expect(component.form.controls.name.value).toBe('João Atualizado');
    expect(textContent()).toContain('Perfil atualizado com sucesso.');
  });

  it('should reapply the PUT response to the form', async () => {
    await setup();
    currentUserProfileService.updateProfile.and.returnValue(
      of(createProfile({ name: 'Nome Normalizado', birthdayDate: '1995-05-20' })),
    );
    component.form.patchValue({ name: 'joão da silva', birthdayDate: '1995-05-20' });

    clickButton('Salvar alterações');

    expect(component.form.controls.name.value).toBe('Nome Normalizado');
  });

  it('should update the shared profile state through the service after a successful save', async () => {
    await setup();
    const updated = createProfile({ name: 'Atualizado', birthdayDate: '1995-05-20' });
    currentUserProfileService.updateProfile.and.callFake(() => {
      currentUserProfileService.profile.set(updated);
      return of(updated);
    });
    component.form.patchValue({ name: 'Atualizado', birthdayDate: '1995-05-20' });

    clickButton('Salvar alterações');

    expect(currentUserProfileService.profile()).toEqual(updated);
  });

  it('should show a message for 400 while keeping the entered values, the central profile and allowing a retry', async () => {
    const originalProfile = createProfile({ name: 'Maria Silva' });
    await setup({ profile: originalProfile });
    currentUserProfileService.updateProfile.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 400 })),
    );
    component.form.patchValue({ name: 'João da Silva', birthdayDate: '1995-05-20' });

    clickButton('Salvar alterações');

    expect(textContent()).toContain('inválidos');
    expect(component.form.controls.name.value).toBe('João da Silva');
    expect(component.form.controls.birthdayDate.value).toBe('1995-05-20');
    expect(currentUserProfileService.clearProfile).not.toHaveBeenCalled();
    expect(currentUserProfileService.profile()).toBe(originalProfile);
    expect(component.isSaving()).toBeFalse();

    currentUserProfileService.updateProfile.and.returnValue(
      of(createProfile({ name: 'João da Silva', birthdayDate: '1995-05-20' })),
    );
    clickButton('Salvar alterações');

    expect(currentUserProfileService.updateProfile).toHaveBeenCalledTimes(2);
    expect(textContent()).toContain('Perfil atualizado com sucesso.');
  });

  it('should show a message for 403 when saving', async () => {
    await setup();
    currentUserProfileService.updateProfile.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 403 })),
    );
    component.form.patchValue({ name: 'João da Silva', birthdayDate: '1995-05-20' });

    clickButton('Salvar alterações');

    expect(textContent()).toContain('Não foi possível atualizar o seu perfil.');
  });

  it('should show a message for 404 when saving', async () => {
    await setup();
    currentUserProfileService.updateProfile.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 404 })),
    );
    component.form.patchValue({ name: 'João da Silva', birthdayDate: '1995-05-20' });

    clickButton('Salvar alterações');

    expect(textContent()).toContain(
      'Não foi possível localizar o perfil associado à sua conta autenticada.',
    );
  });

  it('should show a generic message for unexpected errors', async () => {
    await setup();
    currentUserProfileService.updateProfile.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 500 })),
    );
    component.form.patchValue({ name: 'João da Silva', birthdayDate: '1995-05-20' });

    clickButton('Salvar alterações');

    expect(textContent()).toContain('Não foi possível atualizar o seu perfil. Tente novamente.');
  });

  it('should retry loading the profile', async () => {
    await setup({
      profile: null,
      isLoading: false,
      errorMessage: 'Não foi possível carregar o seu perfil. Tente novamente.',
    });

    clickButton('Tentar novamente');

    expect(currentUserProfileService.loadProfile).toHaveBeenCalledTimes(1);
  });

  it('should expose basic accessibility structure', async () => {
    await setup();

    const compiled = fixture.nativeElement as HTMLElement;
    const submitButton = Array.from(compiled.querySelectorAll('button')).find(
      (button) => button.textContent?.trim() === 'Salvar alterações',
    ) as HTMLButtonElement;

    expect(compiled.querySelectorAll('h1').length).toBe(1);
    expect(compiled.querySelector('label[for="profile-name"]')).not.toBeNull();
    expect(compiled.querySelector('label[for="profile-birthday"]')).not.toBeNull();
    expect(compiled.querySelector('#profile-name')).not.toBeNull();
    expect(compiled.querySelector('#profile-birthday')).not.toBeNull();
    expect(submitButton.getAttribute('type')).toBe('submit');
  });

  function clickButton(label: string): void {
    const button = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('button'),
    ).find((element): element is HTMLButtonElement => element.textContent?.trim() === label);

    if (button === undefined) {
      fail(`Button "${label}" not found`);
      return;
    }

    button.click();
    fixture.detectChanges();
  }

  function textContent(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  function createProfile(overrides: Partial<CurrentUserProfile> = {}): CurrentUserProfile {
    return {
      id: 10,
      name: 'Maria Silva',
      phoneNumber: '34999999999',
      birthdayDate: '1990-01-01',
      roles: ['ROLE_OPERATOR'],
      ministries: ['READER'],
      ...overrides,
    };
  }
});
