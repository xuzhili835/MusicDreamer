package com.musicdreamer.songlist.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 歌单表。 */
@Data
@TableName("playlist")
public class Playlist {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 创建用户ID。 */
    private Long userId;

    /** 歌单名称。 */
    private String name;

    /** 歌单描述。 */
    private String description;

    /** 封面图片URL。 */
    private String coverUrl;

    /** 是否公开(0私有 1公开)。 */
    private Boolean isPublic;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
