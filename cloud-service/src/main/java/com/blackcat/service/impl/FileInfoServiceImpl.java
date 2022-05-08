package com.blackcat.service.impl;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.file.FileNameUtil;
import cn.hutool.core.net.URLDecoder;
import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.SecureUtil;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.blackcat.common.config.exception.DefinitionException;
import com.blackcat.common.dto.FileChunkDTO;
import com.blackcat.common.dto.FileChunkResultDTO;
import com.blackcat.common.utils.Result;
import com.blackcat.common.utils.UserHolder;
import com.blackcat.common.utils.constant.StatusCode;
import com.blackcat.common.dto.FileDeletedList;
import com.blackcat.common.dto.UserDTO;
import com.blackcat.dao.mapper.FileInfoMapper;
import com.blackcat.dao.mapper.FileIdentMapper;
import com.blackcat.dao.mapper.UserMapper;
import com.blackcat.dao.pojo.FileIdent;
import com.blackcat.dao.pojo.FileInfo;
import com.blackcat.dao.pojo.User;
import com.blackcat.service.IFileInfoService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.*;

import static com.blackcat.common.utils.constant.DatabaseConstant.*;
import static com.blackcat.common.utils.constant.MessageConstant.*;
import static com.blackcat.common.utils.constant.RedisConstant.*;
import static com.blackcat.common.utils.constant.SystemConstant.*;

@Service
@Transactional
public class FileInfoServiceImpl extends ServiceImpl<FileInfoMapper, FileInfo> implements IFileInfoService {

    @Resource
    private FileIdentMapper fileIdentMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result getListByPath(String filePath) {
        // 解码文件路径
        filePath = URLDecoder.decode(filePath, StandardCharsets.UTF_8);
        // 获取用户id
        String accountId = UserHolder.getUser().getAccountId();
        // 判断文件路径是否存在
        if (isFilePathNotExist(accountId, filePath)) {
            return new Result(StatusCode.ERROR, FILE_PATH_NOT_ERROR);
        }
        // 查询文件
        List<FileInfo> fileInfoList = query()
                .select(FILE_PATH, FILE_NAME, FILE_TYPE, FILE_CLASS, FILE_SIZE, CREATE_TIME, UPDATE_TIME)
                .eq(OWNER_ID, accountId)
                .eq(FILE_PATH, filePath)
                .eq(DELETED, false)
                .list();
        return new Result(StatusCode.OK, SEARCH_SUCCESS, fileInfoList);
    }

    @Override
    public Result getListByClass(String fileClass) {
        if (!isfileClassExist(fileClass)) {
            return new Result(StatusCode.ERROR, PARAMS_ERROR);
        }
        // 获取用户id
        String accountId = UserHolder.getUser().getAccountId();
        List<FileInfo> list = query()
                .select(FILE_PATH, FILE_NAME, FILE_TYPE, FILE_CLASS, FILE_SIZE, CREATE_TIME, UPDATE_TIME)
                .eq(OWNER_ID, accountId)
                .eq(FILE_CLASS, fileClass)
                .eq(DELETED, false)
                .list();
        return new Result(StatusCode.OK, SEARCH_SUCCESS, list);
    }

    @Override
    public Result newFolder(String filePath, String fileName) {
        // 去除左右两边空白
        fileName = removeLRBlank(fileName);
        // 匹配文件夹名称不能包含非法字符
        if (isNotValidFileName(fileName)) {
            return new Result(StatusCode.ERROR, FILE_NAME_ERROR);
        }
        // 获取用户id
        String accountId = UserHolder.getUser().getAccountId();
        if (isFilePathNotExist(accountId, filePath)) {
            return new Result(StatusCode.ERROR, FILE_PATH_NOT_ERROR);
        }
        // 获取当前时间
        Timestamp nowTime = DateTime.now().toTimestamp();
        // 查询当前文件夹 通过文件id和用户id
        List<FileInfo> list = query()
                .eq(OWNER_ID, accountId)
                .eq(FILE_PATH, filePath)
                .eq(DELETED, false)
                .list();
        for (FileInfo fileInfo : list) {
            if (fileInfo.getFileName().equals(fileName)) {
                return new Result(StatusCode.ERROR, FILE_NAME_REPEAT_ERROR);
            }
        }
        FileInfo fileInfo = new FileInfo();
        fileInfo.setOwnerId(accountId);
        fileInfo.setFilePath(filePath);
        fileInfo.setFileName(fileName);
        fileInfo.setFileType(FOLDER);
        fileInfo.setFileClass(FOLDER);
        fileInfo.setCreateTime(nowTime);
        if (save(fileInfo)) {
            return new Result(StatusCode.OK, CREATE_SUCCESS);
        }
        return new Result(StatusCode.ERROR, CREATE_ERROR);
    }

