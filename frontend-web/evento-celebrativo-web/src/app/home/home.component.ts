import { ChangeDetectionStrategy, Component } from '@angular/core';

import { AuthService } from '../auth.service';

@Component({
  selector: 'app-home',
  standalone: true,
  templateUrl: './home.component.html',
  styleUrl: './home.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HomeComponent {
  constructor(private readonly authService: AuthService) {}

  onLogout(): void {
    this.authService.logout();
  }
}
