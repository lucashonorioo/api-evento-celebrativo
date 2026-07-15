import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
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
  readonly isAdmin = this.authSessionService.hasAuthority('ROLE_ADMIN');
  readonly isSidebarOpen = signal(false);
  readonly isUserMenuOpen = signal(false);
  readonly userInitials = initialsFor(this.username);

  onLogout(): void {
    this.isUserMenuOpen.set(false);
    this.authService.logout();
  }

  toggleSidebar(): void {
    this.isSidebarOpen.update((isOpen) => !isOpen);
  }

  closeSidebar(): void {
    this.isSidebarOpen.set(false);
  }

  toggleUserMenu(): void {
    this.isUserMenuOpen.update((isOpen) => !isOpen);
  }
}

function initialsFor(username: string): string {
  const trimmedUsername = username.trim();

  if (trimmedUsername.length === 0) {
    return 'U';
  }

  const words = trimmedUsername.split(/\s+/).filter((word) => word.length > 0);

  if (words.length >= 2) {
    return `${words[0][0]}${words[1][0]}`.toUpperCase();
  }

  return trimmedUsername.slice(0, 2).toUpperCase();
}
