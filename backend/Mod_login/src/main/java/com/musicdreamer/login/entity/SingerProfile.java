package com.musicdreamer.login.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 歌手资料表（init.sql：singer_profile）。 */
@Data
@TableName("singer_profile")
public class SingerProfile {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String stageName;

    private String bio;

    private String backgroundImage;

    private LocalDateTime verifiedDate;

    private Integer fansCount;

    private Long totalPlays;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
