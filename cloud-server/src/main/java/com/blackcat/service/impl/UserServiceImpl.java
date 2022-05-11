package com.blackcat.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.blackcat.common.utils.Result;
import com.blackcat.common.utils.UserHolder;
import com.blackcat.common.utils.constant.StatusCode;
import com.blackcat.common.dto.UserDTO;
import com.blackcat.dao.mapper.UserMapper;
import com.blackcat.dao.pojo.User;
import com.blackcat.service.IUserService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.blackcat.common.utils.constant.DatabaseConstant.*;
import static com.blackcat.common.utils.constant.MessageConstant.*;
import static com.blackcat.common.utils.constant.RedisConstant.LOGIN_USER_KEY;
import static com.blackcat.common.utils.constant.RedisConstant.LOGIN_USER_TTL;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedisTemplate<String, String> redisTemplate;
    static final String key_pre="CHECK_CODE";
    @Override
    public Result login(Map<String, Object> loginFrom) {
        String accountId = (String) loginFrom.get("accountId");
        String password = (String) loginFrom.get("password");
        if (accountId.isEmpty() || password.isEmpty()) {
            return new Result(StatusCode.LOGIN_ERROR, LOGIN_ERROR2);
        }
        User user = query()
                .eq(ACCOUNT_ID, accountId)
                .eq(PASSWORD, password)
                .one();
        if (user != null) {
            String token = UUID.randomUUID().toString(true);
            UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
            Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),  // 将对象转为map
                    CopyOptions.create()
                            .setIgnoreNullValue(true)
                            .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString())
            );
            String tokenKey = LOGIN_USER_KEY + token;
            stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
            stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
            return new Result(StatusCode.OK, LOGIN_SUCCESS, token);
        } else {
            return new Result(StatusCode.LOGIN_ERROR, LOGIN_ERROR);
        }
    }

    @Override
    public Result register(Map<String, Object> registerFrom) {
        // 获取用户id和密码
        String accountId = (String) registerFrom.get("accountId");
        String password = (String) registerFrom.get("password");
        // 判断用户id和密码是否合法
        if (accountId.isEmpty() || accountId.length() > 20) {
            return new Result(StatusCode.ERROR, ACCOUNT_ID_ERROR);
        }
        if (password.length() < 6 || password.length() > 15) {
            return new Result(StatusCode.ERROR, PASSWORD_ERROR);
        }
        // 查询用户id是否存在
        if (query().eq(ACCOUNT_ID, accountId)
                .one() != null) {
            return new Result(StatusCode.ERROR, ACCOUNT_ID_ERROR2);
        }
        // 创建用户
        User user = new User();
        user.setAccountId(accountId);
        user.setPassword(password);
        if (save(user)) {
            return new Result(StatusCode.OK, REGISTER_SUCCESS);
        }
        return new Result(StatusCode.ERROR, REGISTER_ERROR);
    }

    @Override
    public Result getUserInfo() {
        UserDTO user = UserHolder.getUser();
        user.setAccountId(null);
        return new Result(StatusCode.OK, OPERATION_SUCCESS, user);
    }


}
