import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-access-denied',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './access-denied.component.html',
  styleUrl: './access-denied.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AccessDeniedComponent {}
