package com.musicdreamer.music.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 评论视图：顶层评论挂子评论列表（两级结构）。 */
@Data
public class CommentVO {

    private Long id;

    private Long songId;

    private Long userId;

    private String nickname;

    private String avatar;

    private Long parentId;

    private String content;

    private Integer likeCount;

    private LocalDateTime createTime;

    /** 子评论（时间正序）。 */
    private List<CommentVO> children;
}
