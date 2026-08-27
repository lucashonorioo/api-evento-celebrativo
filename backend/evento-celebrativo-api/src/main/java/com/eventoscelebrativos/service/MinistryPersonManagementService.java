package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.MinistryPersonCreateRequestDTO;
import com.eventoscelebrativos.dto.request.MinistryPersonUpdateRequestDTO;
import com.eventoscelebrativos.dto.response.MinistryPersonResponseDTO;
import org.springframework.data.domain.Page;

/**
 * Orquestra as operacoes de administracao de pessoas escopadas a um Ministry persistente
 * ({@code /ministerios/{ministryId}/pessoas}), usadas por ROLE_ADMIN (qualquer ministryId) e por
 * ROLE_OPERATOR coordenador (somente o ministryId que coordena). A decisao de QUEM pode chamar
 * estas operacoes e responsabilidade de {@link MinistryAuthorizationService} (Method Security no
 * controller); este service decide se o ALVO pertence ao escopo solicitado e executa a operacao.
 */
public interface MinistryPersonManagementService {

    Page<MinistryPersonResponseDTO> findPeople(Long ministryId, int page, int size);

    MinistryPersonResponseDTO findPersonById(Long ministryId, Long personId);

    MinistryPersonResponseDTO create(Long ministryId, MinistryPersonCreateRequestDTO requestDTO);

    MinistryPersonResponseDTO update(Long ministryId, Long personId, MinistryPersonUpdateRequestDTO requestDTO);

    MinistryPersonResponseDTO addOrReactivateMinistry(Long ministryId, Long personId);

    void removeMinistry(Long ministryId, Long personId);
}
