package com.eventoscelebrativos.repository;

import com.eventoscelebrativos.projection.EucharistScaleEventProjection;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.LocalDate;

@DataJpaTest
@ExtendWith(SpringExtension.class)
public class CelebrationEventRepositoryTest {

    @Autowired
    private CelebrationEventRepository  eventRepository;

    @Test
    public void findEucharistScaleShouldReturnCorrectData(){

        LocalDate startDate = LocalDate.of(2025, 7 ,1);
        LocalDate endDate = LocalDate.of(2025,12, 31);
        PageRequest pageable = PageRequest.of(0, 10, Sort.by("name_mass_or_event").ascending());

        Page<EucharistScaleEventProjection> result = eventRepository.findEucharistScale(pageable, startDate, endDate);

        Assertions.assertFalse(result.isEmpty());
        Assertions.assertEquals(0, result.getNumber());
        Assertions.assertEquals(10, result.getSize());

        EucharistScaleEventProjection firstEvent = result.getContent().get(0);
        Assertions.assertEquals("Celebração da Palavra de Sábado", firstEvent.getNameMassOrEvent());
    }


}
