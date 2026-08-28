package com.musicdreamer.setting.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 系统配置表（init.sql：sys_setting）。cfg_value 以 JSON 字符串存储（如 "\"-16\""）。 */
@Data
@TableName("sys_setting")
public class SysSetting {

    /** 配置键为主键（非自增）。 */
    @TableId(value = "cfg_key", type = IdType.INPUT)
    private String cfgKey;

    private String cfgValue;

    private LocalDateTime updatedAt;
}
