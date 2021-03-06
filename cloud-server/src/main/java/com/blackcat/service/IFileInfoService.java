package com.blackcat.service;

import com.blackcat.common.dto.FileChunkDTO;
import com.blackcat.common.utils.Result;
import com.blackcat.common.dto.FileDeletedList;

import java.util.Set;

public interface IFileInfoService {
    Result getListByPath(String filePath);

    Result getListByClass(String fileClass);

    Result newFolder(String filePath, String fileName);

    Result searchFiles(String searchKayWord);

    Result fileDelete(FileDeletedList fileList);

    Result fileRename(String filePath, String oldName, String newName);

    Result fileCopyOrMoveTo(String fromPath, Set<String> fileNameList, String toPath, String opera);

    Result filePreCreate(String filePath, String fileName, Integer chunks, Long fileSize);

    Result uploadChunk(FileChunkDTO chunkDTO);

    Result mergeChunks(String identifier);

}
