package com.blackcat.common.utils.constant;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;

public class SystemConstant {
    // 文件路径
    public static final String FILE_SPACE_PATH = System.getProperty("user.dir") + File.separator + "file-space" + File.separator;
    // 类型映射
    private static final HashMap<String, String> fileClassMap;
    // 类型集合
    private static final HashSet<String> fileClassSet;

    static {
        fileClassMap = new HashMap<String, String>() {{
            put("gif", "picture");
            put("jpg", "picture");
            put("png", "picture");
            put("ico", "picture");
            put("mp3", "music");
            put("mp4", "video");
            put("txt", "document");
            put("doc", "document");
            put("docx", "document");
            put("csv", "document");
            put("xlsx", "document");
            put("xls", "document");
            put("pptx", "document");
            put("ppt", "document");
            put("pdf", "document");
            put("md", "document");
        }};

        fileClassSet = new HashSet<String>(){{
//           add("folder");
           add("picture");
           add("music");
           add("video");
           add("document");
           add("other");
        }};
    }

    // 类别映射
    public static String fileClassMap(String fileType) {
        return fileClassMap.getOrDefault(fileType, "other");
    }

    public static boolean isfileClassExist(String fileClass) {
        return fileClassSet.contains(fileClass);
    }
}
