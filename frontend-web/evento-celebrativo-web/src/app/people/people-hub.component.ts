import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { AuthSessionService } from '../auth-session.service';

interface PeopleLink {
  readonly title: string;
  readonly description: string;
  readonly path: string;
  readonly adminOnly?: boolean;
}

@Component({
  selector: 'app-people-hub',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './people-hub.component.html',
  styleUrl: './people-hub.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PeopleHubComponent {
  private readonly authSessionService = inject(AuthSessionService);

  readonly peopleLinks: readonly PeopleLink[] = [
    {
      title: 'Leitores',
      description: 'Consulte as pessoas cadastradas para as leituras.',
      path: '/app/leitores',
    },
    {
      title: 'Comentaristas',
      description: 'Acesse a listagem de comentaristas.',
      path: '/app/comentaristas',
    },
    {
      title: 'Padres',
      description: 'Consulte os padres cadastrados.',
      path: '/app/padres',
    },
    {
      title: 'Ministros da Palavra',
      description: 'Acesse os ministros da Palavra.',
      path: '/app/ministros-palavra',
    },
    {
      title: 'Ministros da Eucaristia',
      description: 'Consulte os ministros da Eucaristia.',
      path: '/app/ministros-eucaristia',
    },
    {
      title: 'Usuários',
      description: 'Gerencie os perfis de acesso das pessoas cadastradas.',
      path: '/app/admin/usuarios',
      adminOnly: true,
    },
  ];

  readonly isAdmin = this.authSessionService.hasAuthority('ROLE_ADMIN');
}
