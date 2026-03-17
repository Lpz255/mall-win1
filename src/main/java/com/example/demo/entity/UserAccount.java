package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户账号实体。
 */
@Data
@TableName("user_account")
public class UserAccount implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户ID。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 手机号。 */
    @TableField("phone")
    private String phone;

    /** 登录密码。 */
    @TableField("password")
    private String password;

    /** 昵称。 */
    @TableField("name")
    private String name;

    /** 用户等级。 */
    @TableField("user_level")
    private String userLevel;

    /** 用户状态（enabled/disabled）。 */
    @TableField("status")
    private String status;

    /** 注册时间。 */
    @TableField("register_time")
    private LocalDateTime registerTime;

    /** 最近登录时间。 */
    @TableField("last_login_time")
    private LocalDateTime lastLoginTime;
}
