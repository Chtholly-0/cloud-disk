package com.blackcat.common.dto;

import lombok.Data;

import java.util.Set;

@Data
public class FileChunkResultDTO {
    /**
     * 是否跳过上传
     */
    private Boolean skipUpload;

    /**
     * 已上传分片的集合
     */
    private Set<Integer> uploaded;

}
