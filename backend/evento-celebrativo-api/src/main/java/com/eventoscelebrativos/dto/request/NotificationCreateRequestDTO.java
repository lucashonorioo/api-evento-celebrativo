package com.eventoscelebrativos.dto.request;

import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.NotificationAudience;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class NotificationCreateRequestDTO {

    @NotNull(message = "O campo audience é obrigatório")
    private NotificationAudience audience;

    @NotBlank(message = "O campo title é obrigatório")
    private String title;

    @NotBlank(message = "O campo message é obrigatório")
    private String message;

    private List<MinistryType> ministryTypes;

    private List<Long> personIds;

    public NotificationCreateRequestDTO() {
    }

    public NotificationCreateRequestDTO(
            NotificationAudience audience,
            String title,
            String message,
            List<MinistryType> ministryTypes,
            List<Long> personIds
    ) {
        this.audience = audience;
        this.title = title;
        this.message = message;
        this.ministryTypes = ministryTypes;
        this.personIds = personIds;
    }

    public NotificationAudience getAudience() {
        return audience;
    }

    public void setAudience(NotificationAudience audience) {
        this.audience = audience;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<MinistryType> getMinistryTypes() {
        return ministryTypes;
    }

    public void setMinistryTypes(List<MinistryType> ministryTypes) {
        this.ministryTypes = ministryTypes;
    }

    public List<Long> getPersonIds() {
        return personIds;
    }

    public void setPersonIds(List<Long> personIds) {
        this.personIds = personIds;
    }
}
