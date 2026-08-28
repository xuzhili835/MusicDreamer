package com.musicdreamer.media.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 求歌申请：识曲/手动提交 → 管理员 bilisearch 选定下载入库后自动回填。 */
@Data
@TableName("song_request")
public class SongRequest {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("user_id")
    private Long userId;
    private String title;
    private String artist;
    @TableField("cover_url")
    private String coverUrl;
    private Integer source;   // 0手动 1识曲 2外置识别
    private Integer status;   // 0待处理 1已入库 2已拒绝
    @TableField("result_song_id")
    private Long resultSongId;
    @TableField("reject_reason")
    private String rejectReason;
    @TableField("handled_by")
    private Long handledBy;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("handled_at")
    private LocalDateTime handledAt;
}
