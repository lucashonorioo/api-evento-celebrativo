import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { PeopleHubComponent } from './people-hub.component';

describe('PeopleHubComponent', () => {
  let fixture: ComponentFixture<PeopleHubComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PeopleHubComponent],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(PeopleHubComponent);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render the title', () => {
    expect(textContent()).toContain('Central de pessoas');
  });

  it('should link to each person category', () => {
    const linkTargets = links().map((link) => link.getAttribute('href'));

    expect(linkTargets).toContain('/app/leitores');
    expect(linkTargets).toContain('/app/comentaristas');
    expect(linkTargets).toContain('/app/padres');
    expect(linkTargets).toContain('/app/ministros-palavra');
    expect(linkTargets).toContain('/app/ministros-eucaristia');
  });

  it('should not render administrative actions', () => {
    const text = textContent();

    expect(text).not.toContain('Cadastrar');
    expect(text).not.toContain('Editar');
    expect(text).not.toContain('Excluir');
    expect(text).not.toContain('Gerenciar');
  });

  it('should not expose personal data', () => {
    const text = textContent();

    expect(text).not.toContain('telefone');
    expect(text).not.toContain('birthdayDate');
    expect(text).not.toContain('phoneNumber');
    expect(text).not.toContain('access_token');
  });

  function links(): HTMLAnchorElement[] {
    return Array.from(fixture.nativeElement.querySelectorAll('a'));
  }

  function textContent(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }
});
