package com.blackcat.common.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class UserDTO implements Serializable {
    /**
     * 账号
     */
    private String accountId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 头像
     */
    private String icon;

    /**
     * 总空间
     */
    private Long totalSpace;

    /**
     * 空闲空间
     */
    private Long freeSpace;

    /**
     * token
     */
    private String tokenKey = "";
}
