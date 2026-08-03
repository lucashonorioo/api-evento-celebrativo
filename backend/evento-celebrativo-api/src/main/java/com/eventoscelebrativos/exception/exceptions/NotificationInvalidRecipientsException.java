package com.eventoscelebrativos.exception.exceptions;

import com.eventoscelebrativos.dto.response.InvalidRecipientDTO;
import org.springframework.http.HttpStatus;

import java.util.List;

public class NotificationInvalidRecipientsException extends ErrorResponseException {

    private final List<InvalidRecipientDTO> invalidRecipients;

    public NotificationInvalidRecipientsException(List<InvalidRecipientDTO> invalidRecipients) {
        super(
                "Um ou mais destinatários informados são inválidos.",
                HttpStatus.CONFLICT,
                "NOTIFICATION_INVALID_RECIPIENTS"
        );
        this.invalidRecipients = invalidRecipients;
    }

    public List<InvalidRecipientDTO> getInvalidRecipients() {
        return invalidRecipients;
    }
}
