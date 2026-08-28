package com.musicdreamer.songlist.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 专辑表。 */
@Data
@TableName("album")
public class Album {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 发布歌手/管理员用户ID。 */
    private Long userId;

    private String name;

    private String description;

    /** 封面图片URL。 */
    private String coverUrl;

    /** 是否公开(0未发布 1已发布)。 */
    private Boolean isPublic;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
