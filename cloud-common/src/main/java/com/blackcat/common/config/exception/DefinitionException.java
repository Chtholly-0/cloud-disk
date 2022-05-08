package com.blackcat.common.config.exception;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class DefinitionException extends RuntimeException{
    // 错误码
    protected Integer errorCode;
    // 错误信息
    protected String errorMessage;

    public DefinitionException(){}

    public DefinitionException(Integer errorCode, String errorMessage) {
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }
}
