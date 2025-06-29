package com.eventoscelebrativos.exception.exception;

import java.io.Serial;

public class ResourceNotFoundException extends RuntimeException{
    @Serial
    private static final long serialVersionUID = 1L;

    public ResourceNotFoundException(String msg){
        super(msg);
    }
    public ResourceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
