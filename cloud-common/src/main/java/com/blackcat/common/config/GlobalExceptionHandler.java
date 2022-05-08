package com.blackcat.common.config;

import com.blackcat.common.config.exception.DefinitionException;
import com.blackcat.common.utils.Result;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

@ControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理自定义异常
     */
    @ExceptionHandler(value = DefinitionException.class)
    @ResponseBody
    public Result myExceptionHandler(DefinitionException e){
        return Result.defineError(e);
    }

    /**
     * 处理其他异常
     */
    @ExceptionHandler(value = Exception.class)
    @ResponseBody
    public Result exceptionHandler(Exception e) {
        e.printStackTrace();
        return Result.otherError(e);
    }
}
