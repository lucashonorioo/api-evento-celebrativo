package com.eventoscelebrativos.service;

import com.eventoscelebrativos.model.CelebrationEvent;
import com.eventoscelebrativos.model.Person;

import java.util.List;

public interface LegacyScaleMirrorService {

    void synchronizeMirror(CelebrationEvent event, List<Person> people);
}
