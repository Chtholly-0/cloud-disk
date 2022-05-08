package com.blackcat.common.utils.constant;

public class StatusCode {
    public static final int OK = 200; //成功
    public static final int ERROR = 201; //失败
    public static final int LOGIN_ERROR = 202; //未登录
    public static final int ACCESS_ERROR = 203; //权限不足
    public static final int REMOTE_ERROR = 204; //远程调用失败
    public static final int REP_ERROR = 205; //重复操作

    /**
     * 系统出错
     */
    public static final int SYSTEM_ERROR = 400;
}
