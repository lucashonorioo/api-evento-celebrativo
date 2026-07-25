package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.model.CelebrationEvent;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.repository.CelebrationEventRepository;
import com.eventoscelebrativos.service.LegacyScaleMirrorService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Espelha tb_event_person a partir do estado oficial ja aplicado em tb_event_assignment.
 * Existe somente para compatibilidade temporaria: rollback LEGACY, shadow read e auditoria de
 * consistencia; nao decide elegibilidade nem participa da fonte oficial da escala.
 */
@Service
public class LegacyScaleMirrorServiceImpl implements LegacyScaleMirrorService {

    private final CelebrationEventRepository celebrationEventRepository;

    public LegacyScaleMirrorServiceImpl(CelebrationEventRepository celebrationEventRepository) {
        this.celebrationEventRepository = celebrationEventRepository;
    }

    @Override
    @Transactional
    public void synchronizeMirror(CelebrationEvent event, List<Person> people) {
        if (event == null || event.getId() == null || event.getId() <= 0) {
            throw new BusinessException("Evento válido é obrigatório para sincronizar o espelho legado da escala");
        }

        event.getPeople().clear();
        event.getPeople().addAll(people == null ? List.of() : people);
        celebrationEventRepository.save(event);
    }
}
