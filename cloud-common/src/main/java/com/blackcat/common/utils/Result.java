package com.blackcat.common.utils;

import com.blackcat.common.config.exception.DefinitionException;
import com.blackcat.common.utils.constant.StatusCode;
import lombok.Data;

import java.io.Serializable;

import static com.blackcat.common.utils.constant.MessageConstant.ERROR;

@Data
public class Result implements Serializable {
    private static final long serialVersionUID = -2435089504958177374L;

    /**
     * 状态码
     */
    private Integer code;

    /**
     * 消息
     */
    private String message;

    /**
     * 数据
     */
    private Object data;

    public Result(){}

    public Result(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

    public Result(Integer code, String message, Object data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static Result defineError(DefinitionException e){
        return new Result(e.getErrorCode(), e.getErrorMessage());
    }

    public static Result otherError(Exception e){
        return new Result(StatusCode.SYSTEM_ERROR, ERROR);
    }

    public String toString() {
        return "Result{" +
                "code=" + code +
                ", message='" + message + '\'' +
                ", data=" + data +
                '}';
    }
}
