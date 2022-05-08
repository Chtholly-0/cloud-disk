package com.blackcat.service;

import com.blackcat.common.dto.FileChunkDTO;
import com.blackcat.common.utils.Result;
import com.blackcat.common.dto.FileDeletedList;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

public interface IFileInfoService {
    Result getListByPath(String filePath);

    Result getListByClass(String fileClass);

    Result newFolder(String filePath, String fileName);

    Result searchFiles(String searchKayWord);

    Result fileDelete(FileDeletedList fileList);

    Result fileRename(String filePath, String oldName, String newName);

    Result fileCopyOrMoveTo(String fromPath, Set<String> fileNameList, String toPath, String opera);

    Result uploadChunk(FileChunkDTO chunkDTO);

    Result mergeChunks(String identifier);

    Result checkFileChunk(FileChunkDTO chunk);
}
