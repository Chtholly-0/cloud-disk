package com.blackcat.common.utils.constant;

public class RedisConstant {
    // 登录token
    public static final String LOGIN_USER_KEY = "login:token:";
    // token保存时长
    public static final Long LOGIN_USER_TTL = (24 * 60L);

    // 文件信息列表key头
    public static final String FILE_KEY = "file:";

    // 文件key存放时间
    public static final Long FILE_KEY_TTL = 10L;
    // 文件操作key头
    public static final String FILE_OPERA_KEY = "file:opera:";
    // 文件上传信息key头
    public static final String FILE_UPLOAD_KEY = "file:upload:";

    public static String filePathListKey(String accountId, String filePath) {
        return FILE_KEY + accountId + ":" + filePath;
    }
}
