package com.musicdreamer.music.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 歌曲表。审核状态机：0 已下架 / 1 审核中 / 2 已发布（设计 6.1 节）。 */
@Data
@TableName("song")
public class Song {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /** 歌手用户ID。 */
    private Long singerId;

    /** 导入歌曲的原始歌手名（管理员从外站导入时填写，展示时优先于注册歌手昵称）。 */
    private String singerName;

    private String album;

    private String style;

    private String language;

    /** 时长（秒）。 */
    private Integer duration;

    private String fileUrl;

    private String coverUrl;

    private String lyricUrl;

    /** 在线词库记录 id（LRCLIB；本地来源为空）。 */
    private Long lyricSourceId;

    /** 在线词库原始记录链接。 */
    private String lyricSourceUrl;

    private String fileFormat;

    private Long playCount;

    private Integer collectCount;

    /** 0 已下架 / 1 审核中 / 2 已发布。 */
    private Integer status;

    /** 驳回或下架原因。 */
    private String rejectReason;

    private Long auditorId;

    private LocalDateTime auditTime;

    /** 下载来源 URL（查重）。 */
    private String sourceUrl;

    /** 响度补偿增益 dB（未分析为空）。 */
    private Double volumeGain;

    private Double integratedLoudness;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