    @Override
    public Result searchFiles(String searchKayWord) {
        // 获取用户id
        String accountId = UserHolder.getUser().getAccountId();
        searchKayWord = URLDecoder.decode(searchKayWord, StandardCharsets.UTF_8);
        // 长度判断
        if (searchKayWord.length() > 255) {
            return new Result(StatusCode.ERROR, KEY_WORLD_TOO_LONG_ERROR);
        }
        // 是否合法
        if (isNotValidFileName(searchKayWord)) {
            return new Result(StatusCode.ERROR, KEY_WORLD_ILLEGAL_ERROR);
        }
        // 更具关键字模糊匹配获取文件信息列表
        List<FileInfo> fileInfoList = query()
                .select(FILE_PATH, FILE_NAME, FILE_TYPE, FILE_CLASS, FILE_SIZE, CREATE_TIME, UPDATE_TIME)
                .eq(OWNER_ID, accountId)
                .like(FILE_NAME, searchKayWord)
                .eq(DELETED, false)
                .list();
        return new Result(StatusCode.OK, OPERATION_SUCCESS, fileInfoList);
    }

    @Override
    public Result fileDelete(FileDeletedList fileList) {
        // 获取用户id
        String accountId = UserHolder.getUser().getAccountId();
        Timestamp nowTime = DateTime.now().toTimestamp();

        String filePath = fileList.getFilePath();
        // 判断文件路径是否存在
        if (isFilePathNotExist(accountId, filePath)) {
            return new Result(StatusCode.ERROR, FILE_PATH_NOT_ERROR);
        }
        // 获取文件名列表
        List<String> fileNameList = fileList.getFileNameList();
        // 循环判断文件名是否合法
        for (String temp : fileNameList) {
            if (isNotValidFileName(temp)) {
                return new Result(StatusCode.ERROR, FILE_NAME_ERROR);
            }
        }
        boolean update = update()
                .set(DELETED, true)
                .set(UPDATE_TIME, nowTime)
                .eq(FILE_PATH, filePath)
                .in(FILE_NAME, fileNameList)
                .eq(DELETED, false)
                .update();
        return update ? new Result(StatusCode.OK, OPERATION_SUCCESS) :
                new Result(StatusCode.ERROR, OPERATION_ERROR);
    }

    @Override
    public Result fileRename(String filePath, String oldName, String newName) {
        // 文件名是否合法
        if (isNotValidFileName(newName) || isNotValidFileName(oldName)) {
            return new Result(StatusCode.ERROR, FILE_NAME_ERROR);
        }
        // 文件路径解码
        filePath = URLDecoder.decode(filePath, StandardCharsets.UTF_8);
        // 获取用户id
        String accountId = UserHolder.getUser().getAccountId();
        if (isFilePathNotExist(accountId, filePath)) {
            return new Result(StatusCode.ERROR, FILE_PATH_NOT_ERROR);
        }
        List<FileInfo> list = query()
                .eq(OWNER_ID, accountId)
                .eq(FILE_PATH, filePath)
                .eq(DELETED, false)
                .list();
        // 文件是否存在
        boolean isFileExist = false;
        FileInfo fileInfo = null;
        for (FileInfo info : list) {
            if (info.getFileName().equals(oldName)) {
                isFileExist = true;
                fileInfo = info;
                break;
            }
        }
        // 如果文件不存在
        if (!isFileExist) {
            return new Result(StatusCode.ERROR, FILE_NOT_FOUND_ERROR);
        }
        // 新名字不能重复
        for (FileInfo info : list) {
            if (info.getFileName().equals(newName)) {
                return new Result(StatusCode.ERROR, FILE_NAME_REPEAT_ERROR);
            }
        }
        Timestamp nowTime = DateTime.now().toTimestamp();
        if (fileInfo.getFileType().equals(FOLDER)) {
            boolean update = update()
                    .set(FILE_NAME, newName)
                    .set(UPDATE_TIME, nowTime)
                    .eq(FILE_ID, fileInfo.getFileId())
                    .update();
            if (update) {
                String oldPath = fileInfo.getFilePath() + fileInfo.getFileName() + "/";
                String newPath = fileInfo.getFilePath() + newName + "/";
                if (update()
                        .setSql(FILE_PATH + " = concat('" + newPath + "', substr(" + FILE_PATH + ", " + (oldPath.length() + 1) + "))")
                        .likeRight(FILE_PATH, oldPath)
                        .eq(DELETED, false)
                        .update()) {
                    return new Result(StatusCode.OK, RENAME_SUCCESS);
                }
            }
        } else {
            String fileType = FileNameUtil.extName(newName);
            String fileClass = fileClassMap(fileType);
            boolean update = update()
                    .set(FILE_NAME, newName)
                    .set(FILE_TYPE, fileType)
                    .set(FILE_CLASS, fileClass)
                    .set(UPDATE_TIME, nowTime)
                    .eq(FILE_ID, fileInfo.getFileId())
                    .update();
            if (update) {
                return new Result(StatusCode.OK, RENAME_SUCCESS);
            }
        }
        return new Result(StatusCode.ERROR, RENAME_ERROR2);
    }

