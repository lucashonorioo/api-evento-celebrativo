import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { AccessDeniedComponent } from './access-denied.component';

describe('AccessDeniedComponent', () => {
  let fixture: ComponentFixture<AccessDeniedComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AccessDeniedComponent],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(AccessDeniedComponent);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render the title and message', () => {
    const text = textContent();

    expect(text).toContain('Acesso negado');
    expect(text).toContain('Voce nao possui permissao para acessar esta funcionalidade.');
  });

  it('should link back to the authenticated home page', () => {
    const link = fixture.nativeElement.querySelector('.access-denied__link') as HTMLAnchorElement;

    expect(link.textContent).toContain('Voltar para o inicio');
    expect(link.getAttribute('href')).toBe('/app/inicio');
  });

  function textContent(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }
});
