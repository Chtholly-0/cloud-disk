package com.blackcat.web.controller;

import com.blackcat.common.utils.Result;
import com.blackcat.service.impl.UserServiceImpl;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Map;

@RestController
@RequestMapping("/user")
public class UserController {
    @Resource
    private UserServiceImpl userService;

    @RequestMapping("/login")
    public Result login(@RequestBody Map<String, Object> loginFrom) {

        return userService.login(loginFrom);
    }

    @RequestMapping("/register")
    public Result register(@RequestBody Map<String, Object> registerFrom) {
        return userService.register(registerFrom);
    }

    @GetMapping("/info")
    public Result getUserInfo() {
        return userService.getUserInfo();
    }


}