    @Override
    public Result fileCopyOrMoveTo(String fromPath, Set<String> fileNameList, String toPath, String opera) {
        // 操作类型获取
        boolean operaFlag;
        if (opera.equals("copy")) {
            operaFlag = true;
        } else if (opera.equals("move")) {
            operaFlag = false;
        } else {
            return new Result(StatusCode.ERROR, PARAMS_ERROR);
        }
        // 获取用户名
        String accountId = UserHolder.getUser().getAccountId();
        // 获取当前时间
        Timestamp nowTime = DateTime.now().toTimestamp();
        // 判断路径是否存在
        if (isFilePathNotExist(accountId, fromPath) || isFilePathNotExist(accountId, toPath)) {
            return new Result(StatusCode.ERROR, FILE_PATH_NOT_ERROR);
        }
        // 不能在当前目录下
        if (fromPath.equals(toPath)) {
            return new Result(StatusCode.ERROR, operaFlag ? COPY_ERROR : MOVE_ERROR);
        }
        // 不能在其子目录下
        for (String name : fileNameList) {
            if (toPath.indexOf(fromPath + name + "/") == 0) {
                return new Result(StatusCode.ERROR, operaFlag ? COPY_ERROR : MOVE_ERROR);
            }
        }
        // 选中个数不能为0
        if (fileNameList.size() == 0) {
            return new Result(StatusCode.ERROR, PARAMS_ERROR);
        }
        // 选中列表
        List<FileInfo> selectedList = query().eq(OWNER_ID, accountId)
                .eq(FILE_PATH, fromPath)
                .in(FILE_NAME, fileNameList)
                .eq(DELETED, false)
                .list();
        // 未找到
        if (selectedList.size() != fileNameList.size()) {
            return new Result(StatusCode.ERROR, FILE_NOT_FOUND_ERROR);
        }
        // 获取选中的文件夹下的文件 映射关系 文件夹名称--文件列表
        Map<String, List<FileInfo>> folderMap = new HashMap<>();
        for (FileInfo info : selectedList) {
            if (info.getFileType().equals(FOLDER)) {
                List<FileInfo> list = query().eq(OWNER_ID, accountId)
                        .likeRight(FILE_PATH, fromPath + info.getFileName() + "/")
                        .eq(DELETED, false)
                        .list();
                folderMap.put(info.getFileName(), list);
            }
        }
        // 获取目标文件夹下的文件
        List<FileInfo> toPathFileList = query()
                .eq(OWNER_ID, accountId)
                .eq(FILE_PATH, toPath)
                .eq(DELETED, false)
                .list();
        // 目标文件夹下文件名称集合
        Set<String> fileSet = new HashSet<>();
        for (FileInfo info : toPathFileList) {
            fileSet.add(info.getFileName());
        }
        // 查看选中文件与目标目录下文件是否又重复名称
        for (FileInfo info : selectedList) {
            String fileName = info.getFileName();
            String mainName = FileNameUtil.mainName(fileName); // 主名
            String extName = FileNameUtil.extName(fileName); // 扩展名
            int i = 1;
            while (fileSet.contains(fileName)) {
                fileName = mainName + "(" + i + ")" + (extName.length() == 0 ? "" : "." + extName);
                i++;
            }
            if (i != 1) {
                String oldName = info.getFileName();
                if (folderMap.containsKey(oldName)) {
                    List<FileInfo> list = folderMap.get(oldName);
                    String newPath = fromPath + fileName + "/";
                    String oldPath = fromPath + oldName + "/";
                    list.forEach(k -> k.setFilePath(newPath + k.getFilePath().substring(oldPath.length())));
                    folderMap.remove(oldName);
                    folderMap.put(fileName, list);
                }
                info.setFileName(fileName);
            }
        }
        // 修改选中文件的路径
        for (FileInfo info : selectedList) {
            info.setFilePath(toPath);
            info.setUpdateTime(nowTime);
            String fileName = info.getFileName();
            if (folderMap.containsKey(fileName)) {
                List<FileInfo> list = folderMap.get(fileName);
                String newPath = toPath + fileName + "/";
                String oldPath = fromPath + fileName + "/";
                list.forEach(k -> k.setFilePath(newPath + k.getFilePath().substring(oldPath.length())));
            }
        }
        // 所有文件列表
        folderMap.forEach((key, value) -> selectedList.addAll(value));
        boolean isSuccess;
        // 如果是复制
        if (operaFlag) {
            // id置空
            for (FileInfo info : selectedList) {
                info.setFileId(null);
            }
            isSuccess = saveBatch(selectedList);
        } else {
            isSuccess = updateBatchById(selectedList);
        }
        return isSuccess ? new Result(StatusCode.OK, operaFlag ? COPY_SUCCESS : MOVE_SUCCESS) :
                new Result(StatusCode.ERROR, OPERATION_ERROR);
    }

