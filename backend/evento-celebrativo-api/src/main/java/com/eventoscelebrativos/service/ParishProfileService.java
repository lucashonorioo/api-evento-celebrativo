package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.ParishProfileContactUpdateRequestDTO;
import com.eventoscelebrativos.dto.request.ParishProfileUpdateRequestDTO;
import com.eventoscelebrativos.dto.response.ParishProfileResponseDTO;

public interface ParishProfileService {

    ParishProfileResponseDTO findPublicProfile();

    ParishProfileResponseDTO update(ParishProfileUpdateRequestDTO requestDTO);

    /**
     * Atualiza somente o subconjunto de contato/atendimento (telefone institucional, e-mail
     * institucional, endereço da secretaria e horário de funcionamento). Não altera nome nem
     * diocese. Exige que o perfil já tenha sido configurado por ROLE_ADMIN via {@link #update}.
     */
    ParishProfileResponseDTO updateContact(ParishProfileContactUpdateRequestDTO requestDTO);
}
