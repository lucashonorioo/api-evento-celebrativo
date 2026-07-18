package com.eventoscelebrativos.service;

import com.eventoscelebrativos.config.PersonMinistryShadowReadProperties;
import com.eventoscelebrativos.dto.request.ReaderRequestDTO;
import com.eventoscelebrativos.dto.response.ReaderResponseDTO;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.DatabaseException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.mapper.ReaderMapper;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.Reader;
import com.eventoscelebrativos.model.Role;
import com.eventoscelebrativos.repository.ReaderRepository;
import com.eventoscelebrativos.repository.RoleRepository;
import com.eventoscelebrativos.service.impl.ReaderServiceImpl;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReaderServiceImplTest {

    private static final LocalDate BIRTHDAY = LocalDate.of(1990, 1, 10);

    @Mock
    private ReaderRepository readerRepository;

    @Mock
    private ReaderMapper readerMapper;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private PersonMinistryCompatibilityService personMinistryCompatibilityService;

    @Mock
    private MinistryTypeResolver ministryTypeResolver;

    @Mock
    private PersonMinistryReadService personMinistryReadService;

    @Mock
    private PersonMinistryShadowReadComparator personMinistryShadowReadComparator;

    @Mock
    private PersonMinistryShadowReadProperties shadowReadProperties;

    @InjectMocks
    private ReaderServiceImpl service;

    @Test
    void shouldCreateReaderWithEncryptedPasswordAndOperatorRole() {
        ReaderRequestDTO request = request();
        Reader entity = reader(null, "raw-password");
        Role operatorRole = new Role(1L, "ROLE_OPERATOR");
        Reader saved = reader(1L, "encoded-password");
        saved.addRole(operatorRole);
        ReaderResponseDTO response = response(1L);

        when(readerMapper.toEntity(request)).thenReturn(entity);
        when(passwordEncoder.encode("raw-password")).thenReturn("encoded-password");
        when(roleRepository.findByAuthority("ROLE_OPERATOR")).thenReturn(Optional.of(operatorRole));
        when(readerRepository.save(any(Reader.class))).thenReturn(saved);
        when(ministryTypeResolver.resolve(saved)).thenReturn(MinistryType.READER);
        when(readerMapper.toDto(saved)).thenReturn(response);

        ReaderResponseDTO result = service.createReader(request);

        assertSame(response, result);
        ArgumentCaptor<Reader> captor = ArgumentCaptor.forClass(Reader.class);
        verify(readerRepository).save(captor.capture());
        Reader readerToSave = captor.getValue();
        assertEquals("encoded-password", readerToSave.getPassword());
        assertNotEquals("raw-password", readerToSave.getPassword());
        assertTrue(readerToSave.hasRole("ROLE_OPERATOR"));
        verify(personMinistryCompatibilityService).ensureMinistry(saved, MinistryType.READER);
    }

    @Test
    void shouldThrowResourceNotFoundWhenOperatorRoleDoesNotExist() {
        ReaderRequestDTO request = request();
        Reader entity = reader(null, "raw-password");

        when(readerMapper.toEntity(request)).thenReturn(entity);
        when(passwordEncoder.encode("raw-password")).thenReturn("encoded-password");
        when(roleRepository.findByAuthority("ROLE_OPERATOR")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.createReader(request));
        verify(readerRepository, never()).save(any());
    }

    @Test
    void shouldFindReaderByIdWhenExists() {
        Reader reader = reader(1L, "encoded-password");
        ReaderResponseDTO response = response(1L);

        when(readerRepository.findById(1L)).thenReturn(Optional.of(reader));
        when(readerMapper.toDto(reader)).thenReturn(response);

        assertSame(response, service.findReaderById(1L));
    }

    @Test
    void shouldThrowResourceNotFoundWhenReaderIdDoesNotExist() {
        when(readerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.findReaderById(99L));
    }

    @Test
    void shouldThrowBusinessExceptionWhenReaderIdIsInvalid() {
        assertAll(
                () -> assertThrows(BusinessException.class, () -> service.findReaderById(null)),
                () -> assertThrows(BusinessException.class, () -> service.findReaderById(0L)),
                () -> assertThrows(BusinessException.class, () -> service.findReaderById(-1L))
        );
    }

    @Test
    void shouldListReaders() {
        List<Reader> readers = List.of(reader(1L, "encoded-password"));
        List<ReaderResponseDTO> responses = List.of(response(1L));

        when(readerRepository.findAll()).thenReturn(readers);
        when(readerMapper.toDtoList(readers)).thenReturn(responses);

        assertSame(responses, service.findAllReaders());
        verifyNoInteractions(personMinistryReadService, personMinistryShadowReadComparator);
    }

    @Test
    void shouldRunReaderShadowReadWhenEnabledAndKeepLegacyResponse() {
        List<Reader> readers = List.of(reader(1L, "encoded-password"));
        List<ReaderResponseDTO> responses = List.of(response(1L));
        Page<Person> parallelPage = parallelPage(readers, PageRequest.of(0, 1), 1);
        PersonMinistryShadowReadReport report = shadowReport(List.of(1L), List.of(1L),
                List.of(PersonMinistryShadowReadIssueType.MATCH), true);

        when(shadowReadProperties.isReaderEnabled()).thenReturn(true);
        when(readerRepository.findAll()).thenReturn(readers);
        when(personMinistryReadService.findActivePeopleByMinistry(eq(MinistryType.READER), any(Pageable.class)))
                .thenReturn(parallelPage);
        when(personMinistryShadowReadComparator.compare(eq(MinistryType.READER), any(), eq(parallelPage), any()))
                .thenReturn(report);
        when(readerMapper.toDtoList(readers)).thenReturn(responses);

        assertSame(responses, service.findAllReaders());
        verify(personMinistryReadService).findActivePeopleByMinistry(eq(MinistryType.READER), any(Pageable.class));
        ArgumentCaptor<PersonMinistryShadowReadComparisonOptions> optionsCaptor =
                ArgumentCaptor.forClass(PersonMinistryShadowReadComparisonOptions.class);
        verify(personMinistryShadowReadComparator).compare(eq(MinistryType.READER), any(), eq(parallelPage), optionsCaptor.capture());
        assertFalse(optionsCaptor.getValue().compareOrder());
        assertFalse(optionsCaptor.getValue().comparePageMetadata());
    }

    @Test
    void shouldKeepLegacyResponseWhenReaderShadowReadFindsDivergence() {
        List<Reader> readers = List.of(reader(1L, "encoded-password"));
        List<ReaderResponseDTO> responses = List.of(response(1L));
        Page<Person> parallelPage = parallelPage(List.of(), PageRequest.of(0, 1), 0);
        PersonMinistryShadowReadReport report = shadowReport(List.of(1L), List.of(),
                List.of(PersonMinistryShadowReadIssueType.CONTENT_MISMATCH), false);

        when(shadowReadProperties.isReaderEnabled()).thenReturn(true);
        when(readerRepository.findAll()).thenReturn(readers);
        when(personMinistryReadService.findActivePeopleByMinistry(eq(MinistryType.READER), any(Pageable.class)))
                .thenReturn(parallelPage);
        when(personMinistryShadowReadComparator.compare(eq(MinistryType.READER), any(), eq(parallelPage), any()))
                .thenReturn(report);
        when(readerMapper.toDtoList(readers)).thenReturn(responses);

        assertSame(responses, service.findAllReaders());
    }

    @Test
    void shouldNotReorderLegacyReaderListWhenShadowReadIsEnabled() {
        List<Reader> readers = List.of(
                reader(2L, "encoded-password"),
                reader(1L, "encoded-password")
        );
        List<ReaderResponseDTO> responses = List.of(response(2L), response(1L));
        Page<Person> parallelPage = parallelPage(List.of(readers.get(1), readers.get(0)), PageRequest.of(0, 2), 2);
        PersonMinistryShadowReadReport report = shadowReport(List.of(2L, 1L), List.of(1L, 2L),
                List.of(PersonMinistryShadowReadIssueType.MATCH), true);

        when(shadowReadProperties.isReaderEnabled()).thenReturn(true);
        when(readerRepository.findAll()).thenReturn(readers);
        when(personMinistryReadService.findActivePeopleByMinistry(eq(MinistryType.READER), any(Pageable.class)))
                .thenReturn(parallelPage);
        when(personMinistryShadowReadComparator.compare(eq(MinistryType.READER), any(), eq(parallelPage), any()))
                .thenReturn(report);
        when(readerMapper.toDtoList(any())).thenReturn(responses);

        assertSame(responses, service.findAllReaders());

        verify(readerMapper).toDtoList(readers);
        assertEquals(List.of(2L, 1L), readers.stream().map(Reader::getId).toList());
    }

    @Test
    void shouldKeepLegacyResponseWhenReaderShadowReadFails() {
        List<Reader> readers = List.of(reader(1L, "encoded-password"));
        List<ReaderResponseDTO> responses = List.of(response(1L));
        PersonMinistryShadowReadReport report = shadowReport(List.of(1L), List.of(),
                List.of(PersonMinistryShadowReadIssueType.PARALLEL_READ_FAILURE), false);

        when(shadowReadProperties.isReaderEnabled()).thenReturn(true);
        when(readerRepository.findAll()).thenReturn(readers);
        when(personMinistryReadService.findActivePeopleByMinistry(eq(MinistryType.READER), any(Pageable.class)))
                .thenThrow(new IllegalStateException("parallel read failed"));
        when(personMinistryShadowReadComparator.parallelReadFailure(eq(MinistryType.READER), any()))
                .thenReturn(report);
        when(readerMapper.toDtoList(readers)).thenReturn(responses);

        assertSame(responses, service.findAllReaders());
    }

    @Test
    void shouldPropagateLegacyFailureWithoutUsingParallelReadAsFallback() {
        RuntimeException legacyFailure = new IllegalStateException("legacy read failed");

        when(readerRepository.findAll()).thenThrow(legacyFailure);

        assertSame(legacyFailure, assertThrows(RuntimeException.class, () -> service.findAllReaders()));
        verifyNoInteractions(personMinistryReadService, personMinistryShadowReadComparator, readerMapper);
    }

    @Test
    void shouldUpdateReaderWhenExists() {
        ReaderRequestDTO request = request();
        Reader entity = reader(1L, "old-password");
        Reader saved = reader(1L, "encoded-password");
        ReaderResponseDTO response = response(1L);

        when(readerRepository.getReferenceById(1L)).thenReturn(entity);
        when(passwordEncoder.encode("raw-password")).thenReturn("encoded-password");
        when(readerRepository.save(entity)).thenReturn(saved);
        when(ministryTypeResolver.resolve(saved)).thenReturn(MinistryType.READER);
        when(readerMapper.toDto(saved)).thenReturn(response);

        assertSame(response, service.updateReader(1L, request));
        verify(readerMapper).updateReaderFromDto(request, entity);
        assertEquals("encoded-password", entity.getPassword());
        verify(personMinistryCompatibilityService).ensureMinistry(saved, MinistryType.READER);
    }

    @Test
    void shouldThrowResourceNotFoundWhenUpdatingMissingReader() {
        ReaderRequestDTO request = request();
        when(readerRepository.getReferenceById(99L)).thenThrow(new EntityNotFoundException());

        assertThrows(ResourceNotFoundException.class, () -> service.updateReader(99L, request));
    }

    @Test
    void shouldDeleteReaderWhenExists() {
        when(readerRepository.existsById(1L)).thenReturn(true);

        service.deleteReaderById(1L);

        var inOrder = inOrder(personMinistryCompatibilityService, readerRepository);
        inOrder.verify(personMinistryCompatibilityService).deleteAllForPerson(1L);
        inOrder.verify(readerRepository).deleteById(1L);
    }

    @Test
    void shouldThrowResourceNotFoundWhenDeletingMissingReader() {
        when(readerRepository.existsById(99L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> service.deleteReaderById(99L));
        verify(readerRepository, never()).deleteById(anyLong());
    }

    @Test
    void shouldThrowDatabaseExceptionWhenDeletingReferencedReader() {
        when(readerRepository.existsById(1L)).thenReturn(true);
        doThrow(new DataIntegrityViolationException("constraint")).when(readerRepository).flush();

        assertThrows(DatabaseException.class, () -> service.deleteReaderById(1L));
    }

    private ReaderRequestDTO request() {
        return new ReaderRequestDTO("Reader", "34999999991", BIRTHDAY, "raw-password");
    }

    private Reader reader(Long id, String password) {
        Reader reader = new Reader();
        reader.setId(id);
        reader.setName("Reader");
        reader.setPhoneNumber("34999999991");
        reader.setBirthdayDate(BIRTHDAY);
        reader.setPassword(password);
        return reader;
    }

    private ReaderResponseDTO response(Long id) {
        return new ReaderResponseDTO(id, "Reader", "34999999991", BIRTHDAY);
    }

    private Page<Person> parallelPage(List<Reader> readers, PageRequest pageRequest, long totalElements) {
        return new PageImpl<>(List.copyOf(readers), pageRequest, totalElements);
    }

    private PersonMinistryShadowReadReport shadowReport(
            List<Long> legacyIds,
            List<Long> parallelIds,
            List<PersonMinistryShadowReadIssueType> issues,
            boolean matched
    ) {
        return new PersonMinistryShadowReadReport(
                MinistryType.READER,
                0,
                Math.max(legacyIds.size(), 1),
                legacyIds,
                parallelIds,
                missingIds(legacyIds, parallelIds),
                missingIds(parallelIds, legacyIds),
                legacyIds.size(),
                parallelIds.size(),
                legacyIds.isEmpty() ? 0 : 1,
                parallelIds.isEmpty() ? 0 : 1,
                false,
                false,
                false,
                issues,
                matched
        );
    }

    private List<Long> missingIds(List<Long> sourceIds, List<Long> targetIds) {
        return sourceIds.stream()
                .filter(id -> !targetIds.contains(id))
                .distinct()
                .toList();
    }
}
