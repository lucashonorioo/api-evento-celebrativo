import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  DefaultUrlSerializer,
  Router,
  RouterStateSnapshot,
  UrlTree,
} from '@angular/router';

import { AuthSessionService } from './auth-session.service';
import { authGuard } from './auth.guard';

describe('authGuard', () => {
  let authSessionService: jasmine.SpyObj<AuthSessionService>;
  let router: jasmine.SpyObj<Router>;
  let urlSerializer: DefaultUrlSerializer;

  const executeGuard = () =>
    TestBed.runInInjectionContext(() =>
      authGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
    );

  beforeEach(() => {
    authSessionService = jasmine.createSpyObj<AuthSessionService>('AuthSessionService', [
      'hasValidSession',
    ]);
    router = jasmine.createSpyObj<Router>('Router', ['createUrlTree', 'serializeUrl']);
    urlSerializer = new DefaultUrlSerializer();

    router.createUrlTree.and.returnValue(urlSerializer.parse('/login'));
    router.serializeUrl.and.callFake((urlTree: UrlTree) => urlSerializer.serialize(urlTree));

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthSessionService, useValue: authSessionService },
        { provide: Router, useValue: router },
      ],
    });
  });

  it('should allow access when the user has a valid session', () => {
    authSessionService.hasValidSession.and.returnValue(true);

    const result = executeGuard();

    expect(result).toBeTrue();
    expect(router.createUrlTree).not.toHaveBeenCalled();
  });

  it('should redirect to login when the user does not have a session', () => {
    authSessionService.hasValidSession.and.returnValue(false);

    const result = executeGuard();

    expect(router.createUrlTree).toHaveBeenCalledOnceWith(['/login']);
    expect(router.serializeUrl(result as UrlTree)).toBe('/login');
  });

  it('should redirect to login when the session is expired', () => {
    authSessionService.hasValidSession.and.returnValue(false);

    const result = executeGuard();

    expect(router.serializeUrl(result as UrlTree)).toBe('/login');
  });

  it('should redirect to login when the session is invalid', () => {
    authSessionService.hasValidSession.and.returnValue(false);

    const result = executeGuard();

    expect(router.serializeUrl(result as UrlTree)).toBe('/login');
  });
});
