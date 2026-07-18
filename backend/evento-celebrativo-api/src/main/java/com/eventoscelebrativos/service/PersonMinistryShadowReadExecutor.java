package com.eventoscelebrativos.service;

import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PersonMinistryShadowReadExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(PersonMinistryShadowReadExecutor.class);

    private final PersonMinistryReadService personMinistryReadService;
    private final PersonMinistryShadowReadComparator personMinistryShadowReadComparator;

    public PersonMinistryShadowReadExecutor(
            PersonMinistryReadService personMinistryReadService,
            PersonMinistryShadowReadComparator personMinistryShadowReadComparator
    ) {
        this.personMinistryReadService = personMinistryReadService;
        this.personMinistryShadowReadComparator = personMinistryShadowReadComparator;
    }

    public void execute(
            boolean enabled,
            MinistryType ministryType,
            List<? extends Person> legacyPeople,
            PersonMinistryShadowReadComparisonOptions options
    ) {
        if (!enabled) {
            return;
        }

        List<? extends Person> legacySnapshot = List.copyOf(legacyPeople);
        Page<? extends Person> legacyPage = pageFrom(legacySnapshot, Math.max(legacySnapshot.size(), 1));

        try {
            List<Person> parallelPeople = personMinistryReadService.findAllActivePeopleByMinistry(ministryType);
            int pageSize = Math.max(Math.max(legacySnapshot.size(), parallelPeople.size()), 1);
            legacyPage = pageFrom(legacySnapshot, pageSize);
            Page<Person> parallelPage = pageFrom(parallelPeople, pageSize);
            PersonMinistryShadowReadReport report = personMinistryShadowReadComparator.compare(
                    ministryType,
                    legacyPage,
                    parallelPage,
                    options
            );
            logReport(report);
        } catch (RuntimeException exception) {
            logParallelFailure(ministryType, legacyPage, exception);
        }
    }

    private void logReport(PersonMinistryShadowReadReport report) {
        if (report.matched()) {
            LOGGER.debug(
                    "PersonMinistry shadow read matched: ministryType={}, page={}, size={}, legacyCount={}, parallelCount={}, legacyTotalElements={}, parallelTotalElements={}, orderCompared={}, orderDiffers={}, pageMetadataCompared={}",
                    report.ministryType(),
                    report.pageNumber(),
                    report.pageSize(),
                    report.legacyIds().size(),
                    report.parallelIds().size(),
                    report.legacyTotalElements(),
                    report.parallelTotalElements(),
                    report.orderCompared(),
                    report.orderDiffers(),
                    report.pageMetadataCompared()
            );
            return;
        }

        LOGGER.warn(
                "PersonMinistry shadow read divergence: ministryType={}, page={}, size={}, legacyIds={}, parallelIds={}, missingInParallelIds={}, additionalInParallelIds={}, legacyTotalElements={}, parallelTotalElements={}, legacyTotalPages={}, parallelTotalPages={}, orderCompared={}, orderDiffers={}, pageMetadataCompared={}, issues={}, additionalParallelIdsMayRepresentAdditionalMinistries=true",
                report.ministryType(),
                report.pageNumber(),
                report.pageSize(),
                report.legacyIds(),
                report.parallelIds(),
                report.missingInParallelIds(),
                report.additionalInParallelIds(),
                report.legacyTotalElements(),
                report.parallelTotalElements(),
                report.legacyTotalPages(),
                report.parallelTotalPages(),
                report.orderCompared(),
                report.orderDiffers(),
                report.pageMetadataCompared(),
                report.issues()
        );
    }

    private void logParallelFailure(
            MinistryType ministryType,
            Page<? extends Person> legacyPage,
            RuntimeException exception
    ) {
        LOGGER.warn(
                "PersonMinistry shadow read failed: ministryType={}, page={}, size={}, legacyIds={}, legacyTotalElements={}, issues={}",
                ministryType,
                legacyPage.getNumber(),
                legacyPage.getSize(),
                legacyPage.getContent().stream().map(Person::getId).toList(),
                legacyPage.getTotalElements(),
                List.of(PersonMinistryShadowReadIssueType.PARALLEL_READ_FAILURE),
                exception
        );
    }

    private <T extends Person> Page<T> pageFrom(List<T> people, int pageSize) {
        return new PageImpl<>(people, PageRequest.of(0, pageSize), people.size());
    }
}
