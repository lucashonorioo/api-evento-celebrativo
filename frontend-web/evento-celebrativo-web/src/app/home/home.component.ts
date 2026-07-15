import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { AuthSessionService } from '../auth-session.service';

interface QuickAccessLink {
  readonly label: string;
  readonly description: string;
  readonly path: string;
}

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './home.component.html',
  styleUrl: './home.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HomeComponent {
  private readonly authSessionService = inject(AuthSessionService);

  readonly username = this.authSessionService.getUsername() ?? 'Usuario';
  readonly quickAccessLinks: readonly QuickAccessLink[] = [
    {
      label: 'Consultar eventos',
      description: 'Veja os eventos celebrativos publicados.',
      path: '/app/eventos',
    },
    {
      label: 'Consultar escala',
      description: 'Acesse a escala publica de Eucaristia.',
      path: '/app/escala/eucaristia',
    },
    {
      label: 'Pessoas',
      description: 'Consulte leitores, comentaristas, padres e ministros.',
      path: '/app/pessoas',
    },
    {
      label: 'Locais',
      description: 'Consulte os locais cadastrados para os eventos.',
      path: '/app/locais',
    },
  ];
}
