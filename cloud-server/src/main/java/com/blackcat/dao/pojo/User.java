package com.blackcat.dao.pojo;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

@Data
@TableName("user")
public class User implements Serializable {
    /**
     * 账号
     */
    @TableId(value = "account_id")
    private String accountId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 密码
     */
    private String password;

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
}
