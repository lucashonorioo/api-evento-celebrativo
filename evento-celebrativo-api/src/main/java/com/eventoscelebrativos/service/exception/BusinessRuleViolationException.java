package com.eventoscelebrativos.service.exception;

import java.io.Serial;

public class BusinessRuleViolationException extends RuntimeException{
    @Serial
    private static final long serialVersionUID = 1L;
    public BusinessRuleViolationException(String msg){
        super(msg);
    }
    public BusinessRuleViolationException(String msg, Throwable cause){
        super(msg, cause);
    }
}
