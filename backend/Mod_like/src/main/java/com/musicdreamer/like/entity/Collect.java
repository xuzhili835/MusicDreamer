package com.musicdreamer.like.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 歌曲收藏表（uk: user_id + song_id）。 */
@Data
@TableName("collect")
public class Collect {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID。 */
    private Long userId;

    /** 歌曲ID。 */
    private Long songId;

    private LocalDateTime createTime;
}
