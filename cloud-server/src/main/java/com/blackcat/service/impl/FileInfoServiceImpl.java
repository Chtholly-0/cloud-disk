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
     * ????????????????????????????????????
     *
     * @param filePath ????????????
     * @return
     */
    @Override
    public Result getListByPath(String filePath) {
        // ??????????????????
        filePath = URLDecoder.decode(filePath, StandardCharsets.UTF_8);
        // ????????????id
        String accountId = UserHolder.getUser().getAccountId();
        // ??????????????????????????????
        if (isFilePathNotExist(accountId, filePath)) {
            return new Result(StatusCode.PARAMS_ERROR, FILE_PATH_NOT_ERROR);
        }
        String redisKey = fileListKey(accountId, filePath);
        // ??????????????????
        List<FileInfo> fileInfoList;
        // ??????key??????
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(redisKey))) {
            fileInfoList = new ArrayList<>();
            List<String> redisList = stringRedisTemplate.opsForList().range(redisKey, 1, -1);
            if (redisList != null) {
                for (String temp : redisList) {
                    fileInfoList.add(BeanUtil.toBean(temp, FileInfo.class));
                }
            }
        } else {
            // ???????????????
            QueryWrapper<FileInfo> wrapper = new QueryWrapper<>();
            wrapper.select(FILE_PATH, FILE_NAME, FILE_TYPE, FILE_CLASS, FILE_SIZE, CREATE_TIME, UPDATE_TIME)
                    .eq(OWNER_ID, accountId)
                    .eq(FILE_PATH, filePath)
                    .eq(DELETED, false);
            // ??????
            fileInfoList = fileInfoMapper.selectList(wrapper);

            List<String> redisList = new LinkedList<>();
            redisList.add("head");
            for (FileInfo fileInfo : fileInfoList) {
                redisList.add(JSONUtil.toJsonStr(fileInfo));
            }
            stringRedisTemplate.opsForList().rightPushAll(redisKey, redisList);
            // 10??????
            stringRedisTemplate.expire(redisKey, FILE_KEY_TTL, TimeUnit.MINUTES);
        }
        return new Result(StatusCode.OK, SEARCH_SUCCESS, fileInfoList);
    }

    /**
     * ??????????????????????????????????????????
     *
     * @param fileClass ????????????
     * @return
     */
    @Override
    public Result getListByClass(String fileClass) {
        // ??????????????????
        if (!isfileClassExist(fileClass)) {
            return new Result(StatusCode.PARAMS_ERROR, PARAMS_ERROR);
        }
        // ????????????id
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
     * ???????????????
     *
     * @param filePath ????????????
     * @param fileName ????????????
     * @return
     */
    @Override
    public Result newFolder(String filePath, String fileName) {
        // ????????????????????????
        fileName = removeLRBlank(fileName);
        // ?????????????????????????????????????????????
        if (isNotValidFileName(fileName)) {
            return new Result(StatusCode.PARAMS_ERROR, FILE_NAME_ERROR);
        }
        // ????????????id
        String accountId = UserHolder.getUser().getAccountId();
        // ??????????????????????????????
        if (isFilePathNotExist(accountId, filePath)) {
            return new Result(StatusCode.PARAMS_ERROR, FILE_PATH_NOT_ERROR);
        }
        // ??????????????????
        Timestamp nowTime = DateTime.now().toTimestamp();

        List<FileInfo> fileInfoList;
        // ????????????????????????????????????
        QueryWrapper<FileInfo> wrapper = new QueryWrapper<>();
        wrapper.eq(OWNER_ID, accountId)
                .eq(FILE_PATH, filePath)
                .eq(DELETED, false);
        fileInfoList = fileInfoMapper.selectList(wrapper);
        // ??????????????????????????????
        for (FileInfo fileInfo : fileInfoList) {
            if (fileInfo.getFileName().equals(fileName)) {
                return new Result(StatusCode.PARAMS_ERROR, FILE_NAME_REPEAT_ERROR);
            }
        }
        // ??????????????????
        FileInfo fileInfo = new FileInfo();
        fileInfo.setOwnerId(accountId);
        fileInfo.setFilePath(filePath);
        fileInfo.setFileName(fileName);
        fileInfo.setFileType(FOLDER);
        fileInfo.setFileClass(FOLDER);
        fileInfo.setCreateTime(nowTime);

        int insert = fileInfoMapper.insert(fileInfo);
        if (insert == 1) {
            // ??????redis
            String redisKey = fileListKey(accountId, filePath);
            stringRedisTemplate.delete(redisKey);
            return new Result(StatusCode.OK, CREATE_SUCCESS);
        }
        return new Result(StatusCode.SERVICE_ERROR, CREATE_ERROR);
    }

    /**
     * ???????????????????????????????????????
     *
     * @param searchKayWord ?????????
     * @return
     */
    @Override
    public Result searchFiles(String searchKayWord) {
        // ????????????id
        String accountId = UserHolder.getUser().getAccountId();
        // ???????????????
        searchKayWord = URLDecoder.decode(searchKayWord, StandardCharsets.UTF_8);
        // ????????????
        if (searchKayWord.length() > 255) {
            return new Result(StatusCode.PARAMS_ERROR, KEY_WORLD_TOO_LONG_ERROR);
        }
        // ????????????
        if (isNotValidFileName(searchKayWord)) {
            return new Result(StatusCode.PARAMS_ERROR, KEY_WORLD_ILLEGAL_ERROR);
        }
        // ???????????????????????????????????????????????????
        QueryWrapper<FileInfo> wrapper = new QueryWrapper<>();
        wrapper.select(FILE_PATH, FILE_NAME, FILE_TYPE, FILE_CLASS, FILE_SIZE, CREATE_TIME, UPDATE_TIME)
                .eq(OWNER_ID, accountId)
                .like(FILE_NAME, searchKayWord)
                .eq(DELETED, false);

        List<FileInfo> fileInfoList = fileInfoMapper.selectList(wrapper);
        return new Result(StatusCode.OK, OPERATION_SUCCESS, fileInfoList);
    }

    /**
     * ??????????????????
     *
     * @param fileList ????????????????????????
     * @return
     */
    @Override
    public Result fileDelete(FileDeletedList fileList) {
        // ????????????id
        String accountId = UserHolder.getUser().getAccountId();
        // ??????????????????
        Timestamp nowTime = DateTime.now().toTimestamp();
        // ??????????????????
        String filePath = fileList.getFilePath();
        // ??????????????????????????????
        if (isFilePathNotExist(accountId, filePath)) {
            return new Result(StatusCode.PARAMS_ERROR, FILE_PATH_NOT_ERROR);
        }
        // ?????????????????????
        List<String> fileNameList = fileList.getFileNameList();
        // ?????????????????????????????????
        for (String temp : fileNameList) {
            if (isNotValidFileName(temp)) {
                return new Result(StatusCode.PARAMS_ERROR, FILE_NAME_ERROR);
            }
        }
        // ??????
        UpdateWrapper<FileInfo> wrapper = new UpdateWrapper<>();
        wrapper.set(DELETED, true)
                .set(UPDATE_TIME, nowTime)
                .eq(FILE_PATH, filePath)
                .in(FILE_NAME, fileNameList)
                .eq(DELETED, false);
        fileInfoMapper.update(null, wrapper);
        // ??????redis
        String redisKey = fileListKey(accountId, filePath);
        stringRedisTemplate.delete(redisKey);

        return new Result(StatusCode.OK, OPERATION_SUCCESS);
    }

    /**
     * ???????????????
     *
     * @param filePath ????????????
     * @param oldName  ?????????
     * @param newName  ?????????
     * @return
     */
    @Override
    public Result fileRename(String filePath, String oldName, String newName) {
        // ?????????????????????
        if (isNotValidFileName(newName) || isNotValidFileName(oldName)) {
            return new Result(StatusCode.PARAMS_ERROR, FILE_NAME_ERROR);
        }
        // ??????????????????
        filePath = URLDecoder.decode(filePath, StandardCharsets.UTF_8);
        // ????????????id
        String accountId = UserHolder.getUser().getAccountId();
        // ????????????????????????
        if (isFilePathNotExist(accountId, filePath)) {
            return new Result(StatusCode.PARAMS_ERROR, FILE_PATH_NOT_ERROR);
        }
        // ?????????????????????
        QueryWrapper<FileInfo> query = new QueryWrapper<>();
        query.eq(OWNER_ID, accountId)
                .eq(FILE_PATH, filePath)
                .eq(DELETED, false);
        List<FileInfo> list = fileInfoMapper.selectList(query);

        // ??????????????????
        boolean isFileExist = false;
        FileInfo fileInfo = null;
        for (FileInfo info : list) {
            String fileName = info.getFileName();
            // ??????????????? ????????????
            if (fileName.equals(newName)) {
                return new Result(StatusCode.PARAMS_ERROR, FILE_NAME_REPEAT_ERROR);
            }
            // ???????????????????????????
            if (!isFileExist && fileName.equals(oldName)) {
                isFileExist = true;
                fileInfo = info;
            }
        }
        // ?????????????????????
        if (!isFileExist) {
            return new Result(StatusCode.PARAMS_ERROR, FILE_NOT_FOUND_ERROR);
        }
        // ????????????
        Timestamp nowTime = DateTime.now().toTimestamp();
        UpdateWrapper<FileInfo> update = new UpdateWrapper<>();

        // ??????????????????
        if (fileInfo.getFileType().equals(FOLDER)) {
            update.set(FILE_NAME, newName)
                    .set(UPDATE_TIME, nowTime)
                    .eq(FILE_ID, fileInfo.getFileId());
            int result = fileInfoMapper.update(null, update);
            if (result == 1) {
                update.clear();
                String oldPath = fileInfo.getFilePath() + fileInfo.getFileName() + "/";
                String newPath = fileInfo.getFilePath() + newName + "/";
                // ????????????????????????????????????
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
        // ??????redis
        String redisKey = fileListKey(accountId, filePath);
        stringRedisTemplate.delete(redisKey);

        return new Result(StatusCode.SERVICE_ERROR, RENAME_ERROR2);
    }

    /**
     * ???????????????
     *
     * @param fromPath     ????????????
     * @param fileNameList ???????????????
     * @param toPath       ????????????
     * @param opera        ?????? copy???move
     * @return
     */
    @Override
    public Result fileCopyOrMoveTo(String fromPath, Set<String> fileNameList, String toPath, String opera) {
        // ??????????????????
        boolean operaFlag;
        if (opera.equals("copy")) {
            operaFlag = true;
        } else if (opera.equals("move")) {
            operaFlag = false;
        } else {
            return new Result(StatusCode.PARAMS_ERROR, PARAMS_ERROR);
        }
        // ???????????????
        String accountId = UserHolder.getUser().getAccountId();
        // ??????????????????
        Timestamp nowTime = DateTime.now().toTimestamp();
        // ????????????????????????
        if (isFilePathNotExist(accountId, fromPath) || isFilePathNotExist(accountId, toPath)) {
            return new Result(StatusCode.PARAMS_ERROR, FILE_PATH_NOT_ERROR);
        }
        // ????????????????????????
        if (fromPath.equals(toPath)) {
            return new Result(StatusCode.SERVICE_ERROR, operaFlag ? COPY_ERROR : MOVE_ERROR);
        }
        // ????????????????????????
        for (String url : fileNameList) {
            if (toPath.indexOf(fromPath + url + "/") == 0) {
                return new Result(StatusCode.SERVICE_ERROR, operaFlag ? COPY_ERROR : MOVE_ERROR);
            }
        }
        // ?????????????????????0
        if (fileNameList.size() == 0) {
            return new Result(StatusCode.SERVICE_ERROR, PARAMS_ERROR);
        }
        // ??????????????????
        List<FileInfo> selectedList = query().eq(OWNER_ID, accountId)
                .eq(FILE_PATH, fromPath)
                .in(FILE_NAME, fileNameList)
                .eq(DELETED, false)
                .list();
        // ?????????
        if (selectedList.size() != fileNameList.size()) {
            return new Result(StatusCode.PARAMS_ERROR, FILE_NOT_FOUND_ERROR);
        }
        // ???????????????????????????????????? ???????????? ???????????????--????????????
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
        // ?????????????????????????????????
        List<FileInfo> toPathFileList = query()
                .eq(OWNER_ID, accountId)
                .eq(FILE_PATH, toPath)
                .eq(DELETED, false)
                .list();
        // ????????????????????????????????????
        Set<String> fileSet = new HashSet<>();
        for (FileInfo info : toPathFileList) {
            fileSet.add(info.getFileName());
        }
        // ???????????????????????????????????????????????????????????????
        for (FileInfo info : selectedList) {
            String fileName = info.getFileName();
            String mainName = FileNameUtil.mainName(fileName); // ??????
            String extName = FileNameUtil.extName(fileName); // ?????????
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
        // ???????????????????????????
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
        // ??????????????????
        folderMap.forEach((key, value) -> selectedList.addAll(value));
        boolean isSuccess;
        // ???????????????
        if (operaFlag) {
            // id??????
            for (FileInfo info : selectedList) {
                info.setFileId(null);
            }
            isSuccess = saveBatch(selectedList);
        } else {
            isSuccess = updateBatchById(selectedList);
        }
        if (isSuccess) {
            // ????????????
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
     * ???????????????
     *
     * @param filePath ????????????
     * @param fileName ????????????
     * @param chunks   ??????????????????
     * @param fileSize ???????????????
     * @return
     */
    @Override
    public Result filePreCreate(String filePath, String fileName, Integer chunks, Long fileSize) {
        UserDTO user = UserHolder.getUser();
        // ??????id
        String accountId = user.getAccountId();
        // ????????????????????????
        if (isFilePathNotExist(accountId, filePath)) {
            return new Result(StatusCode.PARAMS_ERROR, FILE_PATH_NOT_ERROR);
        }
        // ?????????????????????
        if (isNotValidFileName(fileName)) {
            return new Result(StatusCode.PARAMS_ERROR, FILE_NAME_ERROR);
        }
        // ??????????????????????????????
        if (user.getFreeSpace() < fileSize) {
            return new Result(StatusCode.SERVICE_ERROR, SPACE_NOT_ENOUGH);
        }

        // ????????????
        Timestamp nowTime = DateTime.now().toTimestamp();

        HashMap<String, String> map = new HashMap<>();
        // ??????????????????id
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
     * ??????????????????
     *
     * @param chunkDTO ??????????????????
     * @return
     */
    @Override
    public Result uploadChunk(FileChunkDTO chunkDTO) {
        // ??????id?????????id
        String accountId = UserHolder.getUser().getAccountId();
        String requestId = chunkDTO.getIdentifier();
        // ??????redis???????????????
        String redisKey = fileUploadKey(accountId, requestId);
        if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(redisKey))) {
            return new Result(StatusCode.PARAMS_ERROR, "???????????????");
        }

        // ????????????
        String tempFilePath = getFolderPath(requestId) + "temp" + File.separator;
        File dest = new File(tempFilePath + chunkDTO.getChunkNumber());
        if (!dest.exists()) {
            if (!dest.getParentFile().exists()) {
                dest.getParentFile().mkdirs();
            }
            // ????????????
            try {
                chunkDTO.getFile().transferTo(dest);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return new Result(StatusCode.OK, FILE_UPLOAD_SUCCESS);
    }

    /**
     * ???????????????
     *
     * @param requestId ??????id
     * @return
     */
    @Override
    public Result mergeChunks(String requestId) {
        UserDTO user = UserHolder.getUser();
        // ??????id
        String accountId = user.getAccountId();
        // redis??????????????????key
        String redisKey = fileUploadKey(accountId, requestId);
        // ??????redis??????
        Map<Object, Object> map = stringRedisTemplate.opsForHash().entries(redisKey);
        if (map.size() == 0) {
            return new Result(StatusCode.PARAMS_ERROR, PARAMS_ERROR);
        }
        // ??????????????????
        String fileName = (String) map.get(FILE_NAME);
        String filePath = (String) map.get(FILE_PATH);
        Integer chunks = Integer.valueOf(map.get("chunks").toString());
        long fileSize = Long.parseLong(map.get(FILE_SIZE).toString());
        String fileType = FileNameUtil.extName(fileName);

        // ?????????????????????
        String folderPath = getFolderPath(requestId);
        String tempFilePath = folderPath + "temp" + File.separator;
        File tempFile = new File(tempFilePath);
        // ??????????????????
        String trueName = requestId + ((fileType.length() == 0) ? "" : ("." + fileType));
        File mergeFile = new File(folderPath + trueName);
        // ?????????????????????????????????????????????
        if (checkChunks(tempFilePath, chunks)) {
            File fileTemp = new File(tempFilePath);
            final File[] files = fileTemp.listFiles();
            assert files != null;
            // ???????????????
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
        } else { // ???????????????????????????
            return new Result(StatusCode.SERVICE_ERROR, "????????????????????????");
        }
        // ?????????????????????md5???
        String md5 = SecureUtil.md5(mergeFile);
        // ????????????
        QueryWrapper<FileIdent> query = new QueryWrapper<>();
        query.eq(MD5, md5).last("limit 1");
        FileIdent fileIdent = fileIdentMapper.selectOne(query);

        boolean isFileExit = false;
        if (fileIdent != null) {
            isFileExit = true;
            FileUtil.del(folderPath);
        } else {
            // ??????????????????
            FileUtil.del(tempFilePath);
        }
        Timestamp nowTime = DateTime.now().toTimestamp();
        // ??????????????????
        saveFileInfo(user, filePath, fileName, fileType, fileSize, md5, nowTime, isFileExit, trueName);

        // ?????????????????????redis??????
        stringRedisTemplate.delete(redisKey);
        // ????????????redis??????
        String redisKey2 = fileListKey(accountId, filePath);
        stringRedisTemplate.delete(redisKey2);

        return new Result(StatusCode.OK, FILE_UPLOAD_SUCCESS);
    }

    // ???????????????????????????
    private boolean checkChunks(String fileTempPath, Integer totalChunks) {
        for (int i = 1; i <= totalChunks; i++) {
            File file = new File(fileTempPath + File.separator + i);
            if (!file.exists()) {
                return false;
            }
        }
        return true;
    }

    // ??????????????????
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

    // ??????????????????
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

    // ???????????????????????????
    private boolean isNotValidFileName(String fileName) {
        if (fileName == null || fileName.length() == 0 || fileName.length() > 255) {
            return true;
        }
        int blank = 0;
        // ????????????????????????
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

    // ????????????????????????
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

    // ?????????????????????
    private String getFolderPath(String requestId) {
        return FILE_SPACE_PATH + requestId.charAt(0) +
                File.separator + requestId +
                File.separator;
    }

    // ??????????????????
    private void saveFileInfo(UserDTO user, String filePath, String fileName, String fileType, Long fileSize, String md5, Timestamp nowTime, boolean isFileExist, String trueName) {
        String accountId = user.getAccountId();
        // ?????????????????????
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
        // ????????????
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

        // ???????????????????????? ?????????????????????
        if (isFileExist) {
            UpdateWrapper<FileIdent> wrapper = new UpdateWrapper<>();
            wrapper.setSql(REF_COUNT + " = " + REF_COUNT + " + 1")
                    .eq(MD5, md5);
            fileIdentMapper.update(null, wrapper);
        } else { // ?????????????????????????????????
            FileIdent fileIdent = new FileIdent();
            fileIdent.setMd5(md5);
            fileIdent.setName(trueName);
            fileIdentMapper.insert(fileIdent);
        }

        // ?????????????????????????????????
        UpdateWrapper<User> wrapper = new UpdateWrapper<>();
        wrapper.setSql(FREE_SPACE + " = " + FREE_SPACE + " - " + fileSize)
                .eq(ACCOUNT_ID, accountId);
        userMapper.update(null, wrapper);
        String tokenKey = user.getTokenKey();
        stringRedisTemplate.opsForHash().put(tokenKey, "freeSpace", String.valueOf(user.getFreeSpace() - fileSize));
    }
}
