package com.example.demo.controller;

import com.example.demo.support.DemoContextService;
import com.example.demo.utils.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 用户认证与资料接口。
 */
@RestController
@RequestMapping("/user")
public class UserController {

    private final DemoContextService demoContextService;

    public UserController(DemoContextService demoContextService) {
        this.demoContextService = demoContextService;
    }

    @PostMapping("/login/password")
    public Result loginByPassword(@RequestBody Map<String, Object> body) {
        String phone = String.valueOf(body.getOrDefault("phone", "")).trim();
        String password = String.valueOf(body.getOrDefault("password", "")).trim();
        return Result.success(demoContextService.loginByPassword(phone, password));
    }

    @PostMapping("/login/code")
    public Result loginByCode(@RequestBody Map<String, Object> body) {
        String phone = String.valueOf(body.getOrDefault("phone", "")).trim();
        String code = String.valueOf(body.getOrDefault("code", "")).trim();
        return Result.success(demoContextService.loginByCode(phone, code));
    }

    @PostMapping("/send/code")
    public Result sendCode(@RequestBody Map<String, Object> body) {
        String phone = String.valueOf(body.getOrDefault("phone", "")).trim();
        demoContextService.sendLoginCode(phone);
        return Result.success();
    }

    @PostMapping("/register")
    public Result register(@RequestBody Map<String, Object> body) {
        String phone = String.valueOf(body.getOrDefault("phone", "")).trim();
        String password = String.valueOf(body.getOrDefault("password", "")).trim();
        return Result.success(demoContextService.register(phone, password));
    }

    @GetMapping("/profile")
    public Result profile(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return Result.success(demoContextService.getUserProfile(authorization));
    }

    // 兼容旧接口
    @PostMapping("/login")
    public Result login(@RequestBody Map<String, Object> body) {
        return loginByPassword(body);
    }

    // 兼容旧接口
    @GetMapping("/info")
    public Result info(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return profile(authorization);
    }
}
