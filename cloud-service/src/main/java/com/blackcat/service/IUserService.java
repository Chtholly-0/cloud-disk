package com.blackcat.service;

import com.blackcat.common.utils.Result;

import java.util.Map;

public interface IUserService {
    Result login(Map<String, Object> loginInfo);

    Result register(Map<String, Object> registerFrom);

    Result getUserInfo();
}
