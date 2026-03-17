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
 * 后台认证接口。
 */
@RestController
@RequestMapping("/admin")
public class AdminAuthController {

    private final DemoContextService demoContextService;

    public AdminAuthController(DemoContextService demoContextService) {
        this.demoContextService = demoContextService;
    }

    @PostMapping("/login")
    public Result login(@RequestBody Map<String, Object> body) {
        String username = String.valueOf(body.getOrDefault("username", "")).trim();
        String password = String.valueOf(body.getOrDefault("password", "")).trim();
        return Result.success(demoContextService.adminLogin(username, password));
    }

    @GetMapping("/profile")
    public Result profile(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return Result.success(demoContextService.getAdminProfile(authorization));
    }

    @GetMapping("/rbac/permissions")
    public Result permissions(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return Result.success(demoContextService.getAdminPermissions(authorization));
    }
}
