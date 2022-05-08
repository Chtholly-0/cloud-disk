package com.blackcat.common.utils.constant;

public class RedisConstant {
    // 登录token
    public static final String LOGIN_USER_KEY = "login:token:";
    // token保存时长
    public static final Long LOGIN_USER_TTL = (24 * 60L);

    // 文件key存放时间
    public static final Long FILE_KEY_TTL = 10L;

    // 文件列表key
    public static String fileListKey(String accountId, String filePath) {
        return "file:" + accountId + ":path:" + filePath;
    }
    // 文件上传key
    public static String fileUploadKey(String accountId, String requestId) {
        return "file:" + accountId + ":upload:" + requestId;
    }
}
