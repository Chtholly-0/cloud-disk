package com.blackcat.common.dto;

import com.blackcat.dao.pojo.FileInfo;
import lombok.Data;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.List;

@Data
public class FileDTO implements Serializable {
    // 文件路径
    private String filePath;

    // 文件名称
    private String fileName;

    // 文件类型
    private String fileType;

    // 文件分类
    private String fileClass;

    // 文件大小
    private Long fileSize;

    // 子文件
    private List<FileInfo> children;

    // 创建时间
    private Timestamp createTime;

    // 修改时间
    private Timestamp updateTime;
}
