import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { AuthSessionService } from '../auth-session.service';
import { AuthService } from '../auth.service';
import { CurrentUserProfileService } from '../current-user-profile/current-user-profile.service';

@Component({
  selector: 'app-authenticated-layout',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './authenticated-layout.component.html',
  styleUrl: './authenticated-layout.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AuthenticatedLayoutComponent implements OnInit {
  private readonly authService = inject(AuthService);
  private readonly authSessionService = inject(AuthSessionService);
  private readonly currentUserProfileService = inject(CurrentUserProfileService);

  readonly isAdmin = this.authSessionService.hasAuthority('ROLE_ADMIN');
  readonly peopleLink = this.isAdmin ? '/app/admin/usuarios' : '/app/pessoas';
  readonly isSidebarOpen = signal(false);
  readonly isUserMenuOpen = signal(false);

  readonly isProfileLoading = this.currentUserProfileService.isLoading;
  readonly profileErrorMessage = this.currentUserProfileService.errorMessage;
  readonly displayName = computed(() => displayNameFor(this.currentUserProfileService.profile()));
  readonly userInitial = computed(() => initialFor(this.displayName()));

  ngOnInit(): void {
    this.currentUserProfileService.loadProfile();
  }

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

  closeUserMenu(): void {
    this.isUserMenuOpen.set(false);
  }
}

function displayNameFor(profile: { readonly name: string } | null): string | null {
  if (profile === null) {
    return null;
  }

  const trimmedName = profile.name.trim();

  return trimmedName.length > 0 ? trimmedName : null;
}

function initialFor(displayName: string | null): string | null {
  if (displayName === null) {
    return null;
  }

  return displayName.charAt(0).toUpperCase();
}
