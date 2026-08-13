package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.ParishProfileUpdateRequestDTO;
import com.eventoscelebrativos.dto.response.ParishProfileResponseDTO;
import com.eventoscelebrativos.exception.exceptions.BadRequestException;
import com.eventoscelebrativos.exception.exceptions.ParishProfileNotConfiguredException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.mapper.ParishProfileMapper;
import com.eventoscelebrativos.model.ParishProfile;
import com.eventoscelebrativos.repository.ParishProfileRepository;
import com.eventoscelebrativos.service.impl.ParishProfileServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParishProfileServiceImplTest {

    @Mock
    private ParishProfileRepository parishProfileRepository;

    @Mock
    private ParishProfileMapper parishProfileMapper;

    private ParishProfileServiceImpl parishProfileService;

    @BeforeEach
    void setUp() {
        parishProfileService = new ParishProfileServiceImpl(parishProfileRepository, parishProfileMapper);
    }

    private ParishProfile configuredProfile() {
        ParishProfile profile = new ParishProfile();
        profile.setConfigured(true);
        profile.setName("Paróquia São José");
        profile.setDiocese("Diocese de Uberlândia");
        return profile;
    }

    private ParishProfile unconfiguredProfile() {
        ParishProfile profile = new ParishProfile();
        profile.setConfigured(false);
        return profile;
    }

    // 1. GET configurado retorna dados
    @Test
    void shouldReturnDataWhenProfileIsConfigured() {
        ParishProfile profile = configuredProfile();
        ParishProfileResponseDTO expected = new ParishProfileResponseDTO(
                "Paróquia São José", "Diocese de Uberlândia", null, null, null, null);
        when(parishProfileRepository.findById(ParishProfile.SINGLETON_ID)).thenReturn(Optional.of(profile));
        when(parishProfileMapper.toDto(profile)).thenReturn(expected);

        ParishProfileResponseDTO result = parishProfileService.findPublicProfile();

        assertEquals(expected, result);
    }

    // 2. GET não configurado lança erro semântico
    @Test
    void shouldThrowNotConfiguredExceptionWhenProfileIsNotConfigured() {
        when(parishProfileRepository.findById(ParishProfile.SINGLETON_ID)).thenReturn(Optional.of(unconfiguredProfile()));

        assertThrows(ParishProfileNotConfiguredException.class, () -> parishProfileService.findPublicProfile());
    }

    @Test
    void shouldThrowResourceNotFoundWhenSingletonRowIsMissingOnGet() {
        when(parishProfileRepository.findById(ParishProfile.SINGLETON_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> parishProfileService.findPublicProfile());
    }

    // 3 e 4. primeira configuração atualiza id=1 e configured passa para true
    @Test
    void shouldConfigureSingletonAndMarkAsConfiguredOnFirstUpdate() {
        ParishProfile profile = unconfiguredProfile();
        when(parishProfileRepository.findByIdForUpdate(ParishProfile.SINGLETON_ID)).thenReturn(Optional.of(profile));
        when(parishProfileRepository.save(profile)).thenReturn(profile);
        when(parishProfileMapper.toDto(profile)).thenReturn(new ParishProfileResponseDTO());

        ParishProfileUpdateRequestDTO request = new ParishProfileUpdateRequestDTO(
                "Paróquia São José", "Diocese de Uberlândia", null, null, null, null);
        parishProfileService.update(request);

        assertTrue(profile.isConfigured());
        assertEquals("Paróquia São José", profile.getName());
        assertEquals("Diocese de Uberlândia", profile.getDiocese());
    }

    // 5 e 6. atualização posterior mantém id=1 e não cria segunda linha
    @Test
    void shouldUpdateSameSingletonInstanceWithoutCreatingANewRow() {
        ParishProfile profile = configuredProfile();
        when(parishProfileRepository.findByIdForUpdate(ParishProfile.SINGLETON_ID)).thenReturn(Optional.of(profile));
        when(parishProfileRepository.save(profile)).thenReturn(profile);
        when(parishProfileMapper.toDto(profile)).thenReturn(new ParishProfileResponseDTO());

        ParishProfileUpdateRequestDTO request = new ParishProfileUpdateRequestDTO(
                "Novo Nome", "Nova Diocese", null, null, null, null);
        parishProfileService.update(request);

        verify(parishProfileRepository, times(1)).save(profile);
    }

    // 7. normaliza campos (trim)
    @Test
    void shouldTrimFieldsOnUpdate() {
        ParishProfile profile = unconfiguredProfile();
        when(parishProfileRepository.findByIdForUpdate(ParishProfile.SINGLETON_ID)).thenReturn(Optional.of(profile));
        when(parishProfileRepository.save(profile)).thenReturn(profile);
        when(parishProfileMapper.toDto(profile)).thenReturn(new ParishProfileResponseDTO());

        ParishProfileUpdateRequestDTO request = new ParishProfileUpdateRequestDTO(
                "  Paróquia São José  ", "  Diocese de Uberlândia  ", "  34999990000  ", null,
                "  Praça Central, 1  ", "  Segunda a sexta, das 8h às 17h  ");
        parishProfileService.update(request);

        assertEquals("Paróquia São José", profile.getName());
        assertEquals("Diocese de Uberlândia", profile.getDiocese());
        assertEquals("34999990000", profile.getInstitutionalPhone());
        assertEquals("Praça Central, 1", profile.getOfficeAddress());
        assertEquals("Segunda a sexta, das 8h às 17h", profile.getOfficeHours());
    }

    // 8. blank opcional vira null
    @Test
    void shouldNormalizeBlankOptionalFieldsToNull() {
        ParishProfile profile = unconfiguredProfile();
        when(parishProfileRepository.findByIdForUpdate(ParishProfile.SINGLETON_ID)).thenReturn(Optional.of(profile));
        when(parishProfileRepository.save(profile)).thenReturn(profile);
        when(parishProfileMapper.toDto(profile)).thenReturn(new ParishProfileResponseDTO());

        ParishProfileUpdateRequestDTO request = new ParishProfileUpdateRequestDTO(
                "Paróquia São José", "Diocese de Uberlândia", "   ", "   ", "   ", "   ");
        parishProfileService.update(request);

        assertEquals(null, profile.getInstitutionalPhone());
        assertEquals(null, profile.getInstitutionalEmail());
        assertEquals(null, profile.getOfficeAddress());
        assertEquals(null, profile.getOfficeHours());
    }

    // 9. name obrigatório
    @Test
    void shouldRejectNullName() {
        when(parishProfileRepository.findByIdForUpdate(ParishProfile.SINGLETON_ID))
                .thenReturn(Optional.of(unconfiguredProfile()));

        ParishProfileUpdateRequestDTO request = new ParishProfileUpdateRequestDTO(
                null, "Diocese de Uberlândia", null, null, null, null);

        assertThrows(BadRequestException.class, () -> parishProfileService.update(request));
    }

    @Test
    void shouldRejectBlankName() {
        when(parishProfileRepository.findByIdForUpdate(ParishProfile.SINGLETON_ID))
                .thenReturn(Optional.of(unconfiguredProfile()));

        ParishProfileUpdateRequestDTO request = new ParishProfileUpdateRequestDTO(
                "   ", "Diocese de Uberlândia", null, null, null, null);

        assertThrows(BadRequestException.class, () -> parishProfileService.update(request));
    }

    // 10. diocese obrigatória
    @Test
    void shouldRejectNullDiocese() {
        when(parishProfileRepository.findByIdForUpdate(ParishProfile.SINGLETON_ID))
                .thenReturn(Optional.of(unconfiguredProfile()));

        ParishProfileUpdateRequestDTO request = new ParishProfileUpdateRequestDTO(
                "Paróquia São José", null, null, null, null, null);

        assertThrows(BadRequestException.class, () -> parishProfileService.update(request));
    }

    // 11. email inválido rejeitado
    @Test
    void shouldRejectInvalidInstitutionalEmail() {
        when(parishProfileRepository.findByIdForUpdate(ParishProfile.SINGLETON_ID))
                .thenReturn(Optional.of(unconfiguredProfile()));

        ParishProfileUpdateRequestDTO request = new ParishProfileUpdateRequestDTO(
                "Paróquia São José", "Diocese de Uberlândia", null, "nao-e-um-email", null, null);

        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> parishProfileService.update(request));
        assertEquals("BAD_REQUEST", exception.getErrorCode());
    }

    @Test
    void shouldAcceptValidInstitutionalEmail() {
        ParishProfile profile = unconfiguredProfile();
        when(parishProfileRepository.findByIdForUpdate(ParishProfile.SINGLETON_ID)).thenReturn(Optional.of(profile));
        when(parishProfileRepository.save(profile)).thenReturn(profile);
        when(parishProfileMapper.toDto(profile)).thenReturn(new ParishProfileResponseDTO());

        ParishProfileUpdateRequestDTO request = new ParishProfileUpdateRequestDTO(
                "Paróquia São José", "Diocese de Uberlândia", null, "secretaria@paroquia.org.br", null, null);
        parishProfileService.update(request);

        assertEquals("secretaria@paroquia.org.br", profile.getInstitutionalEmail());
    }

    // 12. campos não autorizados não existem no DTO
    @Test
    void requestDtoShouldOnlyExposeAuthorizedFields() {
        var declaredFields = ParishProfileUpdateRequestDTO.class.getDeclaredFields();
        var fieldNames = new java.util.HashSet<String>();
        for (var field : declaredFields) {
            fieldNames.add(field.getName());
        }
        var expected = java.util.Set.of(
                "name", "diocese", "institutionalPhone", "institutionalEmail", "officeAddress", "officeHours");
        assertEquals(expected, fieldNames);
    }

    // 13. repository usa singleton correto
    @Test
    void shouldAlwaysOperateOnSingletonId() {
        when(parishProfileRepository.findById(ParishProfile.SINGLETON_ID)).thenReturn(Optional.of(configuredProfile()));
        when(parishProfileMapper.toDto(any())).thenReturn(new ParishProfileResponseDTO());

        parishProfileService.findPublicProfile();

        verify(parishProfileRepository).findById(eq(1L));
    }

    // 14. atualização utiliza lock esperado
    @Test
    void shouldUseLockedFinderOnUpdate() {
        ParishProfile profile = unconfiguredProfile();
        when(parishProfileRepository.findByIdForUpdate(ParishProfile.SINGLETON_ID)).thenReturn(Optional.of(profile));
        when(parishProfileRepository.save(profile)).thenReturn(profile);
        when(parishProfileMapper.toDto(profile)).thenReturn(new ParishProfileResponseDTO());

        ParishProfileUpdateRequestDTO request = new ParishProfileUpdateRequestDTO(
                "Paróquia São José", "Diocese de Uberlândia", null, null, null, null);
        parishProfileService.update(request);

        verify(parishProfileRepository).findByIdForUpdate(eq(1L));
        verify(parishProfileRepository, never()).findById(any());
    }

    @Test
    void shouldThrowResourceNotFoundWhenSingletonRowIsMissingOnUpdate() {
        when(parishProfileRepository.findByIdForUpdate(ParishProfile.SINGLETON_ID)).thenReturn(Optional.empty());

        ParishProfileUpdateRequestDTO request = new ParishProfileUpdateRequestDTO(
                "Paróquia São José", "Diocese de Uberlândia", null, null, null, null);

        assertThrows(ResourceNotFoundException.class, () -> parishProfileService.update(request));
    }
}
