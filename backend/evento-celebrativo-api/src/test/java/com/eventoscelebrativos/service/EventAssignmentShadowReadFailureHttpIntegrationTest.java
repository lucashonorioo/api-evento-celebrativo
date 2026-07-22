package com.eventoscelebrativos.service;

import com.eventoscelebrativos.config.EventAssignmentShadowReadProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.jpa.show-sql=false",
        "logging.level.org.springframework=WARN",
        "logging.level.org.hibernate=WARN",
        "logging.level.com.eventoscelebrativos=DEBUG"
})
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class EventAssignmentShadowReadFailureHttpIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventAssignmentShadowReadProperties properties;

    @MockitoBean
    private EventAssignmentReadService eventAssignmentReadService;

    @AfterEach
    void resetFlags() {
        properties.setEventDetailEnabled(false);
        properties.setEventScaleDetailEnabled(false);
        properties.setMonthlyScheduleEnabled(false);
        properties.setEucharistScaleEnabled(false);
    }

    @Test
    void shouldNotCallParallelReadServiceWhenAllFlagsAreDisabled() throws Exception {
        getPublicJson("/eventos/1");
        getAuthorizedJson("/eventos/1/escala", user("operator").roles("OPERATOR"));
        getAuthorizedJson(
                "/eventos/escalas?startDate=2025-07-01&endDate=2025-07-31&type=READER&page=0&size=10",
                user("admin").roles("ADMIN")
        );
        getPublicJson("/eventos/escala/eucaristia?startDate=2025-07-01&endDate=2025-07-31&page=0&size=10");

        verifyNoInteractions(eventAssignmentReadService);
    }

    @Test
    void shouldPreserveOfficialResponseWhenParallelReadFails(CapturedOutput output) throws Exception {
        properties.setEventScaleDetailEnabled(false);
        String expected = getAuthorizedJson("/eventos/1/escala", user("operator").roles("OPERATOR"));

        when(eventAssignmentReadService.findAllByEventId(1L))
                .thenThrow(new IllegalStateException("controlled parallel failure"));

        properties.setEventScaleDetailEnabled(true);
        String actual = getAuthorizedJson("/eventos/1/escala", user("operator").roles("OPERATOR"));

        assertEquals(expected, actual);
        assertTrue(output.getOut().contains("EventAssignment shadow read failed"));
        assertTrue(output.getOut().contains("IllegalStateException"));
    }

    @Test
    void shouldPreserveEventDetailResponseWhenShadowReadFails(CapturedOutput output) throws Exception {
        properties.setEventDetailEnabled(false);
        String expected = getPublicJson("/eventos/1");

        when(eventAssignmentReadService.findAllByEventId(1L))
                .thenThrow(new IllegalStateException("controlled event-detail shadow failure"));

        properties.setEventDetailEnabled(true);
        String actual = getPublicJson("/eventos/1");

        assertEquals(expected, actual);
        assertTrue(output.getOut().contains("EventAssignment shadow read failed"));
        assertTrue(output.getOut().contains("event-detail"));
        assertTrue(output.getOut().contains("IllegalStateException"));
    }

    private String getPublicJson(String url) throws Exception {
        return mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private String getAuthorizedJson(
            String url,
            SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor user
    ) throws Exception {
        return mockMvc.perform(get(url).with(user))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }
}
