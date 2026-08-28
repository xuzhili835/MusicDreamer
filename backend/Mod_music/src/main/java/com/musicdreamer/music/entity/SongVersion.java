package com.musicdreamer.music.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 歌曲版本表。status 语义与主表不同：0 已废弃 / 1 生效中 / 2 审核中。 */
@Data
@TableName("song_version")
public class SongVersion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long songId;

    private Integer version;

    private String fileUrl;

    private String fileFormat;

    private Long fileSize;

    private Integer duration;

    /** 0 已废弃 / 1 生效中 / 2 审核中。 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime auditTime;

    private Long auditorId;
}