    @Override
    public Result checkFileChunk(FileChunkDTO chunk) {
        String identifier = chunk.getIdentifier();
        // 判断md5值是否规范
        if (!identifier.matches("[a-z0-9]+") || identifier.length() != 32) {
            return new Result(StatusCode.SYSTEM_ERROR, PARAMS_ERROR);
        }
        // 获取用户信息已经文件信息
        UserDTO user = UserHolder.getUser();
        String accountId = user.getAccountId();
        String filename = chunk.getFilename();
        String filePath = chunk.getRelativePath();
        Long fileSize = chunk.getTotalSize();
        // 文件名不能非法
        if (isNotValidFileName(filename)) {
            return new Result(StatusCode.ERROR, FILE_NAME_ERROR);
        }
        // 文件路径是否存在
        if (isFilePathNotExist(accountId, filePath)) {
            return new Result(StatusCode.ERROR, FILE_PATH_NOT_ERROR);
        }
        // 空间不足
        if (user.getFreeSpace() < fileSize) {
            return new Result(StatusCode.ERROR, SPACE_NOT_ENOUGH);
        }
        Timestamp nowTime = DateTime.now().toTimestamp();
        FileIdent fileIdent = fileIdentMapper.selectById(identifier);
        FileChunkResultDTO resultDTO = new FileChunkResultDTO();
        String message = null;
        if (fileIdent != null) {
            String fileType = FileNameUtil.extName(filename);

            saveFileInfo(user, filePath, filename, fileType, fileSize, identifier, nowTime, true, null);

            resultDTO.setSkipUpload(true);
            message = FILE_UPLOAD_SUCCESS;
        } else {
            // 不存在 则不能跳过上传
            resultDTO.setSkipUpload(false);
            // 获取临时文件路径
            String tempFilePath = getFolderPath(identifier) + "temp" + File.separator;
            // 获取已经上传过的文件块id集合
            Set<Integer> uploaded = new HashSet<>();
            File tempFile = new File(tempFilePath);
            if (tempFile.exists()) {
                File[] files = tempFile.listFiles();
                if (files != null) {
                    for (File file : files) {
                        uploaded.add(Integer.valueOf(file.getName()));
                    }
                }
            }
            resultDTO.setUploaded(uploaded);
            // 将文件信息存储到redis
            HashMap<String, String> map = new HashMap<>();
            map.put("filename", filename);
            map.put("relativePath", filePath);
            map.put("totalChunks", chunk.getTotalChunks().toString());
            map.put("totalSize", fileSize.toString());
            map.put("time", nowTime.toString());
            String fileUploadKey = FILE_UPLOAD_KEY + accountId + ":" + identifier;
            stringRedisTemplate.opsForHash().putAll(fileUploadKey, map);
        }
        return new Result(StatusCode.OK, message, resultDTO);
    }

