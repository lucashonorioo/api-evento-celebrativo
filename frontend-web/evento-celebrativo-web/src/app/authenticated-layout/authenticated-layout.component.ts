import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { AuthSessionService } from '../auth-session.service';
import { AuthService } from '../auth.service';

@Component({
  selector: 'app-authenticated-layout',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './authenticated-layout.component.html',
  styleUrl: './authenticated-layout.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AuthenticatedLayoutComponent {
  private readonly authService = inject(AuthService);
  private readonly authSessionService = inject(AuthSessionService);

  readonly username = this.authSessionService.getUsername() ?? 'Usuario';

  onLogout(): void {
    this.authService.logout();
  }
}
