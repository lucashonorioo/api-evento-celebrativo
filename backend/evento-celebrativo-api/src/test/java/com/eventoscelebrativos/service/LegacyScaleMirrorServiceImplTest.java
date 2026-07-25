package com.eventoscelebrativos.service;

import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.model.CelebrationEvent;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.Reader;
import com.eventoscelebrativos.repository.CelebrationEventRepository;
import com.eventoscelebrativos.service.impl.LegacyScaleMirrorServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class LegacyScaleMirrorServiceImplTest {

    @Mock
    private CelebrationEventRepository celebrationEventRepository;

    @InjectMocks
    private LegacyScaleMirrorServiceImpl service;

    @Test
    void shouldDeriveEventPeopleFromOfficialStateAndSaveEvent() {
        CelebrationEvent event = event(1L);
        Person first = person(10L);
        Person second = person(20L);

        service.synchronizeMirror(event, List.of(first, second));

        assertEquals(List.of(first, second), event.getPeople());
        verify(celebrationEventRepository).save(event);
    }

    @Test
    void shouldClearPreviousPeopleBeforeApplyingTheOfficialState() {
        CelebrationEvent event = event(1L);
        Person stale = person(99L);
        event.getPeople().add(stale);
        Person current = person(10L);

        service.synchronizeMirror(event, List.of(current));

        assertEquals(List.of(current), event.getPeople());
    }

    @Test
    void shouldTreatNullPeopleAsEmptyMirror() {
        CelebrationEvent event = event(1L);
        event.getPeople().add(person(99L));

        service.synchronizeMirror(event, null);

        assertTrue(event.getPeople().isEmpty());
        verify(celebrationEventRepository).save(event);
    }

    @Test
    void shouldRejectEventWithoutValidId() {
        assertThrows(BusinessException.class, () -> service.synchronizeMirror(event(null), List.of()));
        assertThrows(BusinessException.class, () -> service.synchronizeMirror(event(0L), List.of()));
        verifyNoInteractions(celebrationEventRepository);
    }

    private CelebrationEvent event(Long id) {
        CelebrationEvent event = new CelebrationEvent();
        event.setId(id);
        return event;
    }

    private Person person(Long id) {
        Reader reader = new Reader();
        reader.setId(id);
        reader.setName("Person " + id);
        return reader;
    }
}
