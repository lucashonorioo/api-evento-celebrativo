package com.eventoscelebrativos.controller.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidRequestBodyException extends RuntimeException{
    public InvalidRequestBodyException(String msg){
        super(msg);
    }
}
