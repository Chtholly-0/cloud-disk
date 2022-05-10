package com.blackcat.dao.pojo;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

@Data
@TableName("file_ident")
public class FileIdent implements Serializable {
    /**
     * md5值，文件唯一标识
     */
    @TableId
    private String md5;

    /**
     * 文件真实名
     */
    private String url;

    /**
     * 文件引用数
     */
    private Integer refCount;
}
