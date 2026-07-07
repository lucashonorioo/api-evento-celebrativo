package com.eventoscelebrativos.repository;

import com.eventoscelebrativos.projection.EucharistScaleEventProjection;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

@DataJpaTest
class CelebrationEventRepositoryTest {

    @Autowired
    private CelebrationEventRepository eventRepository;

    @Test
    void shouldFindEucharistScaleWhenEventsExistInPeriod() {
        Page<EucharistScaleEventProjection> result = eventRepository.findEucharistScale(
                PageRequest.of(0, 10),
                LocalDate.of(2025, 7, 1),
                LocalDate.of(2025, 12, 31)
        );

        Assertions.assertEquals(3, result.getTotalElements());
        Assertions.assertEquals(0, result.getNumber());
        Assertions.assertEquals(10, result.getSize());
        Assertions.assertFalse(result.getContent().isEmpty());
    }

    @Test
    void shouldFilterEucharistScaleByPeriod() {
        Page<EucharistScaleEventProjection> result = eventRepository.findEucharistScale(
                PageRequest.of(0, 10),
                LocalDate.of(2025, 7, 12),
                LocalDate.of(2025, 7, 13)
        );

        Assertions.assertEquals(2, result.getTotalElements());
        Assertions.assertTrue(result.getContent().stream()
                .allMatch(event -> !event.getEventDate().isBefore(LocalDate.of(2025, 7, 12))
                        && !event.getEventDate().isAfter(LocalDate.of(2025, 7, 13))));
    }

    @Test
    void shouldPaginateEucharistScale() {
        Page<EucharistScaleEventProjection> firstPage = eventRepository.findEucharistScale(
                PageRequest.of(0, 2),
                LocalDate.of(2025, 7, 1),
                LocalDate.of(2025, 7, 31)
        );
        Page<EucharistScaleEventProjection> secondPage = eventRepository.findEucharistScale(
                PageRequest.of(1, 2),
                LocalDate.of(2025, 7, 1),
                LocalDate.of(2025, 7, 31)
        );

        Assertions.assertEquals(3, firstPage.getTotalElements());
        Assertions.assertEquals(2, firstPage.getNumberOfElements());
        Assertions.assertEquals(1, secondPage.getNumberOfElements());
    }

    @Test
    void shouldGroupMinisterNamesByEvent() {
        Page<EucharistScaleEventProjection> result = eventRepository.findEucharistScale(
                PageRequest.of(0, 10),
                LocalDate.of(2025, 7, 13),
                LocalDate.of(2025, 7, 13)
        );

        Assertions.assertEquals(1, result.getTotalElements());
        EucharistScaleEventProjection event = result.getContent().get(0);
        List<String> ministerNames = Arrays.stream(event.getMinisterNames().split(","))
                .map(String::trim)
                .toList();

        Assertions.assertTrue(event.getNameMassOrEvent().contains("Domingo"));
        Assertions.assertEquals(2, ministerNames.size());
        Assertions.assertTrue(ministerNames.contains("Mariana Ferraz"));
        Assertions.assertTrue(ministerNames.contains("Carlos Silva"));
    }

    @Test
    void shouldReturnEmptyPageWhenNoEventsExistInPeriod() {
        Page<EucharistScaleEventProjection> result = eventRepository.findEucharistScale(
                PageRequest.of(0, 10),
                LocalDate.of(2030, 1, 1),
                LocalDate.of(2030, 1, 31)
        );

        Assertions.assertTrue(result.isEmpty());
        Assertions.assertEquals(0, result.getTotalElements());
    }
}
