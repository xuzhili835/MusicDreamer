package com.musicdreamer.music.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 播放历史表（播完才计），同时是协同过滤评分数据源。 */
@Data
@TableName("play_history")
public class PlayHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long songId;

    private Integer playDuration;

    /** 是否播完（0 否 1 是）。 */
    private Integer playComplete;

    private String deviceType;

    private String ipAddress;

    private LocalDateTime playedAt;
}
