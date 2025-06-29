package com.eventoscelebrativos.exception.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class MissingPathVariableException extends RuntimeException{
    public MissingPathVariableException(String msg){
        super(msg);
    }
}
