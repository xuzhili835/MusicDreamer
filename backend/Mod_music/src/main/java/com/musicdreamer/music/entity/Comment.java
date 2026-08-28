package com.musicdreamer.music.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 歌曲评论表（两级结构，逻辑删 status=0）。 */
@Data
@TableName("comment")
public class Comment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long songId;

    private Long userId;

    /** 父评论ID，顶层评论为 NULL。 */
    private Long parentId;

    private String content;

    private Integer likeCount;

    /** 0 删除 / 1 正常 / 2 审核中。 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
