package com.blackcat.web.controller;

import com.blackcat.common.dto.FileChunkDTO;
import com.blackcat.common.utils.Result;
import com.blackcat.common.utils.constant.StatusCode;

import com.blackcat.common.dto.FileDeletedList;
import com.blackcat.service.impl.FileInfoServiceImpl;

import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Set;


@RestController
@RequestMapping("/file")
public class FileController {
    @Resource
    private FileInfoServiceImpl fileInfoService;

    // 根据文件路径获取文件列表
    @GetMapping("/path")
    public Result getListByPath(@RequestParam String filePath) {
        return fileInfoService.getListByPath(filePath);
    }

    // 更具文件类别获取文件列表
    @GetMapping("/class")
    public Result getListByClass(@RequestParam String fileClass) {
        return fileInfoService.getListByClass(fileClass);
    }

    // 新建文件夹
    @PostMapping("/newFolder")
    public Result newFolder(@RequestParam String filePath, @RequestParam String fileName) {
        return fileInfoService.newFolder(filePath, fileName);
    }

    // 文件关键字查找
    @GetMapping("/search")
    public Result searchFiles(@RequestParam String searchKayWord) {
        return fileInfoService.searchFiles(searchKayWord);
    }

    // 文件删除
    @PostMapping("/delete")
    public Result fileDelete(@RequestBody FileDeletedList fileList) {
        return fileInfoService.fileDelete(fileList);
    }

    // 文件重命名
    @GetMapping("/rename")
    public Result fileRename(@RequestParam String filePath, @RequestParam String oldName, @RequestParam String newName) {
        return fileInfoService.fileRename(filePath, oldName, newName);
    }

    // 文件复制或移动
    @PostMapping("/copyOrMove")
    public Result fileCopyOrMoveTo(@RequestParam String fromPath, @RequestParam Set<String> fileNameList, @RequestParam String toPath, @RequestParam String opera) {
        return fileInfoService.fileCopyOrMoveTo(fromPath, fileNameList, toPath, opera);
    }

    // 文件预创建
    @GetMapping("/preCreate")
    public Result filePreCreate(@RequestParam String filePath, @RequestParam String fileName, @RequestParam Integer chunks, @RequestParam Long fileSize) {
        return fileInfoService.filePreCreate(filePath, fileName, chunks, fileSize);
    }

    // 文件分片上传接口
    @PostMapping("/chunk")
    public Result uploadChunk(FileChunkDTO chunk) {
        return fileInfoService.uploadChunk(chunk);
    }

    // 文件合并接口
    @GetMapping("/merge")
    public Result mergeChunks(@RequestParam String requestId) {
        return fileInfoService.mergeChunks(requestId);
    }

    // 测试接口
    @GetMapping("/test")
    public Result test(@RequestParam String filePath) {
        fileInfoService.isFilePathNotExist("", filePath);
        return new Result(StatusCode.OK, "??????????");
    }

}
