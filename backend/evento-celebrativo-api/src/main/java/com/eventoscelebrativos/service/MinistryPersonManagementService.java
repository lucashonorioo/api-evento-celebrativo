package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.MinistryPersonCreateRequestDTO;
import com.eventoscelebrativos.dto.request.MinistryPersonUpdateRequestDTO;
import com.eventoscelebrativos.dto.response.MinistryPersonResponseDTO;
import com.eventoscelebrativos.model.MinistryType;
import org.springframework.data.domain.Page;

/**
 * Orquestra as operacoes de administracao de pessoas escopadas a um unico {@link MinistryType}
 * ({@code /ministerios/{ministryType}/pessoas}), usadas por ROLE_ADMIN (qualquer ministryType) e por
 * ROLE_OPERATOR coordenador (somente o ministryType que coordena). A decisao de QUEM pode chamar
 * estas operacoes e responsabilidade de {@link MinistryAuthorizationService} (Method Security no
 * controller); este service decide se o ALVO pertence ao escopo solicitado e executa a operacao.
 */
public interface MinistryPersonManagementService {

    Page<MinistryPersonResponseDTO> findPeople(MinistryType ministryType, int page, int size);

    MinistryPersonResponseDTO findPersonById(MinistryType ministryType, Long personId);

    MinistryPersonResponseDTO create(MinistryType ministryType, MinistryPersonCreateRequestDTO requestDTO);

    MinistryPersonResponseDTO update(MinistryType ministryType, Long personId, MinistryPersonUpdateRequestDTO requestDTO);

    MinistryPersonResponseDTO addOrReactivateMinistry(MinistryType ministryType, Long personId);

    void removeMinistry(MinistryType ministryType, Long personId);
}
