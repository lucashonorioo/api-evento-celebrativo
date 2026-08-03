package com.eventoscelebrativos.exception.error;

import com.eventoscelebrativos.dto.response.InvalidRecipientDTO;

import java.time.Instant;
import java.util.List;

public class NotificationInvalidRecipientsErrorResponse extends ErrorResponse {

    private final List<InvalidRecipientDTO> invalidRecipients;

    public NotificationInvalidRecipientsErrorResponse(
            Instant timestamp,
            int status,
            String error,
            String errorCode,
            String path,
            List<InvalidRecipientDTO> invalidRecipients
    ) {
        super(timestamp, status, error, errorCode, path);
        this.invalidRecipients = invalidRecipients;
    }

    public List<InvalidRecipientDTO> getInvalidRecipients() {
        return invalidRecipients;
    }
}
