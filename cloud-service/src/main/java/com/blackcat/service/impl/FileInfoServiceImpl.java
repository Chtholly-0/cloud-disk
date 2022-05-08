package com.blackcat.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.file.FileNameUtil;
import cn.hutool.core.net.URLDecoder;
import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.blackcat.common.config.exception.DefinitionException;
import com.blackcat.common.dto.FileChunkDTO;
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
import java.util.concurrent.TimeUnit;

import static com.blackcat.common.utils.constant.DatabaseConstant.*;
import static com.blackcat.common.utils.constant.MessageConstant.*;
import static com.blackcat.common.utils.constant.RedisConstant.*;
import static com.blackcat.common.utils.constant.SystemConstant.*;

@Service
@Transactional
public class FileInfoServiceImpl extends ServiceImpl<FileInfoMapper, FileInfo> implements IFileInfoService {
    @Resource
    private FileInfoMapper fileInfoMapper;

    @Resource
    private FileIdentMapper fileIdentMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 根据文件路径获取文件列表
     *
     * @param filePath 文件路径
     * @return
     */
    @Override
    public Result getListByPath(String filePath) {
        // 解码文件路径
        filePath = URLDecoder.decode(filePath, StandardCharsets.UTF_8);
        // 获取用户id
        String accountId = UserHolder.getUser().getAccountId();
        // 判断文件路径是否存在
        if (isFilePathNotExist(accountId, filePath)) {
            return new Result(StatusCode.PARAMS_ERROR, FILE_PATH_NOT_ERROR);
        }
        String redisKey = fileListKey(accountId, filePath);
        // 文件信息列表
        List<FileInfo> fileInfoList;
        // 如果key存在
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(redisKey))) {
            fileInfoList = new ArrayList<>();
            List<String> redisList = stringRedisTemplate.opsForList().range(redisKey, 1, -1);
            if (redisList != null) {
                for (String temp : redisList) {
                    fileInfoList.add(BeanUtil.toBean(temp, FileInfo.class));
                }
            }
        } else {
            // 条件构造器
            QueryWrapper<FileInfo> wrapper = new QueryWrapper<>();
            wrapper.select(FILE_PATH, FILE_NAME, FILE_TYPE, FILE_CLASS, FILE_SIZE, CREATE_TIME, UPDATE_TIME)
                    .eq(OWNER_ID, accountId)
                    .eq(FILE_PATH, filePath)
                    .eq(DELETED, false);
            // 查找
            fileInfoList = fileInfoMapper.selectList(wrapper);

            List<String> redisList = new LinkedList<>();
            redisList.add("head");
            for (FileInfo fileInfo : fileInfoList) {
                redisList.add(JSONUtil.toJsonStr(fileInfo));
            }
            stringRedisTemplate.opsForList().rightPushAll(redisKey, redisList);
            // 10分钟
            stringRedisTemplate.expire(redisKey, FILE_KEY_TTL, TimeUnit.MINUTES);
        }
        return new Result(StatusCode.OK, SEARCH_SUCCESS, fileInfoList);
    }

    /**
     * 根据文件类别获取文件信息列表
     *
     * @param fileClass 文件列表
     * @return
     */
    @Override
    public Result getListByClass(String fileClass) {
        // 类型是否存在
        if (!isfileClassExist(fileClass)) {
            return new Result(StatusCode.PARAMS_ERROR, PARAMS_ERROR);
        }
        // 获取用户id
        String accountId = UserHolder.getUser().getAccountId();

        QueryWrapper<FileInfo> wrapper = new QueryWrapper<>();
        wrapper.select(FILE_PATH, FILE_NAME, FILE_TYPE, FILE_CLASS, FILE_SIZE, CREATE_TIME, UPDATE_TIME)
                .eq(OWNER_ID, accountId)
                .eq(FILE_CLASS, fileClass)
                .eq(DELETED, false);

        List<FileInfo> fileInfoList = fileInfoMapper.selectList(wrapper);
        return new Result(StatusCode.OK, SEARCH_SUCCESS, fileInfoList);
    }

    /**
     * 创建文件夹
     *
     * @param filePath 文件路径
     * @param fileName 文件名称
     * @return
     */
    @Override
    public Result newFolder(String filePath, String fileName) {
        // 去除左右两边空白
        fileName = removeLRBlank(fileName);
        // 匹配文件夹名称不能包含非法字符
        if (isNotValidFileName(fileName)) {
            return new Result(StatusCode.PARAMS_ERROR, FILE_NAME_ERROR);
        }
        // 获取用户id
        String accountId = UserHolder.getUser().getAccountId();
        // 判断文件路径是否存在
        if (isFilePathNotExist(accountId, filePath)) {
            return new Result(StatusCode.PARAMS_ERROR, FILE_PATH_NOT_ERROR);
        }
        // 获取当前时间
        Timestamp nowTime = DateTime.now().toTimestamp();

        List<FileInfo> fileInfoList;
        // 查询当前文件夹下文件列表
        QueryWrapper<FileInfo> wrapper = new QueryWrapper<>();
        wrapper.eq(OWNER_ID, accountId)
                .eq(FILE_PATH, filePath)
                .eq(DELETED, false);
        fileInfoList = fileInfoMapper.selectList(wrapper);
        // 循环遍历查找是否重名
        for (FileInfo fileInfo : fileInfoList) {
            if (fileInfo.getFileName().equals(fileName)) {
                return new Result(StatusCode.PARAMS_ERROR, FILE_NAME_REPEAT_ERROR);
            }
        }
        // 存储文件信息
        FileInfo fileInfo = new FileInfo();
        fileInfo.setOwnerId(accountId);
        fileInfo.setFilePath(filePath);
        fileInfo.setFileName(fileName);
        fileInfo.setFileType(FOLDER);
        fileInfo.setFileClass(FOLDER);
        fileInfo.setCreateTime(nowTime);

        int insert = fileInfoMapper.insert(fileInfo);
        if (insert == 1) {
            // 删除redis
            String redisKey = fileListKey(accountId, filePath);
            stringRedisTemplate.delete(redisKey);
            return new Result(StatusCode.OK, CREATE_SUCCESS);
        }
        return new Result(StatusCode.SERVICE_ERROR, CREATE_ERROR);
    }

    /**
     * 根据关键词查找文件信息列表
     *
     * @param searchKayWord 关键词
     * @return
     */
    @Override
    public Result searchFiles(String searchKayWord) {
        // 获取用户id
        String accountId = UserHolder.getUser().getAccountId();
        // 解码关键词
        searchKayWord = URLDecoder.decode(searchKayWord, StandardCharsets.UTF_8);
        // 长度判断
        if (searchKayWord.length() > 255) {
            return new Result(StatusCode.PARAMS_ERROR, KEY_WORLD_TOO_LONG_ERROR);
        }
        // 是否合法
        if (isNotValidFileName(searchKayWord)) {
            return new Result(StatusCode.PARAMS_ERROR, KEY_WORLD_ILLEGAL_ERROR);
        }
        // 更具关键字模糊匹配获取文件信息列表
        QueryWrapper<FileInfo> wrapper = new QueryWrapper<>();
        wrapper.select(FILE_PATH, FILE_NAME, FILE_TYPE, FILE_CLASS, FILE_SIZE, CREATE_TIME, UPDATE_TIME)
                .eq(OWNER_ID, accountId)
                .like(FILE_NAME, searchKayWord)
                .eq(DELETED, false);

        List<FileInfo> fileInfoList = fileInfoMapper.selectList(wrapper);
        return new Result(StatusCode.OK, OPERATION_SUCCESS, fileInfoList);
    }

    /**
     * 删除文件信息
     *
     * @param fileList 要删除的文件列表
     * @return
     */
    @Override
    public Result fileDelete(FileDeletedList fileList) {
        // 获取用户id
        String accountId = UserHolder.getUser().getAccountId();
        // 获取当前时间
        Timestamp nowTime = DateTime.now().toTimestamp();
        // 获取文件路径
        String filePath = fileList.getFilePath();
        // 判断文件路径是否存在
        if (isFilePathNotExist(accountId, filePath)) {
            return new Result(StatusCode.PARAMS_ERROR, FILE_PATH_NOT_ERROR);
        }
        // 获取文件名列表
        List<String> fileNameList = fileList.getFileNameList();
        // 循环判断文件名是否合法
        for (String temp : fileNameList) {
            if (isNotValidFileName(temp)) {
                return new Result(StatusCode.PARAMS_ERROR, FILE_NAME_ERROR);
            }
        }
        // 修改
        UpdateWrapper<FileInfo> wrapper = new UpdateWrapper<>();
        wrapper.set(DELETED, true)
                .set(UPDATE_TIME, nowTime)
                .eq(FILE_PATH, filePath)
                .in(FILE_NAME, fileNameList)
                .eq(DELETED, false);
        fileInfoMapper.update(null, wrapper);
        // 删除redis
        String redisKey = fileListKey(accountId, filePath);
        stringRedisTemplate.delete(redisKey);

        return new Result(StatusCode.OK, OPERATION_SUCCESS);
    }

    /**
     * 文件重命名
     *
     * @param filePath 文件路径
     * @param oldName  旧名称
     * @param newName  新名称
     * @return
     */
    @Override
    public Result fileRename(String filePath, String oldName, String newName) {
        // 文件名是否合法
        if (isNotValidFileName(newName) || isNotValidFileName(oldName)) {
            return new Result(StatusCode.PARAMS_ERROR, FILE_NAME_ERROR);
        }
        // 文件路径解码
        filePath = URLDecoder.decode(filePath, StandardCharsets.UTF_8);
        // 获取用户id
        String accountId = UserHolder.getUser().getAccountId();
        // 文件路径是否存在
        if (isFilePathNotExist(accountId, filePath)) {
            return new Result(StatusCode.PARAMS_ERROR, FILE_PATH_NOT_ERROR);
        }
        // 构造条件构造器
        QueryWrapper<FileInfo> query = new QueryWrapper<>();
        query.eq(OWNER_ID, accountId)
                .eq(FILE_PATH, filePath)
                .eq(DELETED, false);
        List<FileInfo> list = fileInfoMapper.selectList(query);

        // 文件是否存在
        boolean isFileExist = false;
        FileInfo fileInfo = null;
        for (FileInfo info : list) {
            String fileName = info.getFileName();
            // 新名称重复 返回错误
            if (fileName.equals(newName)) {
                return new Result(StatusCode.PARAMS_ERROR, FILE_NAME_REPEAT_ERROR);
            }
            // 查询旧名称是否存在
            if (!isFileExist && fileName.equals(oldName)) {
                isFileExist = true;
                fileInfo = info;
            }
        }
        // 如果文件不存在
        if (!isFileExist) {
            return new Result(StatusCode.PARAMS_ERROR, FILE_NOT_FOUND_ERROR);
        }
        // 当前时间
        Timestamp nowTime = DateTime.now().toTimestamp();
        UpdateWrapper<FileInfo> update = new UpdateWrapper<>();

        // 如果是文件夹
        if (fileInfo.getFileType().equals(FOLDER)) {
            update.set(FILE_NAME, newName)
                    .set(UPDATE_TIME, nowTime)
                    .eq(FILE_ID, fileInfo.getFileId());
            int result = fileInfoMapper.update(null, update);
            if (result == 1) {
                update.clear();
                String oldPath = fileInfo.getFilePath() + fileInfo.getFileName() + "/";
                String newPath = fileInfo.getFilePath() + newName + "/";
                // 将文件夹下的文件路径修改
                update.setSql(FILE_PATH + " = concat('" + newPath + "', substr(" + FILE_PATH + ", " + (oldPath.length() + 1) + "))")
                        .likeRight(FILE_PATH, oldPath)
                        .eq(DELETED, false);
                fileInfoMapper.update(null, update);
                return new Result(StatusCode.OK, RENAME_SUCCESS);
            }
        } else {
            String fileType = FileNameUtil.extName(newName);
            String fileClass = fileClassMap(fileType);
            update.set(FILE_NAME, newName)
                    .set(FILE_TYPE, fileType)
                    .set(FILE_CLASS, fileClass)
                    .set(UPDATE_TIME, nowTime)
                    .eq(FILE_ID, fileInfo.getFileId());
            int result = fileInfoMapper.update(null, update);
            if (result == 1) {
                return new Result(StatusCode.OK, RENAME_SUCCESS);
            }
        }
        // 删除redis
        String redisKey = fileListKey(accountId, filePath);
        stringRedisTemplate.delete(redisKey);

        return new Result(StatusCode.SERVICE_ERROR, RENAME_ERROR2);
    }

    /**
     * 复制或移动
     *
     * @param fromPath     原来路径
     * @param fileNameList 选中文件名
     * @param toPath       目标路径
     * @param opera        操作 copy或move
     * @return
     */
    @Override
    public Result fileCopyOrMoveTo(String fromPath, Set<String> fileNameList, String toPath, String opera) {
        // 操作类型获取
        boolean operaFlag;
        if (opera.equals("copy")) {
            operaFlag = true;
        } else if (opera.equals("move")) {
            operaFlag = false;
        } else {
            return new Result(StatusCode.PARAMS_ERROR, PARAMS_ERROR);
        }
        // 获取用户名
        String accountId = UserHolder.getUser().getAccountId();
        // 获取当前时间
        Timestamp nowTime = DateTime.now().toTimestamp();
        // 判断路径是否存在
        if (isFilePathNotExist(accountId, fromPath) || isFilePathNotExist(accountId, toPath)) {
            return new Result(StatusCode.PARAMS_ERROR, FILE_PATH_NOT_ERROR);
        }
        // 不能在当前目录下
        if (fromPath.equals(toPath)) {
            return new Result(StatusCode.SERVICE_ERROR, operaFlag ? COPY_ERROR : MOVE_ERROR);
        }
        // 不能在其子目录下
        for (String name : fileNameList) {
            if (toPath.indexOf(fromPath + name + "/") == 0) {
                return new Result(StatusCode.SERVICE_ERROR, operaFlag ? COPY_ERROR : MOVE_ERROR);
            }
        }
        // 选中个数不能为0
        if (fileNameList.size() == 0) {
            return new Result(StatusCode.SERVICE_ERROR, PARAMS_ERROR);
        }
        // 获取选中列表
        List<FileInfo> selectedList = query().eq(OWNER_ID, accountId)
                .eq(FILE_PATH, fromPath)
                .in(FILE_NAME, fileNameList)
                .eq(DELETED, false)
                .list();
        // 未找到
        if (selectedList.size() != fileNameList.size()) {
            return new Result(StatusCode.PARAMS_ERROR, FILE_NOT_FOUND_ERROR);
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
        if (isSuccess) {
            // 清除缓存
            String redisKey1 = fileListKey(accountId, fromPath);
            String redisKey2 = fileListKey(accountId, toPath);
            stringRedisTemplate.delete(redisKey1);
            stringRedisTemplate.delete(redisKey2);
            return new Result(StatusCode.OK, operaFlag ? COPY_SUCCESS : MOVE_SUCCESS);
        } else {
            return new Result(StatusCode.SERVICE_ERROR, OPERATION_ERROR);
        }
    }

    /**
     * 文件预创建
     *
     * @param filePath 文件路径
     * @param fileName 文件名称
     * @param chunks   文件总分块数
     * @param fileSize 文件总大小
     * @return
     */
    @Override
    public Result filePreCreate(String filePath, String fileName, Integer chunks, Long fileSize) {
        UserDTO user = UserHolder.getUser();
        // 用户id
        String accountId = user.getAccountId();
        // 文件路径是否存在
        if (isFilePathNotExist(accountId, filePath)) {
            return new Result(StatusCode.PARAMS_ERROR, FILE_PATH_NOT_ERROR);
        }
        // 文件名是否合法
        if (isNotValidFileName(fileName)) {
            return new Result(StatusCode.PARAMS_ERROR, FILE_NAME_ERROR);
        }
        // 判断剩余空间是否足够
        if (user.getFreeSpace() < fileSize) {
            return new Result(StatusCode.SERVICE_ERROR, SPACE_NOT_ENOUGH);
        }

        // 现在时间
        Timestamp nowTime = DateTime.now().toTimestamp();

        HashMap<String, String> map = new HashMap<>();
        // 随机生成请求id
        String requestId = IdUtil.simpleUUID();
        map.put(FILE_PATH, filePath);
        map.put(FILE_NAME, fileName);
        map.put("chunks", String.valueOf(chunks));
        map.put(FILE_SIZE, String.valueOf(fileSize));
        map.put("time", nowTime.toString());

        String redisKey = fileUploadKey(accountId, requestId);
        stringRedisTemplate.opsForHash().putAll(redisKey, map);

        return new Result(StatusCode.OK, null, requestId);
    }

    /**
     * 上传文件分片
     *
     * @param chunkDTO 文件分片信息
     * @return
     */
    @Override
    public Result uploadChunk(FileChunkDTO chunkDTO) {
        // 用户id与请求id
        String accountId = UserHolder.getUser().getAccountId();
        String requestId = chunkDTO.getIdentifier();
        // 查询redis中是否存在
        String redisKey = fileUploadKey(accountId, requestId);
        if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(redisKey))) {
            return new Result(StatusCode.PARAMS_ERROR, "请求不存在");
        }

        // 分块目录
        String tempFilePath = getFolderPath(requestId) + "temp" + File.separator;
        File dest = new File(tempFilePath + chunkDTO.getChunkNumber());
        if (!dest.exists()) {
            if (!dest.getParentFile().exists()) {
                dest.getParentFile().mkdirs();
            }
            // 存储分片
            try {
                chunkDTO.getFile().transferTo(dest);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return new Result(StatusCode.OK, FILE_UPLOAD_SUCCESS);
    }

    /**
     * 合并文件块
     *
     * @param requestId 请求id
     * @return
     */
    @Override
    public Result mergeChunks(String requestId) {
        UserDTO user = UserHolder.getUser();
        // 用户id
        String accountId = user.getAccountId();
        // redis文件上传信息key
        String redisKey = fileUploadKey(accountId, requestId);
        // 获取redis信息
        Map<Object, Object> map = stringRedisTemplate.opsForHash().entries(redisKey);
        if (map.size() == 0) {
            return new Result(StatusCode.PARAMS_ERROR, PARAMS_ERROR);
        }
        // 获取文件信息
        String fileName = (String) map.get(FILE_NAME);
        String filePath = (String) map.get(FILE_PATH);
        Integer chunks = Integer.valueOf(map.get("chunks").toString());
        long fileSize = Long.parseLong(map.get(FILE_SIZE).toString());
        String fileType = FileNameUtil.extName(fileName);

        // 获取文件夹路径
        String folderPath = getFolderPath(requestId);
        String tempFilePath = folderPath + "temp" + File.separator;
        File tempFile = new File(tempFilePath);
        // 文件真实名称
        String trueName = requestId + ((fileType.length() == 0) ? "" : ("." + fileType));
        File mergeFile = new File(folderPath + trueName);
        // 判断分块是否全有，有则开始合并
        if (checkChunks(tempFilePath, chunks)) {
            File fileTemp = new File(tempFilePath);
            final File[] files = fileTemp.listFiles();
            assert files != null;
            // 文件名排序
            Arrays.sort(files, Comparator.comparingInt(a -> Integer.parseInt(a.getName())));

            try (RandomAccessFile randomAccessFileWriter = new RandomAccessFile(mergeFile, "rw")) {
                byte[] bytes = new byte[1024];
                for (File file : files) {
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
            return new Result(StatusCode.SERVICE_ERROR, "数据块未上传完毕");
        }
        // 计算合并后文件md5值
        String md5 = SecureUtil.md5(mergeFile);
        // 查找数据
        QueryWrapper<FileIdent> query = new QueryWrapper<>();
        query.eq(MD5, md5).last("limit 1");
        FileIdent fileIdent = fileIdentMapper.selectOne(query);

        boolean isFileExit = false;
        if (fileIdent != null) {
            isFileExit = true;
            FileUtil.del(folderPath);
        } else {
            // 删除临时文件
            FileUtil.del(tempFile);
        }
        Timestamp nowTime = DateTime.now().toTimestamp();
        // 存储文件信息
        saveFileInfo(user, filePath, fileName, fileType, fileSize, md5, nowTime, isFileExit, trueName);

        // 合并成功后删除redis信息
        stringRedisTemplate.delete(redisKey);
        // 缓存路径redis删除
        String redisKey2 = fileListKey(accountId, filePath);
        stringRedisTemplate.delete(redisKey2);

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
                throw new DefinitionException(StatusCode.PARAMS_ERROR, FILE_PATH_ERROR);
            }
            StringBuilder path = new StringBuilder("/");
            List<FileInfo> list = query()
                    .eq(OWNER_ID, accountId)
                    .and(wrapper -> {
                        for (int i = 1; i < len; i++) {
                            String temp = split[i];
                            if (isNotValidFileName(temp)) {
                                throw new DefinitionException(StatusCode.PARAMS_ERROR, FILE_PATH_ERROR);
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
        throw new DefinitionException(StatusCode.PARAMS_ERROR, FILE_PATH_ERROR);
    }

    // 获取文件夹路径
    private String getFolderPath(String requestId) {
        return FILE_SPACE_PATH + requestId.charAt(0) +
                File.separator + requestId +
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
