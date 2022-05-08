package com.blackcat.common.config.interceptor;

import cn.hutool.json.JSONUtil;
import com.blackcat.common.utils.Result;
import com.blackcat.common.utils.UserHolder;
import com.blackcat.common.utils.constant.StatusCode;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;

import static com.blackcat.common.utils.constant.MessageConstant.NOT_LOGIN;

public class LoginInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1.判断是否需要拦截（ThreadLocal中是否有用户）
        if (UserHolder.getUser() == null) {
            // 没有，需要拦截，设置状态码
            returnJson(response, new Result(StatusCode.LOGIN_ERROR, NOT_LOGIN));
            // 拦截
            return false;
        }
        // 有用户，则放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }

    private void returnJson(HttpServletResponse response, Object data) {
        /* 设置contentType为json */
        response.setContentType("application/json");
        /* 设置编码为utf-8 */
        response.setCharacterEncoding("UTF-8");

        String json = JSONUtil.toJsonStr(data);
        try {
            response.getWriter().write(json);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