    @Override
    public Result uploadChunk(FileChunkDTO chunkDTO) {
        // 分块目录
        String tempFilePath = getFolderPath(chunkDTO.getIdentifier()) + "temp" + File.separator;
        File dest = new File(tempFilePath + chunkDTO.getChunkNumber());
        if (!dest.exists()) {
            dest.getParentFile().mkdirs();
            // 存储分片
            try {
                chunkDTO.getFile().transferTo(dest);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        FileChunkResultDTO resultDTO = new FileChunkResultDTO();
        resultDTO.setSkipUpload(false);
        return new Result(StatusCode.OK, FILE_UPLOAD_SUCCESS, resultDTO);
    }

    @Override
    public Result mergeChunks(String identifier) {
        UserDTO user = UserHolder.getUser();
        // 用户id
        String accountId = user.getAccountId();
        // redis文件上传信息key
        String fileUploadKey = FILE_UPLOAD_KEY + accountId + ":" + identifier;
        // 获取redis信息
        Map<Object, Object> map = stringRedisTemplate.opsForHash().entries(fileUploadKey);
        if (map.size() == 0) {
            return new Result(StatusCode.ERROR, PARAMS_ERROR);
        }
        String fileName = (String) map.get("filename");
        String filePath = (String) map.get("relativePath");
        Integer totalChunks = Integer.valueOf(map.get("totalChunks").toString());
        long fileSize = Long.parseLong(map.get("totalSize").toString());
        String fileType = FileNameUtil.extName(fileName);

        // 获取文件夹路径
        String folderPath = getFolderPath(identifier);
        String fileTempPath = folderPath + "temp" + File.separator;
        File tempFile = new File(fileTempPath);
        // 文件真实名称
        String trueName = IdUtil.simpleUUID() + ((fileType.length() == 0) ? "" : ("." + fileType));
        File mergeFile = new File(folderPath + trueName);
        // 判断分块是否全有，有则开始合并
        if (checkChunks(fileTempPath, totalChunks)) {
            File fileTemp = new File(fileTempPath);
            final File[] files = fileTemp.listFiles();
            assert files != null;
            List<File> fileList = Arrays.asList(files);
            fileList.sort(Comparator.comparingInt(a -> Integer.parseInt(a.getName())));
            if (!mergeFile.getParentFile().exists()) {
                mergeFile.getParentFile().mkdirs();
            }
            try (RandomAccessFile randomAccessFileWriter = new RandomAccessFile(mergeFile, "rw")) {
                byte[] bytes = new byte[1024];
                for (File file : fileList) {
                    RandomAccessFile randomAccessFileReader = new RandomAccessFile(file, "r");
                    int len;
                    while ((len = randomAccessFileReader.read(bytes)) != -1) {
                        randomAccessFileWriter.write(bytes, 0, len);
                    }
                    randomAccessFileReader.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else { // 没有则返回错误信息
            return new Result(StatusCode.ERROR, "合并失败, 数据块未上传完");
        }
        // 计算合并后文件md5值
        String md5 = SecureUtil.md5(mergeFile);
        // 判断文件是否完整
        if (!md5.equals(identifier) || mergeFile.length() != fileSize) {
            FileUtil.del(mergeFile);
            FileUtil.del(tempFile);
            return new Result(StatusCode.SYSTEM_ERROR, "文件数据异常, 请重新上传");
        }
        Timestamp nowTime = DateTime.now().toTimestamp();
        // 判断空间是否空余
        if (user.getFreeSpace() < fileSize) {
            return new Result(StatusCode.ERROR, SPACE_NOT_ENOUGH);
        }
        // 存储文件信息
        saveFileInfo(user, filePath, fileName, fileType, fileSize, md5, nowTime, false, trueName);

        // 合并成功后删除redis信息
        stringRedisTemplate.delete(fileUploadKey);
        // 删除临时文件
        FileUtil.del(tempFile);
        return new Result(StatusCode.OK, FILE_UPLOAD_SUCCESS);
    }

    // 检测分块是否都存在
    private boolean checkChunks(String fileTempPath, Integer totalChunks) {
        for (int i = 1; i <= totalChunks; i++) {
            File file = new File(fileTempPath + File.separator + i);
            if (!file.exists()) {
                return false;
            }
        }
        return true;
    }

    // 时间格式转换
    private String timeTransform(String time) {
        char[] chs = time.toCharArray();
        StringBuilder sb = new StringBuilder("_");
        for (char t : chs) {
            if (t == '-' || t == ':') {
                continue;
            }
            if (t == '.') {
                break;
            }
            sb.append(t == ' ' ? '_' : t);
        }
        return sb.toString();
    }

    // 去除左右空白
    private String removeLRBlank(String str) {
        char[] chs = str.toCharArray();
        int l = 0, r = chs.length - 1;
        while (l < r) {
            if (chs[l] != ' ' && chs[r] != ' ') {
                break;
            }
            if (chs[l] == ' ') {
                l++;
            }
            if (chs[r] == ' ') {
                r--;
            }
        }
        if (l >= r) {
            return "";
        }
        return str.substring(l, r + 1);
    }

    // 判断文件名是否合法
    private boolean isNotValidFileName(String fileName) {
        if (fileName == null || fileName.length() == 0 || fileName.length() > 255) {
            return true;
        }
        int blank = 0;
        // 不能包含非法字符
        for (char c : fileName.toCharArray()) {
            if (c == '/' || c == '\\' || c == '"' || c == ':' || c == '|' || c == '*' || c == '?' || c == '<' || c == '>') {
                return true;
            }
            if (c == ' ') {
                blank++;
            }
        }
        return blank == fileName.length();
    }

    // 检测路径是否存在
    public boolean isFilePathNotExist(String accountId, String filePath) {
        if (filePath.equals("/")) {
            return false;
        }
        if (filePath.matches("/(.+?/)*$")) {
            String[] split = filePath.split("/");
            int len = split.length;
            if (len == 0) {
                throw new DefinitionException(StatusCode.ERROR, FILE_PATH_ERROR);
            }
            StringBuilder path = new StringBuilder("/");
            List<FileInfo> list = query()
                    .eq(OWNER_ID, accountId)
                    .and(wrapper -> {
                        for (int i = 1; i < len; i++) {
                            String temp = split[i];
                            if (isNotValidFileName(temp)) {
                                throw new DefinitionException(StatusCode.ERROR, FILE_PATH_ERROR);
                            }
                            wrapper.or(k -> k
                                    .eq(FILE_PATH, path.toString())
                                    .eq(FILE_NAME, temp)
                            );
                            if (i + 1 < len) {
                                path.append(temp);
                                path.append('/');
                            }
                        }
                    })
                    .eq(FILE_TYPE, FOLDER)
                    .eq(DELETED, false)
                    .list();
            return list.size() != len - 1;
        }
        throw new DefinitionException(StatusCode.ERROR, FILE_PATH_ERROR);
    }

    // 获取文件夹路径
    private String getFolderPath(String identifier) {
        return FILE_SPACE_PATH + identifier.substring(0, 3) +
                File.separator + identifier +
                File.separator;
    }

    // 存储文件信息
    private void saveFileInfo(UserDTO user, String filePath, String fileName, String fileType, Long fileSize, String md5, Timestamp nowTime, boolean isFileExist, String trueName) {
        String accountId = user.getAccountId();
        // 文件名冲突判断
        List<FileInfo> list = query()
                .eq(OWNER_ID, accountId)
                .eq(FILE_PATH, filePath)
                .list();
        for (FileInfo info : list) {
            if (info.getFileName().equals(fileName)) {
                fileName = FileNameUtil.mainName(fileName) + timeTransform(nowTime.toString());
                if (fileType.length() != 0) {
                    fileName += "." + fileType;
                }
                break;
            }
        }
        // 存储文件
        FileInfo fileInfo = new FileInfo();
        fileInfo.setOwnerId(accountId);
        fileInfo.setFilePath(filePath);
        fileInfo.setFileName(fileName);
        fileInfo.setFileType(fileType);
        fileInfo.setFileClass(fileClassMap(fileType));
        fileInfo.setFileSize(fileSize);
        fileInfo.setMd5(md5);
        fileInfo.setCreateTime(nowTime);

        save(fileInfo);

        // 如果文件已经存入 则文件引用加一
        if (isFileExist) {
            UpdateWrapper<FileIdent> wrapper = new UpdateWrapper<>();
            wrapper.setSql(REF_COUNT + " = " + REF_COUNT + " + 1")
                    .eq(MD5, md5);
            fileIdentMapper.update(null, wrapper);
        } else { // 不存在则插入文件标识表
            FileIdent fileIdent = new FileIdent();
            fileIdent.setMd5(md5);
            fileIdent.setName(trueName);
            fileIdentMapper.insert(fileIdent);
        }

        // 用户表减去文件空闲空间
        UpdateWrapper<User> wrapper = new UpdateWrapper<>();
        wrapper.setSql(FREE_SPACE + " = " + FREE_SPACE + " - " + fileSize)
                .eq(ACCOUNT_ID, accountId);
        userMapper.update(null, wrapper);
        String tokenKey = user.getTokenKey();
        stringRedisTemplate.opsForHash().put(tokenKey, "freeSpace", String.valueOf(user.getFreeSpace() - fileSize));
    }
}
