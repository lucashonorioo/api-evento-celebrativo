package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.dto.request.ParishProfileContactUpdateRequestDTO;
import com.eventoscelebrativos.dto.request.ParishProfileUpdateRequestDTO;
import com.eventoscelebrativos.dto.response.ParishProfileResponseDTO;
import com.eventoscelebrativos.exception.exceptions.BadRequestException;
import com.eventoscelebrativos.exception.exceptions.ParishProfileNotConfiguredException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.mapper.ParishProfileMapper;
import com.eventoscelebrativos.model.ParishProfile;
import com.eventoscelebrativos.repository.ParishProfileRepository;
import com.eventoscelebrativos.service.ParishProfileService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

@Service
public class ParishProfileServiceImpl implements ParishProfileService {

    private static final int MAX_NAME_LENGTH = 150;
    private static final int MAX_DIOCESE_LENGTH = 150;
    private static final int MAX_INSTITUTIONAL_PHONE_LENGTH = 30;
    private static final int MAX_INSTITUTIONAL_EMAIL_LENGTH = 254;
    private static final int MAX_OFFICE_ADDRESS_LENGTH = 255;
    private static final int MAX_OFFICE_HOURS_LENGTH = 500;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final ParishProfileRepository parishProfileRepository;
    private final ParishProfileMapper parishProfileMapper;

    public ParishProfileServiceImpl(
            ParishProfileRepository parishProfileRepository,
            ParishProfileMapper parishProfileMapper
    ) {
        this.parishProfileRepository = parishProfileRepository;
        this.parishProfileMapper = parishProfileMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public ParishProfileResponseDTO findPublicProfile() {
        ParishProfile parishProfile = parishProfileRepository.findById(ParishProfile.SINGLETON_ID)
                .orElseThrow(() -> new ResourceNotFoundException("Perfil da paróquia", ParishProfile.SINGLETON_ID));
        if (!parishProfile.isConfigured()) {
            throw new ParishProfileNotConfiguredException();
        }
        return parishProfileMapper.toDto(parishProfile);
    }

    @Override
    @Transactional
    public ParishProfileResponseDTO update(ParishProfileUpdateRequestDTO requestDTO) {
        ParishProfile parishProfile = parishProfileRepository.findByIdForUpdate(ParishProfile.SINGLETON_ID)
                .orElseThrow(() -> new ResourceNotFoundException("Perfil da paróquia", ParishProfile.SINGLETON_ID));

        String name = normalizeRequired(requestDTO.getName(), "nome", MAX_NAME_LENGTH);
        String diocese = normalizeRequired(requestDTO.getDiocese(), "diocese", MAX_DIOCESE_LENGTH);
        applyContactFields(parishProfile, requestDTO.getInstitutionalPhone(), requestDTO.getInstitutionalEmail(),
                requestDTO.getOfficeAddress(), requestDTO.getOfficeHours());

        parishProfile.setName(name);
        parishProfile.setDiocese(diocese);
        parishProfile.setConfigured(true);

        ParishProfile saved = parishProfileRepository.save(parishProfile);
        return parishProfileMapper.toDto(saved);
    }

    @Override
    @Transactional
    public ParishProfileResponseDTO updateContact(ParishProfileContactUpdateRequestDTO requestDTO) {
        ParishProfile parishProfile = parishProfileRepository.findByIdForUpdate(ParishProfile.SINGLETON_ID)
                .orElseThrow(() -> new ResourceNotFoundException("Perfil da paróquia", ParishProfile.SINGLETON_ID));
        if (!parishProfile.isConfigured()) {
            throw new ParishProfileNotConfiguredException();
        }

        applyContactFields(parishProfile, requestDTO.getInstitutionalPhone(), requestDTO.getInstitutionalEmail(),
                requestDTO.getOfficeAddress(), requestDTO.getOfficeHours());

        ParishProfile saved = parishProfileRepository.save(parishProfile);
        return parishProfileMapper.toDto(saved);
    }

    /**
     * Normalização compartilhada do subconjunto de contato entre {@link #update} (PUT /paroquia,
     * ROLE_ADMIN) e {@link #updateContact} (PUT /paroquia/contato, ROLE_ADMIN ou secretaria ativa):
     * os quatro campos nunca podem divergir de regra entre os dois caminhos de escrita.
     */
    private void applyContactFields(
            ParishProfile parishProfile,
            String rawInstitutionalPhone,
            String rawInstitutionalEmail,
            String rawOfficeAddress,
            String rawOfficeHours
    ) {
        String institutionalPhone = normalizeOptional(
                rawInstitutionalPhone, MAX_INSTITUTIONAL_PHONE_LENGTH, "telefone institucional");
        String institutionalEmail = normalizeOptional(
                rawInstitutionalEmail, MAX_INSTITUTIONAL_EMAIL_LENGTH, "e-mail institucional");
        if (institutionalEmail != null && !EMAIL_PATTERN.matcher(institutionalEmail).matches()) {
            throw new BadRequestException("O e-mail institucional informado é inválido");
        }
        String officeAddress = normalizeOptional(
                rawOfficeAddress, MAX_OFFICE_ADDRESS_LENGTH, "endereço da secretaria");
        String officeHours = normalizeOptional(
                rawOfficeHours, MAX_OFFICE_HOURS_LENGTH, "horário de funcionamento");

        parishProfile.setInstitutionalPhone(institutionalPhone);
        parishProfile.setInstitutionalEmail(institutionalEmail);
        parishProfile.setOfficeAddress(officeAddress);
        parishProfile.setOfficeHours(officeHours);
    }

    private String normalizeRequired(String value, String fieldLabel, int maxLength) {
        if (value == null || value.trim().isEmpty()) {
            throw new BadRequestException("O campo " + fieldLabel + " é obrigatório");
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new BadRequestException("O campo " + fieldLabel + " deve ter no máximo " + maxLength + " caracteres");
        }
        return trimmed;
    }

    private String normalizeOptional(String value, int maxLength, String fieldLabel) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > maxLength) {
            throw new BadRequestException("O campo " + fieldLabel + " deve ter no máximo " + maxLength + " caracteres");
        }
        return trimmed;
    }
}
