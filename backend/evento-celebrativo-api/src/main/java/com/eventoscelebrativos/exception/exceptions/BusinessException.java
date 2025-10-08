package com.eventoscelebrativos.exception.exceptions;


import org.springframework.http.HttpStatus;

public class BusinessException extends ErrorResponseException{
   public BusinessException(String msg){
       super(msg, HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_VIOLATION");
   }
}
