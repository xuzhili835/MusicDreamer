package com.musicdreamer.media.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("media_task")
public class MediaTask {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String taskType;   // DOWNLOAD/TRANSCRIBE/LOUDNESS/SUBTITLE/MODEL_DOWNLOAD/TOOL_UPDATE
    private String status;     // PENDING/RUNNING/SUCCESS/FAILED/CANCELLED
    private Integer progress;  // 0-100
    private String stage;      // 中文阶段描述
    private String sourceUrl;
    @TableField("song_id")
    private Long songId;
    private Long operator;
    private String error;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("finished_at")
    private LocalDateTime finishedAt;
}
