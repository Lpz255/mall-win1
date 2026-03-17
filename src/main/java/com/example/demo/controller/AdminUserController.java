package com.example.demo.controller;

import com.example.demo.support.DemoContextService;
import com.example.demo.utils.BusinessException;
import com.example.demo.utils.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 后台用户管理接口。
 */
@RestController
@RequestMapping("/admin/user")
public class AdminUserController {

    private final DemoContextService demoContextService;

    public AdminUserController(DemoContextService demoContextService) {
        this.demoContextService = demoContextService;
    }

    @GetMapping("/list")
    public Result list(@RequestHeader(value = "Authorization", required = false) String authorization,
                       @RequestParam Map<String, Object> params) {
        demoContextService.getAdminPermissions(authorization);

        int pageNum = Math.max(DemoContextService.toInt(params.get("pageNum"), 1), 1);
        int pageSize = Math.max(DemoContextService.toInt(params.get("pageSize"), 10), 1);
        String keyword = String.valueOf(params.getOrDefault("keyword", "")).trim();
        String status = String.valueOf(params.getOrDefault("status", "")).trim();

        List<Map<String, Object>> rows = demoContextService.listUsers(keyword, status, pageNum, pageSize);
        long total = demoContextService.countUsers(keyword, status);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", rows);
        data.put("records", rows);
        data.put("total", total);
        data.put("count", total);
        return Result.success(data);
    }

    @PostMapping("/status")
    public Result status(@RequestHeader(value = "Authorization", required = false) String authorization,
                         @RequestBody Map<String, Object> body) {
        demoContextService.getAdminPermissions(authorization);

        Long userId = DemoContextService.toLong(body.get("id"));
        String status = String.valueOf(body.getOrDefault("status", "")).trim();
        if (userId == null || userId <= 0) {
            throw new BusinessException(400, "用户ID不能为空");
        }
        if (!"enabled".equalsIgnoreCase(status) && !"disabled".equalsIgnoreCase(status)) {
            throw new BusinessException(400, "用户状态不合法");
        }
        demoContextService.toggleUserStatus(userId, status);
        return Result.success();
    }
}
